#!/usr/bin/env ruby
# frozen_string_literal: true

require "fileutils"
require "open3"

ROOT = File.expand_path("../..", __dir__)
WIDTH = 1600
HEIGHT = 1040

COLORS = {
  cyan: "#8bd5ff",
  teal: "#4fe0cf",
  purple: "#b8a1ff",
  amber: "#ffc857",
  rose: "#ff9aae",
}.freeze

ASSETS = {
  "repository-learning-map" => "docs/manual/assets/overview/repository-learning-map",
  "core-abstraction-map" => "docs/manual/assets/architecture/core-abstraction-map",
  "backend-decision-map" => "docs/manual/assets/backends/backend-decision-map",
  "graph-io-pipeline" => "docs/manual/assets/graph-io/graph-io-pipeline",
  "framework-integration-flow" => "docs/manual/assets/frameworks/framework-integration-flow",
}.freeze

def marker_defs
  COLORS.map do |name, color|
    <<~SVG
      <marker id="arrow-#{name}" markerWidth="14" markerHeight="14" refX="12" refY="7" orient="auto" markerUnits="userSpaceOnUse" viewBox="0 0 14 14">
        <polygon points="0,1 14,7 0,13" fill="#{color}" stroke="none"/>
      </marker>
    SVG
  end.join
end

def card(id:, x:, y:, w:, h:, color:, title:, lines:, title_size: 34, line_size: 21, extra_class: "")
  line_gap = 34
  first_line = y + 92
  details = lines.each_with_index.map do |line, index|
    %(<text class="detail" x="#{x + w / 2}" y="#{first_line + index * line_gap}" text-anchor="middle" font-size="#{line_size}">#{line}</text>)
  end.join("\n")
  <<~SVG
    <g id="#{id}">
      <rect class="card #{extra_class}" data-card-id="#{id}" x="#{x}" y="#{y}" width="#{w}" height="#{h}" rx="28" fill="#172238" stroke="#{COLORS.fetch(color)}" stroke-width="5"/>
      <text class="card-title" x="#{x + w / 2}" y="#{y + 55}" text-anchor="middle" font-size="#{title_size}" fill="#{COLORS.fetch(color)}">#{title}</text>
      #{details}
    </g>
  SVG
end

def connector(id:, d:, route:, from:, to:, color:)
  <<~SVG
    <path id="#{id}" class="connector" data-start-card="#{from}" data-end-card="#{to}" data-route="#{route}" d="#{d}" fill="none" stroke="#{COLORS.fetch(color)}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" marker-end="url(#arrow-#{color})"/>
  SVG
end

def svg(title:, subtitle:, body:, description:)
  <<~SVG
    <?xml version="1.0" encoding="UTF-8"?>
    <svg xmlns="http://www.w3.org/2000/svg" width="#{WIDTH}" height="#{HEIGHT}" viewBox="0 0 #{WIDTH} #{HEIGHT}" role="img" aria-labelledby="title desc">
      <title id="title">#{title}</title>
      <desc id="desc">#{description}</desc>
      <defs>
        #{marker_defs}
        <filter id="shadow" x="-15%" y="-15%" width="130%" height="130%">
          <feDropShadow dx="0" dy="8" stdDeviation="8" flood-color="#07101f" flood-opacity="0.45"/>
        </filter>
      </defs>
      <style>
        .frame { fill: #111a2b; stroke: #53657d; stroke-width: 5; }
        .card { filter: url(#shadow); }
        .card-title, .heading { font-family: 'Architects Daughter', 'Comic Sans MS', cursive; }
        .detail, .subtitle, .legend { font-family: 'Comic Mono', 'SFMono-Regular', monospace; fill: #cbd5e5; }
        .connector { opacity: 0.98; }
      </style>
      <rect width="1600" height="1040" fill="#0b1322"/>
      <rect class="frame" x="34" y="34" width="1532" height="972" rx="36"/>
      <text class="heading" x="800" y="100" text-anchor="middle" font-size="52" fill="#f4f7fb">#{title}</text>
      <text class="subtitle" x="800" y="142" text-anchor="middle" font-size="20">#{subtitle}</text>
      #{body}
    </svg>
  SVG
end

def repository_learning_map
  cards = []
  cards << card(id: "foundation", x: 70, y: 265, w: 280, h: 360, color: :cyan,
                title: "Foundation", lines: ["models + opaque IDs", "GraphSession", "paired operation APIs", "schema + transactions"])
  cards << card(id: "backends", x: 390, y: 265, w: 280, h: 360, color: :teal,
                title: "Backends", lines: ["Neo4j + Memgraph", "Apache AGE", "TinkerGraph", "FalkorDB"])
  cards << card(id: "io-frameworks", x: 710, y: 265, w: 280, h: 360, color: :purple,
                title: "Transfer + Apps", lines: ["CSV / NDJSON / GraphML", "OkIO + DAEAD", "Ktor plugin", "Spring auto-config"])
  cards << card(id: "evidence", x: 1030, y: 265, w: 280, h: 360, color: :amber,
                title: "Evidence", lines: ["domain examples", "backend tests", "benchmarks", "failure injection"])
  cards << card(id: "manual", x: 1110, y: 760, w: 400, h: 150, color: :rose,
                title: "Operate from the manual", lines: ["select • test • diagnose • recover"], title_size: 27, line_size: 18)

  links = []
  links << connector(id: "learn-1", d: "M 350 445 H 390", route: "350,445 390,445", from: "foundation", to: "backends", color: :cyan)
  links << connector(id: "learn-2", d: "M 670 445 H 710", route: "670,445 710,445", from: "backends", to: "io-frameworks", color: :teal)
  links << connector(id: "learn-3", d: "M 990 445 H 1030", route: "990,445 1030,445", from: "io-frameworks", to: "evidence", color: :purple)
  links << connector(id: "learn-4", d: "M 1170 625 V 690 Q 1170 710 1190 710 H 1290 Q 1310 710 1310 730 V 760", route: "1170,625 1170,710 1310,710 1310,760", from: "evidence", to: "manual", color: :amber)

  legend = <<~SVG
    <text class="legend" x="90" y="930" font-size="18">Reading path: contract &gt; implementation &gt; integration &gt; evidence &gt; operating guidance</text>
  SVG
  svg(title: "Learn the Graph Repository by Responsibility",
      subtitle: "Graph 0.5.1 • move outward only after the inner contract is understood",
      description: "A repository learning map from core foundations through backends, transfer and framework integration, examples, benchmarks, and operating guidance.",
      body: cards.join + links.join + legend)
end

def core_abstraction_map
  cards = []
  cards << card(id: "core-contract", x: 230, y: 190, w: 1140, h: 170, color: :cyan,
                title: "Immutable snapshots + GraphSession", lines: ["GraphElementId • GraphVertex • GraphEdge • GraphPath", "create / drop / exists; injected driver or data source stays caller-owned"])
  cards << card(id: "sync-ops", x: 130, y: 500, w: 360, h: 190, color: :teal,
                title: "GraphOperations", lines: ["blocking call chains", "session + CRUD + traversal"])
  cards << card(id: "suspend-ops", x: 620, y: 500, w: 360, h: 190, color: :purple,
                title: "GraphSuspendOperations", lines: ["suspend calls + Flow", "coroutine-owned cancellation"], title_size: 30)
  cards << card(id: "vt-ops", x: 1110, y: 500, w: 360, h: 190, color: :amber,
                title: "Virtual-thread adapter", lines: ["async blocking isolation", "GraphVirtualThreadOperations"], title_size: 30)
  cards << card(id: "capabilities", x: 230, y: 800, w: 1140, h: 170, color: :rose,
                title: "Optional capability boundaries", lines: ["merge + batch • schema manager • transaction / suspendTransaction", "unsupported capability means explicit failure"], line_size: 18)

  links = []
  links << connector(id: "core-sync", d: "M 500 360 V 420 Q 500 440 480 440 H 330 Q 310 440 310 460 V 500", route: "500,360 500,440 310,440 310,500", from: "core-contract", to: "sync-ops", color: :cyan)
  links << connector(id: "core-suspend", d: "M 800 360 V 500", route: "800,360 800,500", from: "core-contract", to: "suspend-ops", color: :cyan)
  links << connector(id: "core-vt", d: "M 1100 360 V 420 Q 1100 440 1120 440 H 1270 Q 1290 440 1290 460 V 500", route: "1100,360 1100,440 1290,440 1290,500", from: "core-contract", to: "vt-ops", color: :cyan)
  links << connector(id: "sync-cap", d: "M 310 690 V 730 Q 310 750 330 750 H 600 Q 620 750 620 770 V 800", route: "310,690 310,750 620,750 620,800", from: "sync-ops", to: "capabilities", color: :teal)
  links << connector(id: "suspend-cap", d: "M 800 690 V 800", route: "800,690 800,800", from: "suspend-ops", to: "capabilities", color: :purple)
  links << connector(id: "vt-cap", d: "M 1290 690 V 730 Q 1290 750 1270 750 H 1000 Q 980 750 980 770 V 800", route: "1290,690 1290,750 980,750 980,800", from: "vt-ops", to: "capabilities", color: :amber)

  svg(title: "One Model, Three Execution Surfaces",
      subtitle: "The facade is portable; optional capabilities and resource ownership remain explicit",
      description: "A static abstraction map connecting immutable graph snapshots and session contracts to synchronous, coroutine, and virtual-thread operation families and optional capability boundaries.",
      body: cards.join + links.join)
end

def backend_decision_map
  cards = []
  cards << card(id: "criteria", x: 150, y: 180, w: 1300, h: 150, color: :purple,
                title: "Start from operated infrastructure and required semantics", lines: ["query language • transaction boundary • schema/index capability • local verification"])
  backend_data = [
    ["neo4j", 60, :cyan, "Neo4j", ["Bolt + Cypher", "native transactions", "indexes + constraints", "Neo4j 5 container"]],
    ["memgraph", 360, :teal, "Memgraph", ["Bolt-compatible Cypher", "native transactions", "backend-specific DDL", "Memgraph container"]],
    ["age", 660, :purple, "Apache AGE", ["PostgreSQL + Cypher/SQL", "JDBC / Exposed boundary", "limited portable DDL", "AGE PG16 container"]],
    ["tinker", 960, :amber, "TinkerGraph", ["in-process Gremlin", "in-memory semantics", "limited manager", "no container"]],
    ["falkor", 1260, :rose, "FalkorDB", ["Redis-shaped service", "openCypher subset", "backend-specific indexes", "FalkorDB container"]],
  ]
  backend_data.each do |id, x, color, title, lines|
    cards << card(id: id, x: x, y: 500, w: 280, h: 300, color: color, title: title, lines: lines, title_size: 30, line_size: 18)
  end
  cards << card(id: "neptune", x: 430, y: 880, w: 740, h: 80, color: :rose,
                title: "Amazon Neptune: unavailable in Graph 0.5.1", lines: [], title_size: 27)

  links = []
  [["neo4j", 260, 200, :cyan], ["memgraph", 560, 500, :teal], ["age", 800, 800, :purple], ["tinker", 1040, 1100, :amber], ["falkor", 1340, 1400, :rose]].each do |id, source_x, target_x, color|
    if source_x == target_x
      links << connector(id: "choose-#{id}", d: "M #{source_x} 330 V 500", route: "#{source_x},330 #{target_x},500", from: "criteria", to: id, color: color)
    else
      direction = target_x < source_x ? -1 : 1
      after_first = source_x + (20 * direction)
      before_last = target_x - (20 * direction)
      d = "M #{source_x} 330 V 390 Q #{source_x} 410 #{after_first} 410 H #{before_last} Q #{target_x} 410 #{target_x} 430 V 500"
      links << connector(id: "choose-#{id}", d: d, route: "#{source_x},330 #{source_x},410 #{target_x},410 #{target_x},500", from: "criteria", to: id, color: color)
    end
  end

  svg(title: "Choose a Backend You Can Verify",
      subtitle: "Feature counts do not replace infrastructure fit, semantic checks, or local failure evidence",
      description: "A backend decision map comparing Neo4j, Memgraph, Apache AGE, TinkerGraph, and FalkorDB, with Amazon Neptune explicitly unavailable in Graph 0.5.1.",
      body: cards.join + links.join)
end

def graph_io_pipeline
  cards = []
  cards << card(id: "snapshot", x: 90, y: 230, w: 360, h: 210, color: :cyan,
                title: "Model snapshot", lines: ["vertices + directed edges", "opaque backend IDs"])
  cards << card(id: "format", x: 620, y: 230, w: 360, h: 210, color: :teal,
                title: "Export format", lines: ["CSV pair • NDJSON", "GraphML document"])
  cards << card(id: "protect", x: 1150, y: 230, w: 360, h: 210, color: :purple,
                title: "Optional protection", lines: ["compress, then DAEAD", "single-stream formats"])
  cards << card(id: "transport", x: 1150, y: 600, w: 360, h: 190, color: :amber,
                title: "Owned transport", lines: ["Path / Source / Sink", "atomic temporary write"])
  cards << card(id: "policy", x: 620, y: 600, w: 360, h: 190, color: :rose,
                title: "Import policy + ID map", lines: ["duplicate FAIL / SKIP", "missing endpoint FAIL / SKIP"], title_size: 29)
  cards << card(id: "target", x: 90, y: 600, w: 360, h: 190, color: :cyan,
                title: "Target operations", lines: ["sync / suspend / VT", "batch writes + report"])
  cards << card(id: "guardrails", x: 250, y: 870, w: 1100, h: 100, color: :rose,
                title: "Guardrails: auth mismatch • truncation • size bounds • edge-buffer overflow • partial writes", lines: [], title_size: 25)

  links = []
  links << connector(id: "io-1", d: "M 450 335 H 620", route: "450,335 620,335", from: "snapshot", to: "format", color: :cyan)
  links << connector(id: "io-2", d: "M 980 335 H 1150", route: "980,335 1150,335", from: "format", to: "protect", color: :teal)
  links << connector(id: "io-3", d: "M 1330 440 V 600", route: "1330,440 1330,600", from: "protect", to: "transport", color: :purple)
  links << connector(id: "io-4", d: "M 1150 695 H 980", route: "1150,695 980,695", from: "transport", to: "policy", color: :amber)
  links << connector(id: "io-5", d: "M 620 695 H 450", route: "620,695 450,695", from: "policy", to: "target", color: :rose)
  links << connector(id: "io-guard", d: "M 800 790 V 820 Q 800 840 820 840 H 830 Q 850 840 850 860 V 870", route: "800,790 800,840 850,840 850,870", from: "policy", to: "guardrails", color: :rose)

  svg(title: "Graph I/O Preserves Boundaries, Not Backend Identity",
      subtitle: "Export and import share records; policies, protection, ownership, and failure handling stay explicit",
      description: "A graph I/O architecture pipeline from model snapshots through format, optional compression and DAEAD, transport, import policies and external ID mapping, to target operations and negative-path guardrails.",
      body: cards.join + links.join)
end

def framework_integration_flow
  cards = []
  cards << card(id: "application", x: 500, y: 175, w: 600, h: 130, color: :purple,
                title: "Application-owned boundary", lines: ["configuration + request/coroutine scope"])
  cards << card(id: "ktor", x: 100, y: 420, w: 520, h: 220, color: :amber,
                title: "Ktor GraphPlugin", lines: ["Application / ApplicationCall access", "closeOnStop=false: caller owns", "closeOnStop=true: plugin closes once"], line_size: 19)
  cards << card(id: "spring", x: 980, y: 420, w: 520, h: 220, color: :cyan,
                title: "Spring auto-configuration", lines: ["classpath + properties + conditions", "user beans trigger backoff", "container closes created resources"], title_size: 30, line_size: 19)
  cards << card(id: "ops-family", x: 490, y: 710, w: 620, h: 130, color: :teal,
                title: "GraphOperations family", lines: ["sync • suspend + Flow • virtual-thread adapter"])
  cards << card(id: "selected-backend", x: 550, y: 890, w: 500, h: 80, color: :rose,
                title: "One selected backend", lines: [], title_size: 29)

  links = []
  links << connector(id: "app-ktor", d: "M 650 305 V 350 Q 650 370 630 370 H 380 Q 360 370 360 390 V 420", route: "650,305 650,370 360,370 360,420", from: "application", to: "ktor", color: :amber)
  links << connector(id: "app-spring", d: "M 950 305 V 350 Q 950 370 970 370 H 1220 Q 1240 370 1240 390 V 420", route: "950,305 950,370 1240,370 1240,420", from: "application", to: "spring", color: :cyan)
  links << connector(id: "ktor-ops", d: "M 360 640 V 655 Q 360 675 380 675 H 650 Q 670 675 670 695 V 710", route: "360,640 360,675 670,675 670,710", from: "ktor", to: "ops-family", color: :amber)
  links << connector(id: "spring-ops", d: "M 1240 640 V 655 Q 1240 675 1220 675 H 950 Q 930 675 930 695 V 710", route: "1240,640 1240,675 930,675 930,710", from: "spring", to: "ops-family", color: :cyan)
  links << connector(id: "ops-backend", d: "M 800 840 V 890", route: "800,840 800,890", from: "ops-family", to: "selected-backend", color: :teal)

  svg(title: "Framework Convenience Must Preserve Ownership",
      subtitle: "Ktor and Spring expose the same operation family while shutdown authority stays explicit",
      description: "A framework integration architecture map from an application through Ktor or Spring to the graph operation family and selected backend, including closeOnStop ownership and conditional backoff.",
      body: cards.join + links.join)
end

BUILDERS = {
  "repository-learning-map" => method(:repository_learning_map),
  "core-abstraction-map" => method(:core_abstraction_map),
  "backend-decision-map" => method(:backend_decision_map),
  "graph-io-pipeline" => method(:graph_io_pipeline),
  "framework-integration-flow" => method(:framework_integration_flow),
}.freeze

names = ARGV.empty? ? ASSETS.keys : ARGV
unknown = names - ASSETS.keys
abort "Unknown asset(s): #{unknown.join(', ')}" unless unknown.empty?

names.each do |name|
  base = File.join(ROOT, ASSETS.fetch(name))
  svg_path = "#{base}.svg"
  png_path = "#{base}.png"
  FileUtils.mkdir_p(File.dirname(base))
  File.write(svg_path, BUILDERS.fetch(name).call.gsub(/[ \t]+$/, ""))
  stdout, stderr, status = Open3.capture3("cairosvg", svg_path, "-o", png_path, "-s", "2")
  abort "CairoSVG failed for #{name}: #{stdout}#{stderr}" unless status.success?
  puts "rendered #{name}: #{svg_path} -> #{png_path}"
end
