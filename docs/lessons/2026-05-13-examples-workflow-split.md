# Examples Workflow Split

## Context

Example modules were previously tested from Nightly jobs, with the Ktor example also coupled to the graph-ktor CI job.
That mixed example adoption checks with core library/backend verification.

## Decision

Move all example module build/test coverage into a dedicated `Examples` GitHub Actions workflow. Trigger it daily, on
example-relevant changes, and manually through `workflow_dispatch`.

## Outcome

Nightly now excludes example module tests. General CI no longer runs `:ktor-graph-examples:test` from the graph-ktor job.
The new examples workflow owns all example module `build` tasks:

- `:code-graph-examples:build`
- `:linkedin-graph-examples:build`
- `:ktor-graph-examples:build`
- `:fraud-detection-examples:build`
- `:recommendation-examples:build`
- `:knowledge-graph-examples:build`

## Verification

- `actionlint .github/workflows/ci.yml`
- `actionlint .github/workflows/nightly.yml`
- `actionlint .github/workflows/examples.yml`
- `git diff --check`

## Future Guidance

When adding example modules, update `.github/workflows/examples.yml` instead of Nightly. Nightly should focus on backend
and library validation, while the Examples workflow owns learning/adoption examples.
