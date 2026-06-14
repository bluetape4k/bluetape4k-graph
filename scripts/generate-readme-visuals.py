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
    if slug == "root-readme-overview-01":
        return root_readme_overview_svg()
    if slug == "bluetape4k-graph-architecture-01":
        return root_architecture_svg()
    if slug == "graph-graph-core-architecture-01":
        return graph_core_architecture_overview_svg()
    if slug == "graph-graph-core-architecture-10":
        return graph_core_path_lifecycle_svg()
    if slug == "graph-graph-core-architecture-11":
        return graph_core_operations_usage_svg()
    if slug == "graph-graph-core-architecture-12":
        return graph_core_schema_flow_svg()
    if slug == "graph-graph-core-architecture-13":
        return graph_core_crud_flow_svg()
    if slug == "graph-graph-core-architecture-14":
        return graph_core_path_algorithm_flow_svg()
    if slug == "graph-graph-age-architecture-01":
        return graph_age_layer_structure_svg()
    if slug == "graph-graph-age-architecture-02":
        return graph_age_execution_flow_svg()
    if slug == "graph-graph-age-architecture-10":
        return graph_age_agtype_parse_flow_svg()
    if slug == "graph-graph-age-architecture-12":
        return graph_age_test_environment_svg()
    if slug == "graph-graph-neo4j-architecture-01":
        return graph_neo4j_overview_svg()
    if slug == "graph-graph-neo4j-architecture-02":
        return graph_neo4j_reactive_coroutine_svg()
    if slug == "graph-graph-neo4j-architecture-09":
        return graph_neo4j_neighbors_pattern_svg()
    if slug == "graph-graph-neo4j-architecture-11":
        return graph_neo4j_data_model_svg()
    if slug == "graph-graph-neo4j-architecture-12":
        return graph_neo4j_test_environment_svg()
    if slug == "graph-graph-falkordb-architecture-01":
        return graph_falkordb_overview_svg()
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


def graph_core_operations_usage_svg() -> str:
    width, height = 1620, 980
    out = open_svg(
        "GraphOperations Usage States",
        "A backend facade moves from lifecycle setup through CRUD, traversal, optional capabilities, and close",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / GraphOperations Usage</text>'
    )
    out.append('<rect x="70" y="150" width="1480" height="690" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    top = [
        ("Obtain Facade", ["GraphOperations", "backend implementation", "same API per DB"], PALETTE["sky"]),
        ("Graph Lifecycle", ["createGraph(name)", "graphExists(name)", "dropGraph(name)"], PALETTE["mint"]),
        ("Vertex Ops", ["createVertex", "find / update / delete", "countVertices"], PALETTE["lemon"]),
        ("Edge Ops", ["createEdge", "find by label/start/end", "deleteEdge"], PALETTE["peach"]),
        ("Traversal", ["neighbors", "shortestPath", "allPaths / aStarPath"], PALETTE["lavender"]),
    ]
    x = 100
    for i, (title, lines, fill) in enumerate(top):
        card(out, x, 245, 250, 205, title, lines, fill)
        if i < len(top) - 1:
            arrow(out, x + 250, 348, x + 298, 348, ["init", "write", "link", "query"][i], "line")
        x += 300

    card(out, 250, 585, 360, 150, "Algorithms", ["pageRank / degree", "components", "bfs / dfs / cycles"], PALETTE["aqua"])
    card(out, 690, 585, 360, 150, "Optional Caps", ["transaction { }", "schemaManager()", "mergeVertex / mergeEdge"], PALETTE["rose"])
    card(out, 1130, 585, 250, 150, "Close", ["AutoCloseable", "release resources"], PALETTE["sky"])

    arrow(out, 1225, 450, 430, 583, "", "thin")
    out.append('<text x="445" y="560" text-anchor="middle" class="tiny">algorithms</text>')
    arrow(out, 810, 450, 870, 583, "", "thin")
    out.append('<text x="900" y="560" text-anchor="middle" class="tiny">optional</text>')
    arrow(out, 1380, 450, 1255, 583, "finally", "thin")

    out.append('<rect x="150" y="865" width="1320" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="810" y="898" text-anchor="middle" class="tiny">'
        'Source: GraphOperations repository contracts; unsupported optional capabilities fail explicitly instead of falling back silently.</text>'
    )
    return close_svg(out)


def graph_core_schema_flow_svg() -> str:
    width, height = 1640, 1000
    out = open_svg(
        "Schema DSL Flow",
        "Declare labels and properties once, then use them for schema DDL and graph operations",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / Schema DSL Flow</text>'
    )
    out.append('<rect x="70" y="150" width="1500" height="710" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    top = [
        ("Declare Labels", ["object PersonLabel", "object WorksAtLabel", "VertexLabel / EdgeLabel"], PALETTE["sky"]),
        ("Collect Properties", ["PropertyHolder.properties", "PropertyDef(name, type)", "typed DSL functions"], PALETTE["mint"]),
        ("Validate Names", ["GraphSchemaNames", "safe label/property", "fail before backend"], PALETTE["lemon"]),
        ("Schema Manager", ["createIndex", "createUniqueConstraint", "dropIndex / list"], PALETTE["lavender"]),
    ]
    x = 130
    for i, (title, lines, fill) in enumerate(top):
        card(out, x, 245, 285, 205, title, lines, fill)
        if i < len(top) - 1:
            arrow(out, x + 285, 348, x + 338, 348, ["defs", "props", "ddl"][i], "line")
        x += 360

    card(out, 190, 585, 380, 170, "Use In Operations", ["PersonLabel.label", "PersonLabel.email.name", "createVertex / filters"], PALETTE["aqua"])
    card(out, 650, 585, 380, 170, "Backend DDL", ["Neo4j / Memgraph", "TinkerGraph recorded index", "FalkorDB index support"], PALETTE["peach"])
    card(out, 1110, 585, 330, 170, "Unsupported", ["AGE portable DDL", "unique constraints gaps", "explicit exception"], PALETTE["rose"])

    arrow(out, 272, 450, 380, 583, "names", "thin")
    arrow(out, 1275, 450, 840, 583, "supported", "thin")
    arrow(out, 1360, 450, 1275, 583, "unsupported", "thin")

    out.append('<rect x="150" y="885" width="1340" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="820" y="918" text-anchor="middle" class="tiny">'
        'Source: schema package README and GraphSchemaManager.kt support matrix; no silent success for unsupported DDL.</text>'
    )
    return close_svg(out)


def graph_core_crud_flow_svg() -> str:
    width, height = 1660, 1000
    out = open_svg(
        "Graph Core CRUD Flow",
        "Vertex and edge repositories validate input, call backend adapters, and return model values",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / CRUD Flow</text>'
    )
    out.append('<rect x="70" y="150" width="1520" height="710" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    card(out, 130, 245, 280, 180, "Call API", ["GraphOperations", "suspend variant", "virtual-thread async"], PALETTE["sky"])
    card(out, 500, 210, 310, 180, "Validate Input", ["label not blank", "safe identifiers", "batch size / endpoints"], PALETTE["lemon"])
    card(out, 500, 495, 310, 180, "Batch Guard", ["createVertices", "createEdges", "validate before backend"], PALETTE["rose"])
    card(out, 900, 210, 290, 180, "Vertex Repo", ["create / find", "update / delete", "count by label"], PALETTE["mint"])
    card(out, 900, 495, 290, 180, "Edge Repo", ["create / find", "startId / endId", "delete by id"], PALETTE["peach"])
    card(out, 1270, 350, 240, 190, "Return", ["GraphVertex?", "GraphEdge list", "Boolean / Long"], PALETTE["lavender"])

    arrow(out, 410, 335, 498, 300, "request", "line")
    arrow(out, 655, 390, 655, 493, "bulk", "thin")
    arrow(out, 810, 300, 898, 300, "vertex", "line")
    arrow(out, 810, 585, 898, 585, "edge", "line")
    arrow(out, 1190, 300, 1268, 410, "models", "line")
    arrow(out, 1190, 585, 1268, 470, "models", "line")

    card(out, 300, 735, 1060, 115, "Failure Contract", [
        "Invalid input fails before backend query.",
        "Unsupported backend behavior should surface as explicit exceptions.",
    ], PALETTE["aqua"])

    out.append('<rect x="160" y="885" width="1340" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="830" y="918" text-anchor="middle" class="tiny">'
        'Source: GraphVertexRepository, GraphEdgeRepository, and GraphBatchValidation contracts.</text>'
    )
    return close_svg(out)


def graph_core_path_algorithm_flow_svg() -> str:
    width, height = 1760, 1040
    out = open_svg(
        "Graph Core Path Algorithm Flow",
        "Traversal calls choose backend-native paths or JVM fallback runners for weighted and graph algorithms",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-core / Path Algorithm Flow</text>'
    )
    out.append('<rect x="70" y="150" width="1620" height="720" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    arrow(out, 420, 318, 498, 318, "call", "line")
    arrow(out, 830, 300, 908, 275, "native", "thin")
    arrow(out, 830, 345, 908, 510, "weighted", "line")
    arrow(out, 1210, 275, 1298, 355, "neighbors", "thin")
    arrow(out, 1210, 510, 1298, 430, "incident", "line")
    arrow(out, 1450, 490, 860, 618, "", "line")
    arrow(out, 650, 400, 320, 618, "limits", "thin")
    arrow(out, 470, 705, 558, 705, "guard", "line")
    arrow(out, 880, 705, 968, 725, "found", "line")
    arrow(out, 1280, 725, 1368, 725, "models", "line")

    card(out, 120, 235, 300, 165, "Traversal Request", [
        "shortestPath / allPaths",
        "aStarPath",
        "bfs / dfs / pageRank",
    ], PALETTE["sky"])
    card(out, 500, 235, 330, 165, "Capability Gate", [
        "backend native query",
        "or JVM fallback",
        "PathOptions / algorithm options",
    ], PALETTE["mint"])
    card(out, 910, 200, 300, 150, "Unweighted Path", [
        "native traversal when present",
        "edgeLabel / direction",
        "maxDepth bound",
    ], PALETTE["lemon"])
    card(out, 910, 420, 300, 180, "Weighted Path", [
        "ShortestPathFallback",
        "Dijkstra / A* runner",
        "weightProperty required",
        "heuristic for A*",
    ], PALETTE["lavender"])
    card(out, 1300, 305, 300, 185, "Fetch Graph Slice", [
        "findVertexById",
        "incident edges",
        "OUT / IN / BOTH",
        "distinct + sorted for BOTH",
    ], PALETTE["aqua"])

    card(out, 170, 620, 300, 170, "Safety Guards", [
        "maxVisited / maxVertices",
        "maxDepth",
        "missing weight policy",
    ], PALETTE["rose"])
    card(out, 560, 620, 320, 170, "Frontier Loop", [
        "priority queue / queue / stack",
        "visited set",
        "cameFrom or rank state",
    ], PALETTE["peach"])
    card(out, 970, 650, 310, 150, "Reconstruct", [
        "PathReconstructor",
        "VertexStep / EdgeStep order",
        "totalWeight preserved",
    ], PALETTE["mint"])
    card(out, 1370, 625, 260, 195, "Return Models", [
        "GraphPath?",
        "TraversalVisit list",
        "PageRankScore list",
        "GraphCycle / Component",
    ], PALETTE["sky"])

    out.append('<rect x="170" y="892" width="1420" height="62" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="880" y="917" text-anchor="middle" dominant-baseline="middle" class="tiny">'
        'Source: ShortestPathFallback, DijkstraRunner, AStarRunner, BfsDfsRunner, PageRankCalculator, CycleDetector, UnionFind.</text>'
    )
    out.append(
        '<text x="880" y="940" text-anchor="middle" dominant-baseline="middle" class="tiny">'
        'Weighted paths validate positive finite weights; unweighted and analytics paths return the graph-core model contracts.</text>'
    )
    return close_svg(out)


def graph_age_layer_structure_svg() -> str:
    width, height = 1760, 1040
    out = open_svg(
        "Graph AGE Module Layer Structure",
        "Apache AGE adapter maps graph-core contracts to Cypher-over-SQL through Exposed and PostgreSQL JDBC",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-age / Layer Structure</text>'
    )
    out.append('<rect x="70" y="150" width="1620" height="720" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    # Draw arrows before cards so routed lines never cover card text.
    arrow(out, 500, 305, 558, 305, "API", "line")
    arrow(out, 960, 305, 872, 305, "wraps", "thin")
    arrow(out, 690, 380, 320, 468, "queries", "line")
    arrow(out, 730, 380, 1100, 468, "maps", "thin")
    arrow(out, 870, 365, 1318, 520, "fallback", "thin")
    arrow(out, 650, 550, 472, 550, "", "thin")
    arrow(out, 320, 630, 420, 678, "exec", "line")
    arrow(out, 585, 760, 712, 760, "transaction", "line")
    arrow(out, 1045, 760, 1148, 760, "JDBC", "line")
    arrow(out, 1320, 680, 1238, 632, "", "thin")

    card(out, 160, 230, 340, 150, "graph-core Contracts", [
        "GraphOperations",
        "GraphSuspendOperations",
        "model + repository APIs",
    ], PALETTE["sky"])
    card(out, 560, 230, 310, 150, "AGE Operations", [
        "AgeGraphOperations",
        "transaction scope",
        "schema / merge support",
    ], PALETTE["mint"])
    card(out, 960, 230, 310, 150, "Coroutine + Cache", [
        "AgeGraphSuspendOperations",
        "Dispatchers.IO",
        "CachingAgeGraphOperations",
    ], PALETTE["lemon"])

    card(out, 170, 470, 300, 160, "SQL Builder", [
        "AgeSql",
        "Cypher-over-SQL",
        "batch rows",
    ], PALETTE["rose"])
    card(out, 560, 470, 300, 160, "Property Serializer", [
        "safe identifiers",
        "literal escaping",
        "Kotlin -> Cypher values",
    ], PALETTE["lavender"])
    card(out, 950, 470, 330, 160, "agtype Parser", [
        "parseVertex / parseEdge",
        "parsePath",
        "Graph model mapping",
    ], PALETTE["aqua"])
    card(out, 1320, 405, 260, 190, "JVM Fallbacks", [
        "ShortestPathFallback",
        "BFS / DFS",
        "PageRank / cycles",
        "UnionFind components",
    ], PALETTE["peach"])

    card(out, 255, 680, 330, 160, "Exposed Transaction", [
        "org.jetbrains.exposed",
        "exec(...) result sets",
        "external DB ownership",
    ], PALETTE["mint"])
    card(out, 715, 680, 330, 160, "HikariCP + JDBC", [
        "PostgreSQL driver",
        "LOAD 'age'",
        "SET search_path",
    ], PALETTE["sky"])
    card(out, 1150, 680, 360, 160, "PostgreSQL AGE", [
        "ag_catalog.cypher",
        "graphName namespace",
        "agtype rows",
    ], PALETTE["lemon"])

    out.append('<rect x="180" y="892" width="1400" height="62" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="880" y="917" text-anchor="middle" dominant-baseline="middle" class="tiny">'
        'Source: AgeGraphOperations, AgeGraphSuspendOperations, CachingAgeGraphOperations, AgeSql, AgePropertySerializer, AgeTypeParser.</text>'
    )
    out.append(
        '<text x="880" y="940" text-anchor="middle" dominant-baseline="middle" class="tiny">'
        'AGE setup is connection-owned: HikariCP initializes LOAD age and search_path; graph-core models remain the public boundary.</text>'
    )
    return close_svg(out)


def graph_age_execution_flow_svg() -> str:
    width, height = 1760, 1040
    out = open_svg(
        "Apache AGE Execution Flow",
        "AGE operations wrap Cypher in SQL, execute through Exposed/JDBC, then parse agtype rows into graph-core models",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-age / Apache AGE Flow</text>'
    )
    out.append('<rect x="70" y="150" width="1620" height="720" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    # Lines first; cards stay visually dominant and labels are kept in open gutters.
    arrow(out, 390, 310, 468, 310, "call", "line")
    arrow(out, 760, 310, 838, 310, "transaction", "line")
    arrow(out, 1120, 310, 1198, 310, "SQL", "line")
    arrow(out, 1330, 400, 1330, 478, "execute", "line")
    arrow(out, 1200, 585, 1120, 735, "agtype", "line")
    arrow(out, 840, 740, 768, 740, "parse", "line")
    arrow(out, 980, 475, 1205, 385, "literals", "thin")
    arrow(out, 610, 385, 450, 540, "weighted", "thin")

    card(out, 120, 235, 270, 150, "Graph API Call", [
        "createVertex / createEdge",
        "neighbors / shortestPath",
        "batch + merge paths",
    ], PALETTE["sky"])
    card(out, 470, 235, 290, 150, "AgeGraphOperations", [
        "validate labels and ids",
        "choose native AGE query",
        "or fallback runner",
    ], PALETTE["mint"])
    card(out, 840, 235, 280, 150, "Exposed Boundary", [
        "exposedTransaction",
        "newSuspendedTransaction",
        "exec(...) callback",
    ], PALETTE["lemon"])
    card(out, 1200, 235, 300, 150, "AgeSql", [
        "wrap Cypher in SELECT",
        "ag_catalog.cypher",
        "typed result columns",
    ], PALETTE["rose"])

    card(out, 780, 455, 300, 150, "Property Serialization", [
        "AgePropertySerializer",
        "safe identifiers",
        "escaped Cypher literals",
    ], PALETTE["lavender"])
    card(out, 1200, 455, 300, 150, "PostgreSQL AGE", [
        "LOAD age + search_path",
        "MATCH / CREATE / RETURN",
        "agtype result rows",
    ], PALETTE["aqua"])
    card(out, 840, 665, 280, 150, "AgeTypeParser", [
        "parseVertex / parseEdge",
        "parsePath",
        "JSON-like agtype",
    ], PALETTE["peach"])
    card(out, 490, 665, 280, 150, "Graph Models", [
        "GraphVertex",
        "GraphEdge",
        "GraphPath",
    ], PALETTE["mint"])
    card(out, 150, 500, 300, 165, "Fallback Branch", [
        "weightProperty set",
        "ShortestPathFallback",
        "Dijkstra / A* uses edges",
    ], PALETTE["lemon"])

    out.append('<rect x="180" y="892" width="1400" height="62" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="880" y="917" text-anchor="middle" dominant-baseline="middle" class="tiny">'
        'Source: AgeGraphOperations CRUD/traversal methods, AgeSql.cypher, AgePropertySerializer, AgeTypeParser, AgeGraphSuspendOperations.</text>'
    )
    out.append(
        '<text x="880" y="940" text-anchor="middle" dominant-baseline="middle" class="tiny">'
        'Every connection must load AGE and set search_path; weighted shortestPath routes to graph-core JVM fallback instead of native Cypher.</text>'
    )
    return close_svg(out)


def graph_age_agtype_parse_flow_svg() -> str:
    width, height = 1840, 1180
    out = open_svg(
        "Apache AGE agtype Parse Flow",
        "AgeTypeParser dispatches AGE result suffixes into vertex, edge, path, and lightweight JSON parsing branches",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-age / agtype parse flow</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="880" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    # Flow lines first; cards are drawn afterward to keep text unobstructed.
    arrow(out, 920, 345, 920, 420, "result string", "line")
    arrow(out, 790, 520, 355, 620, "", "thin")
    arrow(out, 920, 520, 920, 620, "", "thin")
    arrow(out, 1050, 520, 1485, 620, "", "thin")
    relation_label(565, 570, "::vertex")
    relation_label(920, 570, "::edge")
    relation_label(1275, 570, "::path")
    arrow(out, 360, 860, 650, 912, "parseJsonObject", "thin")
    arrow(out, 920, 860, 770, 912, "parseJsonObject", "thin")
    arrow(out, 1485, 860, 930, 912, "parse array", "thin")
    arrow(out, 1040, 960, 1210, 960, "model output", "line")

    card(out, 650, 215, 540, 130, "PostgreSQL AGE Result", [
        "ag_catalog.cypher(...) returns agtype text",
        "rows expose v/e/p columns to operations",
    ], PALETTE["sky"])
    card(out, 700, 420, 440, 120, "Suffix Router", [
        "isVertex / isEdge / isPath",
        "remove ::vertex, ::edge, or ::path",
    ], PALETTE["mint"])
    card(out, 120, 620, 470, 240, "Vertex Branch", [
        "parseVertex(agtype)",
        "parseJsonObject(json)",
        "id -> GraphElementId",
        "label + properties -> GraphVertex",
    ], PALETTE["lemon"])
    card(out, 685, 620, 470, 240, "Edge Branch", [
        "parseEdge(agtype)",
        "parseJsonObject(json)",
        "id/start_id/end_id -> IDs",
        "label + properties -> GraphEdge",
    ], PALETTE["rose"])
    card(out, 1250, 620, 470, 240, "Path Branch", [
        "parsePath(agtype)",
        "parseAgtypeElements(content)",
        "element suffix -> vertex or edge",
        "steps -> GraphPath",
    ], PALETTE["aqua"])
    card(out, 560, 910, 480, 120, "Lightweight JSON Helpers", [
        "parseJsonObject / parseJsonArray",
        "parseValue + findClosing for nested content",
    ], PALETTE["peach"])
    card(out, 1210, 910, 430, 120, "Graph Core Output", [
        "GraphVertex, GraphEdge, GraphPath",
        "PathStep.VertexStep / EdgeStep",
    ], PALETTE["lavender"])

    out.append('<rect x="180" y="1060" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="1093" text-anchor="middle" class="tiny">'
        'Source: AgeTypeParser.kt; suffix dispatch and lightweight JSON parsing convert AGE agtype strings into graph-core model types.</text>'
    )
    return close_svg(out)


def graph_age_test_environment_svg() -> str:
    width, height = 1840, 1180
    out = open_svg(
        "Graph AGE Test Environment",
        "Integration tests wire PostgreSQL AGE Testcontainers, HikariCP connection initialization, Exposed, and sync/suspend operations",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-age / Test Environment</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="880" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    # Draw arrows first so card text stays on top.
    arrow(out, 390, 300, 485, 300, "launcher", "line")
    arrow(out, 770, 300, 865, 300, "image", "line")
    arrow(out, 1150, 300, 1245, 300, "jdbcUrl", "line")
    arrow(out, 1425, 405, 1425, 510, "pool", "thin")
    arrow(out, 250, 375, 665, 510, "construct ops", "line")
    arrow(out, 870, 595, 1060, 595, "uses Exposed tx", "line")
    arrow(out, 465, 680, 300, 790, "reset graph", "thin")
    arrow(out, 680, 680, 680, 790, "create/read/write", "thin")
    arrow(out, 870, 680, 1060, 790, "exec SQL", "thin")
    arrow(out, 1250, 840, 1425, 840, "agtype", "line")
    relation_label(920, 760, "same graphName boundary")

    card(out, 120, 225, 270, 150, "JUnit Tests", [
        "@BeforeAll setup",
        "@BeforeEach resetGraph",
        "runSuspendIO / runTest",
    ], PALETTE["sky"])
    card(out, 485, 225, 285, 150, "PostgreSQLAgeServer", [
        "Launcher.postgresqlAge",
        "shared Testcontainer",
        "jdbcUrl/user/password",
    ], PALETTE["mint"])
    card(out, 865, 225, 285, 150, "Docker AGE", [
        "apache/age:PG16_latest",
        "PostgreSQL + AGE extension",
        "CREATE EXTENSION once",
    ], PALETTE["lemon"])
    card(out, 1245, 225, 360, 180, "HikariCP DataSource", [
        "driverClassName PostgreSQL",
        "maximumPoolSize = 5",
        "connectionInitSql loads AGE",
        "sets ag_catalog search_path",
    ], PALETTE["rose"])
    card(out, 1060, 510, 365, 170, "Exposed Database", [
        "Database.connect(dataSource)",
        "TransactionManager.exec(...)",
        "JDBC transaction boundary",
    ], PALETTE["aqua"])
    card(out, 465, 510, 405, 170, "Graph Operations Under Test", [
        "AgeGraphOperations(graphName)",
        "AgeGraphSuspendOperations(graphName)",
        "sync and suspend tests share setup",
    ], PALETTE["lavender"])
    card(out, 120, 790, 360, 170, "Graph Reset", [
        "if graphExists -> dropGraph",
        "createGraph(test_graph)",
        "clean state per test",
    ], PALETTE["peach"])
    card(out, 560, 790, 360, 170, "Test Operations", [
        "createVertex / createEdge",
        "neighbors / shortestPath",
        "merge and algorithm scenarios",
    ], PALETTE["sky"])
    card(out, 1060, 790, 360, 170, "PostgreSQL AGE Exec", [
        "AgeSql.cypher(...)",
        "ag_catalog.cypher executes",
        "returns agtype columns",
    ], PALETTE["mint"])
    card(out, 1425, 790, 285, 170, "Assertions", [
        "AgeTypeParser maps results",
        "GraphVertex / Edge / Path",
        "bluetape4k assertions",
    ], PALETTE["lemon"])

    out.append('<rect x="180" y="1060" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="1093" text-anchor="middle" class="tiny">'
        'Source: AgeGraphOperationsTest.kt, AgeGraphSuspendOperationsTest.kt, README setup; every pooled connection runs LOAD age and search_path.</text>'
    )
    return close_svg(out)


def graph_neo4j_overview_svg() -> str:
    width, height = 1840, 1180
    out = open_svg(
        "Neo4j Graph Module Overview",
        "Graph-core operations over Neo4j Java Driver 5.x with coroutine bridge, elementId mapping, schema support, and optional caching",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-neo4j / Overview</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="880" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    # Arrows first; cards later keep labels and text readable.
    arrow(out, 360, 310, 470, 310, "calls", "line")
    arrow(out, 760, 310, 880, 310, "implements", "line")
    arrow(out, 1160, 310, 1280, 310, "decorates", "thin")
    arrow(out, 1060, 405, 830, 525, "sync queries", "thin")
    arrow(out, 1060, 405, 1035, 525, "records", "thin")
    arrow(out, 1060, 405, 1325, 525, "schema", "thin")
    arrow(out, 760, 650, 600, 785, "ReactiveSession", "line")
    arrow(out, 600, 900, 920, 900, "Cypher + params", "line")
    arrow(out, 920, 900, 1250, 900, "Bolt", "line")
    arrow(out, 1050, 650, 1050, 785, "maps", "thin")
    relation_label(920, 760, "elementId() IDs and PathStep ordering")

    card(out, 120, 235, 240, 150, "Application", [
        "domain services",
        "sync or suspend callers",
        "usage examples",
    ], PALETTE["sky"])
    card(out, 470, 235, 290, 150, "graph-core APIs", [
        "GraphOperations",
        "GraphSuspendOperations",
        "schema / merge / tx contracts",
    ], PALETTE["mint"])
    card(out, 880, 225, 280, 180, "Neo4j Facade", [
        "Neo4jGraphOperations",
        "Neo4jGraphSuspendOperations",
        "native MERGE + tx DSL",
        "elementId() lookups",
    ], PALETTE["lemon"])
    card(out, 1280, 235, 320, 150, "Optional Wrappers", [
        "CachingNeo4jGraphOperations",
        "Neo4jGraphSchemaManager",
        "indexes + constraints",
    ], PALETTE["rose"])
    card(out, 540, 525, 290, 150, "Coroutine Bridge", [
        "Neo4jCoroutineSession",
        "ReactiveSession read/write",
        "Publisher -> Flow -> List",
    ], PALETTE["lavender"])
    card(out, 1035, 525, 290, 150, "Record Mapper", [
        "Node -> GraphVertex",
        "Relationship -> GraphEdge",
        "Path -> GraphPath",
    ], PALETTE["aqua"])
    card(out, 1325, 525, 290, 150, "Schema Manager", [
        "createIndex",
        "unique constraints",
        "list/drop metadata",
    ], PALETTE["peach"])
    card(out, 420, 785, 300, 150, "Neo4j Java Driver", [
        "Driver externally owned",
        "SessionConfig database",
        "Reactive + blocking sessions",
    ], PALETTE["sky"])
    card(out, 820, 785, 300, 150, "Cypher Query Layer", [
        "MATCH / CREATE / MERGE",
        "direction traversal",
        "shortestPath / allPaths",
    ], PALETTE["mint"])
    card(out, 1250, 785, 300, 150, "Neo4j Database", [
        "nodes and relationships",
        "Bolt protocol",
        "record values returned",
    ], PALETTE["lemon"])

    out.append('<rect x="180" y="1060" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="1093" text-anchor="middle" class="tiny">'
        'Source: graph-neo4j README, Neo4jGraphOperations.kt, Neo4jCoroutineSession.kt, Neo4jRecordMapper.kt; driver ownership stays external.</text>'
    )
    return close_svg(out)


def graph_neo4j_reactive_coroutine_svg() -> str:
    width, height = 1840, 1080
    out = open_svg(
        "Neo4j Reactive-Coroutine Bridge",
        "Neo4jCoroutineSession converts ReactiveSession query publishers into coroutine-friendly lists and mapped graph-core results",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-neo4j / Reactive-Coroutine Bridge</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="780" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    # Primary horizontal bridge.
    arrow(out, 355, 320, 470, 320, "read/write", "line")
    arrow(out, 760, 320, 875, 320, "session", "line")
    arrow(out, 1165, 320, 1280, 320, "run Query", "line")
    arrow(out, 1480, 420, 1480, 545, "publisher", "thin")
    arrow(out, 1300, 620, 1070, 620, "asFlow().toList()", "line")
    arrow(out, 880, 620, 650, 620, "map records", "line")
    arrow(out, 560, 405, 560, 545, "finally close", "thin")
    relation_label(920, 760, "session.close<Void>().awaitFirstOrNull() keeps driver ownership external")

    card(out, 115, 245, 240, 150, "Coroutine Caller", [
        "suspend repo method",
        "GraphSuspendOperations",
        "runRead / runWrite",
    ], PALETTE["sky"])
    card(out, 470, 245, 290, 160, "Coroutine Session", [
        "Neo4jCoroutineSession",
        "read(block) / write(block)",
        "sessionConfig() per database",
    ], PALETTE["lavender"])
    card(out, 875, 245, 290, 160, "ReactiveSession", [
        "driver.session(...)",
        "Neo4j Java Driver API",
        "read/write query execution",
    ], PALETTE["mint"])
    card(out, 1280, 245, 290, 175, "Reactive Result", [
        "run(Query(cypher, params))",
        "awaitSingle()",
        "records() publisher",
        "Record stream",
    ], PALETTE["lemon"])
    card(out, 1280, 545, 400, 160, "kotlinx-coroutines-reactive", [
        "Publisher<Record>.asFlow()",
        "collect with toList()",
        "non-blocking bridge to suspend code",
    ], PALETTE["rose"])
    card(out, 670, 545, 400, 160, "Graph Mapping", [
        "Neo4jRecordMapper",
        "Node / Relationship / Path",
        "GraphVertex / GraphEdge / GraphPath",
    ], PALETTE["aqua"])
    card(out, 245, 545, 405, 160, "Caller Receives", [
        "List<Record> or graph models",
        "exceptions propagate",
        "driver remains externally owned",
    ], PALETTE["peach"])

    out.append('<rect x="180" y="955" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="988" text-anchor="middle" class="tiny">'
        'Source: Neo4jCoroutineSession.kt and Neo4jRecordMapper.kt; ReactiveSession is closed after collection, but the Driver is not closed.</text>'
    )
    return close_svg(out)


def graph_neo4j_neighbors_pattern_svg() -> str:
    width, height = 1840, 1080
    out = open_svg(
        "Neo4j Neighbors Direction Patterns",
        "neighbors(startId, options) builds a safe edge pattern for OUTGOING, INCOMING, or BOTH traversal and maps DISTINCT neighbor records",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-neo4j / Neighbors Direction Patterns</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="780" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    arrow(out, 365, 300, 535, 300, "validate", "line")
    arrow(out, 365, 535, 535, 535, "depth", "thin")
    arrow(out, 830, 300, 1010, 275, "edgePart", "line")
    arrow(out, 830, 535, 1010, 535, "edgePart", "thin")
    arrow(out, 830, 770, 1010, 795, "edgePart", "line")
    arrow(out, 1360, 275, 1500, 420, "MATCH", "thin")
    arrow(out, 1360, 535, 1500, 535, "MATCH", "thin")
    arrow(out, 1360, 795, 1500, 650, "MATCH", "thin")
    relation_label(920, 205, "same construction in sync and suspend operations")

    card(out, 115, 225, 250, 180, "NeighborOptions", [
        "startId: GraphElementId",
        "edgeLabel optional",
        "direction",
        "maxDepth",
    ], PALETTE["sky"])
    card(out, 115, 455, 250, 160, "Depth Builder", [
        "maxDepth == 1 -> empty",
        "else *1..maxDepth",
        "label + depth suffix",
    ], PALETTE["mint"])

    card(out, 535, 215, 295, 170, "OUTGOING", [
        "(start)-[edgePart]->",
        "(neighbor)",
        "follows outgoing rels",
    ], PALETTE["lemon"])
    card(out, 535, 450, 295, 170, "INCOMING", [
        "(start)<-[edgePart]-",
        "(neighbor)",
        "follows incoming rels",
    ], PALETTE["lavender"])
    card(out, 535, 685, 295, 170, "BOTH", [
        "(start)-[edgePart]-",
        "(neighbor)",
        "undirected pattern",
    ], PALETTE["aqua"])

    card(out, 1010, 205, 350, 160, "Cypher Pattern", [
        "MATCH pattern",
        "WHERE elementId(start) = $startId",
        "RETURN DISTINCT neighbor",
    ], PALETTE["peach"])
    card(out, 1010, 455, 350, 160, "Safe Identifier Rules", [
        "edgeLabel.requireNotBlank",
        "requireSafeIdentifier",
        "params carry startId.value",
    ], PALETTE["rose"])
    card(out, 1010, 705, 350, 160, "Result Mapping", [
        "record key: neighbor",
        "Neo4jRecordMapper.recordToVertex",
        "GraphVertex list or Flow",
    ], PALETTE["sky"])

    card(out, 1500, 420, 220, 230, "Neo4j", [
        "elementId(start)",
        "relationship direction",
        "distinct neighbors",
        "records returned",
    ], PALETTE["mint"])

    out.append('<rect x="180" y="965" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="998" text-anchor="middle" class="tiny">'
        'Source: Neo4jGraphOperations.kt and Neo4jGraphSuspendOperations.kt neighbors(); direction changes only the Cypher relationship arrows.</text>'
    )
    return close_svg(out)


def graph_neo4j_data_model_svg() -> str:
    width, height = 1840, 1080
    out = open_svg(
        "Neo4j Data Model Mapping",
        "Neo4j Node, Relationship, and Path values are mapped to graph-core Vertex, Edge, and Path models using Neo4j 5.x element IDs",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-neo4j / Data Model Mapping</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="780" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    arrow(out, 405, 325, 590, 325, "nodeToVertex", "line")
    arrow(out, 405, 585, 590, 585, "relationshipToEdge", "line")
    arrow(out, 960, 325, 1135, 325, "GraphElementId", "thin")
    arrow(out, 960, 585, 1135, 585, "GraphElementId", "thin")
    arrow(out, 760, 705, 760, 730, "", "line")
    arrow(out, 1135, 825, 1410, 770, "ordered steps", "thin")
    relation_label(920, 220, "Neo4j numeric id() is not used")
    relation_label(860, 715, "pathToGraphPath")

    card(out, 125, 250, 280, 180, "Neo4j Node", [
        "elementId()",
        "labels().firstOrNull()",
        "asMap() properties",
        "Record key: n / neighbor",
    ], PALETTE["sky"])
    card(out, 125, 510, 280, 180, "Neo4j Relationship", [
        "elementId()",
        "startNodeElementId()",
        "endNodeElementId()",
        "type() + asMap()",
    ], PALETTE["mint"])
    card(out, 590, 250, 370, 180, "GraphVertex", [
        "id: GraphElementId",
        "label: first node label",
        "Unknown when no label",
        "properties: Map",
    ], PALETTE["lemon"])
    card(out, 590, 510, 370, 180, "GraphEdge", [
        "id: GraphElementId",
        "label: rel.type()",
        "startId / endId",
        "properties: Map",
    ], PALETTE["lavender"])
    card(out, 1135, 250, 355, 180, "Stable ID Policy", [
        "GraphElementId(value)",
        "elementId-based lookup",
        "Neo4j 5.x compatible",
        "deprecated id() avoided",
    ], PALETTE["rose"])
    card(out, 1135, 510, 355, 180, "Cypher Endpoints", [
        "MATCH by elementId(a)",
        "MATCH by elementId(b)",
        "relationship endpoints use",
        "start/end element IDs",
    ], PALETTE["peach"])

    card(out, 470, 730, 580, 180, "Neo4j Path", [
        "nodes().toList()",
        "relationships().toList()",
        "each node becomes VertexStep",
        "each relationship becomes EdgeStep",
    ], PALETTE["aqua"])
    card(out, 1410, 720, 300, 180, "GraphPath", [
        "List<PathStep>",
        "VertexStep",
        "EdgeStep",
        "cost + metadata defaults",
    ], PALETTE["sky"])

    out.append('<rect x="180" y="965" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="998" text-anchor="middle" class="tiny">'
        'Source: Neo4jRecordMapper.kt and graph-neo4j README; Path mapping preserves node/relationship order as graph-core PathStep values.</text>'
    )
    return close_svg(out)


def graph_neo4j_test_environment_svg() -> str:
    width, height = 1840, 1080
    out = open_svg(
        "Neo4j Test Environment",
        "Tests share Neo4jServer, create drivers in @BeforeAll, clear graph state before each test, and verify sync/suspend operations",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-neo4j / Test Environment</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="780" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    arrow(out, 430, 300, 545, 300, "boltUrl", "line")
    arrow(out, 895, 300, 1065, 300, "driver", "line")
    arrow(out, 895, 540, 1065, 540, "driver", "thin")
    arrow(out, 715, 410, 715, 515, "GraphDatabase.driver", "line")
    arrow(out, 1225, 420, 1225, 515, "BeforeEach", "thin")
    arrow(out, 1370, 630, 1370, 755, "", "line")
    arrow(out, 1385, 315, 1600, 470, "Bolt", "thin")
    arrow(out, 1385, 610, 1600, 570, "", "thin")
    arrow(out, 1185, 845, 1095, 845, "AfterAll", "thin")
    relation_label(920, 205, "Neo4jServer.Launcher.neo4j is shared across integration tests")
    relation_label(1490, 735, "test cases")
    relation_label(1550, 675, "Cypher cleanup")

    card(out, 115, 225, 315, 160, "Test Dependencies", [
        "bluetape4k-testcontainers",
        "testcontainers-neo4j",
        "kotlinx-coroutines-test",
    ], PALETTE["sky"])
    card(out, 545, 225, 350, 190, "Neo4jServer Singleton", [
        "Neo4jServer.Launcher.neo4j",
        "container-backed Bolt URL",
        "AuthTokens.none()",
        "shared launch wrapper",
    ], PALETTE["mint"])
    card(out, 1065, 225, 320, 190, "Driver Per Test Class", [
        "GraphDatabase.driver(boltUrl)",
        "@BeforeAll setup",
        "@AfterAll driver.close()",
        "driver owned by tests",
    ], PALETTE["lemon"])

    card(out, 545, 515, 350, 180, "Operations Under Test", [
        "Neo4jGraphOperations",
        "Neo4jGraphSuspendOperations",
        "schema / merge / weighted path",
        "algorithm tests",
    ], PALETTE["lavender"])
    card(out, 1065, 515, 320, 180, "Clean State", [
        "@BeforeEach clearGraph",
        "ops.dropGraph(default)",
        "MATCH (n) DETACH DELETE n",
    ], PALETTE["peach"])
    card(out, 1460, 400, 250, 180, "Neo4j Container", [
        "nodes",
        "relationships",
        "indexes",
        "Bolt protocol",
    ], PALETTE["aqua"])
    card(out, 1185, 755, 370, 120, "Assertions", [
        "create / find / update / delete",
        "neighbors / path / schema / merge",
    ], PALETTE["rose"])
    card(out, 745, 745, 350, 155, "Teardown", [
        "@AfterAll teardown",
        "driver.close()",
        "operations do not close driver",
    ], PALETTE["sky"])

    out.append('<rect x="180" y="965" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="998" text-anchor="middle" class="tiny">'
        'Source: graph-neo4j tests and README Testcontainers setup; tests close Driver explicitly because graph operations do not own it.</text>'
    )
    return close_svg(out)


def graph_falkordb_overview_svg() -> str:
    width, height = 1840, 1080
    out = open_svg(
        "FalkorDB Backend Overview",
        "Redis-module graph backend using jfalkordb Driver, graph-core contracts, Cypher, schema indexes, merge, and coroutine IO isolation",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-falkordb / Overview</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="800" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')
    out.append('<text x="920" y="205" text-anchor="middle" class="tiny">Source: graph-falkordb README, FalkorDBGraphOperations, FalkorDBGraphSuspendOperations, SchemaManager, RecordMapper, and FalkorDBServer fixture</text>')

    arrow(out, 390, 318, 565, 318, "GraphOperations", "line")
    arrow(out, 390, 540, 565, 540, "GraphSuspendOperations", "line")
    arrow(out, 915, 318, 1095, 318, "queryList", "line")
    arrow(out, 915, 540, 1095, 540, "Dispatchers.IO", "thin")
    arrow(out, 1245, 405, 1455, 405, "Cypher", "line")
    arrow(out, 1245, 595, 1455, 500, "index DDL", "thin")
    arrow(out, 760, 700, 760, 775, "to model", "thin")
    arrow(out, 1095, 700, 1095, 775, "fallback", "thin")

    card(out, 115, 245, 275, 170, "graph-core API", [
        "GraphOperations",
        "GraphMergeOperations",
        "GraphSchemaManager",
        "GraphVertex / Edge / Path",
    ], PALETTE["sky"])
    card(out, 115, 465, 275, 170, "Coroutine API", [
        "GraphSuspendOperations",
        "Flow results",
        "channelFlow backpressure",
        "cancellation rethrows",
    ], PALETTE["lavender"])

    card(out, 565, 245, 350, 210, "Sync Operations", [
        "driver.graph(graphName).use",
        "CREATE uses named props",
        "id(n) = toInteger($id)",
        "MERGE vertex / edge",
        "driver owned externally",
    ], PALETTE["mint"])
    card(out, 565, 485, 350, 185, "Suspend Operations", [
        "sync delegate for merge/schema",
        "withContext(Dispatchers.IO)",
        "queryListIO",
        "flowQuery",
    ], PALETTE["peach"])

    card(out, 1095, 245, 330, 185, "jfalkordb Driver", [
        "FalkorDB.driver(host, port)",
        "Graph context per call",
        "ResultSet / Record",
        "Redis connection lifecycle",
    ], PALETTE["lemon"])
    card(out, 1095, 485, 330, 185, "Schema & Merge", [
        "CREATE INDEX",
        "CALL db.indexes()",
        "unique constraint unsupported",
        "native Cypher MERGE",
    ], PALETTE["rose"])

    card(out, 1455, 310, 265, 210, "FalkorDB", [
        "Redis module",
        "openCypher subset",
        "integer node IDs",
        "GRAPH.CONSTRAINT gap",
        "falkordb:v4.18.1 tests",
    ], PALETTE["aqua"])

    card(out, 585, 775, 350, 150, "Record Mapping", [
        "Node -> GraphVertex",
        "Edge -> GraphEdge",
        "Path -> GraphPath steps",
    ], PALETTE["sky"])
    card(out, 1015, 775, 365, 150, "Algorithms", [
        "shortest path fallback",
        "BFS / DFS / PageRank",
        "cycle and component helpers",
    ], PALETTE["lavender"])

    out.append('<rect x="180" y="965" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="997" text-anchor="middle" class="tiny">'
        'Source note: FalkorDB operations do not close the externally owned Driver; suspend operations isolate blocking jfalkordb calls on Dispatchers.IO.</text>'
    )
    return close_svg(out)


def root_readme_overview_svg() -> str:
    width, height = 1840, 1080
    out = open_svg(
        "Bluetape4k Graph Overview",
        "Unified Kotlin graph API with multiple database backends, bulk I/O, application integrations, examples, benchmarks, and BOM alignment",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: root README / Overview</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="800" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')
    out.append('<text x="920" y="205" text-anchor="middle" class="tiny">Source: README module structure, settings.gradle.kts includeModules, backend capability table, and module READMEs</text>')

    arrow(out, 395, 420, 590, 420, "shared contracts", "line")
    arrow(out, 915, 420, 1095, 420, "native adapters", "line")
    arrow(out, 745, 590, 745, 710, "bulk graph data", "thin")
    arrow(out, 1045, 590, 1045, 710, "app integration", "thin")
    arrow(out, 600, 675, 430, 800, "domain flows", "thin")
    arrow(out, 1210, 675, 1390, 790, "measure & align", "thin")

    card(out, 115, 315, 280, 220, "Application Code", [
        "services and examples",
        "blocking or suspend API",
        "schema / merge / traversal",
        "portable graph models",
    ], PALETTE["sky"])

    card(out, 590, 285, 325, 285, "graph-core", [
        "GraphOperations",
        "GraphSuspendOperations",
        "GraphVertex / Edge / Path",
        "Schema DSL",
        "algorithms and fallbacks",
    ], PALETTE["mint"])

    card(out, 1095, 245, 345, 350, "Database Backends", [
        "Neo4j Java Driver",
        "Memgraph protocol",
        "Apache AGE over JDBC",
        "TinkerGraph / Gremlin",
        "FalkorDB Redis module",
    ], PALETTE["lemon"])
    card(out, 1480, 300, 245, 235, "Local Testing", [
        "Testcontainers",
        "Neo4j / AGE",
        "Memgraph / FalkorDB",
        "TinkerGraph in-memory",
    ], PALETTE["aqua"])

    card(out, 545, 710, 310, 175, "Graph I/O", [
        "CSV",
        "Jackson NDJSON",
        "GraphML",
        "OkIO streaming",
    ], PALETTE["peach"])
    card(out, 900, 710, 315, 175, "Integrations", [
        "Ktor GraphPlugin",
        "Spring Boot 4 auto-config",
        "virtual-thread adapters",
        "coroutine wrappers",
    ], PALETTE["rose"])
    card(out, 115, 760, 315, 160, "Examples", [
        "code / fraud / knowledge",
        "linkedin / recommendation",
        "observability / Ktor",
    ], PALETTE["lavender"])
    card(out, 1390, 745, 325, 180, "Benchmarks & BOM", [
        "JMH graph operations",
        "I/O benchmarks",
        "backend benchmarks",
        "dependency BOM",
    ], PALETTE["sky"])

    out.append('<rect x="180" y="965" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="997" text-anchor="middle" class="tiny">'
        'Source note: root README presents graph-core as the stable API surface; backend modules translate it to each database driver and query model.</text>'
    )
    return close_svg(out)


def root_architecture_svg() -> str:
    width, height = 1840, 1080
    out = open_svg(
        "Bluetape4k Graph Architecture",
        "Layered graph platform architecture: public APIs, core contracts, capabilities, backend adapters, database runtimes, I/O, integrations, and validation",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: root README / Architecture</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="800" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')
    out.append('<text x="920" y="205" text-anchor="middle" class="tiny">Source: README architecture sections, module layout, graph-core contracts, backend READMEs, benchmark and integration modules</text>')

    arrow(out, 315, 335, 520, 335, "calls", "line")
    arrow(out, 845, 335, 1015, 335, "capabilities", "line")
    arrow(out, 1325, 335, 1510, 335, "query model", "line")
    arrow(out, 685, 535, 685, 690, "bulk data", "thin")
    arrow(out, 250, 440, 250, 560, "web apps", "thin")
    arrow(out, 1275, 535, 1420, 690, "measure", "thin")

    card(out, 115, 250, 270, 190, "Public API", [
        "GraphOperations",
        "GraphSuspendOperations",
        "schema / merge / transaction",
        "virtual-thread adapters",
    ], PALETTE["sky"])
    card(out, 520, 245, 325, 205, "graph-core", [
        "GraphVertex / GraphEdge",
        "GraphPath / PathStep",
        "repositories and sessions",
        "validation and ID model",
    ], PALETTE["mint"])
    card(out, 1015, 245, 310, 205, "Capabilities", [
        "batch insert",
        "schema/index manager",
        "merge/upsert",
        "weighted paths",
    ], PALETTE["lavender"])
    card(out, 1510, 245, 275, 205, "Database Runtime", [
        "Neo4j",
        "Memgraph",
        "PostgreSQL AGE",
        "TinkerGraph / FalkorDB",
    ], PALETTE["aqua"])

    card(out, 1030, 505, 370, 205, "Backend Adapters", [
        "Neo4j Java Driver",
        "Neo4j-compatible Memgraph",
        "Exposed/JDBC AGE SQL",
        "Gremlin and jfalkordb",
    ], PALETTE["lemon"])
    card(out, 520, 690, 330, 190, "Graph I/O", [
        "CSV import/export",
        "Jackson NDJSON",
        "GraphML StAX",
        "OkIO compression/encryption",
    ], PALETTE["peach"])
    card(out, 115, 560, 320, 190, "App Integrations", [
        "Ktor 3 plugin",
        "Spring Boot 4 auto-config",
        "domain example apps",
        "coroutine-friendly APIs",
    ], PALETTE["rose"])
    card(out, 1420, 690, 300, 190, "Validation", [
        "JMH benchmarks",
        "Testcontainers backends",
        "BOM version alignment",
        "README visual evidence",
    ], PALETTE["sky"])

    out.append('<rect x="155" y="805" width="300" height="115" rx="14" fill="#F8FAFC" stroke="#CBD5E1" stroke-width="2"/>')
    out.append('<text x="305" y="848" text-anchor="middle" class="label">Source Sets</text>')
    out.append('<text x="305" y="885" text-anchor="middle" class="small">main / test / fixtures</text>')
    out.append('<text x="305" y="913" text-anchor="middle" class="small">examples / benchmark</text>')

    out.append('<rect x="180" y="965" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="997" text-anchor="middle" class="tiny">'
        'Source note: graph-core owns the stable contracts; adapters translate contracts into each backend driver and database runtime.</text>'
    )
    return close_svg(out)


def graph_age_operations_class_svg() -> str:
    width, height = 1840, 1180
    out = open_svg(
        "AgeGraphOperations Class Model",
        "Synchronous AGE adapter implements graph-core operations, transactions, schema access, merge fallback, and algorithms",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-age / AgeGraphOperations</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="890" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def class_box(x: float, y: float, w: float, h: float, title: str, stereotype: str, lines: list[str], fill: str) -> None:
        stroke = CARD_STROKES.get(fill, PALETTE["slate"])
        title_class = "small" if len(title) > 22 else "label"
        out.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="3" class="card"/>'
        )
        out.append(f'<text x="{x+w/2}" y="{y+34}" text-anchor="middle" dominant-baseline="middle" class="tiny">{esc(stereotype)}</text>')
        out.append(f'<text x="{x+w/2}" y="{y+68}" text-anchor="middle" dominant-baseline="middle" class="{title_class}">{esc(title)}</text>')
        out.append(f'<line x1="{x}" y1="{y+88}" x2="{x+w}" y2="{y+88}" stroke="{stroke}" stroke-width="2"/>')
        yy = y + 122
        for line in lines:
            out.append(f'<text x="{x+24}" y="{yy}" class="small">{esc(line)}</text>')
            yy += 26

    # Relationship lines first so class compartments remain readable.
    for x in [300, 710, 1120, 1530]:
        arrow(out, x, 420, 920, 457, "", "thin")
    out.append('<text x="920" y="440" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">implements graph-core interfaces</text>')
    out.append('<text x="920" y="440" text-anchor="middle" class="tiny">implements graph-core interfaces</text>')
    arrow(out, 740, 745, 300, 785, "", "thin")
    arrow(out, 880, 745, 710, 785, "", "thin")
    arrow(out, 1060, 745, 1120, 785, "", "thin")
    arrow(out, 1130, 745, 1530, 785, "", "thin")
    for label, x in [("builds SQL", 520), ("parses rows", 820), ("scopes tx", 1090), ("fallback", 1360)]:
        out.append(f'<text x="{x}" y="770" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(label)}</text>')
        out.append(f'<text x="{x}" y="770" text-anchor="middle" class="tiny">{esc(label)}</text>')

    class_box(
        120,
        215,
        360,
        205,
        "GraphOperations",
        "<<interface>>",
        ["session + vertex repo", "edge + traversal repo", "algorithm repo"],
        PALETTE["sky"],
    )
    class_box(
        530,
        215,
        360,
        205,
        "GraphTransactionalOperations",
        "<<interface>>",
        ["transaction(block)", "GraphTransactionScope", "Exposed transaction bridge"],
        PALETTE["mint"],
    )
    class_box(
        940,
        215,
        360,
        205,
        "GraphSchemaManagement",
        "<<interface>>",
        ["schemaManager()", "explicit unsupported AGE DDL", "index / constraint surface"],
        PALETTE["lemon"],
    )
    class_box(
        1350,
        215,
        360,
        205,
        "GraphMergeOperations",
        "<<interface>>",
        ["mergeVertex", "mergeEdge", "match/update/create fallback"],
        PALETTE["rose"],
    )
    class_box(
        560,
        460,
        720,
        285,
        "AgeGraphOperations",
        "<<class>>",
        [
            "- graphName: String",
            "- batchChunkSize: Int = 500",
            "+ create/drop/exists graph",
            "+ vertex + edge CRUD and batch create",
            "+ neighbors / shortestPath / allPaths / aStarPath",
            "+ merge, transaction, schemaManager, algorithms",
        ],
        PALETTE["lavender"],
    )
    class_box(
        120,
        785,
        360,
        190,
        "AgeSql",
        "<<object>>",
        ["cypher(graph, query, columns)", "create/match/update/delete", "batch rows + graph namespace"],
        PALETTE["rose"],
    )
    class_box(
        530,
        785,
        360,
        190,
        "AgeTypeParser",
        "<<object>>",
        ["parseVertex / parseEdge", "parsePath", "agtype -> graph models"],
        PALETTE["aqua"],
    )
    class_box(
        940,
        785,
        360,
        190,
        "AgeGraphTransactionScope",
        "<<class>>",
        ["delegates scoped operations", "used inside transaction { }", "same graphName boundary"],
        PALETTE["mint"],
    )
    class_box(
        1350,
        785,
        360,
        190,
        "Graph Algorithms",
        "<<fallback>>",
        ["ShortestPathFallback", "BfsDfsRunner / CycleDetector", "PageRankCalculator / UnionFind"],
        PALETTE["peach"],
    )

    out.append('<rect x="180" y="1060" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="1093" text-anchor="middle" class="tiny">'
        'Source: AgeGraphOperations.kt; class implements graph-core sync APIs and delegates SQL, parsing, transactions, and fallback algorithms.</text>'
    )
    return close_svg(out)


def graph_age_sql_class_svg() -> str:
    width, height = 1840, 1180
    out = open_svg(
        "AgeSql Class Model",
        "Apache AGE Cypher-over-SQL factory for graph setup, CRUD, batch rows, traversal, and algorithm helpers",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-age / AgeSql</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="890" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def class_box(x: float, y: float, w: float, h: float, title: str, stereotype: str, lines: list[str], fill: str) -> None:
        stroke = CARD_STROKES.get(fill, PALETTE["slate"])
        title_class = "small" if len(title) > 22 else "label"
        out.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="3" class="card"/>'
        )
        out.append(f'<text x="{x+w/2}" y="{y+34}" text-anchor="middle" dominant-baseline="middle" class="tiny">{esc(stereotype)}</text>')
        out.append(f'<text x="{x+w/2}" y="{y+68}" text-anchor="middle" dominant-baseline="middle" class="{title_class}">{esc(title)}</text>')
        out.append(f'<line x1="{x}" y1="{y+88}" x2="{x+w}" y2="{y+88}" stroke="{stroke}" stroke-width="2"/>')
        yy = y + 122
        for line in lines:
            out.append(f'<text x="{x+24}" y="{yy}" class="small">{esc(line)}</text>')
            yy += 26

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    # Relationship lines first so cards and labels remain readable.
    arrow(out, 480, 315, 675, 390, "", "thin")
    arrow(out, 1360, 315, 1165, 390, "", "thin")
    relation_label(920, 285, "builds SQL strings for ag_catalog.cypher")
    arrow(out, 650, 520, 510, 610, "", "thin")
    arrow(out, 1190, 520, 1330, 610, "", "thin")
    relation_label(560, 565, "vertex SQL")
    relation_label(1280, 565, "edge SQL")
    arrow(out, 760, 620, 450, 800, "", "thin")
    arrow(out, 920, 620, 920, 800, "", "thin")
    arrow(out, 1080, 620, 1390, 800, "", "thin")
    relation_label(600, 760, "batch rows")
    relation_label(920, 760, "serializes props")
    relation_label(1240, 760, "path helpers")

    class_box(
        120,
        220,
        360,
        225,
        "Setup + Graph DDL",
        "<<functions>>",
        ["loadAge()", "setSearchPath()", "createExtension()", "create/drop/exists graph"],
        PALETTE["sky"],
    )
    class_box(
        650,
        345,
        540,
        275,
        "AgeSql",
        "<<object>>",
        [
            "+ cypher(graph, query, columns)",
            "+ vertex + edge CRUD factories",
            "+ batch create and endpoint match",
            "+ neighbors / shortestPath / allPaths",
            "+ degreeCentrality / match all",
            "- commonSortedPropertyKeys / row props",
        ],
        PALETTE["lavender"],
    )
    class_box(
        1360,
        220,
        360,
        190,
        "Cypher Wrapper",
        "<<SQL shape>>",
        ["SELECT * FROM ag_catalog.cypher", "graphName + $$ query $$", "typed agtype column list"],
        PALETTE["mint"],
    )
    class_box(
        120,
        550,
        390,
        210,
        "Vertex SQL",
        "<<factory group>>",
        ["createVertex / createVerticesBatch", "matchVertices / matchVertexById", "updateVertex / deleteVertex", "countVertices"],
        PALETTE["lemon"],
    )
    class_box(
        1330,
        550,
        390,
        210,
        "Edge SQL",
        "<<factory group>>",
        ["createEdge / createEdgesBatch", "matchEdgeBetween", "match by label/start/end id", "updateEdge / deleteEdge"],
        PALETTE["rose"],
    )
    class_box(
        250,
        800,
        400,
        220,
        "Batch Row Models",
        "<<data classes>>",
        ["BatchVertexRow(index, props)", "BatchEdgeRow(index, from, to)", "shared sorted property keys"],
        PALETTE["aqua"],
    )
    class_box(
        720,
        800,
        400,
        220,
        "AgePropertySerializer",
        "<<dependency>>",
        ["toCypherProps(properties)", "toCypherAssignments(variable)", "toCypherValue(value)", "escape strings + safe keys"],
        PALETTE["peach"],
    )
    class_box(
        1190,
        800,
        400,
        220,
        "Traversal + Algorithms",
        "<<factory group>>",
        ["neighbors(direction, depth)", "shortestPath / allPaths", "degreeCentrality", "matchAllVertices / matchAllEdges"],
        PALETTE["sky"],
    )

    out.append('<rect x="180" y="1060" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="1093" text-anchor="middle" class="tiny">'
        'Source: AgeSql.kt and AgePropertySerializer.kt; graphName/label/property inputs are converted into AGE Cypher-over-SQL strings.</text>'
    )
    return close_svg(out)


def graph_age_type_parser_class_svg() -> str:
    width, height = 1840, 1280
    out = open_svg(
        "AgeTypeParser Class Model",
        "Apache AGE agtype parser converts vertex, edge, path, and lightweight JSON strings into graph-core models",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-age / AgeTypeParser</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="990" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def class_box(x: float, y: float, w: float, h: float, title: str, stereotype: str, lines: list[str], fill: str) -> None:
        stroke = CARD_STROKES.get(fill, PALETTE["slate"])
        title_class = "small" if len(title) > 22 else "label"
        out.append(
            f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="14" fill="{fill}" '
            f'stroke="{stroke}" stroke-width="3" class="card"/>'
        )
        out.append(f'<text x="{x+w/2}" y="{y+34}" text-anchor="middle" dominant-baseline="middle" class="tiny">{esc(stereotype)}</text>')
        out.append(f'<text x="{x+w/2}" y="{y+68}" text-anchor="middle" dominant-baseline="middle" class="{title_class}">{esc(title)}</text>')
        out.append(f'<line x1="{x}" y1="{y+88}" x2="{x+w}" y2="{y+88}" stroke="{stroke}" stroke-width="2"/>')
        yy = y + 122
        for line in lines:
            out.append(f'<text x="{x+24}" y="{yy}" class="small">{esc(line)}</text>')
            yy += 26

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    # Relationship lines first so parser cards remain readable.
    arrow(out, 520, 325, 650, 400, "", "thin")
    arrow(out, 1220, 400, 1330, 325, "", "thin")
    relation_label(920, 285, "agtype suffix selects parser branch")
    arrow(out, 650, 610, 365, 690, "", "thin")
    arrow(out, 760, 635, 455, 895, "", "thin")
    arrow(out, 920, 635, 920, 895, "", "thin")
    arrow(out, 1080, 635, 1385, 895, "", "thin")
    arrow(out, 1220, 610, 1475, 690, "", "thin")
    relation_label(560, 660, "vertex")
    relation_label(690, 850, "path scan")
    relation_label(920, 850, "JSON values")
    relation_label(1150, 850, "model steps")
    relation_label(1280, 660, "edge")

    class_box(
        120,
        220,
        400,
        210,
        "AGE agtype Input",
        "<<strings>>",
        ["{...}::vertex", "{...}::edge", "[...]::path", "JSON object / array payload"],
        PALETTE["sky"],
    )
    class_box(
        650,
        355,
        570,
        280,
        "AgeTypeParser",
        "<<object : KLogging>>",
        [
            "+ parseVertex(agtype)",
            "+ parseEdge(agtype)",
            "+ parsePath(agtype)",
            "+ isVertex / isEdge / isPath",
            "+ parseJsonObject / parseJsonArray",
            "- parseValue / findClosing / agtype scan",
        ],
        PALETTE["lavender"],
    )
    class_box(
        1330,
        220,
        400,
        210,
        "Graph Core Models",
        "<<outputs>>",
        ["GraphVertex(id, label, props)", "GraphEdge(id, start, end)", "GraphPath(PathStep list)", "GraphElementId.of(Long)"],
        PALETTE["mint"],
    )
    class_box(
        120,
        650,
        390,
        210,
        "Vertex Parser",
        "<<branch>>",
        ["removeSuffix(::vertex)", "parse id / label / properties", "GraphElementId.of(id)", "return GraphVertex"],
        PALETTE["lemon"],
    )
    class_box(
        1330,
        650,
        390,
        210,
        "Edge Parser",
        "<<branch>>",
        ["removeSuffix(::edge)", "parse id / label", "parse start_id / end_id", "return GraphEdge"],
        PALETTE["rose"],
    )
    class_box(
        250,
        895,
        410,
        215,
        "Path Parser",
        "<<branch>>",
        ["removeSuffix(::path)", "parseAgtypeElements(content)", "VertexStep / EdgeStep", "warn unknown agtype element"],
        PALETTE["aqua"],
    )
    class_box(
        715,
        895,
        410,
        215,
        "Lightweight JSON Parser",
        "<<private helpers>>",
        ["parseJsonObject(json)", "parseJsonArray(json)", "parseValue(content, start)", "findClosing(open, close)"],
        PALETTE["peach"],
    )
    class_box(
        1180,
        895,
        410,
        215,
        "Path Steps",
        "<<graph-core>>",
        ["PathStep.VertexStep", "PathStep.EdgeStep", "GraphPath(steps)", "preserves vertex/edge order"],
        PALETTE["sky"],
    )

    out.append('<rect x="180" y="1160" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="1193" text-anchor="middle" class="tiny">'
        'Source: AgeTypeParser.kt; AGE agtype suffixes are stripped, JSON payloads parsed, and graph-core model objects returned.</text>'
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


def graph_neo4j_operations_class_svg() -> str:
    width, height = 1840, 1180
    out = open_svg(
        "Neo4jGraphOperations Class Model",
        "Synchronous graph-core adapter over Neo4j Driver sessions, Cypher execution, record mapping, schema DDL, and JVM algorithm fallbacks",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-neo4j / Neo4jGraphOperations</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="880" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    # Draw relations before cards so labels never cover class labels.
    arrow(out, 430, 325, 610, 395, "implements", "line")
    arrow(out, 430, 535, 610, 505, "implements", "line")
    arrow(out, 430, 745, 610, 615, "implements", "line")
    arrow(out, 970, 395, 1160, 305, "opens", "line")
    arrow(out, 970, 505, 1160, 455, "maps records", "thin")
    arrow(out, 970, 615, 1160, 625, "delegates", "thin")
    arrow(out, 850, 710, 850, 835, "tx scope", "thin")
    arrow(out, 1005, 745, 1160, 845, "fallbacks", "thin")
    relation_label(805, 248, "Driver externally owned")

    card(out, 120, 240, 310, 170, "GraphOperations", [
        "GraphVertexRepository",
        "GraphEdgeRepository",
        "GraphTraversalRepository",
        "GraphAlgorithmRepository",
    ], PALETTE["sky"])
    card(out, 120, 455, 310, 160, "GraphTransactional", [
        "transaction(block)",
        "GraphTransactionScope",
        "commit or rollback",
    ], PALETTE["mint"])
    card(out, 120, 670, 310, 160, "Schema + Merge", [
        "GraphSchemaManagement",
        "GraphMergeOperations",
        "MERGE vertex / edge",
    ], PALETTE["peach"])

    card(out, 610, 295, 360, 475, "Neo4jGraphOperations", [
        "driver: Driver",
        "database: String = neo4j",
        "session(): Session",
        "runQuery(cypher, params)",
        "elementId() based IDs",
    ], PALETTE["lemon"])

    card(out, 1160, 220, 380, 165, "Neo4j Driver Session", [
        "SessionConfig.withDatabase",
        "blocking Session",
        "s.run(cypher, params)",
    ], PALETTE["lavender"])
    card(out, 1160, 430, 380, 175, "Neo4jRecordMapper", [
        "recordToVertex / Edge",
        "recordToPath",
        "Node / Relationship / Path",
    ], PALETTE["aqua"])
    card(out, 1160, 650, 380, 160, "Neo4jGraphSchemaManager", [
        "createIndex",
        "unique constraints",
        "list/drop metadata",
    ], PALETTE["rose"])

    card(out, 650, 835, 400, 120, "Neo4jGraphTransactionScope", [
        "uses Transaction",
        "same mapper and Cypher contract",
    ], PALETTE["mint"])
    card(out, 1160, 845, 380, 140, "Algorithm Fallbacks", [
        "ShortestPathFallback",
        "BfsDfsRunner",
        "CycleDetector / UnionFind",
    ], PALETTE["sky"])

    out.append('<rect x="180" y="1065" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="1098" text-anchor="middle" class="tiny">'
        'Source: Neo4jGraphOperations.kt; labels and property keys are validated before Cypher text is assembled; close() leaves Driver ownership external.</text>'
    )
    return close_svg(out)


def graph_neo4j_coroutine_session_class_svg() -> str:
    width, height = 1840, 1100
    out = open_svg(
        "Neo4jCoroutineSession Class Model",
        "Small AutoCloseable bridge that opens a ReactiveSession, runs Query publishers, collects records through Flow, and closes sessions without owning Driver",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-neo4j / Neo4jCoroutineSession</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="800" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    # Relations are drawn first; cards sit above them.
    arrow(out, 395, 330, 560, 330, "constructs", "line")
    arrow(out, 920, 330, 1080, 330, "opens", "line")
    arrow(out, 1270, 430, 1270, 555, "run Query", "line")
    arrow(out, 1200, 740, 960, 740, "", "line")
    arrow(out, 760, 705, 760, 520, "toList()", "thin")
    arrow(out, 1080, 835, 960, 835, "", "thin")
    arrow(out, 760, 520, 455, 520, "List<Record>", "line")
    relation_label(760, 252, "Driver ownership remains external")
    relation_label(1080, 730, "records().asFlow()")
    relation_label(970, 810, "finally close")

    card(out, 125, 255, 270, 160, "Caller", [
        "suspend service",
        "read/write block",
        "query helper call",
    ], PALETTE["sky"])
    card(out, 125, 470, 330, 170, "Result Contract", [
        "read(block): List<T>",
        "write(block): List<T>",
        "runReadQuery(): List<Record>",
        "runWriteQuery(): List<Record>",
    ], PALETTE["mint"])

    card(out, 560, 235, 360, 330, "Neo4jCoroutineSession", [
        "driver: Driver",
        "database: String = neo4j",
        "sessionConfig()",
        "close() leaves driver open",
        "requires non-blank Cypher",
    ], PALETTE["lemon"])
    card(out, 560, 650, 400, 145, "kotlinx-coroutines-reactive", [
        "awaitSingle()",
        "awaitFirstOrNull()",
        "Publisher<Record>.asFlow()",
    ], PALETTE["aqua"])

    card(out, 1080, 245, 380, 170, "ReactiveSession", [
        "driver.session(ReactiveSession)",
        "SessionConfig.withDatabase",
        "one session per operation",
    ], PALETTE["lavender"])
    card(out, 1080, 550, 380, 160, "Reactive Result Stream", [
        "Query(cypher, params)",
        "result.records() publisher",
        "Record values returned",
    ], PALETTE["peach"])
    card(out, 1080, 810, 380, 120, "Session Close", [
        "session.close<Void>()",
        "awaitFirstOrNull() in finally",
    ], PALETTE["rose"])

    out.append('<rect x="180" y="990" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="1023" text-anchor="middle" class="tiny">'
        'Source: Neo4jCoroutineSession.kt; read/write collect Flow values, query helpers collect Record publisher values, and close() does not close Driver.</text>'
    )
    return close_svg(out)


def graph_neo4j_record_mapper_class_svg() -> str:
    width, height = 1840, 1100
    out = open_svg(
        "Neo4jRecordMapper Class Model",
        "Record, Node, Relationship, and Path values from Neo4j Driver are converted into graph-core domain models with elementId-based IDs",
        width,
        height,
    )
    out[-1] = (
        f'<text x="{width/2}" y="{height-48}" text-anchor="middle" class="tiny">'
        f'{esc(REPOSITORY_URL)} | project: {esc(PROJECT_NAME)} | module: graph-neo4j / Neo4jRecordMapper</text>'
    )
    out.append('<rect x="70" y="150" width="1700" height="800" rx="18" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="2" filter="url(#softShadow)"/>')

    def relation_label(x: float, y: float, text: str) -> None:
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round">{esc(text)}</text>')
        out.append(f'<text x="{x}" y="{y}" text-anchor="middle" class="tiny">{esc(text)}</text>')

    # Left-to-right conversion flow, with Path expansion kept separate at the bottom.
    arrow(out, 390, 315, 560, 315, "record[key]", "line")
    arrow(out, 390, 525, 560, 525, "record[key]", "line")
    arrow(out, 390, 735, 560, 735, "record[key]", "line")
    arrow(out, 900, 315, 1090, 315, "nodeToVertex", "line")
    arrow(out, 900, 525, 1090, 525, "relationshipToEdge", "line")
    arrow(out, 900, 735, 1090, 735, "pathToGraphPath", "line")
    arrow(out, 750, 805, 750, 830, "interleaves", "thin")
    relation_label(750, 205, "key.requireNotBlank before record extraction")

    card(out, 125, 240, 265, 150, "Record -> Node", [
        "recordToVertex(record)",
        "default key: n",
        "record[key].asNode()",
    ], PALETTE["sky"])
    card(out, 125, 450, 265, 150, "Record -> Rel", [
        "recordToEdge(record)",
        "default key: r",
        "asRelationship()",
    ], PALETTE["mint"])
    card(out, 125, 660, 265, 150, "Record -> Path", [
        "recordToPath(record)",
        "default key: p",
        "record[key].asPath()",
    ], PALETTE["peach"])

    card(out, 560, 225, 340, 190, "Node Conversion", [
        "node.elementId()",
        "first label or Unknown",
        "node.asMap()",
        "returns GraphVertex",
    ], PALETTE["lemon"])
    card(out, 560, 445, 340, 185, "Relationship Conversion", [
        "rel.elementId()",
        "start/end elementId",
        "rel.type() + asMap()",
        "returns GraphEdge",
    ], PALETTE["lavender"])
    card(out, 560, 665, 340, 145, "Path Conversion", [
        "path.nodes().toList()",
        "path.relationships().toList()",
        "build ordered PathStep list",
    ], PALETTE["aqua"])

    card(out, 1090, 225, 360, 185, "GraphVertex", [
        "id: GraphElementId",
        "label: String",
        "properties: Map",
    ], PALETTE["sky"])
    card(out, 1090, 445, 360, 185, "GraphEdge", [
        "id: GraphElementId",
        "label: relationship type",
        "startId / endId",
    ], PALETTE["mint"])
    card(out, 1090, 665, 360, 185, "GraphPath", [
        "List<PathStep>",
        "VertexStep(node)",
        "EdgeStep(relationship)",
    ], PALETTE["peach"])
    card(out, 520, 830, 460, 115, "PathStep Ordering", [
        "VertexStep, EdgeStep, VertexStep ...",
        "relationship added when index < rels.size",
    ], PALETTE["rose"])

    out.append('<rect x="180" y="990" width="1480" height="52" rx="12" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(
        '<text x="920" y="1023" text-anchor="middle" class="tiny">'
        'Source: Neo4jRecordMapper.kt; elementId(), startNodeElementId(), and endNodeElementId() avoid deprecated numeric id() mapping.</text>'
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
    if slug == "graph-graph-age-class-03":
        return graph_age_operations_class_svg()
    if slug == "graph-graph-age-class-04":
        return graph_age_sql_class_svg()
    if slug == "graph-graph-age-class-05":
        return graph_age_type_parser_class_svg()
    if slug == "graph-graph-neo4j-class-03":
        return graph_neo4j_operations_class_svg()
    if slug == "graph-graph-neo4j-class-04":
        return graph_neo4j_coroutine_session_class_svg()
    if slug == "graph-graph-neo4j-class-05":
        return graph_neo4j_record_mapper_class_svg()
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
        line_height = 24
        card_center_y = top + 37
        first_label_y = card_center_y - ((len(label_lines) - 1) * line_height / 2)
        for li, line in enumerate(label_lines):
            out.append(
                f'<text x="{x}" y="{first_label_y + li * line_height}" text-anchor="middle" '
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
        pill_center_y = pill_y + 15
        out.append(f'<rect x="{pill_x:.1f}" y="{pill_y}" width="{pill_w}" height="30" rx="8" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
        out.append(f'<circle cx="{pill_x + 24:.1f}" cy="{pill_center_y}" r="13" fill="{badge_fills[tone]}"/>')
        out.append(f'<text x="{pill_x + 24:.1f}" y="{pill_center_y}" text-anchor="middle" dominant-baseline="middle" class="tiny" style="fill:#FFFFFF">{index}</text>')
        out.append(f'<text x="{pill_x + 48:.1f}" y="{pill_center_y}" dominant-baseline="middle" class="tiny">{esc(label)}</text>')
    note_y = panel_bottom - 48
    out.append(f'<rect x="150" y="{note_y - 27}" width="{width - 300}" height="42" rx="11" fill="#FFFFFF" stroke="#D6E2ED" stroke-width="1.4"/>')
    out.append(f'<text x="{width / 2}" y="{note_y - 6}" text-anchor="middle" dominant-baseline="middle" class="tiny">Numbered labels sit above message lines; lifelines and activations stay separated from participant cards.</text>')
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
