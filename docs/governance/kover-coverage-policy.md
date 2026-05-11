# Kover Coverage Policy

## Current Status

`bluetape4k-graph` aggregates Kover reports from production modules and excludes
benchmark/example modules from coverage aggregation.

## Policy

Status: report-only transition.

Graph database backends require external runtimes and have different coverage
profiles from pure graph-io modules. Coverage gates should be module-level.

## Threshold Plan

- Treat Kover as a trend signal, not a build gate.
- Use Nightly XML reports and existing coverage artifact uploads to identify
  coverage regressions.
- Open a focused issue when a module needs coverage repair; do not introduce a
  failing threshold as the default enforcement mechanism.
- Keep benchmark and example modules outside production coverage gates.

## CI/Nightly Contract

Nightly uploads coverage artifacts and keeps trend visibility. CI and Nightly
must not fail solely because a module is below a fixed coverage percentage
unless a future issue explicitly reintroduces that gate.
