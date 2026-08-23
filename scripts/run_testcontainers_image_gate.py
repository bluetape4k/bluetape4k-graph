#!/usr/bin/env python3
"""Run graph Testcontainers image families sequentially with fail-closed evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shlex
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, Iterable, List, Optional, Sequence

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.testcontainers_image_gate import (
    MANIFEST,
    load_manifest,
    load_shared_paths,
    select_entries,
    validate_manifest,
)


MAX_ATTEMPTS = 3
MAX_OUTPUT_CHARS = 12_000
RATE_LIMIT_MARKERS = (
    "toomanyrequests",
    "rate limit",
    "429 too many requests",
    "pull rate limit",
)
PULL_FAILURE_MARKERS = (
    "pull access denied",
    "manifest unknown",
    "not found",
    "failed to resolve source metadata",
    "repository does not exist",
)
READINESS_MARKERS = (
    "timed out waiting for container",
    "container startup failed",
    "container did not start",
    "wait strategy",
    "startup check strategy",
    "readiness",
    "port.*not.*open",
    "containerlaunchexception",
    "connection refused",
)
INFRASTRUCTURE_MARKERS = (
    "cannot connect to the docker daemon",
    "docker daemon",
    "no space left on device",
    "connection reset",
    "broken pipe",
    "eof",
    "runner exception",
)
SECRET_PATTERN = re.compile(
    r"(?i)(password|passwd|token|secret|authorization|api[_-]?key)\s*[:=]\s*([^\s,;]+)"
)
BEARER_PATTERN = re.compile(r"(?i)(bearer\s+)[^\s,;]+")

CommandRunner = Callable[[List[str], int], Any]
DiagnosticRunner = Callable[[Dict[str, Any]], Dict[str, str]]


def redact(value: str) -> str:
    value = BEARER_PATTERN.sub(r"\1<redacted>", value)
    return SECRET_PATTERN.sub(r"\1=<redacted>", value)


def _bounded(value: Any) -> str:
    text = redact(str(value or ""))
    if len(text) <= MAX_OUTPUT_CHARS:
        return text
    return text[:MAX_OUTPUT_CHARS] + "\n...[truncated]"


def classify_failure(returncode: Optional[int], stdout: str, stderr: str) -> str:
    """Classify pull/rate-limit/readiness/infrastructure/product failures separately."""

    if returncode == 0:
        return "success"
    haystack = (stdout + "\n" + stderr).lower()
    if any(marker in haystack for marker in RATE_LIMIT_MARKERS):
        return "pull_rate_limit"
    if any(marker in haystack for marker in PULL_FAILURE_MARKERS) and (
        "pull" in haystack or "image" in haystack or "docker" in haystack
    ):
        return "image_pull_failure"
    if any(marker in haystack for marker in INFRASTRUCTURE_MARKERS):
        return "infrastructure_failure"
    if returncode is None or any(re.search(marker, haystack) for marker in READINESS_MARKERS):
        return "readiness_timeout"
    return "application_failure"


def _subprocess_runner(command: List[str], timeout_seconds: int) -> Any:
    try:
        return subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout_seconds,
            cwd=str(ROOT),
        )
    except subprocess.TimeoutExpired as error:
        return type(
            "TimeoutResult",
            (),
            {
                "returncode": None,
                "stdout": error.stdout or "",
                "stderr": (error.stderr or "") + " timeout",
            },
        )()
    except OSError as error:
        return type(
            "RunnerErrorResult",
            (),
            {
                "returncode": 127,
                "stdout": "",
                "stderr": "runner exception: %s" % error,
            },
        )()


def _command_text(result: Any) -> str:
    return _bounded(
        "exit=%s\nstdout=%s\nstderr=%s"
        % (
            getattr(result, "returncode", None),
            getattr(result, "stdout", ""),
            getattr(result, "stderr", ""),
        )
    )


def _stdout_from_command_text(value: str) -> str:
    if "stdout=" not in value:
        return ""
    output = value.split("stdout=", 1)[1]
    return output.split("\nstderr=", 1)[0]


class GateRunner:
    """Run each selected family in order and write machine/human evidence."""

    def __init__(
        self,
        entries: Iterable[Dict[str, Any]],
        report_dir: Path,
        command_runner: CommandRunner = _subprocess_runner,
        diagnostic_runner: Optional[DiagnosticRunner] = None,
        gradle_command: str = "./gradlew",
        max_attempts: int = 3,
        timeout_minutes: int = 30,
        manifest_path: Optional[Path] = None,
    ) -> None:
        self.entries = [dict(entry) for entry in entries]
        self.report_dir = report_dir
        self.command_runner = command_runner
        self.diagnostic_runner = diagnostic_runner or self._collect_diagnostics
        self.gradle_command = shlex.split(gradle_command)
        self.max_attempts = max(1, min(max_attempts, MAX_ATTEMPTS))
        self.timeout_seconds = max(60, timeout_minutes * 60)
        self.manifest_digest = self._manifest_digest(manifest_path)

    def _manifest_digest(self, manifest_path: Optional[Path]) -> str:
        path = manifest_path or MANIFEST
        if path.is_file():
            return hashlib.sha256(path.read_bytes()).hexdigest()
        canonical = json.dumps(self.entries, ensure_ascii=False, sort_keys=True).encode("utf-8")
        return hashlib.sha256(canonical).hexdigest()

    def _command(self, entry: Dict[str, Any]) -> List[str]:
        return self.gradle_command + [
            str(entry["testTask"]),
            "--tests",
            str(entry["testPattern"]),
            "--no-daemon",
            "--no-configuration-cache",
            "--rerun-tasks",
            "--console=plain",
        ]

    def _diagnostic_call(self, command: List[str]) -> str:
        try:
            result = self.command_runner(command, min(self.timeout_seconds, 60))
            return _command_text(result)
        except Exception as error:  # diagnostics must never hide the product result
            return _bounded("diagnostic exception: %s" % error)

    def _collect_diagnostics(self, entry: Dict[str, Any]) -> Dict[str, str]:
        image_ref = "%s:%s" % (entry["image"], entry["tag"])
        image_inspect = self._diagnostic_call(["docker", "image", "inspect", image_ref])
        diagnostics: Dict[str, str] = {
            "image_ref": image_ref,
            "image_inspect": image_inspect,
            "docker_events": self._diagnostic_call(
                ["docker", "events", "--since", "15m", "--until", "now"]
            ),
        }
        digest = ""
        try:
            result = json.loads(_stdout_from_command_text(image_inspect))
            if isinstance(result, list) and result:
                data = result[0]
                digests = data.get("RepoDigests") or []
                digest = digests[0] if digests else data.get("Id", "")
        except (ValueError, TypeError, KeyError, IndexError):
            digest = ""
        diagnostics["image_digest"] = _bounded(digest)

        ps = self._diagnostic_call(
            ["docker", "ps", "-a", "--no-trunc", "--filter", "ancestor=%s" % image_ref, "--format", "{{.ID}}"]
        )
        diagnostics["docker_ps"] = ps
        container_ids = [line.strip() for line in _stdout_from_command_text(ps).splitlines() if line.strip()]
        for index, container_id in enumerate(container_ids[:10]):
            diagnostics["container_%d_inspect" % index] = self._diagnostic_call(
                ["docker", "inspect", container_id]
            )
            diagnostics["container_%d_logs" % index] = self._diagnostic_call(
                ["docker", "logs", "--tail", "500", container_id]
            )
        return diagnostics

    def _run_family(self, entry: Dict[str, Any]) -> Dict[str, Any]:
        command = self._command(entry)
        attempts: List[Dict[str, Any]] = []
        first_failure: Optional[str] = None
        final_status = "blocked"
        for attempt in range(1, self.max_attempts + 1):
            started = time.monotonic()
            result = self.command_runner(command, self.timeout_seconds)
            elapsed = round(time.monotonic() - started, 3)
            raw_stdout = str(getattr(result, "stdout", "") or "")
            raw_stderr = str(getattr(result, "stderr", "") or "")
            returncode = getattr(result, "returncode", None)
            status = classify_failure(returncode, raw_stdout, raw_stderr)
            if status == "success" and "BUILD SUCCESSFUL" not in (raw_stdout + raw_stderr):
                status = "blocked"
            if status != "success" and first_failure is None:
                first_failure = status
            attempts.append(
                {
                    "attempt": attempt,
                    "command": redact(" ".join(command)),
                    "returncode": returncode,
                    "elapsed_seconds": elapsed,
                    "status": status,
                    "stdout": _bounded(raw_stdout),
                    "stderr": _bounded(raw_stderr),
                }
            )
            if status == "success":
                final_status = "success" if attempt == 1 else "success_after_retry"
                break
            final_status = status

        result_payload: Dict[str, Any] = {
            "id": entry["id"],
            "image": entry["image"],
            "tag": entry["tag"],
            "test_task": entry["testTask"],
            "test_pattern": entry["testPattern"],
            "readiness": entry["readiness"],
            "workload": entry["workload"],
            "release_required": bool(entry["releaseRequired"]),
            "status": final_status,
            "first_failure": first_failure,
            "attempts": attempts,
        }
        if first_failure is not None:
            try:
                result_payload["diagnostics"] = self.diagnostic_runner(entry)
            except Exception as error:
                result_payload["diagnostics"] = {"diagnostic_error": _bounded(error)}
        return result_payload

    def run(self) -> Dict[str, Any]:
        self.report_dir.mkdir(parents=True, exist_ok=True)
        results: List[Dict[str, Any]] = []
        for entry in self.entries:
            result = self._run_family(entry)
            results.append(result)
            (self.report_dir / (str(entry["id"]) + ".json")).write_text(
                json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )

        statuses = [str(result["status"]) for result in results]
        summary: Dict[str, Any] = {
            "schema_version": 1,
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "manifest_digest": self.manifest_digest,
            "selected": len(results),
            "success": statuses.count("success"),
            "success_after_retry": statuses.count("success_after_retry"),
            "readiness_timeout": sum(result["first_failure"] == "readiness_timeout" for result in results),
            "pull_rate_limit": sum(result["first_failure"] == "pull_rate_limit" for result in results),
            "image_pull_failure": sum(result["first_failure"] == "image_pull_failure" for result in results),
            "infrastructure_failure": sum(result["first_failure"] == "infrastructure_failure" for result in results),
            "application_failure": sum(result["first_failure"] == "application_failure" for result in results),
            "blocked": sum(result["status"] == "blocked" for result in results),
            "coverage": "%d/%d" % (statuses.count("success"), len(results)),
            "release_gate": bool(results)
            and all(result["status"] == "success" and result["release_required"] for result in results),
            "status": "skipped" if not results else ("success" if all(status == "success" for status in statuses) else "failed"),
            "results": results,
        }
        (self.report_dir / "summary.json").write_text(
            json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        self._write_markdown(summary)
        return summary

    def _write_markdown(self, summary: Dict[str, Any]) -> None:
        lines = [
            "# Graph Testcontainers image family gate",
            "",
            "- 상태: `%s`" % summary["status"],
            "- 선택/첫 시도 성공: `%s`" % summary["coverage"],
            "- retry 후 성공(게이트 차단): `%s`" % summary["success_after_retry"],
            "- readiness timeout: `%s`" % summary["readiness_timeout"],
            "- pull/rate-limit: `%s/%s`" % (summary["pull_rate_limit"], summary["image_pull_failure"]),
            "- application failure: `%s`" % summary["application_failure"],
            "- infrastructure failure: `%s`" % summary["infrastructure_failure"],
            "- 차단: `%s`" % summary["blocked"],
            "- release gate: `%s`" % str(summary["release_gate"]).lower(),
            "- manifest digest: `%s`" % summary["manifest_digest"],
            "",
            "| family | image | tag | status | first failure | attempts |",
            "|---|---|---|---|---|---:|",
        ]
        for result in summary["results"]:
            lines.append(
                "| `%s` | `%s` | `%s` | `%s` | `%s` | %d |"
                % (
                    result["id"],
                    result["image"],
                    result["tag"],
                    result["status"],
                    result["first_failure"] or "-",
                    len(result["attempts"]),
                )
            )
        (self.report_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def _blocked_summary(report_dir: Path, errors: Sequence[str], manifest_path: Path) -> Dict[str, Any]:
    report_dir.mkdir(parents=True, exist_ok=True)
    summary: Dict[str, Any] = {
        "schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "manifest_digest": hashlib.sha256(manifest_path.read_bytes()).hexdigest()
        if manifest_path.is_file()
        else "",
        "selected": 0,
        "success": 0,
        "success_after_retry": 0,
        "readiness_timeout": 0,
        "pull_rate_limit": 0,
        "image_pull_failure": 0,
        "infrastructure_failure": 0,
        "application_failure": 0,
        "blocked": 1,
        "coverage": "0/0",
        "release_gate": False,
        "status": "blocked",
        "errors": list(errors),
        "results": [],
    }
    (report_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (report_dir / "summary.md").write_text(
        "# Graph Testcontainers image family gate\n\n- 상태: `blocked`\n\n"
        + "\n".join("- %s" % error for error in errors)
        + "\n",
        encoding="utf-8",
    )
    return summary


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=MANIFEST)
    parser.add_argument("--scope", choices=("changed", "full"), default="changed")
    parser.add_argument("--changed-path", action="append", default=[])
    parser.add_argument("--changed-path-file", type=Path)
    parser.add_argument("--report-dir", type=Path, default=Path("build/reports/testcontainers-image-gate"))
    parser.add_argument("--gradle-command", default="./gradlew")
    parser.add_argument("--max-attempts", type=int, default=3)
    parser.add_argument("--timeout-minutes", type=int, default=30)
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    try:
        entries = load_manifest(args.manifest)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        _blocked_summary(args.report_dir, [str(error)], args.manifest)
        return 1

    errors = validate_manifest(entries, ROOT)
    if errors:
        _blocked_summary(args.report_dir, errors, args.manifest)
        return 1

    changed_paths = {str(path) for path in args.changed_path}
    if args.changed_path_file:
        try:
            changed_paths.update(
                line.strip()
                for line in args.changed_path_file.read_text(encoding="utf-8").splitlines()
                if line.strip()
            )
        except OSError as error:
            _blocked_summary(args.report_dir, [str(error)], args.manifest)
            return 1
    try:
        shared_paths = load_shared_paths(args.manifest)
    except (OSError, ValueError, json.JSONDecodeError) as error:
        _blocked_summary(args.report_dir, [str(error)], args.manifest)
        return 1
    selected = select_entries(entries, changed_paths, scope=args.scope, shared_paths=shared_paths)
    summary = GateRunner(
        selected,
        args.report_dir,
        gradle_command=args.gradle_command,
        max_attempts=args.max_attempts,
        timeout_minutes=args.timeout_minutes,
        manifest_path=args.manifest,
    ).run()
    print(json.dumps({key: summary[key] for key in ("status", "coverage", "release_gate")}, ensure_ascii=False))
    return 0 if summary["status"] == "success" or summary["status"] == "skipped" else 1


if __name__ == "__main__":
    raise SystemExit(main())
