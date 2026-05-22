#!/usr/bin/env python3
"""Render ApiModelBenchmark JMH JSON as a two-panel SVG chart."""

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

THROUGHPUT_ROWS = [
    ("sync", "pageRankSyncThroughput"),
    ("virtual-thread", "pageRankVirtualThreadThroughput"),
    ("coroutine", "pageRankCoroutineThroughput"),
]

LATENCY_GROUPS = [
    (
        "BFS depth=5 latency",
        [
            ("sync", "bfsSyncLatency"),
            ("virtual-thread", "bfsVirtualThreadLatency"),
            ("coroutine", "bfsCoroutineLatency"),
        ],
    ),
    (
        "100-way BFS latency",
        [
            ("virtual-thread", "bfs100wayVirtualThreadLatency"),
            ("coroutine", "bfs100wayCoroutineLatency"),
        ],
    ),
    (
        "100-way launch/create cost",
        [
            ("virtual-thread", "virtualThread100wayCreationCost"),
            ("coroutine", "coroutine100wayLaunchCost"),
        ],
    ),
]


def esc(value: object) -> str:
    return str(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def load_scores(path: Path) -> dict[str, dict[str, float | str]]:
    scores: dict[str, dict[str, float | str]] = {}
    for row in json.loads(path.read_text()):
        operation = row.get("benchmark", "").split(".")[-1]
        primary = row["primaryMetric"]
        alloc_metric = row.get("secondaryMetrics", {}).get("gc.alloc.rate.norm", {})
        scores[operation] = {
            "score": float(primary["score"]),
            "error": float(primary.get("scoreError") or 0.0),
            "unit": primary.get("scoreUnit", ""),
            "alloc": float(alloc_metric.get("score") or 0.0),
        }
    return scores


def bar(parts: list[str], x: int, y: int, w: float, h: int, color: str) -> None:
    parts.append(f'<rect x="{x}" y="{y}" width="{max(w, 2):.1f}" height="{h}" fill="{color}" rx="3"/>')


def render(scores: dict[str, dict[str, float | str]], output: Path) -> None:
    width = 980
    height = 700
    left = 220
    chart_width = 590
    bar_h = 18
    row_gap = 11

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        f'<rect x="0" y="0" width="{width}" height="{height}" fill="#FAFAFA"/>',
        '<text x="490" y="32" text-anchor="middle" font-family="&apos;Architects Daughter&apos;,&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,cursive" font-size="18" '
        'font-weight="400" fill="#252525">API Model Benchmark</text>',
        '<text x="490" y="54" text-anchor="middle" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="12" '
        'fill="#666666">TinkerGraph fixture, JMH smoke run, one fork, one warmup, three measurement iterations.</text>',
    ]

    legend_x = left
    for model, color in MODEL_COLORS.items():
        parts.append(f'<rect x="{legend_x}" y="74" width="12" height="12" fill="{color}" rx="2"/>')
        parts.append(
            f'<text x="{legend_x + 16}" y="84" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="11" fill="#333333">'
            f'{esc(model)}</text>'
        )
        legend_x += 150

    parts.extend(
        [
            '<rect x="705" y="70" width="222" height="28" fill="#EAF5EA" stroke="#86B586" rx="6"/>',
            '<text x="816" y="89" text-anchor="middle" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="12" '
            'font-weight="400" fill="#2F6F3A">Direction is per panel</text>',
            '<text x="24" y="128" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="15" font-weight="400" '
            'fill="#222222">PageRank throughput</text>',
            f'<text x="{left}" y="128" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="11" '
            'font-weight="400" fill="#2F6F3A">Longer bars are better, ops/s.</text>',
        ]
    )

    y = 148
    max_throughput = max(float(scores[operation]["score"]) for _, operation in THROUGHPUT_ROWS)
    for model, operation in THROUGHPUT_ROWS:
        score = float(scores[operation]["score"])
        unit = scores[operation]["unit"]
        color = MODEL_COLORS[model]
        parts.append(
            f'<text x="{left - 10}" y="{y + 13}" text-anchor="end" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" '
            f'font-size="12" fill="#333333">{esc(model)}</text>'
        )
        bar(parts, left, y, (score / max_throughput) * chart_width, bar_h, color)
        parts.append(
            f'<text x="{left + (score / max_throughput) * chart_width + 8:.1f}" y="{y + 13}" '
            f'font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="11" fill="#333333">{score:,.0f} {esc(unit)}</text>'
        )
        y += bar_h + row_gap

    y += 48
    parts.extend(
        [
            '<text x="24" y="{0}" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="15" font-weight="400" '
            'fill="#222222">Latency and launch cost</text>'.format(y),
            f'<text x="{left}" y="{y}" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="11" '
            'font-weight="400" fill="#2F6F3A">Shorter bars are better, us/op.</text>',
        ]
    )
    y += 20

    for title, rows in LATENCY_GROUPS:
        max_latency = max(float(scores[operation]["score"]) for _, operation in rows)
        parts.append(
            f'<text x="24" y="{y + 15}" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="13" '
            f'font-weight="400" fill="#222222">{esc(title)}</text>'
        )
        parts.append(
            f'<text x="{left + chart_width}" y="{y + 15}" text-anchor="end" '
            f'font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="10" fill="#888888">max {max_latency:.3g} us/op</text>'
        )
        y += 28
        for model, operation in rows:
            score = float(scores[operation]["score"])
            unit = scores[operation]["unit"]
            color = MODEL_COLORS[model]
            parts.append(
                f'<text x="{left - 10}" y="{y + 13}" text-anchor="end" font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" '
                f'font-size="12" fill="#333333">{esc(model)}</text>'
            )
            bar(parts, left, y, (score / max_latency) * chart_width, bar_h, color)
            parts.append(
                f'<text x="{left + (score / max_latency) * chart_width + 8:.1f}" y="{y + 13}" '
                f'font-family="&apos;Comic Sans MS&apos;,&apos;Comic Sans&apos;,&apos;Comic Neue&apos;,&apos;Chalkboard SE&apos;,cursive" font-size="11" fill="#333333">{score:.3f} {esc(unit)}</text>'
            )
            y += bar_h + row_gap
        y += 32

    parts.append("</svg>")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(parts) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("jmh_json", type=Path)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("docs/images/readme-charts/graph-api-model-chart-01.svg"),
    )
    parser.add_argument(
        "--png-output",
        type=Path,
        default=Path("docs/images/readme-charts/graph-api-model-chart-01.png"),
    )
    parser.add_argument("--skip-png", action="store_true")
    args = parser.parse_args()

    render(load_scores(args.jmh_json), args.output)
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
