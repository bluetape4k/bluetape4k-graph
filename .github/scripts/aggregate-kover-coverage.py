#!/usr/bin/env python3
"""
Aggregate Kover XML reports and print a module coverage table to GitHub Step Summary.

Usage:
    aggregate-kover-coverage.py [coverage-root]
        [--expected-artifact NAME ...] [--expected-module NAME ...]

Each module's report.xml (or reportJvm.xml) is parsed for exactly one
report-level INSTRUCTION counter. Invalid reports and missing expected
artifacts or modules fail the command instead of being reported as zero
coverage.
"""
from __future__ import annotations

import argparse
import glob
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


class CoverageReportError(ValueError):
    """Kover report or artifact contract cannot be safely aggregated."""


def parse_report(path: str) -> tuple[int, int]:
    """Return (covered, missed) for one valid INSTRUCTION counter."""
    try:
        root = ET.parse(path).getroot()
    except (ET.ParseError, OSError) as error:
        raise CoverageReportError(f"{path}: malformed Kover XML ({error})") from error

    counters = [counter for counter in root.findall("counter") if counter.get("type") == "INSTRUCTION"]
    if len(counters) != 1:
        raise CoverageReportError(
            f"{path}: exactly one INSTRUCTION counter is required (found {len(counters)})"
        )

    counter = counters[0]
    try:
        covered = int(counter.get("covered", ""))
        missed = int(counter.get("missed", ""))
    except (TypeError, ValueError) as error:
        raise CoverageReportError(f"{path}: INSTRUCTION counter values must be integers") from error
    if covered < 0 or missed < 0:
        raise CoverageReportError(f"{path}: INSTRUCTION counter values must be non-negative")
    return covered, missed


def module_from_path(root_dir: str, path: str) -> str:
    # Layout after artifact download (merge-multiple=false):
    #   <root>/coverage-<area>/<base-dir>/<module-dir>/build/reports/kover/report.xml
    # For most modules, module-dir IS the Gradle project name (e.g. graph-core).
    # For graph-io/* modules (withBaseDir=true in settings.gradle.kts), the leaf dir
    # is a short name (core, csv, …) and the module name is graph-io-<leaf>.
    rel = os.path.relpath(path, root_dir)
    parts = rel.split(os.sep)
    for i in range(len(parts) - 1, -1, -1):
        if parts[i] == "build" and i >= 1:
            leaf = parts[i - 1]
            if i >= 2 and parts[i - 2] == "graph-io":
                return "graph-io-" + leaf
            return leaf
    return os.path.basename(os.path.dirname(os.path.dirname(path)))


def expected_artifact_path(root_dir: Path, name: str) -> Path:
    """Resolve one workflow-owned artifact directory without path traversal."""
    relative = Path(name)
    if relative.is_absolute() or len(relative.parts) != 1 or relative.name != name:
        raise CoverageReportError(f"{root_dir}: invalid expected coverage artifact name: {name}")
    try:
        root_resolved = root_dir.resolve()
        candidate = (root_dir / relative).resolve()
    except (OSError, RuntimeError) as error:
        raise CoverageReportError(
            f"{root_dir}: cannot resolve expected coverage artifact {name} ({error})"
        ) from error
    if candidate.parent != root_resolved:
        raise CoverageReportError(f"{root_dir}: invalid expected coverage artifact name: {name}")
    return candidate


def report_path_inside_root(root_dir: Path, path: str) -> bool:
    """Reject report symlinks that resolve outside the downloaded artifact root."""
    try:
        Path(path).resolve().relative_to(root_dir.resolve())
    except ValueError:
        return False
    except (OSError, RuntimeError) as error:
        raise CoverageReportError(f"{path}: cannot resolve report path ({error})") from error
    return True


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root_dir", nargs="?", default="coverage-artifacts")
    parser.add_argument("--expected-artifact", action="append", default=[])
    parser.add_argument("--expected-module", action="append", default=[])
    args = parser.parse_args(argv)
    root_dir = args.root_dir
    expected_artifacts = args.expected_artifact
    expected_modules = args.expected_module
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")

    patterns = [
        f"{root_dir}/**/report.xml",
        f"{root_dir}/**/reportJvm.xml",
    ]

    rows: list[tuple[str, int, int, float]] = []
    total_covered = 0
    total_missed = 0
    errors: list[str] = []

    report_paths_by_module: dict[str, str] = {}
    for pattern in patterns:
        for xml_path in sorted(glob.glob(pattern, recursive=True)):
            try:
                inside_root = report_path_inside_root(Path(root_dir), xml_path)
            except CoverageReportError as error:
                errors.append(str(error))
                continue
            if not inside_root:
                errors.append(f"{xml_path}: report path resolves outside coverage root")
                continue
            module = module_from_path(root_dir, xml_path)
            previous_path = report_paths_by_module.get(module)
            if previous_path is not None:
                errors.append(
                    f"{module}: duplicate Kover reports are not allowed ({previous_path}, {xml_path})"
                )
                continue
            report_paths_by_module[module] = xml_path

            try:
                covered, missed = parse_report(xml_path)
            except CoverageReportError as error:
                errors.append(str(error))
                continue
            total = covered + missed
            pct = (covered * 100.0 / total) if total else 0.0
            rows.append((module, covered, missed, pct))
            total_covered += covered
            total_missed += missed

    artifact_root = Path(root_dir)
    artifact_dirs = (
        sorted(path for path in artifact_root.glob("coverage-*") if path.is_dir())
        if artifact_root.is_dir()
        else []
    )
    for artifact_dir in artifact_dirs:
        if not any(artifact_dir.glob("**/report.xml")) and not any(
            artifact_dir.glob("**/reportJvm.xml")
        ):
            errors.append(f"{artifact_dir}: no Kover XML report found")

    for expected_artifact in expected_artifacts:
        try:
            expected_path = expected_artifact_path(artifact_root, expected_artifact)
        except CoverageReportError as error:
            errors.append(str(error))
            continue
        if not expected_path.is_dir():
            errors.append(f"{expected_path}: expected coverage artifact is missing")
        elif not any(expected_path.glob("**/report.xml")) and not any(
            expected_path.glob("**/reportJvm.xml")
        ):
            errors.append(f"{expected_path}: expected Kover XML report is missing")

    present_modules = {module for module, _covered, _missed, _pct in rows}
    for expected_module in expected_modules:
        if not expected_module or "/" in expected_module or "\\" in expected_module:
            errors.append(f"{root_dir}: invalid expected coverage module name: {expected_module}")
        elif expected_module not in present_modules:
            errors.append(f"{root_dir}: expected Kover module is missing: {expected_module}")

    if not rows and not errors:
        errors.append(f"{root_dir}: no Kover XML reports found")

    lines: list[str] = []
    lines.append("## Kover Coverage Summary")
    lines.append("")
    if not rows:
        lines.append("_No coverage reports found._")
    else:
        lines.append("| Module | Instruction Covered | Instruction Missed | Coverage |")
        lines.append("|--------|--------------------:|-------------------:|---------:|")
        for module, covered, missed, pct in rows:
            lines.append(f"| `{module}` | {covered} | {missed} | {pct:.2f}% |")
        grand_total = total_covered + total_missed
        grand_pct = (total_covered * 100.0 / grand_total) if grand_total else 0.0
        lines.append(f"| **TOTAL** | **{total_covered}** | **{total_missed}** | **{grand_pct:.2f}%** |")
    if errors:
        lines.extend(["", "### Coverage validation errors", ""])
        lines.extend(f"- {error}" for error in errors)

    output = "\n".join(lines) + "\n"
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as fp:
            fp.write(output)
    print(output)
    if errors:
        if errors == [f"{root_dir}: no Kover XML reports found"]:
            print(f"error: no Kover XML reports found under {root_dir}", file=sys.stderr)
        else:
            print("error: coverage validation failed", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
