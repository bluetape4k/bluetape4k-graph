#!/usr/bin/env python3
"""Generate README SVG/PNG visuals for bluetape4k-graph.

The diagrams intentionally use a small, local SVG vocabulary so the rendered
assets stay reviewable and reproducible without a Gradle build.
"""

from __future__ import annotations

import html
import math
import re
import shutil
import subprocess
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]
IMAGE_ROOT = ROOT / "docs" / "images"
DIAGRAM_DIR = IMAGE_ROOT / "readme-diagrams"
CHART_DIR = IMAGE_ROOT / "readme-charts"

W = 1440
H = 900

PALETTE = {
    "ink": "#36424F",
    "muted": "#667085",
    "paper": "#FFFDF8",
    "frame": "#7DAED3",
    "sky": "#B7DBF4",
    "mint": "#BFE8D2",
    "peach": "#FFD5B8",
    "rose": "#F5B5C8",
    "lavender": "#CBBCEB",
    "lemon": "#F8E8A6",
    "aqua": "#B9E7E2",
    "coral": "#F5A69C",
    "blue": "#8FC4EA",
    "green": "#9FD7B7",
    "orange": "#F4BE8D",
    "purple": "#B9A9DD",
    "red": "#EFA3A8",
}

SERIES_COLORS = [
    PALETTE["blue"],
    PALETTE["orange"],
    PALETTE["green"],
    PALETTE["rose"],
    PALETTE["lavender"],
    PALETTE["lemon"],
]


def esc(value: object) -> str:
    return html.escape(str(value), quote=True)


def wrap(text: str, width: int = 22) -> list[str]:
    words = str(text).replace("`", "").split()
    lines: list[str] = []
    current = ""
    for word in words:
        next_line = word if not current else f"{current} {word}"
        if len(next_line) <= width:
            current = next_line
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines or [""]


def style() -> str:
    return """
    <defs>
      <filter id="softShadow" x="-8%" y="-8%" width="116%" height="116%">
        <feDropShadow dx="0" dy="8" stdDeviation="6" flood-color="#26384A" flood-opacity="0.10"/>
      </filter>
      <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
        <path d="M 0 0 L 10 5 L 0 10 z" fill="#6F8396"/>
      </marker>
    </defs>
    <style>
      .title { font-family: 'Architects Daughter', cursive; font-size: 48px; fill: #36424F; }
      .subtitle { font-family: 'Comic Mono', monospace; font-size: 20px; fill: #667085; }
      .label { font-family: 'Architects Daughter', cursive; font-size: 27px; fill: #36424F; }
      .small { font-family: 'Comic Mono', monospace; font-size: 18px; fill: #536273; }
      .tiny { font-family: 'Comic Mono', monospace; font-size: 15px; fill: #667085; }
      .card { stroke: #D7E0EA; stroke-width: 2; filter: url(#softShadow); }
      .line { stroke: #6F8396; stroke-width: 3; fill: none; marker-end: url(#arrow); }
      .thin { stroke: #A5B5C4; stroke-width: 2; fill: none; marker-end: url(#arrow); }
      .dash { stroke: #A5B5C4; stroke-width: 2; stroke-dasharray: 8 8; fill: none; marker-end: url(#arrow); }
    </style>
    """


def open_svg(title: str, subtitle: str, width: int = W, height: int = H) -> list[str]:
    footer = f"github.com/bluetape4k/bluetape4k-graph | bluetape4k-graph | {title}"
    out = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">',
        style(),
        f'<rect x="0" y="0" width="{width}" height="{height}" fill="{PALETTE["paper"]}"/>',
        f'<rect x="24" y="24" width="{width-48}" height="{height-48}" rx="22" fill="none" stroke="{PALETTE["frame"]}" stroke-width="4"/>',
        f'<circle cx="{width-64}" cy="64" r="15" fill="{PALETTE["peach"]}" opacity="0.95"/>',
        f'<circle cx="{width-104}" cy="64" r="15" fill="{PALETTE["mint"]}" opacity="0.95"/>',
        f'<circle cx="{width-144}" cy="64" r="15" fill="{PALETTE["lavender"]}" opacity="0.95"/>',
        f'<text x="72" y="82" class="title">{esc(title)}</text>',
        f'<text x="74" y="118" class="subtitle">{esc(subtitle)}</text>',
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">{esc(footer)}</text>',
    ]
    return out


def close_svg(out: list[str]) -> str:
    out.append("</svg>")
    return "\n".join(out) + "\n"


def card(out: list[str], x: float, y: float, w: float, h: float, title: str, lines: list[str], fill: str) -> None:
    out.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" class="card"/>')
    out.append(f'<text x="{x+22}" y="{y+40}" class="label">{esc(title)}</text>')
    yy = y + 76
    for line in lines[:5]:
        out.append(f'<text x="{x+24}" y="{yy}" class="small">{esc(line)}</text>')
        yy += 28


def arrow(out: list[str], x1: float, y1: float, x2: float, y2: float, label: str = "", cls: str = "line") -> None:
    out.append(f'<path d="M {x1} {y1} C {(x1+x2)/2} {y1}, {(x1+x2)/2} {y2}, {x2} {y2}" class="{cls}"/>')
    if label:
        lx = (x1 + x2) / 2
        ly = (y1 + y2) / 2 - 18
        label_w = max(54, min(190, len(label) * 8 + 26))
        out.append(f'<rect x="{lx-label_w/2}" y="{ly-25}" width="{label_w}" height="31" rx="9" fill="#FFFDF8" opacity="0.94"/>')
        out.append(f'<text x="{lx}" y="{ly}" text-anchor="middle" class="tiny">{esc(label)}</text>')


def module_title(slug: str) -> str:
    text = slug.replace("root-readme-", "").replace("-01", "")
    text = re.sub(r"^(graph-graph-|examples-|benchmark-|ktor-)", "", text)
    text = re.sub(r"-(architecture|class|sequence|data-flow|erd)-\\d+$", "", text)
    text = text.replace("-", " ")
    return " ".join(part.upper() if part in {"io", "api", "db", "bom"} else part.capitalize() for part in text.split())


def diagram_kind(slug: str) -> str:
    if "sequence" in slug:
        return "sequence"
    if "class" in slug or "erd" in slug or "capability-matrix" in slug:
        return "class"
    if "data-flow" in slug:
        return "data"
    return "architecture"


def architecture_svg(slug: str) -> str:
    title = "Bluetape4k Graph Overview" if slug.startswith("root") else f"{module_title(slug)} Architecture"
    subtitle = "Current README and module source model"
    out = open_svg(title, subtitle)
    if slug.startswith("root"):
        cols = [
            ("Application layer", ["Ktor plugin", "Spring Boot auto-config", "Domain examples"]),
            ("Graph contract", ["GraphOperations", "Suspend APIs", "Schema DSL", "Algorithms"]),
            ("Backend adapters", ["Neo4j", "Memgraph", "AGE", "TinkerGraph", "FalkorDB"]),
            ("Bulk I/O", ["CSV", "Jackson NDJSON", "GraphML", "OkIO"]),
        ]
    elif "benchmark" in slug:
        cols = [
            ("Fixtures", ["JMH state", "Testcontainers", "TinkerGraph"]),
            ("Benchmarks", ["Graph DB matrix", "Domain workloads", "API model", "Graph-IO"]),
            ("Reports", ["Markdown tables", "JSON result", "Pastel charts"]),
        ]
    elif "graph-age" in slug:
        cols = [("Kotlin API", ["GraphOperations", "Transactions", "Schema DSL"]), ("AGE adapter", ["Cypher over SQL", "Exposed/JDBC", "Agtype mapping"]), ("PostgreSQL AGE", ["graphs", "vertices", "edges"])]
    elif "graph-neo4j" in slug:
        cols = [("Kotlin API", ["GraphOperations", "Suspend session", "Cache wrapper"]), ("Neo4j adapter", ["Driver session", "Cypher builder", "Record mapper"]), ("Neo4j", ["nodes", "relationships", "indexes"])]
    elif "falkordb" in slug:
        cols = [("Kotlin API", ["GraphOperations", "Merge/upsert", "Traversal"]), ("FalkorDB adapter", ["jfalkordb", "openCypher subset", "Redis connection"]), ("FalkorDB", ["graph key", "nodes", "edges"])]
    elif "graph-ktor" in slug or "ktor-graph" in slug:
        cols = [("Ktor app", ["Routes", "ApplicationCall", "GraphPlugin"]), ("Plugin", ["Graph registry", "Backend neutral", "Lifecycle owned by caller"]), ("GraphOperations", ["TinkerGraph demo", "Neo4j/AGE helpers", "Responses"])]
    elif "bom" in slug:
        cols = [("Consumers", ["Gradle projects", "Published modules"]), ("Graph BOM", ["Aligned versions", "Dependency constraints", "Catalog friendly"]), ("Modules", ["graph-core", "backends", "graph-io", "ktor"])]
    else:
        name = module_title(slug)
        cols = [("Example goal", [name, "domain model", "sample query"]), ("Graph model", ["vertices", "edges", "properties"]), ("Backends", ["AGE", "Neo4j", "Memgraph", "TinkerGraph", "FalkorDB"])]
    x0 = 78
    gap = 28
    w = (W - 156 - gap * (len(cols) - 1)) / len(cols)
    colors = [PALETTE["sky"], PALETTE["mint"], PALETTE["peach"], PALETTE["lavender"]]
    for i, (name, lines) in enumerate(cols):
        x = x0 + i * (w + gap)
        card(out, x, 210, w, 360, name, lines, colors[i % len(colors)])
        if i:
            arrow(out, x - gap + 4, 390, x - 8, 390, "maps")
    card(out, 164, 650, 1112, 120, "Source truth", ["README module contract, AGENTS layout, Kotlin source names, benchmark result tables"], PALETTE["aqua"])
    return close_svg(out)


def class_svg(slug: str) -> str:
    title = "Backend Capability Matrix" if "capability" in slug else f"{module_title(slug)} Class Model"
    subtitle = "Contracts, data classes, and adapter responsibilities"
    out = open_svg(title, subtitle)
    if "capability" in slug:
        cards = [
            ("Neo4j", ["Cypher", "native MERGE", "transactions", "indexes"]),
            ("Memgraph", ["Neo4j protocol", "Cypher", "fast writes", "containers"]),
            ("AGE", ["PostgreSQL", "Cypher SQL", "transaction fallback", "DB consolidation"]),
            ("TinkerGraph", ["in-memory", "Gremlin", "tests/examples", "fast local"]),
            ("FalkorDB", ["Redis module", "openCypher subset", "read-mostly fit"]),
        ]
    elif "graph-core" in slug:
        cards = [
            ("GraphElementId", ["backend id", "typed wrapper", "Serializable"]),
            ("GraphVertex", ["id", "label", "properties"]),
            ("GraphEdge", ["startId", "endId", "label", "properties"]),
            ("GraphPath", ["PathStep list", "cost", "metadata"]),
            ("GraphOperations", ["session", "repositories", "traversal"]),
        ]
    elif "graph-age" in slug:
        cards = [("AgeGraphOperations", ["GraphOperations", "transactions", "schema manager"]), ("AgeSql", ["Cypher SQL wrapper", "parameters", "graph name"]), ("AgeTypeParser", ["agtype", "vertex", "edge", "path"]), ("PostgreSQLAgeServer", ["Testcontainer", "extension init"])]
    elif "graph-neo4j" in slug:
        cards = [("Neo4jGraphOperations", ["driver", "session", "repositories"]), ("Neo4jCoroutineSession", ["Flow bridge", "suspend tx", "cancellation"]), ("Neo4jRecordMapper", ["node", "relationship", "path"]), ("CachingNeo4jGraphOperations", ["read cache", "write memo", "invalidate"])]
    else:
        cards = [("Domain vertex", ["id", "label", "properties"]), ("Domain edge", ["source", "target", "relationship"]), ("Scenario service", ["seed", "query", "explain"]), ("Backend runner", ["AGE", "Neo4j", "TinkerGraph"])]
    cols = min(5, len(cards))
    w = 240 if cols >= 5 else 300
    gap = 24
    total = cols * w + (cols - 1) * gap
    x0 = (W - total) / 2
    for i, (name, lines) in enumerate(cards):
        x = x0 + i * (w + gap)
        card(out, x, 230, w, 330, name, lines, [PALETTE["mint"], PALETTE["lavender"], PALETTE["peach"], PALETTE["sky"], PALETTE["lemon"]][i % 5])
        if i:
            arrow(out, x - gap + 4, 395, x - 8, 395, "uses", "thin")
    card(out, 220, 655, 1000, 110, "Read path", ["API contracts stay backend-neutral; adapters translate to Cypher, Gremlin, SQL, or Redis graph calls"], PALETTE["aqua"])
    return close_svg(out)


def data_svg(slug: str) -> str:
    title = "Bluetape4k Graph Data Flow" if slug.startswith("root-readme-") else f"{module_title(slug)} Data Flow"
    subtitle = "Example data moves from domain input to graph query output"
    out = open_svg(title, subtitle)
    nodes = [
        ("Input records", ["files", "events", "domain rows"], PALETTE["lemon"]),
        ("Mapper", ["labels", "edge types", "properties"], PALETTE["sky"]),
        ("GraphOperations", ["create vertices", "create edges", "traverse"], PALETTE["mint"]),
        ("Backend graph", ["storage", "indexes", "queries"], PALETTE["peach"]),
        ("Result view", ["paths", "neighbors", "recommendations"], PALETTE["lavender"]),
    ]
    x = 80
    box_w = 220
    step = 270
    for i, (name, lines, color) in enumerate(nodes):
        card(out, x, 260, box_w, 250, name, lines, color)
        if i < len(nodes) - 1:
            arrow(out, x + box_w, 385, x + step - 14, 385, ["map", "write", "query", "shape"][i], "line")
        x += step
    card(out, 190, 640, 1060, 105, "Purpose", ["Shows the example's full flow so readers understand setup, graph loading, traversal, and output."], PALETTE["aqua"])
    return close_svg(out)


def sequence_svg(slug: str) -> str:
    title = "Bluetape4k Graph Sequence" if slug.startswith("root-readme-") else f"{module_title(slug)} Sequence"
    subtitle = "Happy-path operation flow with labels placed away from cards"
    out = open_svg(title, subtitle)
    actors = ["Caller", "GraphOperations", "Adapter", "Graph DB", "Result"]
    xs = [150, 430, 710, 990, 1260]
    for x, actor in zip(xs, actors):
        out.append(f'<rect x="{x-90}" y="190" width="180" height="62" rx="14" fill="{PALETTE["sky"]}" class="card"/>')
        out.append(f'<text x="{x}" y="230" text-anchor="middle" class="label">{esc(actor)}</text>')
        out.append(f'<line x1="{x}" y1="270" x2="{x}" y2="710" stroke="#C2CED9" stroke-width="3" stroke-dasharray="8 8"/>')
    steps = [
        ("create/query request", 310),
        ("validate contract", 385),
        ("translate native query", 460),
        ("execute", 535),
        ("map vertex/edge/path", 610),
        ("return domain result", 685),
    ]
    pairs = [(0, 1), (1, 2), (2, 3), (3, 2), (2, 4), (4, 0)]
    for (label, y), (a, b) in zip(steps, pairs):
        cls = "dash" if b < a else "line"
        arrow(out, xs[a] + (70 if b > a else -70), y, xs[b] - (70 if b > a else -70), y, label, cls)
    return close_svg(out)


def generic_diagram(slug: str) -> str:
    kind = diagram_kind(slug)
    if kind == "sequence":
        return sequence_svg(slug)
    if kind == "class":
        return class_svg(slug)
    if kind == "data":
        return data_svg(slug)
    return architecture_svg(slug)


def chart_svg(title: str, subtitle: str, categories: list[str], series: list[tuple[str, list[float]]], unit: str, lower_is_better: bool = True) -> str:
    width = 1500
    height = max(860, 220 + len(categories) * max(62, 22 * len(series)))
    out = open_svg(title, subtitle, width, height)
    left = 350
    right = width - 110
    top = 195
    row_h = max(64, 24 * len(series))
    max_value = max(max(vals) for _, vals in series if vals)
    if max_value <= 0:
        max_value = 1
    out.append(f'<text x="{left}" y="{top-24}" class="tiny">Unit: {esc(unit)} / {"lower is better" if lower_is_better else "higher is better"}</text>')
    for i, (name, _) in enumerate(series):
        lx = left + i * 190
        out.append(f'<rect x="{lx}" y="{top-58}" width="24" height="16" rx="5" fill="{SERIES_COLORS[i % len(SERIES_COLORS)]}"/>')
        out.append(f'<text x="{lx+34}" y="{top-44}" class="tiny">{esc(name)}</text>')
    for ci, cat in enumerate(categories):
        y = top + ci * row_h + 36
        for li, line in enumerate(wrap(cat, 25)[:2]):
            out.append(f'<text x="86" y="{y + li*21}" class="tiny">{esc(line)}</text>')
        out.append(f'<line x1="{left}" y1="{y+8}" x2="{right}" y2="{y+8}" stroke="#E7EEF5" stroke-width="2"/>')
        for si, (name, vals) in enumerate(series):
            value = vals[ci]
            bar_h = 15
            yy = y - 25 + si * 21
            bw = (right - left - 120) * math.sqrt(value / max_value)
            out.append(f'<rect x="{left}" y="{yy}" width="{max(2, bw)}" height="{bar_h}" rx="6" fill="{SERIES_COLORS[si % len(SERIES_COLORS)]}"/>')
            out.append(f'<text x="{left + bw + 12}" y="{yy+13}" class="tiny">{value:g}</text>')
    return close_svg(out)


CHARTS: dict[str, tuple[str, str, list[str], list[tuple[str, list[float]]], str, bool]] = {
    "root-readme-module-chart-01": (
        "Module Composition", "Repository areas counted from current layout",
        ["Graph backends", "Graph-IO modules", "Examples", "Benchmarks", "Integrations", "BOM"],
        [("module count", [6, 6, 12, 4, 2, 1])], "modules", False,
    ),
    "graph-api-model-chart-01": (
        "API Model Smoke Benchmark", "PageRank throughput on TinkerGraph fixture",
        ["Sync", "Virtual Thread", "Coroutine Flow"],
        [("throughput", [138943.484, 40283.460, 36879.554])], "ops/s", False,
    ),
    "graph-api-model-production-chart-01": (
        "API Model Production Benchmark", "Concurrency latency, TinkerGraph fixture",
        ["BFS VT", "BFS Coroutine", "VT creation", "Coroutine launch"],
        [("10", [31.151, 38.795, 8.867, 0.586]), ("100", [142.730, 157.676, 28.225, 4.826]), ("1000", [921.873, 1151.154, 200.810, 47.657])], "us/op", True,
    ),
    "authz-inheritance-postgresql-latency-chart-01": (
        "Authorization Inheritance Latency", "PostgreSQL AGE fixture, medium rows",
        ["shallow", "deep inheritance", "deny heavy", "wide groups"],
        [("AGE/Cypher", [57.382, 604.833, 448.263, 250.083]), ("PostgreSQL CTE", [12.085, 9.385, 9.450, 1.521]), ("PostgreSQL iterative", [1.056, 2.102, 4.310, 3.658])], "ms/op", True,
    ),
    "authz-inheritance-adoption-latency-chart-01": (
        "Authorization Adoption Probe", "Large path-shaped traversal rows",
        ["long chain", "deep wide"],
        [("Neo4j Cypher", [12.731, 56.467]), ("PostgreSQL CTE", [55.364, 11.596]), ("PostgreSQL iterative", [47.568, 27.836])], "ms/op", True,
    ),
    "graph-domain-workload-testcontainers-latency-chart-01": (
        "Domain Workload Latency", "Social, IAM, fraud, and code graph workloads",
        ["Code deps", "Reverse deps", "Fraud neighborhood", "Fraud path", "IAM reachability", "Social fan-out", "Two-hop candidates"],
        [("TinkerGraph", [0.009, 0.006, 0.038, 1.000, 0.033, 0.030, 0.164]), ("Neo4j", [0.530, 0.579, 1.119, 0.509, 0.508, 0.991, 2.756]), ("Memgraph", [0.306, 0.348, 0.620, 0.405, 0.300, 0.549, 1.755])], "ms/op", True,
    ),
    "graph-write-ingestion-testcontainers-latency-chart-01": (
        "Graph Write Ingestion", "100 and 1,000 row sustained write latency",
        ["Vertex 100", "Vertex 1000", "Edge 100", "Edge 1000", "Mixed 100", "Mixed 1000", "Repeated 100", "Repeated 1000"],
        [("TinkerGraph", [2.606, 11.902, 3.298, 18.971, 7.102, 30.819, 32.221, 154.891]), ("Neo4j", [2.740, 8.499, 3.788, 13.762, 7.352, 23.510, 35.181, 113.940]), ("Memgraph", [0.965, 5.429, 1.190, 7.572, 2.199, 13.336, 11.456, 66.612]), ("AGE", [1.347, 9.100, 9.436, 260.430, 27.426, 279.704, 149.935, 1428.239]), ("FalkorDB", [1.467, 6.210, 32.347, 3026.397, 51.437, 3354.140, 263.976, 17285.768])], "ms/op", True,
    ),
    "graph-write-ingestion-10k-testcontainers-latency-chart-01": (
        "Graph Write Ingestion 10k", "Selective 10,000 row latency",
        ["Vertex-only", "Edge-only", "Mixed", "Repeated mixed"],
        [("TinkerGraph", [71.152, 161.907, 272.236, 1243.229]), ("Neo4j", [67.135, 108.949, 203.669, 919.678]), ("Memgraph", [53.518, 76.105, 134.994, 647.574])], "ms/op", True,
    ),
    "graph-db-medium-testcontainers-latency-chart-01": (
        "Graph DB Medium Benchmark", "Common GraphOperations contract, medium fixture",
        ["batchInsertCycle", "countPersons", "oneHopNeighbors", "shortestPath"],
        [("TinkerGraph", [44.967, 0.308, 0.003, 0.019]), ("Neo4j", [15.690, 0.528, 0.665, 0.700]), ("Memgraph", [11.364, 1.341, 0.308, 0.386]), ("AGE", [309.090, 2.176, 10.175, 12.420]), ("FalkorDB", [1929.180, 0.197, 1.046, 0.512])], "ms/op", True,
    ),
    "graph-db-testcontainers-latency-chart-01": (
        "Graph DB Small Benchmark", "Common GraphOperations contract, small fixture",
        ["batchInsertCycle", "countPersons", "oneHopNeighbors", "shortestPath"],
        [("TinkerGraph", [5.704, 0.030, 0.004, 0.022]), ("Neo4j", [6.903, 0.779, 0.807, 0.795]), ("Memgraph", [1.954, 0.394, 0.346, 0.344]), ("AGE", [21.580, 0.645, 0.941, 1.279]), ("FalkorDB", [38.670, 0.195, 0.639, 0.290])], "ms/op", True,
    ),
    "graph-io-export-latency-chart-01": (
        "Graph-IO Export Latency", "1,000 vertices and 2,000 edges to file",
        ["CSV", "Jackson2 NDJSON", "Jackson3 NDJSON", "GraphML"],
        [("Sync", [1.017, 1.194, 1.275, 2.582]), ("VirtualThread", [1.185, 1.221, 1.300, 4.192]), ("Suspend", [1.477, 1.318, 1.329, 2.455])], "ms/op", True,
    ),
    "graph-io-import-latency-chart-01": (
        "Graph-IO Import Latency", "File to TinkerGraph",
        ["CSV", "Jackson2 NDJSON", "Jackson3 NDJSON", "GraphML"],
        [("Sync", [17.854, 18.831, 19.852, 21.111]), ("VirtualThread", [17.624, 18.120, 19.302, 21.095]), ("Suspend", [23.393, 151.415, 155.279, 22.380])], "ms/op", True,
    ),
    "graph-io-roundtrip-latency-chart-01": (
        "Graph-IO Round Trip Latency", "Export plus import on small dataset",
        ["CSV", "Jackson2 NDJSON", "Jackson3 NDJSON", "GraphML"],
        [("Sync", [19.752, 18.880, 19.142, 21.707]), ("VirtualThread", [17.629, 18.677, 18.956, 21.450]), ("Suspend", [18.512, 151.615, 164.172, 21.236])], "ms/op", True,
    ),
    "weighted-shortest-path-dijkstra-optimization-chart-01": (
        "Weighted Shortest Path Optimization", "Dijkstra baseline versus accepted candidate",
        ["100 vertices", "1,000 vertices", "10,000 vertices"],
        [("Baseline", [0.699, 8.072, 86.092]), ("Candidate", [0.710, 7.501, 83.417])], "ms/op", True,
    ),
    "graph-vt-vertex-latency-chart-01": (
        "Virtual Thread Vertex Latency", "Blocking adapter overhead baseline",
        ["findVertexById", "findVerticesByLabel", "neighbors"],
        [("Sync", [1.758, 2.485, 2.985]), ("VirtualThread", [8.055, 8.679, 10.595])], "us/op", True,
    ),
    "graph-vt-traversal-latency-chart-01": (
        "Virtual Thread Traversal Latency", "Traversal operations through adapter",
        ["shortestPath", "allPaths"],
        [("Sync", [22.031, 21.045]), ("VirtualThread", [31.540, 32.882])], "us/op", True,
    ),
    "graph-vt-algorithm-latency-chart-01": (
        "Virtual Thread Algorithm Latency", "Algorithm operations through adapter",
        ["bfs", "dfs", "pageRank"],
        [("Sync", [4.216, 4.277, 7.270]), ("VirtualThread", [12.588, 12.115, 15.257])], "us/op", True,
    ),
    "graph-vt-sync-latency-chart-01": (
        "Sync Baseline Latency", "GraphOperations baseline rows",
        ["findVertexById", "findVerticesByLabel", "neighbors", "shortestPath", "allPaths", "bfs", "dfs", "pageRank"],
        [("Sync", [1.758, 2.485, 2.985, 22.031, 21.045, 4.216, 4.277, 7.270])], "us/op", True,
    ),
    "graph-vt-overhead-chart-01": (
        "Virtual Thread Overhead", "Adapter overhead versus sync baseline",
        ["findVertexById", "findVerticesByLabel", "neighbors", "shortestPath", "allPaths", "bfs", "dfs", "pageRank"],
        [("absolute overhead", [6.297, 6.194, 7.610, 9.509, 11.837, 8.372, 7.838, 7.987])], "us/op", True,
    ),
}


def collect_png_refs() -> set[Path]:
    refs: set[Path] = set()
    pattern = re.compile(r"\(([^)]*(?:docs/images|../images)/[^)]+\.png)\)")
    for markdown in ROOT.rglob("*.md"):
        text = markdown.read_text()
        for match in pattern.findall(text):
            target = (markdown.parent / match).resolve()
            if target.is_relative_to(ROOT):
                refs.add(target.relative_to(ROOT))
    refs.update({
        Path("docs/images/readme-diagrams/root-readme-data-flow-01.png"),
        Path("docs/images/readme-diagrams/root-readme-sequence-01.png"),
    })
    return refs


def render_png(svg_path: Path) -> None:
    png_path = svg_path.with_suffix(".png")
    subprocess.run(["cairosvg", str(svg_path), "-o", str(png_path), "--scale", "2"], check=True)


def write_visual(path: Path) -> None:
    slug = path.stem
    svg_path = path.with_suffix(".svg")
    svg_path.parent.mkdir(parents=True, exist_ok=True)
    if path.parent == CHART_DIR.relative_to(ROOT) or path.parent == CHART_DIR:
        spec = CHARTS.get(slug)
        if not spec:
            raise KeyError(f"missing chart spec: {slug}")
        svg = chart_svg(*spec)
    elif slug in CHARTS:
        svg = chart_svg(*CHARTS[slug])
    else:
        svg = generic_diagram(slug)
    svg = "\n".join(line.rstrip() for line in svg.splitlines()) + "\n"
    svg_path.write_text(svg)
    ElementTree.fromstring(svg)
    render_png(svg_path)


def main() -> None:
    if IMAGE_ROOT.exists():
        shutil.rmtree(IMAGE_ROOT)
    DIAGRAM_DIR.mkdir(parents=True, exist_ok=True)
    CHART_DIR.mkdir(parents=True, exist_ok=True)
    refs = collect_png_refs()
    for rel in sorted(refs):
        write_visual(ROOT / rel)
    print(f"generated {len(refs)} README PNG visuals and matching SVG sources")


if __name__ == "__main__":
    main()
