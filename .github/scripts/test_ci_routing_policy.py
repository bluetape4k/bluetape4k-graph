#!/usr/bin/env python3
import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CI_WORKFLOW = ROOT / ".github/workflows/ci.yml"
NIGHTLY_WORKFLOW = ROOT / ".github/workflows/nightly-tests.yml"
BENCHMARK_WORKFLOW = ROOT / ".github/workflows/benchmark.yml"
EXAMPLES_WORKFLOW = ROOT / ".github/workflows/examples.yml"
TESTCONTAINERS_CONTRACT_WORKFLOW = ROOT / ".github/workflows/testcontainers-contract.yml"
BRANCH_GOVERNANCE_WORKFLOW = ROOT / ".github/workflows/branch-governance.yml"
BRANCH_POLICY = ROOT / "config/branch-governance.json"


def job_block(workflow: str, job: str) -> str:
    match = re.search(
        rf"(?ms)^  {re.escape(job)}:\n(?P<body>.*?)(?=^  [a-zA-Z0-9_-]+:\n|\Z)",
        workflow,
    )
    if match is None:
        raise AssertionError(f"job not found: {job}")
    return match.group("body")


def trigger_block(workflow: str) -> str:
    return workflow.split("\nconcurrency:", 1)[0].split("\npermissions:", 1)[0]


class CiRoutingPolicyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.ci = CI_WORKFLOW.read_text(encoding="utf-8")
        cls.nightly = NIGHTLY_WORKFLOW.read_text(encoding="utf-8")
        cls.benchmark = BENCHMARK_WORKFLOW.read_text(encoding="utf-8")
        cls.examples = EXAMPLES_WORKFLOW.read_text(encoding="utf-8")
        cls.testcontainers_contract = TESTCONTAINERS_CONTRACT_WORKFLOW.read_text(encoding="utf-8")
        cls.branch_governance = BRANCH_GOVERNANCE_WORKFLOW.read_text(encoding="utf-8")
        cls.branch_policy = BRANCH_POLICY.read_text(encoding="utf-8")

    def test_active_workflows_do_not_treat_main_as_a_canonical_branch(self) -> None:
        for workflow in (
            self.ci,
            self.examples,
            self.testcontainers_contract,
            self.branch_governance,
        ):
            self.assertNotRegex(
                trigger_block(workflow),
                r"(?m)^\s*branches:\s*\[[^\]]*\bmain\b",
            )

    def test_branch_governance_keeps_develop_canonical_and_main_frozen(self) -> None:
        self.assertIn('"canonical_branch": "develop"', self.branch_policy)
        self.assertIn('"mode": "frozen-history-anchor"', self.branch_policy)
        self.assertIn("verify_branch_governance.py", self.branch_governance)

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
        self.assertIn(":graph-benchmark:test", self.benchmark)
        self.assertIn("BenchmarkCatalogContractTest", self.benchmark)
        self.assertIn("BenchmarkContainerLifecycleContractTest", self.benchmark)

    def test_nightly_retains_full_image_family_gate(self) -> None:
        gate = job_block(self.nightly, "testcontainers-image-gate")
        self.assertIn("--scope full", gate)
        self.assertIn("inputs.scope == 'full'", gate)


if __name__ == "__main__":
    unittest.main()
