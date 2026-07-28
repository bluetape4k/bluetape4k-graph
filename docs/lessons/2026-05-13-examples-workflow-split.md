# Examples Workflow 분리

## 맥락

Example modules were previously tested from Nightly jobs, with the Ktor example also coupled to the graph-ktor CI job.
That mixed example adoption checks with core library/backend verification.

## 결정

Move all example module build/test coverage into a dedicated `Examples` GitHub Actions workflow. Trigger it daily, on
example-relevant changes, and manually through `workflow_dispatch`.

## 결과

Nightly now excludes example module tests. General CI no longer runs `:ktor-graph-examples:test` from the graph-ktor job.
The new examples workflow owns all example module `build` tasks:

- `:code-graph-examples:build`
- `:linkedin-graph-examples:build`
- `:ktor-graph-examples:build`
- `:fraud-detection-examples:build`
- `:recommendation-examples:build`
- `:knowledge-graph-examples:build`

## 검증

- `actionlint .github/workflows/ci.yml`
- `actionlint .github/workflows/nightly.yml`
- `actionlint .github/workflows/examples.yml`
- `git diff --check`

## 향후 지침

When adding example modules, update `.github/workflows/examples.yml` instead of Nightly. Nightly should focus on backend
and library validation, while the Examples workflow owns learning/adoption examples.
