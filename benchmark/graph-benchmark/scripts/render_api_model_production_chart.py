#!/usr/bin/env python3
"""Render production-window ApiModelBenchmark JMH JSON as SVG/PNG charts."""

from __future__ import annotations

import argparse
import json
import shutil
import subprocess
from pathlib import Path


MODEL_COLORS = {
    "sync": "#4A90D9",
    "virtual-thread": "#5ABF6B",
    "coroutine": "#E8B040",
}

CONCURRENCY_GROUPS = [
    (
        "Concurrent BFS latency",
        [
            ("virtual-thread", "bfsConcurrentVirtualThreadLatency"),
            ("coroutine", "bfsConcurrentCoroutineLatency"),
        ],
    ),
    (
        "Concurrent launch/create cost",
        [
            ("virtual-thread", "virtualThreadConcurrentCreationCost"),
            ("coroutine", "coroutineConcurrentLaunchCost"),
        ],
    ),
]


def esc(value: object) -> str:
    return str(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def format_score(score: float) -> str:
    if score >= 100:
        return f"{score:,.0f}"
    if score >= 10:
        return f"{score:,.1f}"
    if score >= 1:
        return f"{score:,.2f}"
    return f"{score:,.3f}"


def load_rows(path: Path) -> dict[tuple[str, str], dict[str, float | str]]:
    rows: dict[tuple[str, str], dict[str, float | str]] = {}
    for row in json.loads(path.read_text()):
        operation = row.get("benchmark", "").split(".")[-1]
        primary = row["primaryMetric"]
        alloc_metric = row.get("secondaryMetrics", {}).get("gc.alloc.rate.norm", {})
        concurrency = row.get("params", {}).get("concurrency", "")
        rows[(operation, concurrency)] = {
            "score": float(primary["score"]),
            "error": float(primary.get("scoreError") or 0.0),
            "unit": primary.get("scoreUnit", ""),
            "alloc": float(alloc_metric.get("score") or 0.0),
        }
    return rows


def bar(parts: list[str], x: int, y: int, w: float, h: int, color: str) -> None:
    parts.append(f'<rect x="{x}" y="{y}" width="{max(w, 2):.1f}" height="{h}" fill="{color}" rx="3"/>')


def render(rows: dict[tuple[str, str], dict[str, float | str]], output: Path, title: str, subtitle: str) -> None:
    width = 1080
    left = 270
    chart_width = 620
    bar_h = 16
    row_gap = 7
    group_gap = 28

    concurrencies = sorted(
        {concurrency for _, concurrency in rows if concurrency},
        key=lambda value: int(value),
    )
    group_h = 30 + (len(concurrencies) * 2) * (bar_h + row_gap) + group_gap
    height = 106 + len(CONCURRENCY_GROUPS) * group_h + 24

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        f'<rect x="0" y="0" width="{width}" height="{height}" fill="#FAFAFA"/>',
        '<text x="540" y="30" text-anchor="middle" font-family="&apos;Architects Daughter&apos;,&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,cursive" font-size="18" '
        f'font-weight="400" fill="#252525">{esc(title)}</text>',
        '<text x="540" y="52" text-anchor="middle" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="12" '
        f'fill="#666666">{esc(subtitle)}</text>',
        '<rect x="790" y="16" width="238" height="28" fill="#EAF5EA" stroke="#86B586" rx="6"/>',
        '<text x="909" y="35" text-anchor="middle" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="12" '
        'font-weight="400" fill="#2F6F3A">Lower score = faster</text>',
    ]

    legend_x = left
    for model in ["virtual-thread", "coroutine"]:
        color = MODEL_COLORS[model]
        parts.append(f'<rect x="{legend_x}" y="66" width="12" height="12" fill="{color}" rx="2"/>')
        parts.append(
            f'<text x="{legend_x + 16}" y="76" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="11" fill="#333333">'
            f'{esc(model)}</text>'
        )
        legend_x += 160

    parts.append(
        f'<text x="{left}" y="96" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="11" '
        'font-weight="400" fill="#2F6F3A">Shorter bars are better. Allocation is available in the raw JSON.</text>'
    )

    y = 122
    for title_text, operation_rows in CONCURRENCY_GROUPS:
        scores = [
            float(rows[(operation, concurrency)]["score"])
            for _, operation in operation_rows
            for concurrency in concurrencies
            if (operation, concurrency) in rows
        ]
        max_score = max(scores, default=1.0)
        parts.append(
            f'<text x="24" y="{y + 16}" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="13" '
            f'font-weight="400" fill="#222222">{esc(title_text)}</text>'
        )
        parts.append(
            f'<text x="{left + chart_width}" y="{y + 16}" text-anchor="end" '
            f'font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="10" fill="#888888">max {format_score(max_score)} us/op</text>'
        )
        y += 30
        for concurrency in concurrencies:
            for model, operation in operation_rows:
                row = rows.get((operation, concurrency))
                if row is None:
                    continue
                score = float(row["score"])
                bar_w = max(2, (score / max_score) * chart_width)
                color = MODEL_COLORS[model]
                label = f"{concurrency}-way {model}"
                parts.append(
                    f'<text x="{left - 10}" y="{y + 12}" text-anchor="end" '
                    f'font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="11" fill="#333333">{esc(label)}</text>'
                )
                bar(parts, left, y, bar_w, bar_h, color)
                parts.append(
                    f'<text x="{left + bar_w + 8:.1f}" y="{y + 12}" '
                    f'font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="10" fill="#333333">{format_score(score)}</text>'
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
        default=Path("docs/images/readme-charts/graph-api-model-production-chart-01.svg"),
    )
    parser.add_argument(
        "--png-output",
        type=Path,
        default=Path("docs/images/readme-charts/graph-api-model-production-chart-01.png"),
    )
    parser.add_argument("--title", default="API Model Production-Window Benchmark")
    parser.add_argument(
        "--subtitle",
        default="TinkerGraph fixture, concurrency 10/100/1,000, latency us/op.",
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
        subprocess.run([rsvg_convert, str(args.output), "--output", str(args.png_output)], check=True)
        print(args.png_output)


if __name__ == "__main__":
    main()
