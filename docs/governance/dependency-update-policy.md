# Dependency Update Policy

## Current Status

`bluetape4k-graph` is a leaf repository in the bluetape4k dependency graph.
Gradle and Maven library versions are governed centrally through
`bluetape4k-dependencies`.

## Policy

- Change shared library versions in `bluetape4k-dependencies` first.
- Materialize central updates into this repository with the shared sync scripts.
- Keep this repository's Dependabot configuration limited to GitHub Actions.
- Do not enable Renovate in this repository unless the organization changes the
  central dependency-governance model.

## Dependabot Scope

`.github/dependabot.yml` intentionally tracks only GitHub Actions updates on
the `develop` branch. Gradle package updates are omitted so leaf repositories do
not drift away from the central BOM and version catalog.

## Verification Contract

- Parse `.github/dependabot.yml` after edits.
- For central version sync work, run the relevant `sync-shared-versions.py` and
  `sync-dependabot-ignores.py` checks from the central dependency repository.
- Record any intentional exception in a focused issue before changing the
  per-repository Dependabot scope.
