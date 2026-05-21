#!/usr/bin/env python3
"""Render GraphWriteIngestionBenchmark JMH JSON as an SVG/PNG latency chart."""

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

OPERATIONS = [
    "vertexOnlyBatchInsert",
    "edgeOnlyBatchInsert",
    "mixedVertexEdgeInsert",
    "repeatedMixedBatches",
]


def esc(value: object) -> str:
    return str(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def load_rows(path: Path) -> dict[str, list[dict]]:
    rows = json.loads(path.read_text())
    grouped: dict[str, list[dict]] = {}
    for row in rows:
        benchmark = row.get("benchmark", "")
        operation = benchmark.split(".")[-1]
        if operation not in OPERATIONS:
            continue
        params = row.get("params", {})
        batch_size = params.get("batchSize", "unknown")
        backend = params.get("backend", "unknown")
        grouped.setdefault(f"{operation} / batch={batch_size}", []).append(
            {
                "backend": backend,
                "score": float(row["primaryMetric"]["score"]),
                "error": row["primaryMetric"].get("scoreError"),
                "unit": row["primaryMetric"].get("scoreUnit", ""),
            }
        )
    return grouped


def render(grouped: dict[str, list[dict]], output: Path, title: str, subtitle: str) -> None:
    backends = ["tinkergraph", "neo4j", "memgraph", "age", "falkordb"]
    groups = sorted(
        grouped,
        key=lambda key: (
            OPERATIONS.index(key.split(" / ")[0]),
            int(key.rsplit("=", maxsplit=1)[-1]),
        ),
    )

    width = 1080
    left = 250
    chart_width = 620
    top = 92
    bar_h = 16
    row_gap = 7
    group_gap = 30
    group_h = 28 + len(backends) * (bar_h + row_gap) + group_gap
    height = top + 26 + len(groups) * group_h + 24

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        f'<rect x="0" y="0" width="{width}" height="{height}" fill="#FAFAFA"/>',
        '<text x="540" y="30" text-anchor="middle" font-family="sans-serif" font-size="18" '
        f'font-weight="bold" fill="#252525">{esc(title)}</text>',
        '<text x="540" y="52" text-anchor="middle" font-family="sans-serif" font-size="12" '
        f'fill="#666666">{esc(subtitle)}</text>',
        '<rect x="790" y="16" width="238" height="28" fill="#EAF5EA" stroke="#86B586" rx="6"/>',
        '<text x="909" y="35" text-anchor="middle" font-family="sans-serif" font-size="12" '
        'font-weight="bold" fill="#2F6F3A">Lower score = faster</text>',
    ]

    legend_x = left
    for backend in backends:
        color = BACKEND_COLORS[backend]
        parts.append(f'<rect x="{legend_x}" y="66" width="12" height="12" fill="{color}" rx="2"/>')
        parts.append(
            f'<text x="{legend_x + 16}" y="76" font-family="sans-serif" font-size="11" fill="#333333">'
            f'{esc(backend)}</text>'
        )
        legend_x += 118

    parts.append(
        f'<text x="{left}" y="96" font-family="sans-serif" font-size="11" '
        'font-weight="bold" fill="#2F6F3A">Shorter bars are better because each bar is time per benchmark operation.</text>'
    )

    y = top + 26
    for group in groups:
        rows = sorted(grouped.get(group, []), key=lambda row: backends.index(row["backend"]))
        max_score = max((row["score"] for row in rows), default=1.0)
        parts.append(
            f'<text x="24" y="{y + 16}" font-family="sans-serif" font-size="13" '
            f'font-weight="bold" fill="#222222">{esc(group)}</text>'
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
                f'<text x="{left - 10}" y="{y + 12}" text-anchor="end" '
                f'font-family="sans-serif" font-size="11" fill="#333333">{esc(backend)}</text>'
            )
            parts.append(f'<rect x="{left}" y="{y}" width="{bar_w:.1f}" height="{bar_h}" fill="{color}" rx="3"/>')
            parts.append(
                f'<text x="{left + bar_w + 8:.1f}" y="{y + 12}" '
                f'font-family="sans-serif" font-size="10" fill="#333333">{score:.3g}</text>'
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
        default=Path("docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.svg"),
    )
    parser.add_argument(
        "--png-output",
        type=Path,
        default=Path("docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png"),
    )
    parser.add_argument("--title", default="Graph Write Ingestion Testcontainers Benchmark")
    parser.add_argument(
        "--subtitle",
        default="Latency, ms/op. Lower score is faster. 100 and 1,000 row batches.",
    )
    parser.add_argument("--skip-png", action="store_true")
    args = parser.parse_args()

    render(load_rows(args.jmh_json), args.output, args.title, args.subtitle)
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
