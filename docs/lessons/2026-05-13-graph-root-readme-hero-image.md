# Graph Root README hero image

- Date: 2026-05-13
- 범위: root `README.md`, `README.ko.md`, `WIP.md`, `CHANGELOG.md`, `AGENTS.md`, `CLAUDE.md`

## 맥락

The root README needed a representative graph database image that matches the existing bluetape4k workspace visual language. After the image was accepted, the root project docs also needed to catch up with recently merged graph changes: Spring Boot module rename, domain examples, examples workflow, and graph-okio DAEAD streaming.

## 결정

Use the organization profile workbench image only as a visual reference, then generate a new graph-specific PNG under `docs/assets/` with a dedicated image generation model. Add the same image to both English and Korean root README files because these are library-user documents. Keep agent-facing guidance (`AGENTS.md`, `CLAUDE.md`) in English and refresh WIP/CHANGELOG state from live GitHub issue/PR status.

## 결과

The README hero now shows a newly generated bluetape4k-style workbench scene with graph database blocks, blue tape, and a glowing graph topology. The repository owns the rendered image and does not depend on external image paths. The root README pair now includes a fuller project overview, supported database guidance, Mermaid architecture diagrams, updated module layout, graph-okio DAEAD notes, and the current examples workflow. WIP and agent docs now reflect the current issue queue and module/runtime surface.

## 검증

- `magick identify docs/assets/bluetape4k-graph-workbench.png`
- `rg "bluetape4k-graph-workbench.png" README.md README.ko.md`
- `gh issue list --repo bluetape4k/bluetape4k-graph --state open --limit 100 --json number,title,labels,assignees,updatedAt,url`
- `gh pr list --repo bluetape4k/bluetape4k-graph --state merged --limit 25 --json number,title,mergedAt,url`
- `git diff --check`
- `rg "buildSrc/src/main/kotlin/Libs.kt|Java\\*\\*: 25|graph-spring-boot4-starter" README.md README.ko.md AGENTS.md CLAUDE.md WIP.md CHANGELOG.md`

## 향후 가드

For root README imagery, first check `.github/profile/assets/` for visual direction, but use an image generation model for final raster assets rather than assembling overlays by hand.

For root documentation refreshes, verify live GitHub issue/PR status before rewriting `WIP.md`; stale WIP queues can outlive fast-moving merges by only a few hours.
