#!/usr/bin/env python3
import re
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("aggregate-kover-coverage.py")
ROOT = SCRIPT.parents[2]
WORKFLOWS = (
    ROOT / ".github/workflows/ci.yml",
    ROOT / ".github/workflows/nightly-tests.yml",
)


class AggregateKoverCoverageTest(unittest.TestCase):
    def run_script(self, root: Path, *arguments: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(root), *arguments],
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
        )

    def test_empty_coverage_root_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = self.run_script(Path(tmp))

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("_No coverage reports found._", result.stdout)
        self.assertIn("error: no Kover XML reports found", result.stderr)

    def test_report_xml_succeeds(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "coverage-core" / "graph" / "graph-core" / "build" / "reports" / "kover"
            report.mkdir(parents=True)
            (report / "report.xml").write_text(
                textwrap.dedent(
                    """\
                    <report>
                      <counter type="INSTRUCTION" missed="2" covered="8"/>
                    </report>
                    """
                ),
                encoding="utf-8",
            )

            result = self.run_script(Path(tmp))

        self.assertEqual(result.returncode, 0)
        self.assertIn("| `graph-core` | 8 | 2 | 80.00% |", result.stdout)
        self.assertEqual(result.stderr, "")

    def test_report_jvm_xml_succeeds_without_duplicate_rows(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = self.write_report(
                Path(tmp),
                '<report><counter type="INSTRUCTION" missed="1" covered="9"/></report>',
                name="reportJvm.xml",
            )
            result = self.run_script(report)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(result.stdout.count("| `graph-core` |"), 1)
        self.assertIn("| `graph-core` | 9 | 1 | 90.00% |", result.stdout)
        self.assertEqual(result.stderr, "")

    def test_report_xml_and_report_jvm_xml_for_one_module_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = self.write_report(
                Path(tmp),
                '<report><counter type="INSTRUCTION" missed="2" covered="8"/></report>',
            )
            report_dir = root / "coverage-core" / "graph" / "graph-core" / "build" / "reports" / "kover"
            (report_dir / "reportJvm.xml").write_text(
                '<report><counter type="INSTRUCTION" missed="1" covered="9"/></report>',
                encoding="utf-8",
            )
            result = self.run_script(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("duplicate Kover reports are not allowed", result.stdout + result.stderr)

    def test_explicit_zero_instruction_counter_is_not_a_parse_failure(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = self.write_report(
                Path(tmp),
                '<report><counter type="INSTRUCTION" missed="0" covered="0"/></report>',
            )
            result = self.run_script(report)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("| `graph-core` | 0 | 0 | 0.00% |", result.stdout)
        self.assertEqual(result.stderr, "")

    def test_malformed_report_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = self.write_report(Path(tmp), "<report>")
            result = self.run_script(report)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("malformed Kover XML", result.stdout + result.stderr)
        self.assertNotIn("| `graph-core` | 0 | 0 | 0.00% |", result.stdout)

    def test_missing_instruction_counter_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = self.write_report(
                Path(tmp),
                '<report><counter type="LINE" missed="2" covered="8"/></report>',
            )
            result = self.run_script(report)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exactly one INSTRUCTION counter is required", result.stdout + result.stderr)

    def test_duplicate_instruction_counters_fail_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = self.write_report(
                Path(tmp),
                """
                <report>
                  <counter type="INSTRUCTION" missed="2" covered="8"/>
                  <counter type="INSTRUCTION" missed="1" covered="9"/>
                </report>
                """,
            )
            result = self.run_script(report)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("exactly one INSTRUCTION counter is required", result.stdout + result.stderr)

    def test_invalid_instruction_counter_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = self.write_report(
                Path(tmp),
                '<report><counter type="INSTRUCTION" missed="oops" covered="8"/></report>',
            )
            result = self.run_script(report)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("INSTRUCTION counter values must be integers", result.stdout + result.stderr)

    def test_expected_artifact_missing_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = self.write_report(
                Path(tmp),
                '<report><counter type="INSTRUCTION" missed="2" covered="8"/></report>',
            )
            result = self.run_script(report, "--expected-artifact", "coverage-core", "--expected-artifact", "coverage-ktor")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("expected coverage artifact is missing", result.stdout + result.stderr)

    def test_expected_module_missing_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            report = self.write_report(
                Path(tmp),
                '<report><counter type="INSTRUCTION" missed="2" covered="8"/></report>',
            )
            result = self.run_script(
                report,
                "--expected-module",
                "graph-core",
                "--expected-module",
                "graph-neo4j",
            )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("expected Kover module is missing: graph-neo4j", result.stdout + result.stderr)

    def test_partial_valid_and_invalid_reports_fail_but_keep_valid_row(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            valid = self.write_report(
                root,
                '<report><counter type="INSTRUCTION" missed="2" covered="8"/></report>',
            )
            invalid_report = root / "coverage-ktor" / "ktor" / "ktor-core" / "build" / "reports" / "kover"
            invalid_report.mkdir(parents=True)
            (invalid_report / "report.xml").write_text("<report>", encoding="utf-8")
            result = self.run_script(valid)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("| `graph-core` | 8 | 2 | 80.00% |", result.stdout)
        self.assertIn("malformed Kover XML", result.stdout + result.stderr)

    def test_expected_artifact_path_traversal_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            result = self.run_script(Path(tmp), "--expected-artifact", "../outside")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("invalid expected coverage artifact name", result.stdout + result.stderr)

    def test_report_symlink_outside_root_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp, tempfile.TemporaryDirectory() as outside:
            root = Path(tmp)
            report = Path(outside) / "report.xml"
            report.write_text(
                '<report><counter type="INSTRUCTION" missed="1" covered="9"/></report>',
                encoding="utf-8",
            )
            report_dir = root / "coverage-core" / "graph" / "graph-core" / "build" / "reports" / "kover"
            report_dir.mkdir(parents=True)
            try:
                (report_dir / "report.xml").symlink_to(report)
            except (OSError, NotImplementedError) as error:
                self.skipTest(f"symlink fixture unavailable: {error}")

            result = self.run_script(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("resolves outside coverage root", result.stdout + result.stderr)

    def test_report_symlink_loop_fails_closed_with_summary_error(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report_dir = root / "coverage-core" / "graph" / "graph-core" / "build" / "reports" / "kover"
            report_dir.mkdir(parents=True)
            loop = report_dir / "report.xml"
            try:
                loop.symlink_to(loop)
            except (OSError, NotImplementedError) as error:
                self.skipTest(f"symlink fixture unavailable: {error}")

            result = self.run_script(root)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("cannot resolve report path", result.stdout)
        self.assertIn("Coverage validation errors", result.stdout)

    def test_workflows_fail_closed_for_coverage_artifacts(self) -> None:
        for workflow in WORKFLOWS:
            source = workflow.read_text(encoding="utf-8")
            coverage_job = self.named_job(source, "coverage-report")
            self.assertIn("--expected-artifact", coverage_job, workflow)
            for module in self.expected_modules(workflow):
                self.assertIn(f"--expected-module {module}", coverage_job, (workflow, module))
            self.assertNotIn("continue-on-error: true", coverage_job, workflow)

            upload_steps = self.named_steps(source, "Upload coverage report")
            self.assertEqual(7, len(upload_steps), workflow)
            for step in upload_steps:
                self.assertIn("if-no-files-found: error", step, workflow)

    def test_kover_generation_steps_remain_report_only(self) -> None:
        for workflow in WORKFLOWS:
            source = workflow.read_text(encoding="utf-8")
            report_steps = self.named_steps(source, "Generate Kover XML report")
            self.assertEqual(7, len(report_steps), workflow)
            for step in report_steps:
                self.assertIn("continue-on-error: true", step, workflow)

    @staticmethod
    def write_report(root: Path, contents: str, name: str = "report.xml") -> Path:
        report = root / "coverage-core" / "graph" / "graph-core" / "build" / "reports" / "kover"
        report.mkdir(parents=True)
        (report / name).write_text(contents, encoding="utf-8")
        return root

    @staticmethod
    def named_job(source: str, job_name: str) -> str:
        match = re.search(
            rf"^  {re.escape(job_name)}:\n.*?(?=^  [A-Za-z0-9_-]+:\s*$|\Z)",
            source,
            flags=re.MULTILINE | re.DOTALL,
        )
        if match is None:
            raise AssertionError(f"job not found: {job_name}")
        return match.group(0)

    @staticmethod
    def named_steps(source: str, step_name: str) -> list[str]:
        return re.findall(
            rf"^      - name: {re.escape(step_name)}\n.*?(?=^      - name:|\Z)",
            source,
            flags=re.MULTILINE | re.DOTALL,
        )

    @staticmethod
    def expected_modules(workflow: Path) -> tuple[str, ...]:
        # Both workflows cover the same production module manifest; CI adds
        # only the expected-artifact skip guards around this list.
        return (
            "graph-core",
            "graph-tinkerpop",
            "graph-io-core",
            "graph-io-csv",
            "graph-io-jackson2",
            "graph-io-jackson3",
            "graph-io-graphml",
            "graph-io-micrometer",
            "graph-io-okio",
            "graph-spring-boot",
            "graph-ktor",
            "graph-neo4j",
            "graph-memgraph",
            "graph-age",
            "graph-falkordb",
        )


if __name__ == "__main__":
    unittest.main()
