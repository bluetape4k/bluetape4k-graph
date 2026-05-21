#!/usr/bin/env python3
"""Render GraphDbComparisonBenchmark JMH JSON as a backend-grouped SVG chart."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
from pathlib import Path


BACKEND_COLORS = {
    "tinkergraph": "#4A90D9",
    "neo4j": "#5ABF6B",
    "memgraph": "#E8B040",
    "age": "#D96C6C",
    "falkordb": "#7B68EE",
}


def esc(value: object) -> str:
    return str(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def load_rows(path: Path) -> dict[str, list[dict]]:
    rows = json.loads(path.read_text())
    grouped: dict[str, list[dict]] = {}
    for row in rows:
        benchmark = row.get("benchmark", "")
        operation = benchmark.split(".")[-1]
        params = row.get("params", {})
        backend = params.get("backend", "unknown")
        if not benchmark.endswith(f"GraphDbComparisonBenchmark.{operation}"):
            continue
        grouped.setdefault(operation, []).append(
            {
                "backend": backend,
                "score": float(row["primaryMetric"]["score"]),
                "error": row["primaryMetric"].get("scoreError"),
                "unit": row["primaryMetric"].get("scoreUnit", ""),
            }
        )
    return grouped


def render(grouped: dict[str, list[dict]], output: Path) -> None:
    operations = ["batchInsertCycle", "countPersons", "oneHopNeighbors", "shortestPath"]
    backends = ["tinkergraph", "neo4j", "memgraph", "age", "falkordb"]

    width = 960
    left = 190
    chart_width = 590
    top = 72
    bar_h = 18
    row_gap = 8
    group_gap = 34
    group_h = 28 + len(backends) * (bar_h + row_gap) + group_gap
    height = top + 26 + len(operations) * group_h + 24

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        f'<rect x="0" y="0" width="{width}" height="{height}" fill="#FAFAFA"/>',
        '<text x="480" y="30" text-anchor="middle" font-family="sans-serif" font-size="18" '
        'font-weight="bold" fill="#252525">Graph DB Testcontainers Benchmark</text>',
        '<text x="480" y="52" text-anchor="middle" font-family="sans-serif" font-size="12" '
        'fill="#666666">Latency, ms/op. Lower score is faster. small dataset, 1 warmup, 3 measurement iterations.</text>',
        '<rect x="704" y="16" width="224" height="28" fill="#EAF5EA" stroke="#86B586" rx="6"/>',
        '<text x="816" y="35" text-anchor="middle" font-family="sans-serif" font-size="12" '
        'font-weight="bold" fill="#2F6F3A">Lower score = faster</text>',
    ]

    legend_x = left
    for backend in backends:
        color = BACKEND_COLORS[backend]
        parts.append(f'<rect x="{legend_x}" y="62" width="12" height="12" fill="{color}" rx="2"/>')
        parts.append(
            f'<text x="{legend_x + 16}" y="72" font-family="sans-serif" font-size="11" fill="#333333">'
            f'{esc(backend)}</text>'
        )
        legend_x += 118

    parts.append(
        f'<text x="{left}" y="90" font-family="sans-serif" font-size="11" '
        'font-weight="bold" fill="#2F6F3A">Shorter bars are better because each bar is time per operation.</text>'
    )

    y = top + 26
    for operation in operations:
        rows = sorted(grouped.get(operation, []), key=lambda r: backends.index(r["backend"]))
        max_score = max((r["score"] for r in rows), default=1.0)
        parts.append(
            f'<text x="24" y="{y + 16}" font-family="sans-serif" font-size="14" '
            f'font-weight="bold" fill="#222222">{esc(operation)}</text>'
        )
        parts.append(
            f'<text x="{left + chart_width}" y="{y + 16}" text-anchor="end" '
            f'font-family="sans-serif" font-size="10" fill="#888888">max {max_score:.3g} ms/op</text>'
        )
        y += 28
        for row in rows:
            score = row["score"]
            backend = row["backend"]
            bar_w = max(2, (score / max_score) * chart_width)
            color = BACKEND_COLORS.get(backend, "#888888")
            parts.append(
                f'<text x="{left - 10}" y="{y + 13}" text-anchor="end" '
                f'font-family="sans-serif" font-size="12" fill="#333333">{esc(backend)}</text>'
            )
            parts.append(f'<rect x="{left}" y="{y}" width="{bar_w:.1f}" height="{bar_h}" fill="{color}" rx="3"/>')
            parts.append(
                f'<text x="{left + bar_w + 8:.1f}" y="{y + 13}" '
                f'font-family="sans-serif" font-size="11" fill="#333333">{score:.3g}</text>'
            )
            y += bar_h + row_gap
        y += group_gap

    parts.append("</svg>")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(parts) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("jmh_json", type=Path)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.svg"),
    )
    parser.add_argument(
        "--png-output",
        type=Path,
        default=Path("docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.png"),
    )
    parser.add_argument("--skip-png", action="store_true")
    args = parser.parse_args()

    render(load_rows(args.jmh_json), args.output)
    print(args.output)
    if not args.skip_png:
        rsvg_convert = shutil.which("rsvg-convert")
        if rsvg_convert is None:
            raise SystemExit("rsvg-convert is required to render PNG output")
        args.png_output.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            [rsvg_convert, str(args.output), "--output", str(args.png_output)],
            check=True,
        )
        print(args.png_output)


if __name__ == "__main__":
    main()
