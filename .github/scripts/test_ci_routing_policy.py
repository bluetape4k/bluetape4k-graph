#!/usr/bin/env python3
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CI_WORKFLOW = ROOT / ".github/workflows/ci.yml"
NIGHTLY_WORKFLOW = ROOT / ".github/workflows/nightly-tests.yml"


def job_block(workflow: str, job: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(job)}:\n(?P<body>.*?)(?=^  [a-zA-Z0-9_-]+:\n|\Z)",
        workflow,
    )
    if match is None:
        raise AssertionError(f"job not found: {job}")
    return match.group("body")


class CiRoutingPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.ci = CI_WORKFLOW.read_text(encoding="utf-8")
        cls.nightly = NIGHTLY_WORKFLOW.read_text(encoding="utf-8")

    def test_ci_workflow_change_is_not_a_common_or_benchmark_change(self) -> None:
        changes = job_block(self.ci, "changes")
        self.assertNotIn("- '.github/workflows/ci.yml'", changes)

    def test_build_is_limited_to_runtime_changes(self) -> None:
        build = job_block(self.ci, "build")
        self.assertIn("needs.changes.outputs.runtime == 'true'", build)

    def test_duplicate_image_family_gate_is_not_in_pr_ci(self) -> None:
        self.assertNotRegex(self.ci, r"(?m)^  testcontainers-image-gate:$")

    def test_benchmark_matrix_stops_after_first_failure(self) -> None:
        benchmark = job_block(self.ci, "test-graph-benchmark")
        self.assertIn("fail-fast: true", benchmark)
        self.assertIn("max-parallel: 1", benchmark)

    def test_nightly_retains_full_image_family_gate(self) -> None:
        gate = job_block(self.nightly, "testcontainers-image-gate")
        self.assertIn("--scope full", gate)
        self.assertIn("inputs.scope == 'full'", gate)


if __name__ == "__main__":
    unittest.main()
