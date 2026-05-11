# Kover Coverage Policy

## Current Status

`bluetape4k-graph` aggregates Kover reports from production modules and excludes
benchmark/example modules from coverage aggregation.

## Policy

Status: report-only transition.

Graph database backends require external runtimes and have different coverage
profiles from pure graph-io modules. Coverage gates should be module-level.

## Threshold Plan

- Gate graph-io/core and pure serialization modules first, targeting 80%.
- Gate graph DB backends only after backend-specific baseline runs.
- Keep benchmark and example modules outside production coverage gates.

## CI/Nightly Contract

Nightly uploads coverage artifacts. Add `koverVerify` for individual modules
after thresholds are introduced.
