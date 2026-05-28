# Graphviz README Diagram Regeneration

## Context

Several existing architecture and sequence README diagrams had PNG/SVG assets but no Graphviz `.dot`, `.plain`, or
`-sketch.svg` evidence. That made route review and future visual fixes hard to verify deterministically.

## Decision

Regenerate the legacy architecture and sequence assets from the existing visual model with Graphviz as the layout and
route evidence source. For sequence diagrams, keep the time axis readable while increasing the outer canvas margins and
using tighter actor/message card padding.

## Outcome

All architecture and sequence README diagrams now have matching PNG, SVG, Graphviz `.dot`, `.plain`, and `-sketch.svg`
companions. New sequence diagrams use wider outer margins and more compact internal message lanes.

## Verification Evidence

- GNO docs lookup returned no reusable repo-doc hit; GNO GitHub lookup surfaced prior README diagram PRs #180 and #186.
- Graphviz companion scan: PASS for 46 architecture/sequence PNGs.
- Graphviz evidence summary: 361 nodes and 435 routed edges across architecture/sequence `.plain` files.
- SVG/XML validation: PASS for architecture/sequence SVG files.
- PNG validation: PASS for 46 architecture/sequence PNG files.
- README image links: PASS for 141 local embedded image links.
- Visual inspection: PASS via architecture, benchmark architecture, and sequence contact sheets plus focused sequence/architecture previews.

## Future Guard

Do not add README architecture or sequence diagrams without Graphviz `.dot`, `.plain`, and `-sketch.svg` evidence. For
sequence diagrams, prefer larger outer canvas margins before increasing actor or message label padding.
