# 2026-05-20 — Benchmark result charts

## 맥락

Graph benchmark documents used Mermaid xychart blocks for performance results.
The charts were valid but less readable than the static pastel chart style now
used for README visuals.

## 결정

Replace Mermaid xychart result blocks with generated SVG + PNG charts under
`docs/images/readme-charts/`. Use separate charts for Vertex, Traversal,
Algorithm, overall Sync vs VirtualThread, overhead, and Graph-IO import/export
results.

## 결과

The virtual-thread benchmark and Graph-IO bulk benchmark now have static charts
that preserve the measured values and make latency outliers visible.

## 검증

- `xmllint --noout docs/images/readme-charts/*.svg`
- `identify docs/images/readme-charts/*.png`
- Manual visual spot-check for the overall Sync vs VirtualThread chart.

## 향후 지침

Avoid repeating the same overall chart in multiple sections. Generate a focused
section chart when the document has section-specific benchmark tables.
