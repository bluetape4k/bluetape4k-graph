#!/usr/bin/env python3
"""Validate benchmark catalog and wrapper contracts without starting Gradle tests."""

from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
BENCHMARK_ROOT = REPO_ROOT / "benchmark"


class BenchmarkCatalogContractTest(unittest.TestCase):
    def test_catalog_matches_registered_benchmark_modules(self) -> None:
        catalog = json.loads((BENCHMARK_ROOT / "benchmark-modules.json").read_text())
        catalog_projects = [entry["project"] for entry in catalog]
        registered_projects = sorted(
            path.name
            for path in BENCHMARK_ROOT.iterdir()
            if path.is_dir() and (path / "build.gradle.kts").exists()
        )

        self.assertEqual(sorted(catalog_projects), registered_projects)
        self.assertEqual(len(catalog_projects), len(set(catalog_projects)))
        for project in catalog_projects:
            self.assertTrue((BENCHMARK_ROOT / project / "build.gradle.kts").exists())

    def test_catalog_drives_only_the_manual_benchmark_workflow(self) -> None:
        ci = (REPO_ROOT / ".github/workflows/ci.yml").read_text()
        benchmark = (REPO_ROOT / ".github/workflows/benchmark.yml").read_text()

        for forbidden in ("graph-benchmarks:", "benchmark-catalog:", "test-graph-benchmark:"):
            self.assertNotIn(forbidden, ci)
        for required in (
            "benchmark/benchmark-modules.json",
            "benchmark-catalog:",
            "matrix: ${{ fromJSON(needs.benchmark-catalog.outputs.matrix) }}",
            'run: ./gradlew ":${{ matrix.project }}:benchmark"',
            "name: ${{ matrix.id }}-results",
        ):
            self.assertIn(required, benchmark)


class BenchmarkScriptContractTest(unittest.TestCase):
    def test_age_wrapper_emits_one_valid_json_summary_line(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            report_root = self._write_report(
                Path(temp) / "age" / "main.json",
                [
                    self._row("pkg.CreateVertexBenchmark.createVertex", 1.5, "ms/op"),
                    self._row("pkg.FindVerticesBenchmark.findVertices", 250.0, "us/op"),
                ],
            )
            result = self._run(
                "benchmark-age.sh",
                BENCHMARK_SKIP_RUN="true",
                BENCHMARK_AGE_REPORT_ROOT=str(report_root.parent),
            )

        self.assertEqual(result.returncode, 0, result.stderr)
        lines = [line for line in result.stdout.splitlines() if line.strip()]
        self.assertEqual(len(lines), 1)
        payload = json.loads(lines[0])
        self.assertEqual(payload["primary"], 875.0)
        self.assertEqual(payload["sub_scores"]["createVertex"], 1500.0)
        self.assertEqual(payload["sub_scores"]["findVertices"], 250.0)

    def test_neo4j_age_wrapper_emits_combined_schema(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            age_root = self._write_report(
                Path(temp) / "age" / "main.json",
                [self._row("pkg.CreateVertexBenchmark.createVertex", 1.0, "ms/op")],
            )
            neo4j_root = self._write_report(
                Path(temp) / "neo4j" / "main.json",
                [self._row("pkg.Neo4jCreateVertexBenchmark.createVertex", 2.0, "ms/op")],
            )
            result = self._run(
                "benchmark-neo4j-age.sh",
                BENCHMARK_SKIP_RUN="true",
                BENCHMARK_AGE_REPORT_ROOT=str(age_root.parent),
                BENCHMARK_NEO4J_REPORT_ROOT=str(neo4j_root.parent),
            )

        self.assertEqual(result.returncode, 0, result.stderr)
        payload = json.loads(result.stdout.strip())
        self.assertEqual(payload["schema"], "bluetape4k.graph.backend-benchmark-summary.v1")
        self.assertEqual(payload["primary"], 1500.0)
        self.assertEqual(payload["unit"], "us/op")
        self.assertEqual(payload["direction"], "lower_is_better")
        self.assertIn("benchmarks", payload)
        self.assertEqual(payload["sub_scores"]["age_createVertex"], 1000.0)
        self.assertEqual(payload["sub_scores"]["neo4j_createVertex"], 2000.0)

    def test_age_wrapper_fails_when_report_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            result = self._run(
                "benchmark-age.sh",
                BENCHMARK_SKIP_RUN="true",
                BENCHMARK_AGE_REPORT_ROOT=str(Path(temp) / "missing"),
            )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ERROR: benchmark JSON report not found", result.stderr)
        self.assertEqual(result.stdout.strip(), "")

    def test_neo4j_age_wrapper_explains_malformed_report(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            age_root = self._write_report(
                Path(temp) / "age" / "main.json",
                [self._row("pkg.CreateVertexBenchmark.createVertex", 1.0, "ms/op")],
            )
            neo4j_root = self._write_report(
                Path(temp) / "neo4j" / "main.json",
                {"not": "a JMH list"},
            )
            result = self._run(
                "benchmark-neo4j-age.sh",
                BENCHMARK_SKIP_RUN="true",
                BENCHMARK_AGE_REPORT_ROOT=str(age_root.parent),
                BENCHMARK_NEO4J_REPORT_ROOT=str(neo4j_root.parent),
            )

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("ERROR: Expected JMH result list for neo4j report", result.stderr)
        self.assertEqual(result.stdout.strip(), "")

    @staticmethod
    def _row(name: str, score: float, unit: str) -> dict[str, object]:
        return {"benchmark": name, "primaryMetric": {"score": score, "scoreUnit": unit}}

    @staticmethod
    def _write_report(path: Path, content: object) -> Path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(content))
        return path

    @staticmethod
    def _run(script: str, **env: str) -> subprocess.CompletedProcess[str]:
        process_env = os.environ.copy()
        process_env.update(env)
        return subprocess.run(
            [str(REPO_ROOT / "scripts" / script)],
            cwd=REPO_ROOT,
            env=process_env,
            capture_output=True,
            text=True,
            check=False,
        )


if __name__ == "__main__":
    unittest.main()
