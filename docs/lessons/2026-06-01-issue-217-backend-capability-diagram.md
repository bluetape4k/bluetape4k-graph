# 이슈 217 backend capability diagram

## 맥락

Issue #217 asked for README-facing diagrams that map the backend capability matrix into committed SVG/PNG assets.

## 결정

The root README now embeds one shared English-label PNG for both English and Korean documents. The Graphviz DOT source captures current backend comparison data plus graph-core schema, merge, and transaction support matrices.

## 결과

Added `root-readme-backend-capability-matrix-01.{dot,plain,svg,png}` under `docs/images/readme-diagrams/` and linked the PNG from both root README files.

## 검증

- Rendered DOT to `.plain`, `.svg`, and `.png` with Graphviz.
- Inspected the generated PNG at readable scale.
- `git diff --check`
- SVG parsed with Python `xml.etree.ElementTree`.
- README image links resolve to the committed PNG.
- Checked the SVG for stale UI font names (`Inter`, `Arial`, `Helvetica`).

## 향후 메모

For capability diagrams, keep the diagram labels English and update the same PNG link in every localized README. Re-read `graph-core` capability matrices before changing support wording.
