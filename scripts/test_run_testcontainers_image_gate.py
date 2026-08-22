#!/usr/bin/env python3
"""Test the graph Testcontainers image-family gate runner."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

from scripts.run_testcontainers_image_gate import GateRunner, classify_failure, redact
from scripts.testcontainers_image_gate import load_manifest


class TestRunTestcontainersImageGate(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        root = Path(__file__).resolve().parents[1]
        cls.entries = load_manifest(root / "scripts/testcontainers_image_gate_manifest.json")

    def test_failure_classes_are_distinct(self) -> None:
        self.assertEqual("readiness_timeout", classify_failure(1, "Timed out waiting for container to start", ""))
        self.assertEqual("pull_rate_limit", classify_failure(1, "toomanyrequests: rate limit exceeded", ""))
        self.assertEqual("infrastructure_failure", classify_failure(1, "Cannot connect to the Docker daemon", ""))
        self.assertEqual("application_failure", classify_failure(1, "AssertionFailedError: expected vertex", ""))

    def test_redaction_removes_secret_values(self) -> None:
        value = redact("token=abc123 Authorization: Bearer secret-value")
        self.assertNotIn("abc123", value)
        self.assertNotIn("secret-value", value)
        self.assertIn("<redacted>", value)

    def test_first_attempt_success_opens_the_gate(self) -> None:
        calls: list[list[str]] = []

        def command_runner(command: list[str], timeout: int) -> SimpleNamespace:
            calls.append(command)
            return SimpleNamespace(returncode=0, stdout="BUILD SUCCESSFUL", stderr="")

        with tempfile.TemporaryDirectory() as directory:
            summary = GateRunner(
                [self.entries[0]],
                Path(directory),
                command_runner=command_runner,
                max_attempts=3,
            ).run()

            self.assertEqual("success", summary["status"])
            self.assertTrue(summary["release_gate"])
            self.assertEqual("success", summary["results"][0]["status"])
            self.assertEqual(1, len(calls))
            self.assertIn(self.entries[0]["testTask"], calls[0])
            self.assertIn(self.entries[0]["testPattern"], calls[0])

    def test_retry_success_is_recorded_and_does_not_open_the_gate(self) -> None:
        attempts = iter(
            [
                SimpleNamespace(returncode=1, stdout="toomanyrequests: rate limit exceeded", stderr=""),
                SimpleNamespace(returncode=0, stdout="BUILD SUCCESSFUL", stderr=""),
            ],
        )
        diagnostics = lambda entry: {"docker_events": "rate limited"}

        with tempfile.TemporaryDirectory() as directory:
            summary = GateRunner(
                [self.entries[0]],
                Path(directory),
                command_runner=lambda command, timeout: next(attempts),
                diagnostic_runner=diagnostics,
                max_attempts=3,
            ).run()

            result = summary["results"][0]
            self.assertEqual("success_after_retry", result["status"])
            self.assertEqual("pull_rate_limit", result["attempts"][0]["status"])
            self.assertFalse(summary["release_gate"])
            self.assertEqual({"docker_events": "rate limited"}, result["diagnostics"])

    def test_readiness_failure_preserves_diagnostics_and_summary_artifacts(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            summary = GateRunner(
                [self.entries[1]],
                Path(directory),
                command_runner=lambda command, timeout: SimpleNamespace(
                    returncode=1,
                    stdout="Timed out waiting for container to start",
                    stderr="",
                ),
                diagnostic_runner=lambda entry: {
                    "image_digest": "sha256:abc",
                    "docker_logs": "readiness timeout",
                    "docker_events": "container exited",
                },
                max_attempts=1,
            ).run()

            self.assertEqual("failed", summary["status"])
            self.assertEqual(1, summary["readiness_timeout"])
            self.assertFalse(summary["release_gate"])
            self.assertTrue((Path(directory) / "summary.json").is_file())
            self.assertTrue((Path(directory) / "summary.md").is_file())
            payload = json.loads((Path(directory) / "memgraph.json").read_text(encoding="utf-8"))
            self.assertEqual("sha256:abc", payload["diagnostics"]["image_digest"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
