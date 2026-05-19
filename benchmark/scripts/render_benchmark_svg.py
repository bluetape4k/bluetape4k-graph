#!/usr/bin/env python3
"""
Render JMH/kotlinx-benchmark JSON results as SVG horizontal bar charts.

Usage:
    python render_benchmark_svg.py <jmh-result.json>
    cat jmh-result.json | python render_benchmark_svg.py -

Input JSON format (JMH / kotlinx-benchmark):
    [
      {
        "benchmark": "io.bluetape4k.graph.benchmark.ShortestPathBenchmark.syncShortestPath100Pairs",
        "primaryMetric": {
          "score": 1.23,
          "scoreUnit": "ms/op",
          "scoreError": 0.12
        }
      },
      ...
    ]

Output:
    One SVG file per benchmark class under docs/benchmark-results/<ClassName>.svg.
    Paths of generated files are printed to stdout.
"""

import json
import math
import os
import sys
from collections import defaultdict
from typing import Dict, List, Optional, Tuple

# ---------------------------------------------------------------------------
# Colours
# ---------------------------------------------------------------------------
COLOR_SYNC = "#4A90D9"
COLOR_VT = "#E87040"
COLOR_OTHER = "#7B68EE"
BACKGROUND = "#FAFAFA"
GRID_COLOR = "#E0E0E0"
TEXT_COLOR = "#333333"
FONT_FAMILY = "sans-serif"

# ---------------------------------------------------------------------------
# Layout constants
# ---------------------------------------------------------------------------
LEFT_MARGIN = 240       # space for method names
RIGHT_MARGIN = 80       # space for value labels
TOP_MARGIN = 70         # title + legend
BOTTOM_MARGIN = 50
BAR_HEIGHT = 28
BAR_GAP = 10
GROUP_GAP = 6
ERROR_CAP_HEIGHT = 6
GRID_LINES = 5


# ---------------------------------------------------------------------------
# Data model
# ---------------------------------------------------------------------------
def _bar_color(method_name: str) -> str:
    name_lower = method_name.lower()
    if "vt" in name_lower or "virtual" in name_lower:
        return COLOR_VT
    if "sync" in name_lower:
        return COLOR_SYNC
    return COLOR_OTHER


def _short_name(full_benchmark: str) -> Tuple[str, str]:
    """Return (class_name, method_name) from a fully-qualified benchmark string."""
    parts = full_benchmark.rsplit(".", 1)
    if len(parts) == 2:
        class_fqn, method = parts
        class_name = class_fqn.rsplit(".", 1)[-1]
        return class_name, method
    return full_benchmark, full_benchmark


def parse_results(data: List[dict]) -> Dict[str, List[dict]]:
    """Group benchmark entries by class name."""
    groups: Dict[str, List[dict]] = defaultdict(list)
    for entry in data:
        benchmark = entry.get("benchmark", "")
        primary = entry.get("primaryMetric", {})
        score = primary.get("score", 0.0)
        score_error = primary.get("scoreError", 0.0)
        score_unit = primary.get("scoreUnit", "")
        class_name, method = _short_name(benchmark)
        groups[class_name].append({
            "method": method,
            "score": float(score),
            "score_error": float(score_error) if score_error is not None else 0.0,
            "score_unit": score_unit,
            "color": _bar_color(method),
        })
    return dict(groups)


# ---------------------------------------------------------------------------
# SVG primitives
# ---------------------------------------------------------------------------
def _esc(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def _rect(x: float, y: float, w: float, h: float, fill: str,
          rx: float = 3, opacity: float = 1.0) -> str:
    return (
        f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" '
        f'fill="{fill}" rx="{rx}" opacity="{opacity:.2f}"/>'
    )


def _text(x: float, y: float, content: str, anchor: str = "start",
          font_size: int = 12, fill: str = TEXT_COLOR,
          font_weight: str = "normal") -> str:
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" text-anchor="{anchor}" '
        f'font-family="{FONT_FAMILY}" font-size="{font_size}" '
        f'font-weight="{font_weight}" fill="{fill}">{_esc(content)}</text>'
    )


def _line(x1: float, y1: float, x2: float, y2: float,
          stroke: str = GRID_COLOR, stroke_width: float = 1.0,
          dash: Optional[str] = None) -> str:
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    return (
        f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
        f'stroke="{stroke}" stroke-width="{stroke_width:.1f}"{dash_attr}/>'
    )


# ---------------------------------------------------------------------------
# SVG chart generator
# ---------------------------------------------------------------------------
def render_group_svg(class_name: str, entries: List[dict]) -> str:
    n = len(entries)
    chart_height = n * (BAR_HEIGHT + BAR_GAP) + GROUP_GAP
    svg_height = TOP_MARGIN + chart_height + BOTTOM_MARGIN
    bar_area_width = 500
    svg_width = LEFT_MARGIN + bar_area_width + RIGHT_MARGIN

    max_score = max(e["score"] for e in entries) if entries else 1.0
    if max_score <= 0:
        max_score = 1.0
    scale_max = max_score * 1.15

    unit = entries[0]["score_unit"] if entries else ""

    lines: List[str] = []
    lines.append(
        f'<svg xmlns="http://www.w3.org/2000/svg" '
        f'width="{svg_width}" height="{svg_height}" '
        f'viewBox="0 0 {svg_width} {svg_height}">'
    )

    # Background
    lines.append(_rect(0, 0, svg_width, svg_height, BACKGROUND, rx=0))

    # Title
    lines.append(_text(
        svg_width / 2, 28,
        f"{class_name} — {unit}",
        anchor="middle", font_size=15, font_weight="bold",
    ))

    # Legend
    legend_x = LEFT_MARGIN
    legend_y = 48
    lines.append(_rect(legend_x, legend_y - 10, 14, 14, COLOR_SYNC, rx=2))
    lines.append(_text(legend_x + 18, legend_y, "sync", font_size=11))
    lines.append(_rect(legend_x + 70, legend_y - 10, 14, 14, COLOR_VT, rx=2))
    lines.append(_text(legend_x + 88, legend_y, "virtual-thread", font_size=11))

    # Grid lines
    for i in range(GRID_LINES + 1):
        gx = LEFT_MARGIN + bar_area_width * i / GRID_LINES
        lines.append(_line(
            gx, TOP_MARGIN,
            gx, TOP_MARGIN + chart_height,
            stroke=GRID_COLOR, stroke_width=1.0, dash="4,3",
        ))
        grid_val = scale_max * i / GRID_LINES
        label = f"{grid_val:.2f}" if grid_val < 10 else f"{grid_val:.1f}"
        lines.append(_text(
            gx, TOP_MARGIN + chart_height + 18,
            label, anchor="middle", font_size=10, fill="#888888",
        ))

    # X-axis baseline
    lines.append(_line(
        LEFT_MARGIN, TOP_MARGIN + chart_height,
        LEFT_MARGIN + bar_area_width, TOP_MARGIN + chart_height,
        stroke="#BBBBBB", stroke_width=1.5,
    ))

    # Bars
    for idx, entry in enumerate(entries):
        bar_y = TOP_MARGIN + idx * (BAR_HEIGHT + BAR_GAP)
        bar_w = max(2.0, entry["score"] / scale_max * bar_area_width)
        error_w = entry["score_error"] / scale_max * bar_area_width

        # Method name
        lines.append(_text(
            LEFT_MARGIN - 8, bar_y + BAR_HEIGHT / 2 + 4,
            entry["method"], anchor="end", font_size=11,
        ))

        # Bar
        lines.append(_rect(LEFT_MARGIN, bar_y, bar_w, BAR_HEIGHT, entry["color"], opacity=0.88))

        # Error bar (right cap)
        if error_w > 0:
            err_x = LEFT_MARGIN + bar_w
            mid_y = bar_y + BAR_HEIGHT / 2
            lines.append(_line(
                err_x - error_w, mid_y,
                err_x + error_w, mid_y,
                stroke="#555555", stroke_width=1.5,
            ))
            lines.append(_line(
                err_x - error_w, mid_y - ERROR_CAP_HEIGHT / 2,
                err_x - error_w, mid_y + ERROR_CAP_HEIGHT / 2,
                stroke="#555555", stroke_width=1.5,
            ))
            lines.append(_line(
                err_x + error_w, mid_y - ERROR_CAP_HEIGHT / 2,
                err_x + error_w, mid_y + ERROR_CAP_HEIGHT / 2,
                stroke="#555555", stroke_width=1.5,
            ))

        # Score label
        score_label = (
            f"{entry['score']:.3f}"
            if entry["score"] < 10
            else f"{entry['score']:.1f}"
        )
        label_x = LEFT_MARGIN + bar_w + error_w + 4
        lines.append(_text(
            label_x, bar_y + BAR_HEIGHT / 2 + 4,
            score_label, font_size=10, fill="#555555",
        ))

    lines.append("</svg>")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
def main() -> None:
    args = sys.argv[1:]
    if args and args[0] != "-":
        input_path = args[0]
        with open(input_path, encoding="utf-8") as f:
            data = json.load(f)
    else:
        data = json.load(sys.stdin)

    if not isinstance(data, list):
        print("ERROR: expected a JSON array at the top level", file=sys.stderr)
        sys.exit(1)

    groups = parse_results(data)
    if not groups:
        print("No benchmark entries found.", file=sys.stderr)
        sys.exit(1)

    out_dir = os.path.join("docs", "benchmark-results")
    os.makedirs(out_dir, exist_ok=True)

    for class_name, entries in sorted(groups.items()):
        svg_content = render_group_svg(class_name, entries)
        out_path = os.path.join(out_dir, f"{class_name}.svg")
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(svg_content)
        print(out_path)


if __name__ == "__main__":
    main()
