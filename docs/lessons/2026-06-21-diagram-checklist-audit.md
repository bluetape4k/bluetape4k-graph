# Diagram Checklist Audit

## Context

The graph repository already had README-facing SVG/PNG pairs, but the current
`bluetape4k-diagram` checklist is stricter than the older Graphviz-era lessons.
The audit focused on renderer-safe markers, rounded connector geometry, PNG
canvas backgrounds, catalog-backed card icons, and contact-sheet visual drift
across all README diagrams and charts.

After the first audit pass, a stricter user review found failures that the
original script-only gate did not prove away: collapsed rounded bends,
non-perpendicular connector endpoints, connectors crossing card interiors, card
over card/line occlusion, and duplicate card icons.

## Decision

Keep the existing source-backed diagrams and repair checklist failures in place
instead of regenerating the full set. Standardize SVG markers with
`markerUnits="userSpaceOnUse"`, replace unsafe/reversed rounded `Q` bends with
plain horizontal/vertical orthogonal connector segments when the rounded
geometry is not reliable, add explicit full-canvas backgrounds where CairoSVG
produced transparent PNG margins, and embed wiki catalog icons directly in SVG
cards for confirmed runtime services, databases, caches, and frameworks.

Remaining diagonal segments are intentional style exceptions for ERD
relationships, UML dependencies, sequence branch/return paths, and graph-domain
flow examples where a fully orthogonal route would make the diagram less clear.
Code-only class, helper, repository, loader, writer, DTO, and chart-series cards
remain text-only unless they represent a real runtime dependency. TinkerPop /
TinkerGraph currently has no confirmed catalog icon, so it should stay
text-only until the shared icon catalog gains one.

## Outcome

All README-facing SVG assets parse and render with CairoSVG. Geometry audit
reports zero collapsed or reversed rounded connector failures; remaining
`sharp_L_no_Q` reports are accepted because they are deliberate orthogonal
fallback routes. Marker audits report no implicit marker units, no
`strokeWidth` markers, no `context-stroke` markers, and no Graphviz residue.
Runtime/service cards use embedded catalog icons where available, with duplicate
PostgreSQL/AGE icon pairs avoided on combined PostgreSQL + Apache AGE cards.

The follow-up strict audit added card-aware connector checks for endpoint
attachment, card-interior crossings, collapsed `Q` corners, visible arrowhead
size, duplicate icon tags, and high-risk full-size PNG inspection. ERD
relationship lines are treated as the only explicit exception when the requested
scope says not to modify them.

## Verification Evidence

- SVG/XML parse and CairoSVG render: 105 SVG files.
- Geometry audit: 105 files, 0 real failures outside deliberate sharp
  orthogonal fallback routes.
- Marker pattern audit: no implicit units, no `strokeWidth`, no `context-stroke`.
- PNG canvas audit: no RGBA/transparent README PNG outputs.
- Icon audit: all embedded catalog icons checked for duplicate same-icon use;
  0 duplicate icon issues after follow-up cleanup.
- Strict connector/card audit: 105 SVG files, 0 issue files outside deliberate
  sharp orthogonal fallback routes and explicitly excluded ERD
  relationship-line cases.
- Visual QA: full-size inspection for repaired root, AGE, Neo4j, Ktor,
  knowledge-graph, data-flow, and duplicate-icon diagrams; contact sheets for
  all diagrams and all charts.

## Future Guard

Do not reuse the old Graphviz companion-file requirement for new README
diagrams. Current work should follow `bluetape4k-diagram`: source-first
semantics, CairoSVG PNG output, marker/color parity checks, catalog icon checks,
geometry audit, strict connector/card audit, and rendered PNG inspection. Do
not claim checklist success from SVG/XML/render success alone. When rounded
orthogonal bends are visually unstable or reversed, prefer readable
horizontal/vertical orthogonal lines and then verify the rendered PNG.
