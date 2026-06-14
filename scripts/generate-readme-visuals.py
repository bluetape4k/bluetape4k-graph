#!/usr/bin/env python3
"""Generate README SVG/PNG visuals for bluetape4k-graph.

The diagrams intentionally use a small, local SVG vocabulary so the rendered
assets stay reviewable and reproducible without a Gradle build.
"""

from __future__ import annotations

import html
import math
import re
import subprocess
from pathlib import Path
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]
IMAGE_ROOT = ROOT / "docs" / "images"
DIAGRAM_DIR = IMAGE_ROOT / "readme-diagrams"
CHART_DIR = IMAGE_ROOT / "readme-charts"

W = 1440
H = 900
REPOSITORY_URL = "https://github.com/bluetape4k/bluetape4k-graph"
PROJECT_NAME = "bluetape4k-graph"

PALETTE = {
    "ink": "#111827",
    "muted": "#4B5563",
    "paper": "#F8FAFC",
    "frame": "#60A5FA",
    "sky": "#DBEAFE",
    "mint": "#DCFCE7",
    "peach": "#FED7AA",
    "rose": "#FEE2E2",
    "lavender": "#EDE9FE",
    "lemon": "#FEF3C7",
    "aqua": "#CCFBF1",
    "coral": "#FECACA",
    "blue": "#60A5FA",
    "green": "#34D399",
    "orange": "#FB923C",
    "purple": "#A78BFA",
    "red": "#FB7185",
    "slate": "#94A3B8",
}

SERIES_COLORS = [
    PALETTE["blue"],
    PALETTE["orange"],
    PALETTE["green"],
    PALETTE["rose"],
    PALETTE["lavender"],
    PALETTE["lemon"],
]

CHART_SERIES = [
    ("#FFE7D8", "#F97316"),
    ("#DBEAFE", "#2563EB"),
    ("#DCFCE7", "#16A34A"),
    ("#FCE7F3", "#DB2777"),
    ("#EDE9FE", "#7C3AED"),
    ("#FEF3C7", "#D97706"),
    ("#CCFBF1", "#0D9488"),
    ("#FEE2E2", "#DC2626"),
]

CARD_STROKES = {
    PALETTE["sky"]: "#3B82F6",
    PALETTE["mint"]: "#22C55E",
    PALETTE["peach"]: "#F97316",
    PALETTE["rose"]: "#EF4444",
    PALETTE["lavender"]: "#8B5CF6",
    PALETTE["lemon"]: "#EAB308",
    PALETTE["aqua"]: "#14B8A6",
    PALETTE["coral"]: "#F43F5E",
}


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
        <feDropShadow dx="0" dy="8" stdDeviation="6" flood-color="#0F172A" flood-opacity="0.14"/>
      </filter>
      <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
        <path d="M 0 0 L 10 5 L 0 10 z" fill="#2563EB"/>
      </marker>
    </defs>
    <style>
      .title { font-family: 'Architects Daughter', cursive; font-size: 48px; fill: #111827; }
      .subtitle { font-family: 'Comic Mono', monospace; font-size: 20px; fill: #4B5563; }
      .label { font-family: 'Architects Daughter', cursive; font-size: 27px; fill: #111827; }
      .small { font-family: 'Comic Mono', monospace; font-size: 18px; fill: #374151; }
      .tiny { font-family: 'Comic Mono', monospace; font-size: 15px; fill: #4B5563; }
      .card { filter: url(#softShadow); }
      .line { stroke: #2563EB; stroke-width: 3; fill: none; marker-end: url(#arrow); }
      .thin { stroke: #60A5FA; stroke-width: 2; fill: none; marker-end: url(#arrow); }
      .dash { stroke: #64748B; stroke-width: 2; stroke-dasharray: 8 8; fill: none; marker-end: url(#arrow); }
    </style>
    """


def footer_module(title: str) -> str:
    module = re.sub(r"\s+(Architecture|Class Model|Class|Sequence|Data Flow)$", "", title)
    return module.strip() or PROJECT_NAME


def open_svg(title: str, subtitle: str, width: int = W, height: int = H) -> list[str]:
    footer = f"{REPOSITORY_URL} | project: {PROJECT_NAME} | module: {footer_module(title)}"
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
    stroke = CARD_STROKES.get(fill, PALETTE["slate"])
    out.append(
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" '
        f'stroke="{stroke}" stroke-width="3" class="card"/>'
    )
    out.append(f'<rect x="{x+10}" y="{y+10}" width="12" height="{h-20}" rx="6" fill="{stroke}" opacity="0.95"/>')
    out.append(f'<text x="{x+22}" y="{y+40}" class="label">{esc(title)}</text>')
    yy = y + 76
    for line in lines[:5]:
        out.append(f'<text x="{x+24}" y="{yy}" class="small">{esc(line)}</text>')
        yy += 28


def arrow(out: list[str], x1: float, y1: float, x2: float, y2: float, label: str = "", cls: str = "line") -> None:
    out.append(f'<path d="M {x1} {y1} C {(x1+x2)/2} {y1}, {(x1+x2)/2} {y2}, {x2} {y2}" class="{cls}"/>')
    if label:
        lx = (x1 + x2) / 2
        ly = (y1 + y2) / 2 - 28
        out.append(
            f'<text x="{lx}" y="{ly}" text-anchor="middle" class="tiny" '
            f'stroke="{PALETTE["paper"]}" stroke-width="7" stroke-linejoin="round">{esc(label)}</text>'
        )
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
    if slug == "graph-graph-core-architecture-01":
        return graph_core_architecture_overview_svg()
    if slug == "graph-graph-core-architecture-10":
        return graph_core_path_lifecycle_svg()
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
            ("Reports", ["Markdown tables", "JSON result", "Light charts"]),
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


def graph_core_path_lifecycle_svg() -> str:
    width, height = 1600, 980
    out = open_svg(
        "GraphPath Lifecycle",
        "Path construction, derived views, and traversal algorithm outputs",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / GraphPath Lifecycle</text>'
    )
    out.append('<rect x="70" y="150" width="1460" height="690" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    steps = [
        ("No path", ["emptyGraphPath()", "GraphPath.EMPTY", "isEmpty == true"], PALETTE["rose"]),
        ("Construct", ["graphPathOf(...)", "PathStep list", "VertexStep / EdgeStep"], PALETTE["sky"]),
        ("Normalize", ["steps alternate", "vertices derived", "edges derived"], PALETTE["mint"]),
        ("Measure", ["length = edges.size", "totalWeight default", "weighted cost optional"], PALETTE["lemon"]),
        ("Return", ["shortestPath", "allPaths", "aStarPath"], PALETTE["lavender"]),
    ]
    x = 95
    for i, (title, lines, fill) in enumerate(steps):
        card(out, x, 260, 250, 210, title, lines, fill)
        if i < len(steps) - 1:
            arrow(out, x + 250, 365, x + 283, 365, ["build", "view", "count", "emit"][i], "line")
        x += 285

    out.append('<text x="800" y="570" text-anchor="middle" class="tiny">example step sequence</text>')
    sequence = [
        ("VertexStep(A)", PALETTE["sky"]),
        ("EdgeStep(KNOWS)", PALETTE["lemon"]),
        ("VertexStep(B)", PALETTE["sky"]),
        ("EdgeStep(WORKS_AT)", PALETTE["lemon"]),
        ("VertexStep(Company)", PALETTE["sky"]),
    ]
    x = 135
    for i, (label, fill) in enumerate(sequence):
        stroke = CARD_STROKES[fill]
        out.append(f'<rect x="{x}" y="605" width="240" height="58" rx="12" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
        out.append(f'<text x="{x+120}" y="641" text-anchor="middle" class="small">{esc(label)}</text>')
        if i < len(sequence) - 1:
            arrow(out, x + 240, 634, x + 286, 634, "", "thin")
        x += 290

    card(out, 385, 705, 830, 125, "Contract", ["GraphPath does not mutate; builders create value objects.", "Traversal and algorithm repositories return path results."], PALETTE["aqua"])

    out.append('<rect x="150" y="865" width="1300" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="800" y="898" text-anchor="middle" class="tiny">'
        'Source: GraphPath.kt and GraphTraversalRepository; weighted runners may override totalWeight with accumulated cost.</text>'
    )
    return close_svg(out)


def graph_core_architecture_overview_svg() -> str:
    width, height = 1760, 1040
    out = open_svg(
        "Graph Core Architecture",
        "Backend-neutral graph contracts, models, schema DSL, algorithms, and adapter-facing APIs",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / Architecture Overview</text>'
    )
    out.append('<rect x="70" y="150" width="1620" height="740" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    layers = [
        ("Application Code", 120, 200, 260, 120, PALETTE["sky"], ["sync, suspend, or", "virtual-thread callers"]),
        ("API Facade", 470, 185, 360, 150, PALETTE["mint"], ["GraphOperations", "GraphSuspendOperations", "GraphVirtualThreadOperations"]),
        ("Repository Contracts", 930, 185, 400, 150, PALETTE["lemon"], ["Session / Vertex / Edge", "Traversal / Algorithm", "Transaction / Merge / Schema"]),
        ("Adapters", 1430, 190, 240, 145, PALETTE["lavender"], ["Neo4j, Memgraph", "AGE, TinkerGraph", "FalkorDB"]),
        ("Domain Models", 190, 470, 350, 175, PALETTE["aqua"], ["GraphElementId", "GraphVertex / GraphEdge", "GraphPath / PathStep", "Options and scores"]),
        ("Schema DSL", 650, 470, 320, 175, PALETTE["rose"], ["VertexLabel / EdgeLabel", "PropertyDef", "GraphSchemaManager"]),
        ("Algorithm Helpers", 1080, 470, 330, 175, PALETTE["peach"], ["Dijkstra / A*", "BFS / DFS / cycles", "PageRank / components"]),
        ("Execution Bridges", 520, 710, 520, 150, PALETTE["sky"], ["Coroutine repositories expose", "Flow and suspend APIs", "VT adapters wrap sync operations"]),
    ]
    for title, x, y, w, h, fill, lines in layers:
        card(out, x, y, w, h, title, lines, fill)

    arrow(out, 380, 260, 468, 260, "calls", "line")
    arrow(out, 830, 260, 928, 260, "composes", "line")
    arrow(out, 1330, 260, 1428, 260, "implements", "line")
    arrow(out, 650, 335, 365, 468, "returns", "thin")
    arrow(out, 1040, 335, 810, 468, "validates", "thin")
    arrow(out, 1130, 335, 1240, 468, "traverses", "thin")
    arrow(out, 830, 645, 780, 708, "sync/suspend/vt", "thin")

    out.append('<rect x="150" y="910" width="1460" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="880" y="943" text-anchor="middle" class="tiny">'
        'Source: graph-core README plus repository/model/schema/algo/vt package structure; arrows show API ownership boundaries.</text>'
    )
    return close_svg(out)


def graph_core_model_class_svg() -> str:
    width, height = 1600, 1040
    out = open_svg(
        "Graph Core Model Classes",
        "Backend-neutral IDs, immutable vertex/edge records, and traversal direction enum",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / Model Classes</text>'
    )
    out.append('<rect x="70" y="150" width="1460" height="710" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def class_box(x: float, y: float, w: float, h: float, title: str, stereotype: str, lines: list[str], fill: str) -> None:
        stroke = CARD_STROKES.get(fill, PALETTE["slate"])
        out.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="3" class="card"/>'
        )
        out.append(f'<text x="{x+w/2}" y="{y+34}" text-anchor="middle" class="tiny">{esc(stereotype)}</text>')
        out.append(f'<text x="{x+w/2}" y="{y+68}" text-anchor="middle" class="label">{esc(title)}</text>')
        out.append(f'<line x1="{x}" y1="{y+88}" x2="{x+w}" y2="{y+88}" stroke="{stroke}" stroke-width="2"/>')
        yy = y + 124
        for line in lines:
            out.append(f'<text x="{x+26}" y="{yy}" class="small">{esc(line)}</text>')
            yy += 28

    class_box(
        600,
        215,
        400,
        235,
        "GraphElementId",
        "<<value class>>",
        ["value: String", "of(value: String)", "of(value: Long)", "validates nonblank String"],
        PALETTE["mint"],
    )
    class_box(
        145,
        575,
        390,
        235,
        "GraphVertex",
        "<<data class>>",
        ["id: GraphElementId", "label: String", "properties: Map<String, Any?>", "implements Serializable"],
        PALETTE["sky"],
    )
    class_box(
        600,
        575,
        440,
        265,
        "GraphEdge",
        "<<data class>>",
        ["id: GraphElementId", "label: String", "startId: GraphElementId", "endId: GraphElementId", "properties: Map<String, Any?>"],
        PALETTE["lemon"],
    )
    class_box(
        1125,
        575,
        310,
        235,
        "Direction",
        "<<enum>>",
        ["OUTGOING", "INCOMING", "BOTH", "used by traversal options"],
        PALETTE["rose"],
    )

    card(out, 1125, 245, 310, 110, "Traversal Options", ["neighbors and degree", "choose edge direction"], PALETTE["lavender"])
    arrow(out, 340, 572, 700, 452, "id", "thin")
    arrow(out, 820, 572, 820, 452, "id/start/end", "thin")
    arrow(out, 1280, 355, 1280, 572, "direction", "thin")

    out.append('<rect x="150" y="890" width="1300" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="800" y="923" text-anchor="middle" class="tiny">'
        'Source: graph-core model package; graphElementIdOf converts Any IDs, and GraphEdge stores start/end vertex IDs.</text>'
    )
    return close_svg(out)


def graph_core_path_class_svg() -> str:
    width, height = 1640, 1060
    out = open_svg(
        "Graph Core Path Classes",
        "Path results alternate vertex and edge steps while exposing derived path views",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / Path Classes</text>'
    )
    out.append('<rect x="70" y="150" width="1500" height="750" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def class_box(x: float, y: float, w: float, h: float, title: str, stereotype: str, lines: list[str], fill: str) -> None:
        stroke = CARD_STROKES.get(fill, PALETTE["slate"])
        out.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="3" class="card"/>'
        )
        out.append(f'<text x="{x+w/2}" y="{y+34}" text-anchor="middle" class="tiny">{esc(stereotype)}</text>')
        out.append(f'<text x="{x+w/2}" y="{y+68}" text-anchor="middle" class="label">{esc(title)}</text>')
        out.append(f'<line x1="{x}" y1="{y+88}" x2="{x+w}" y2="{y+88}" stroke="{stroke}" stroke-width="2"/>')
        yy = y + 124
        for line in lines:
            out.append(f'<text x="{x+26}" y="{yy}" class="small">{esc(line)}</text>')
            yy += 28

    class_box(135, 230, 330, 185, "PathStep", "<<sealed class>>", ["VertexStep(vertex)", "EdgeStep(edge)"], PALETTE["mint"])
    class_box(110, 525, 360, 175, "VertexStep", "<<data class>>", ["vertex: GraphVertex", "path node snapshot"], PALETTE["sky"])
    class_box(520, 525, 360, 175, "EdgeStep", "<<data class>>", ["edge: GraphEdge", "relationship snapshot"], PALETTE["lemon"])
    class_box(
        1010,
        250,
        430,
        295,
        "GraphPath",
        "<<data class>>",
        ["steps: List<PathStep>", "totalWeight: Double", "vertices: List<GraphVertex>", "edges: List<GraphEdge>", "length: Int = edges.size"],
        PALETTE["lavender"],
    )

    arrow(out, 290, 523, 275, 417, "inherits", "thin")
    arrow(out, 700, 523, 380, 417, "inherits", "thin")
    arrow(out, 1010, 390, 468, 320, "steps", "thin")

    out.append('<text x="820" y="735" text-anchor="middle" class="tiny">alternating order</text>')
    step_items = [
        ("VertexStep(A)", PALETTE["sky"]),
        ("EdgeStep(KNOWS)", PALETTE["lemon"]),
        ("VertexStep(B)", PALETTE["sky"]),
        ("EdgeStep(WORKS_AT)", PALETTE["lemon"]),
        ("VertexStep(Company)", PALETTE["sky"]),
    ]
    x = 120
    for i, (label, fill) in enumerate(step_items):
        stroke = CARD_STROKES[fill]
        out.append(f'<rect x="{x}" y="760" width="240" height="58" rx="12" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
        out.append(f'<text x="{x+120}" y="796" text-anchor="middle" class="small">{esc(label)}</text>')
        if i < len(step_items) - 1:
            arrow(out, x + 240, 789, x + 288, 789, "", "thin")
        x += 290

    out.append('<rect x="150" y="925" width="1340" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="820" y="958" text-anchor="middle" class="tiny">'
        'Source: GraphPath.kt; default totalWeight is edge count, while weighted algorithms may set accumulated cost.</text>'
    )
    return close_svg(out)


def graph_core_repository_class_svg() -> str:
    width, height = 1760, 1120
    out = open_svg(
        "Graph Core Repository Contracts",
        "Facade interfaces compose session, vertex, edge, traversal, algorithm, and optional capabilities",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / Repository Contracts</text>'
    )
    out.append('<rect x="70" y="150" width="1620" height="800" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    columns = [
        (
            "GraphOperations",
            "sync facade",
            ["GraphSession", "GraphVertexRepository", "GraphEdgeRepository", "GraphGenericRepository"],
            ["blocking calls", "List<T> results", "backend implements directly"],
            PALETTE["sky"],
        ),
        (
            "GraphSuspendOperations",
            "coroutine facade",
            ["GraphSuspendSession", "GraphSuspendVertexRepository", "GraphSuspendEdgeRepository", "GraphSuspendGenericRepository"],
            ["suspend calls", "Flow<T> streams", "cancellation-aware adapters"],
            PALETTE["mint"],
        ),
        (
            "GraphVirtualThreadOperations",
            "virtual-thread facade",
            ["AutoCloseable", "Session + Vertex/Edge repos", "Traversal + Algorithm repos", "CompletableFuture API"],
            ["CompletableFuture<T>", "Async method surface", "wraps sync operations"],
            PALETTE["lavender"],
        ),
    ]

    x_positions = [115, 630, 1145]
    for x, (title, subtitle, inherited, behavior, fill) in zip(x_positions, columns):
        card(out, x, 215, 470, 230, title, [subtitle] + inherited, fill)
        card(out, x, 505, 470, 205, "Repository Profile", behavior, PALETTE["lemon"] if fill != PALETTE["lemon"] else PALETTE["aqua"])
        arrow(out, x + 235, 447, x + 235, 503, "uses", "thin")

    capability_cards = [
        ("Traversal", ["neighbors", "shortestPath", "allPaths", "aStarPath"], PALETTE["aqua"]),
        ("Algorithms", ["pageRank", "degree", "components", "bfs / dfs / cycles"], PALETTE["peach"]),
        ("Optional Extensions", ["transaction blocks", "schemaManager", "mergeVertex / mergeEdge"], PALETTE["rose"]),
    ]
    x = 160
    for title, lines, fill in capability_cards:
        card(out, x, 735, 430, 180, title, lines, fill)
        x += 520

    out.append('<rect x="170" y="975" width="1420" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="880" y="1008" text-anchor="middle" class="tiny">'
        'Source: graph-core repository package; GraphGenericRepository bundles traversal and algorithm repositories.</text>'
    )
    return close_svg(out)


def graph_core_schema_class_svg() -> str:
    width, height = 1700, 1060
    out = open_svg(
        "Graph Core Schema DSL",
        "Type-safe label/property declarations feed backend schema managers for indexes and constraints",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / Schema DSL</text>'
    )
    out.append('<rect x="70" y="150" width="1560" height="750" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    card(
        out,
        125,
        235,
        400,
        225,
        "PropertyHolder",
        ["properties: List<PropertyDef<*>>", "string / integer / long", "boolean / stringList / json", "localDate / localDateTime / enum"],
        PALETTE["mint"],
    )
    card(
        out,
        610,
        235,
        390,
        205,
        "PropertyDef<T>",
        ["name: String", "type: KClass<out T>", "created by DSL functions"],
        PALETTE["lemon"],
    )
    card(
        out,
        1100,
        230,
        420,
        260,
        "GraphSchemaManager",
        ["createIndex(label, property)", "createUniqueConstraint(...)", "dropIndex(label, property)", "listIndexes()", "listConstraints()"],
        PALETTE["lavender"],
    )
    card(
        out,
        125,
        590,
        400,
        180,
        "VertexLabel",
        ["label: String", "extends PropertyHolder", "object PersonLabel : VertexLabel"],
        PALETTE["sky"],
    )
    card(
        out,
        610,
        590,
        390,
        200,
        "EdgeLabel",
        ["label: String", "from: VertexLabel", "to: VertexLabel", "extends PropertyHolder"],
        PALETTE["peach"],
    )
    card(
        out,
        1100,
        585,
        420,
        205,
        "Suspend Schema",
        ["GraphSuspendSchemaManager", "BlockingGraphSuspendSchemaManager", "Dispatchers.IO bridge"],
        PALETTE["aqua"],
    )

    arrow(out, 525, 350, 608, 350, "creates", "thin")
    arrow(out, 330, 588, 330, 462, "inherits", "thin")
    arrow(out, 805, 588, 380, 462, "inherits", "thin")
    arrow(out, 1000, 350, 1098, 350, "uses", "thin")
    arrow(out, 1310, 490, 1310, 583, "suspend", "thin")

    out.append('<rect x="170" y="925" width="1360" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="850" y="958" text-anchor="middle" class="tiny">'
        'Source: graph-core schema package; unsupported schema DDL fails explicitly instead of silently no-oping.</text>'
    )
    return close_svg(out)


def class_svg(slug: str) -> str:
    if slug == "graph-graph-core-class-02":
        return graph_core_model_class_svg()
    if slug == "graph-graph-core-class-03":
        return graph_core_path_class_svg()
    if slug == "graph-graph-core-class-04":
        return graph_core_repository_class_svg()
    if slug == "graph-graph-core-class-05":
        return graph_core_schema_class_svg()
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
    if slug == "graph-graph-core-sequence-06":
        return graph_core_sequence_svg(
            "createVertex",
            "GraphOperations creates a backend-neutral vertex and maps the native generated id.",
            [
                ("user", "User Code", "blue"),
                ("ops", "GraphOperations", "green"),
                ("adapter", "Backend Adapter", "amber"),
                ("db", "Graph Database", "rose"),
            ],
            [
                ("user", "ops", "createVertex(label, properties)", 330, "blue", False, 300),
                ("ops", "adapter", "validate label and properties", 420, "green", False, 315),
                ("adapter", "db", "execute CREATE vertex query", 515, "amber", False, 305),
                ("db", "adapter", "generated native id", 610, "teal", True, 250),
                ("adapter", "ops", "map to GraphElementId", 705, "teal", True, 275),
                ("ops", "user", "GraphVertex", 805, "teal", True, 190),
            ],
        )
    if slug == "graph-graph-core-sequence-07":
        return graph_core_sequence_svg(
            "shortestPath",
            "GraphOperations resolves cached paths first, then asks the backend when traversal is required.",
            [
                ("user", "User Code", "blue"),
                ("ops", "GraphOperations", "green"),
                ("algo", "Path Algorithm", "lemon"),
                ("adapter", "Backend Adapter", "amber"),
                ("db", "Graph Database", "rose"),
            ],
            [
                ("user", "ops", "shortestPath(fromId, toId, label, maxDepth)", 330, "blue", False, 430),
                ("ops", "algo", "check cache and route strategy", 415, "green", False, 335),
                ("algo", "ops", "cached GraphPath if present", 500, "teal", True, 300),
                ("algo", "adapter", "BFS or backend shortest path query", 585, "amber", False, 360),
                ("adapter", "db", "execute path search", 670, "amber", False, 245),
                ("db", "adapter", "vertices and edges", 755, "teal", True, 230),
                ("adapter", "algo", "construct GraphPath", 840, "teal", True, 240),
                ("algo", "ops", "cache result", 925, "teal", True, 200),
                ("ops", "user", "GraphPath?", 1010, "teal", True, 190),
            ],
        )
    if slug == "graph-graph-core-sequence-08":
        return graph_core_sequence_svg(
            "neighbors",
            "GraphOperations traverses adjacent vertices with direction, relationship label, and depth.",
            [
                ("user", "User Code", "blue"),
                ("ops", "GraphOperations", "green"),
                ("adapter", "Backend Adapter", "amber"),
                ("db", "Graph Database", "rose"),
            ],
            [
                ("user", "ops", "neighbors(startId, label, direction, depth)", 330, "blue", False, 390),
                ("ops", "adapter", "build traversal query", 420, "green", False, 265),
                ("adapter", "db", "relationship traversal query", 515, "amber", False, 305),
                ("db", "adapter", "neighboring vertices", 610, "teal", True, 250),
                ("adapter", "ops", "List<GraphVertex>", 705, "teal", True, 245),
                ("ops", "user", "List<GraphVertex>", 805, "teal", True, 245),
            ],
        )
    if slug == "graph-graph-core-sequence-09":
        return graph_core_sequence_svg(
            "createEdge",
            "GraphOperations creates a typed relationship between existing vertices and maps backend metadata.",
            [
                ("user", "User Code", "blue"),
                ("ops", "GraphOperations", "green"),
                ("adapter", "Backend Adapter", "amber"),
                ("db", "Graph Database", "rose"),
            ],
            [
                ("user", "ops", "createEdge(fromId, toId, label, properties)", 330, "blue", False, 390),
                ("ops", "adapter", "validate endpoints and edge label", 420, "green", False, 330),
                ("adapter", "db", "MATCH endpoints and CREATE relationship", 515, "amber", False, 390),
                ("db", "adapter", "edge id and metadata", 610, "teal", True, 250),
                ("adapter", "ops", "map to GraphEdge", 705, "teal", True, 230),
                ("ops", "user", "GraphEdge", 805, "teal", True, 190),
            ],
        )
    if slug == "graph-graph-age-sequence-06":
        return graph_core_sequence_svg(
            "createVertex",
            "AGE adapter runs blocking PostgreSQL AGE work on Dispatchers.IO and parses agtype into GraphVertex.",
            [
                ("user", "User Code", "blue"),
                ("ops", "AgeGraphOperations", "green"),
                ("io", "Dispatchers.IO", "amber"),
                ("tx", "Exposed Transaction", "rose"),
                ("age", "PostgreSQL AGE", "teal"),
                ("parser", "AgeTypeParser", "lemon"),
                ("result", "GraphVertex", "blue"),
            ],
            [
                ("user", "ops", "createVertex(Person, props)", 330, "blue", False, 310),
                ("ops", "io", "withContext(Dispatchers.IO)", 415, "green", False, 305),
                ("io", "tx", "transaction(database)", 500, "amber", False, 255),
                ("tx", "age", "LOAD age and SET search_path", 585, "amber", False, 330),
                ("tx", "age", "exec(AgeSql.createVertex(...))", 670, "amber", False, 340),
                ("age", "tx", "agtype vertex row", 755, "teal", True, 240),
                ("tx", "parser", "parseVertex(agtype)", 840, "green", False, 250),
                ("parser", "result", "GraphVertex(id, label, props)", 925, "green", False, 320),
                ("result", "ops", "GraphVertex", 1010, "teal", True, 205),
                ("ops", "user", "GraphVertex", 1095, "teal", True, 205),
            ],
            module_name="graph-age",
        )
    if slug == "graph-graph-age-sequence-07":
        return graph_core_sequence_svg(
            "createEdge",
            "AGE adapter creates a relationship through Cypher SQL and maps the agtype edge result.",
            [
                ("user", "User Code", "blue"),
                ("ops", "AgeGraphOperations", "green"),
                ("io", "Dispatchers.IO", "amber"),
                ("tx", "Exposed Transaction", "rose"),
                ("age", "PostgreSQL AGE", "teal"),
                ("parser", "AgeTypeParser", "lemon"),
                ("result", "GraphEdge", "blue"),
            ],
            [
                ("user", "ops", "createEdge(fromId, toId, label, props)", 330, "blue", False, 390),
                ("ops", "io", "withContext(Dispatchers.IO)", 415, "green", False, 305),
                ("io", "tx", "transaction(database)", 500, "amber", False, 255),
                ("tx", "age", "MATCH vertices and CREATE edge", 585, "amber", False, 360),
                ("age", "tx", "agtype edge row", 670, "teal", True, 225),
                ("tx", "parser", "parseEdge(agtype)", 755, "green", False, 235),
                ("parser", "result", "GraphEdge(id, endpoints, props)", 840, "green", False, 330),
                ("result", "ops", "GraphEdge", 925, "teal", True, 190),
                ("ops", "user", "GraphEdge", 1010, "teal", True, 190),
            ],
            module_name="graph-age",
        )
    if slug == "graph-graph-age-sequence-08":
        return graph_core_sequence_svg(
            "shortestPath",
            "AGE shortestPath executes Cypher over PostgreSQL AGE and parses agtype path elements.",
            [
                ("user", "User Code", "blue"),
                ("ops", "AgeGraphOperations", "green"),
                ("io", "Dispatchers.IO", "amber"),
                ("tx", "Exposed Transaction", "rose"),
                ("age", "PostgreSQL AGE", "teal"),
                ("parser", "AgeTypeParser", "lemon"),
                ("result", "GraphPath", "blue"),
            ],
            [
                ("user", "ops", "shortestPath(fromId, toId, label, maxDepth)", 330, "blue", False, 430),
                ("ops", "io", "withContext(Dispatchers.IO)", 415, "green", False, 305),
                ("io", "tx", "transaction(database)", 500, "amber", False, 255),
                ("tx", "age", "exec(AgeSql.shortestPath(...))", 585, "amber", False, 345),
                ("age", "tx", "agtype path row", 670, "teal", True, 225),
                ("tx", "parser", "parsePath(agtype)", 755, "green", False, 235),
                ("parser", "result", "GraphPath(steps)", 840, "green", False, 230),
                ("result", "ops", "GraphPath or null", 925, "teal", True, 245),
                ("ops", "user", "GraphPath?", 1010, "teal", True, 190),
            ],
            module_name="graph-age",
        )
    if slug == "graph-graph-age-sequence-09":
        return graph_core_sequence_svg(
            "neighbors",
            "AGE neighbors query translates direction and label into Cypher SQL, then parses vertex rows.",
            [
                ("user", "User Code", "blue"),
                ("ops", "AgeGraphOperations", "green"),
                ("io", "Dispatchers.IO", "amber"),
                ("tx", "Exposed Transaction", "rose"),
                ("age", "PostgreSQL AGE", "teal"),
                ("parser", "AgeTypeParser", "lemon"),
                ("result", "List<GraphVertex>", "blue"),
            ],
            [
                ("user", "ops", "neighbors(startId, label, direction, depth)", 330, "blue", False, 410),
                ("ops", "io", "withContext(Dispatchers.IO)", 415, "green", False, 305),
                ("io", "tx", "transaction(database)", 500, "amber", False, 255),
                ("tx", "age", "exec(AgeSql.neighbors(...))", 585, "amber", False, 320),
                ("age", "tx", "agtype vertex rows", 670, "teal", True, 250),
                ("tx", "parser", "parseVertex(row)", 755, "green", False, 225),
                ("parser", "result", "List<GraphVertex>", 840, "green", False, 245),
                ("result", "ops", "List<GraphVertex>", 925, "teal", True, 245),
                ("ops", "user", "List<GraphVertex>", 1010, "teal", True, 245),
            ],
            module_name="graph-age",
        )
    if slug == "graph-graph-age-sequence-11":
        return graph_core_sequence_svg(
            "HikariCP",
            "Connection initialization loads AGE once and sets the graph search path before use.",
            [
                ("init", "Connection Init", "blue"),
                ("pool", "HikariCP Pool", "green"),
                ("pg", "PostgreSQL", "amber"),
                ("age", "AGE Extension", "rose"),
            ],
            [
                ("init", "pool", "connectionInitSql", 330, "blue", False, 245),
                ("pool", "pg", "open TCP connection", 420, "green", False, 270),
                ("pg", "pool", "connected", 510, "teal", True, 190),
                ("pool", "pg", "LOAD age", 600, "amber", False, 190),
                ("pg", "age", "extension available", 690, "amber", False, 245),
                ("age", "pg", "OK", 780, "teal", True, 155),
                ("pg", "pool", "SET search_path", 870, "amber", False, 245),
                ("pool", "init", "connection ready", 960, "teal", True, 230),
            ],
            module_name="graph-age",
        )
    if slug == "graph-graph-neo4j-sequence-06":
        return graph_core_sequence_svg(
            "createVertex",
            "Neo4j suspend operations bridge ReactiveSession publishers into coroutine-friendly mapped results.",
            [
                ("user", "User Code", "blue"),
                ("ops", "Neo4jSuspendOps", "green"),
                ("session", "ReactiveSession", "amber"),
                ("driver", "Neo4j Driver", "rose"),
                ("db", "Neo4j DB", "teal"),
                ("records", "Reactive Records", "lemon"),
                ("mapper", "Neo4jRecordMapper", "green"),
                ("result", "GraphVertex", "blue"),
            ],
            [
                ("user", "ops", "createVertex(label, props)", 330, "blue", False, 310),
                ("ops", "session", "session()", 415, "green", False, 165),
                ("session", "driver", "driver.session(ReactiveSession)", 500, "amber", False, 360),
                ("driver", "session", "ReactiveSession", 585, "teal", True, 230),
                ("ops", "session", "run(Query(CREATE...RETURN n))", 670, "green", False, 370),
                ("session", "db", "execute Cypher", 755, "amber", False, 220),
                ("db", "records", "Publisher<Record>", 840, "teal", True, 240),
                ("records", "mapper", "asReactiveFlow().toList().map", 925, "green", False, 360),
                ("mapper", "result", "recordToVertex(record)", 1010, "green", False, 280),
                ("result", "ops", "GraphVertex", 1095, "teal", True, 205),
                ("ops", "user", "GraphVertex", 1180, "teal", True, 205),
            ],
            module_name="graph-neo4j",
        )
    if slug == "graph-graph-neo4j-sequence-07":
        return graph_core_sequence_svg(
            "createEdge",
            "Neo4j createEdge matches endpoint elementIds, creates a relationship, and maps the returned record.",
            [
                ("user", "User Code", "blue"),
                ("ops", "Neo4jSuspendOps", "green"),
                ("session", "ReactiveSession", "amber"),
                ("driver", "Neo4j Driver", "rose"),
                ("db", "Neo4j DB", "teal"),
                ("records", "Reactive Records", "lemon"),
                ("mapper", "Neo4jRecordMapper", "green"),
                ("result", "GraphEdge", "blue"),
            ],
            [
                ("user", "ops", "createEdge(fromId, toId, label, props)", 330, "blue", False, 400),
                ("ops", "session", "session()", 415, "green", False, 165),
                ("session", "driver", "driver.session(ReactiveSession)", 500, "amber", False, 360),
                ("driver", "session", "ReactiveSession", 585, "teal", True, 230),
                ("ops", "session", "run(Query(MATCH...CREATE r))", 670, "green", False, 360),
                ("session", "db", "match endpoints and create edge", 755, "amber", False, 350),
                ("db", "records", "Publisher<Record>", 840, "teal", True, 240),
                ("records", "mapper", "asReactiveFlow().toList().map", 925, "green", False, 360),
                ("mapper", "result", "recordToEdge(record)", 1010, "green", False, 260),
                ("result", "ops", "GraphEdge", 1095, "teal", True, 190),
                ("ops", "user", "GraphEdge", 1180, "teal", True, 190),
            ],
            module_name="graph-neo4j",
        )
    if slug == "graph-graph-neo4j-sequence-08":
        return graph_core_sequence_svg(
            "shortestPath",
            "Unweighted shortestPath uses Neo4j Cypher directly; weighted paths fall back to the Dijkstra helper.",
            [
                ("user", "User Code", "blue"),
                ("ops", "Neo4jSuspendOps", "green"),
                ("options", "PathOptions", "amber"),
                ("session", "ReactiveSession", "rose"),
                ("db", "Neo4j DB", "teal"),
                ("records", "Reactive Records", "lemon"),
                ("mapper", "Neo4jRecordMapper", "green"),
                ("result", "GraphPath", "blue"),
            ],
            [
                ("user", "ops", "shortestPath(fromId, toId, options)", 330, "blue", False, 390),
                ("ops", "options", "check weightProperty", 415, "green", False, 270),
                ("options", "ops", "unweighted Cypher path", 500, "teal", True, 280),
                ("ops", "session", "session()", 585, "green", False, 165),
                ("ops", "session", "run(Query(shortestPath(...)))", 670, "green", False, 350),
                ("session", "db", "MATCH p = shortestPath", 755, "amber", False, 305),
                ("db", "records", "Publisher<Record>", 840, "teal", True, 240),
                ("records", "mapper", "asReactiveFlow().toList().map", 925, "green", False, 360),
                ("mapper", "result", "recordToPath(record)", 1010, "green", False, 255),
                ("result", "ops", "GraphPath or null", 1095, "teal", True, 245),
                ("ops", "user", "GraphPath?", 1180, "teal", True, 190),
            ],
            module_name="graph-neo4j",
        )
    if slug == "graph-graph-neo4j-sequence-10":
        return graph_core_sequence_svg(
            "Publisher to Coroutine",
            "Reactive Streams results are awaited, converted to Flow, materialized, and mapped before session close.",
            [
                ("caller", "Suspend Caller", "blue"),
                ("ops", "runQuery / flowQuery", "green"),
                ("session", "ReactiveSession", "amber"),
                ("result", "ReactiveResult", "rose"),
                ("publisher", "Publisher<Record>", "teal"),
                ("flow", "Kotlin Flow", "lemon"),
                ("mapper", "Mapper", "green"),
                ("close", "Session Close", "blue"),
            ],
            [
                ("caller", "ops", "invoke suspend query", 330, "blue", False, 280),
                ("ops", "session", "s.run(Query).awaitSingle()", 415, "green", False, 350),
                ("session", "result", "ReactiveResult", 500, "teal", True, 230),
                ("result", "publisher", "records()", 585, "amber", False, 165),
                ("publisher", "flow", "asReactiveFlow()", 670, "green", False, 240),
                ("flow", "mapper", "toList().map(mapper)", 755, "green", False, 265),
                ("mapper", "ops", "List<T>", 840, "teal", True, 170),
                ("ops", "close", "NonCancellable close", 925, "amber", False, 275),
                ("close", "session", "close<Void>().awaitFirstOrNull()", 1010, "teal", True, 365),
                ("ops", "caller", "mapped result", 1095, "teal", True, 220),
            ],
            module_name="graph-neo4j",
        )
    if slug == "examples-fraud-detection-examples-sequence-03":
        return graph_core_sequence_svg(
            "Fraud Analysis Flow",
            "The example records account transfers, then runs cycle, component, and PageRank analytics through GraphOperations.",
            [
                ("test", "Abstract Test", "blue"),
                ("service", "FraudService", "green"),
                ("ops", "GraphOperations", "amber"),
                ("graph", "Transfer Graph", "rose"),
                ("cycles", "Cycle Detection", "teal"),
                ("components", "Components", "lemon"),
                ("rank", "PageRank", "green"),
                ("review", "Review Result", "blue"),
            ],
            [
                ("test", "service", "initialize()", 330, "blue", False, 180),
                ("service", "ops", "graphExists / createGraph", 415, "green", False, 295),
                ("test", "service", "addAccount(Alice, Bob, Carol)", 500, "blue", False, 335),
                ("service", "graph", "create Account vertices", 585, "amber", False, 285),
                ("test", "service", "recordTransfer loop", 670, "blue", False, 250),
                ("service", "graph", "create TRANSFERRED_TO edges", 755, "amber", False, 345),
                ("service", "cycles", "detectCycles(maxDepth)", 840, "green", False, 275),
                ("service", "components", "connectedComponents(minSize)", 925, "green", False, 330),
                ("service", "rank", "pageRank(topK)", 1010, "green", False, 210),
                ("cycles", "review", "circular transfer", 1095, "teal", True, 240),
                ("components", "review", "suspicious cluster", 1180, "teal", True, 255),
                ("rank", "review", "high-risk ranking", 1265, "teal", True, 250),
            ],
            module_name="fraud-detection-examples",
        )
    if slug == "examples-recommendation-examples-sequence-03":
        return graph_core_sequence_svg(
            "Recommendation Flow",
            "The example turns purchases and follows into short graph traversals plus product PageRank.",
            [
                ("test", "Abstract Test", "blue"),
                ("service", "RecommendService", "green"),
                ("ops", "GraphOperations", "amber"),
                ("purchase", "Purchase Graph", "rose"),
                ("similar", "Similar Users", "teal"),
                ("follow", "Follow Graph", "lemon"),
                ("rank", "PageRank", "green"),
                ("result", "Recommendations", "blue"),
            ],
            [
                ("test", "service", "add users and products", 330, "blue", False, 285),
                ("service", "purchase", "create PURCHASED edges", 415, "amber", False, 300),
                ("test", "service", "recommendProducts(Alice)", 500, "blue", False, 300),
                ("service", "ops", "neighbors(Alice, PURCHASED)", 585, "green", False, 315),
                ("ops", "purchase", "owned products", 670, "teal", True, 210),
                ("service", "similar", "incoming PURCHASED buyers", 755, "green", False, 320),
                ("similar", "purchase", "other bought products", 840, "green", False, 275),
                ("purchase", "result", "exclude owned products", 925, "teal", True, 300),
                ("test", "service", "recommendFollows(Alice)", 1010, "blue", False, 290),
                ("service", "follow", "two-hop FOLLOWS", 1095, "green", False, 235),
                ("service", "rank", "pageRank(products)", 1180, "green", False, 245),
                ("rank", "result", "popular products", 1265, "teal", True, 235),
            ],
            module_name="recommendation-examples",
        )
    if slug == "examples-knowledge-graph-examples-sequence-03":
        return graph_core_sequence_svg(
            "Path Inference Flow",
            "The example links documents, entities, and concepts, then explains relationships with bounded allPaths traversal.",
            [
                ("test", "Abstract Test", "blue"),
                ("service", "KnowledgeService", "green"),
                ("ops", "GraphOperations", "amber"),
                ("facts", "Fact Graph", "rose"),
                ("mentions", "Mention Lookup", "teal"),
                ("related", "Related Traversal", "lemon"),
                ("paths", "allPaths Bound", "green"),
                ("result", "Explanation", "blue"),
            ],
            [
                ("test", "service", "add document/entities", 330, "blue", False, 275),
                ("service", "facts", "create vertices", 415, "amber", False, 210),
                ("test", "service", "mention / relate / classify", 500, "blue", False, 310),
                ("service", "facts", "create typed edges", 585, "amber", False, 235),
                ("test", "service", "findMentionedEntities(doc)", 670, "blue", False, 320),
                ("service", "mentions", "neighbors(MENTIONS)", 755, "green", False, 270),
                ("mentions", "result", "mentioned entities", 840, "teal", True, 260),
                ("test", "service", "findRelatedEntities(entity)", 925, "blue", False, 320),
                ("service", "related", "neighbors(RELATED_TO)", 1010, "green", False, 290),
                ("test", "service", "inferRelationshipPaths()", 1095, "blue", False, 305),
                ("service", "paths", "allPaths(maxDepth).take(maxPaths)", 1180, "green", False, 380),
                ("paths", "result", "bounded explanation paths", 1265, "teal", True, 330),
            ],
            module_name="knowledge-graph-examples",
        )
    title = "Bluetape4k Graph Sequence" if slug.startswith("root-readme-") else f"{module_title(slug)} Sequence"
    subtitle = "Happy-path operation flow with labels placed away from cards"
    out = open_svg(title, subtitle)
    actors = [
        ("Caller", 150, 190),
        ("GraphOperations", 445, 260),
        ("Adapter", 740, 190),
        ("Graph DB", 1035, 210),
        ("Result", 1280, 190),
    ]
    xs = [x for _, x, _ in actors]
    for actor, x, actor_w in actors:
        out.append(f'<rect x="{x-actor_w/2}" y="190" width="{actor_w}" height="62" rx="14" fill="{PALETTE["sky"]}" class="card"/>')
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


def graph_core_sequence_svg(
    title: str,
    subtitle: str,
    participants: list[tuple[str, str, str]],
    messages: list[tuple[str, str, str, int, str, bool, int]],
    module_name: str = "graph-core",
) -> str:
    width = max(1600, 500 + (len(participants) - 1) * 340)
    max_y = max(message[3] for message in messages)
    panel_bottom = max_y + 110
    height = panel_bottom + 140
    out = open_svg(title, subtitle, width, height)
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: {esc(module_name)} / {esc(title)}</text>'
    )
    out.insert(2, '<defs><marker id="seqArrow" markerWidth="6" markerHeight="6" refX="5.5" refY="3" orient="auto" markerUnits="strokeWidth"><path d="M 0 0 L 6 3 L 0 6 z" fill="#2E9C9B"/></marker></defs>')
    tones = {
        "blue": (PALETTE["sky"], "#3B82F6"),
        "green": (PALETTE["mint"], "#22C55E"),
        "amber": (PALETTE["lemon"], "#D97706"),
        "rose": (PALETTE["rose"], "#DB2777"),
        "lemon": (PALETTE["lemon"], "#D97706"),
        "teal": (PALETTE["aqua"], "#2E9C9B"),
    }
    left, right = 250, width - 250
    step = (right - left) / (len(participants) - 1)
    xs = {key: left + index * step for index, (key, _, _) in enumerate(participants)}
    top, bottom = 190, 835
    bottom = panel_bottom - 92
    out.append(f'<rect x="70" y="150" width="{width - 140}" height="{panel_bottom - 150}" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')
    for key, label, tone in participants:
        fill, stroke = tones[tone]
        x = xs[key]
        out.append(f'<rect x="{x - 135}" y="{top}" width="270" height="74" rx="12" fill="{fill}" stroke="{stroke}" stroke-width="2.2" filter="url(#softShadow)"/>')
        label_lines = wrap(label, 16)[:2]
        card_center_y = top + 37
        first_label_y = card_center_y if len(label_lines) == 1 else card_center_y - 12
        for li, line in enumerate(label_lines):
            out.append(
                f'<text x="{x}" y="{first_label_y + li * 24}" text-anchor="middle" '
                f'dominant-baseline="middle" class="label">{esc(line)}</text>'
            )
        out.append(f'<line x1="{x}" y1="{top + 92}" x2="{x}" y2="{bottom}" stroke="#B8C6D6" stroke-width="2.1" stroke-dasharray="8 8"/>')

    for key, _, tone in participants:
        related = [y for source, target, _, y, _, _, _ in messages if source == key or target == key]
        if not related:
            continue
        fill, stroke = tones[tone]
        y1, y2 = min(related) - 18, max(related) + 18
        x = xs[key]
        out.append(f'<rect x="{x - 8}" y="{y1}" width="16" height="{y2 - y1}" rx="6" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>')

    line_colors = {
        "blue": "#4F83BF",
        "green": "#2F9E6B",
        "amber": "#D6A441",
        "teal": "#2E9C9B",
    }
    badge_fills = {
        "blue": "#4F83BF",
        "green": "#2F9E6B",
        "amber": "#D6A441",
        "teal": "#2E9C9B",
    }
    for index, (source, target, label, y, tone, dashed, pill_w) in enumerate(messages, start=1):
        sx, tx = xs[source], xs[target]
        start = sx + (10 if tx > sx else -10)
        end = tx - (10 if tx > sx else -10)
        color = line_colors[tone]
        dash = ' stroke-dasharray="10 8"' if dashed else ""
        out.append(f'<line x1="{start}" y1="{y}" x2="{end}" y2="{y}" stroke="{color}" stroke-width="2.7"{dash} marker-end="url(#seqArrow)"/>')
        pill_x = (sx + tx) / 2 - pill_w / 2
        pill_y = y - 44
        out.append(f'<rect x="{pill_x:.1f}" y="{pill_y}" width="{pill_w}" height="30" rx="8" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
        out.append(f'<circle cx="{pill_x + 24:.1f}" cy="{pill_y + 15}" r="13" fill="{badge_fills[tone]}"/>')
        out.append(f'<text x="{pill_x + 24:.1f}" y="{pill_y + 20}" text-anchor="middle" class="tiny" style="fill:#FFFFFF">{index}</text>')
        out.append(f'<text x="{pill_x + 48:.1f}" y="{pill_y + 20}" class="tiny">{esc(label)}</text>')
    note_y = panel_bottom - 48
    out.append(f'<rect x="150" y="{note_y - 27}" width="{width - 300}" height="42" rx="11" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(f'<text x="{width / 2}" y="{note_y}" text-anchor="middle" class="tiny">Numbered labels sit above message lines; lifelines and activations stay separated from participant cards.</text>')
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
    width = 1760
    chart_top = 245
    bar_h = 34
    series_gap = 42
    row_h = max(96, len(series) * series_gap + 38)
    axis_gap = 52
    source_gap = 48
    footer_gap = 92
    axis_y = chart_top + len(categories) * row_h + 16
    panel_x = 56
    panel_y = 150
    panel_bottom = axis_y + axis_gap + source_gap
    panel_h = panel_bottom - panel_y
    height = panel_bottom + footer_gap
    out = open_svg(title, subtitle, width, height)
    longest_label = max(len(category) for category in categories)
    left = max(330, min(470, 170 + longest_label * 9))
    right_margin = 150
    plot_w = width - left - right_margin
    max_value = max(max(vals) for _, vals in series if vals)
    if max_value <= 0:
        max_value = 1
    positive_values = [value for _, vals in series for value in vals if value > 0]
    min_positive = min(positive_values) if positive_values else max_value
    use_log_scale = max_value / min_positive > 25
    scale_max = math.log10(max_value + 1) if use_log_scale else max_value
    out.append(f'<rect x="{panel_x}" y="{panel_y}" width="{width - panel_x * 2}" height="{panel_h}" rx="12" fill="#FFFFFF" stroke="#BFDBFE" stroke-width="2" filter="url(#softShadow)"/>')
    out.append(f'<text x="{panel_x + 32}" y="{panel_y + 46}" class="label">Measured ranking</text>')
    out.append(f'<text x="{left}" y="{panel_y + 46}" class="small">{esc(unit)} / {"lower is better" if lower_is_better else "higher is better"}</text>')
    scale_note = "log scale" if use_log_scale else "linear scale"
    out.append(f'<text x="{left + plot_w}" y="{panel_y + 76}" text-anchor="end" class="tiny">0 to {max_value:g} ({scale_note})</text>')
    for i, (name, _) in enumerate(series):
        fill, stroke = CHART_SERIES[i % len(CHART_SERIES)]
        lx = width - right_margin - (len(series) - i) * 165
        out.append(f'<rect x="{lx}" y="{panel_y + 30}" width="24" height="16" rx="5" fill="{fill}" stroke="{stroke}" stroke-width="1.7"/>')
        out.append(f'<text x="{lx + 34}" y="{panel_y + 44}" class="tiny">{esc(name)}</text>')
    for ti in range(5):
        if use_log_scale:
            ratio = ti / 4
            tick = 0 if ti == 0 else math.pow(10, scale_max * ratio) - 1
        else:
            tick = max_value * ti / 4
            ratio = tick / max_value
        tx = left + ratio * plot_w
        out.append(f'<line x1="{tx:.1f}" y1="{chart_top - 18}" x2="{tx:.1f}" y2="{axis_y}" stroke="#D7E2EC" stroke-width="1" stroke-dasharray="4 7"/>')
        out.append(f'<text x="{tx:.1f}" y="{axis_y + 30}" text-anchor="middle" class="tiny">{tick:g}</text>')
    out.append(f'<line x1="{left}" y1="{axis_y}" x2="{left + plot_w}" y2="{axis_y}" stroke="#94A3B8" stroke-width="1.2"/>')
    for ci, cat in enumerate(categories):
        y = chart_top + ci * row_h
        label_y = y + 18 + (len(series) - 1) * series_gap / 2
        for li, line in enumerate(wrap(cat, 28)[:2]):
            out.append(f'<text x="{left - 28}" y="{label_y + li * 22:.1f}" text-anchor="end" class="small">{esc(line)}</text>')
        out.append(f'<line x1="{left}" y1="{label_y + 10:.1f}" x2="{left + plot_w}" y2="{label_y + 10:.1f}" stroke="#E7EEF5" stroke-width="1.6"/>')
        for si, (name, vals) in enumerate(series):
            value = vals[ci]
            fill, stroke = CHART_SERIES[si % len(CHART_SERIES)]
            yy = y + si * series_gap
            if value > 0:
                measure = math.log10(value + 1) if use_log_scale else value
                bw = max(18, plot_w * measure / scale_max)
            else:
                bw = 0
            out.append(f'<rect x="{left}" y="{yy}" width="{plot_w}" height="{bar_h}" rx="8" fill="#EEF4F9" stroke="#D7E2EC" stroke-width="1.2"/>')
            if value > 0:
                out.append(f'<rect x="{left}" y="{yy}" width="{min(plot_w, bw):.1f}" height="{bar_h}" rx="8" fill="{fill}" stroke="{stroke}" stroke-width="2"/>')
            if value > 0 and bw > plot_w - 96:
                value_x = left + min(plot_w, bw) - 12
                anchor = ' text-anchor="end"'
            else:
                value_x = left + min(plot_w - 6, bw + 14 if value > 0 else 14)
                anchor = ""
            out.append(f'<text x="{value_x:.1f}" y="{yy + 23}" class="small"{anchor}>{value:g}</text>')
    out.append(f'<text x="{left}" y="{axis_y + 72}" class="tiny">Source: README benchmark table values. Complementary colors separate comparable series.</text>')
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
    CHART_DIR.mkdir(parents=True, exist_ok=True)
    for slug in sorted(CHARTS):
        write_visual(CHART_DIR / f"{slug}.png")
    print(f"generated {len(CHARTS)} README chart PNG visuals and matching SVG sources")


if __name__ == "__main__":
    main()
