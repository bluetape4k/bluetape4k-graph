# Examples README Diagram Baseline

## Context

Several example modules had runnable scenarios and backend tests, but their README files did not consistently include
scenario, Architecture Diagram, ERD, Data Flow, and expected output sections.

## Decision

Backfill `code-graph-examples`, `linkedin-graph-examples`, and `ktor-graph-examples` with a shared README structure and
PNG-embedded diagrams, while keeping SVG source and Graphviz evidence files under `docs/images/readme-diagrams/`.

## Outcome

All current example README locale sets now include a scenario and visual explanation surface before API details.

## Verification Evidence

- README section scan: PASS for every `examples/*/README*.md` locale set.
- README image links: PASS, 42 embedded image links resolved.
- SVG/XML validation: PASS for generated and touched example diagram SVG files.
- PNG rendering: PASS, 12 example PNG assets detected as valid PNG files.
- Diagram companion files: PASS, each PNG has matching SVG, Graphviz `.dot`, `.plain`, and `-sketch.svg` evidence.
- Visual inspection: PASS, contact sheets were reviewed for the generated architecture, ERD, and data-flow assets.
- `git diff --check`: PASS.

## Future Guard

New example modules should treat scenario, Architecture Diagram, ERD or graph model, Data Flow, and expected output as
required README DoD items.
