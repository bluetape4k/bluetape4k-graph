#!/usr/bin/env python3
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CI_WORKFLOW = ROOT / ".github/workflows/ci.yml"
NIGHTLY_WORKFLOW = ROOT / ".github/workflows/nightly-tests.yml"
BENCHMARK_WORKFLOW = ROOT / ".github/workflows/benchmark.yml"


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
        cls.benchmark = BENCHMARK_WORKFLOW.read_text(encoding="utf-8")

    def test_ci_workflow_change_is_not_a_common_or_benchmark_change(self) -> None:
        changes = job_block(self.ci, "changes")
        self.assertNotIn("- '.github/workflows/ci.yml'", changes)

    def test_build_is_limited_to_runtime_changes(self) -> None:
        build = job_block(self.ci, "build")
        self.assertIn("needs.changes.outputs.runtime == 'true'", build)
        changes = job_block(self.ci, "changes")
        runtime_filter = changes.split("            runtime:\n", 1)[1]
        self.assertNotIn("- 'benchmark/**'", runtime_filter)

    def test_duplicate_image_family_gate_is_not_in_pr_ci(self) -> None:
        self.assertNotRegex(self.ci, r"(?m)^  testcontainers-image-gate:$")

    def test_ci_does_not_run_benchmark_lifecycle(self) -> None:
        self.assertNotRegex(self.ci, r"(?m)^  benchmark-catalog:$")
        self.assertNotRegex(self.ci, r"(?m)^  test-graph-benchmark:$")
        self.assertNotIn("graph-benchmarks", self.ci)
        for project in (
            "graph-age-benchmark",
            "graph-benchmark",
            "graph-io-benchmark",
            "graph-neo4j-benchmark",
        ):
            self.assertIn(f"-x :{project}:build", self.ci)
            self.assertIn(f"-x :{project}:build", self.nightly)

    def test_benchmarks_remain_manual_only(self) -> None:
        self.assertIn("workflow_dispatch:", self.benchmark)
        self.assertRegex(self.benchmark, r"(?m)^  benchmark-catalog:$")

    def test_nightly_retains_full_image_family_gate(self) -> None:
        gate = job_block(self.nightly, "testcontainers-image-gate")
        self.assertIn("--scope full", gate)
        self.assertIn("inputs.scope == 'full'", gate)


if __name__ == "__main__":
    unittest.main()
