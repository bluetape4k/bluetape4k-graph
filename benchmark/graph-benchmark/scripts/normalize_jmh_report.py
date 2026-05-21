#!/usr/bin/env python3
"""Normalize kotlinx-benchmark/JMH JSON and optionally compare it with a baseline."""

from __future__ import annotations

import argparse
import json
import platform
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass(frozen=True)
class BenchmarkResult:
    name: str
    benchmark_class: str
    operation: str
    params: dict[str, str]
    score: float
    score_error: float | None
    unit: str

    @property
    def key(self) -> str:
        if not self.params:
            return self.name
        params = ",".join(f"{key}={value}" for key, value in sorted(self.params.items()))
        return f"{self.name}[{params}]"


def load_results(path: Path) -> list[BenchmarkResult]:
    raw = json.loads(path.read_text())
    if not isinstance(raw, list):
        raise ValueError(f"Expected JMH result list: {path}")

    results: list[BenchmarkResult] = []
    for row in raw:
        metric = row.get("primaryMetric", {})
        benchmark = row.get("benchmark", "")
        parts = benchmark.split(".")
        benchmark_class = parts[-2] if len(parts) >= 2 else benchmark
        operation = parts[-1] if parts else benchmark
        params = {str(k): str(v) for k, v in row.get("params", {}).items()}
        results.append(
            BenchmarkResult(
                name=benchmark,
                benchmark_class=benchmark_class,
                operation=operation,
                params=params,
                score=float(metric.get("score", 0.0)),
                score_error=metric.get("scoreError"),
                unit=str(metric.get("scoreUnit", "")),
            )
        )
    return results


def git_commit() -> str:
    try:
        return subprocess.check_output(["git", "rev-parse", "--short", "HEAD"], text=True).strip()
    except Exception:
        return "unknown"


def normalize(path: Path, baseline: Path | None, direction: str) -> dict[str, Any]:
    results = load_results(path)
    baseline_by_key = {result.key: result for result in load_results(baseline)} if baseline else {}

    normalized = []
    for result in results:
        base = baseline_by_key.get(result.key)
        delta_percent = None
        improved = None
        if base and base.score:
            delta_percent = ((result.score - base.score) / base.score) * 100.0
            improved = delta_percent > 0 if direction == "higher_is_better" else delta_percent < 0

        normalized.append(
            {
                "key": result.key,
                "benchmark": result.name,
                "benchmarkClass": result.benchmark_class,
                "operation": result.operation,
                "params": result.params,
                "score": result.score,
                "scoreError": result.score_error,
                "unit": result.unit,
                "baselineScore": base.score if base else None,
                "deltaPercent": delta_percent,
                "improved": improved,
            }
        )

    return {
        "schema": "bluetape4k.graph-benchmark.normalized.v1",
        "source": str(path),
        "baseline": str(baseline) if baseline else None,
        "direction": direction,
        "gitCommit": git_commit(),
        "environment": {
            "python": platform.python_version(),
            "platform": platform.platform(),
        },
        "results": normalized,
    }


def write_markdown(report: dict[str, Any], path: Path) -> None:
    lines = [
        "# graph-benchmark Report",
        "",
        f"- Source: `{report['source']}`",
        f"- Baseline: `{report['baseline']}`",
        f"- Direction: `{report['direction']}`",
        f"- Git commit: `{report['gitCommit']}`",
        "",
        "| Benchmark | Params | Score | Baseline | Delta | Unit | Improved |",
        "|---|---:|---:|---:|---:|---|---|",
    ]
    for row in report["results"]:
        params = ", ".join(f"{k}={v}" for k, v in sorted(row["params"].items())) or "-"
        baseline = "-" if row["baselineScore"] is None else f"{row['baselineScore']:.6g}"
        delta = "-" if row["deltaPercent"] is None else f"{row['deltaPercent']:.2f}%"
        improved = "-" if row["improved"] is None else str(row["improved"]).lower()
        lines.append(
            f"| `{row['benchmarkClass']}.{row['operation']}` | {params} | {row['score']:.6g} | "
            f"{baseline} | {delta} | {row['unit']} | {improved} |"
        )
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("jmh_json", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--direction", choices=["lower_is_better", "higher_is_better"], default="lower_is_better")
    parser.add_argument("--markdown", type=Path)
    args = parser.parse_args()

    report = normalize(args.jmh_json, args.baseline, args.direction)
    print(json.dumps(report, indent=2, sort_keys=True))
    if args.markdown:
        write_markdown(report, args.markdown)


if __name__ == "__main__":
    main()
