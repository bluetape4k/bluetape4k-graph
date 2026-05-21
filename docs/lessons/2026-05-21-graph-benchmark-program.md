# Graph Benchmark Program

## Context

Issue #193 adds a single benchmark program for graph DB and graph-io performance comparison, with self-improve evidence gates.

## Decision

Use `benchmark/graph-benchmark` as the unified kotlinx-benchmark entry point instead of scattering comparison logic across backend-specific benchmark modules.

## Outcome

The benchmark harness now has DB and graph-io comparison classes plus a normalized JMH report script for before/after scoring.

The first real Testcontainers-backed graph DB run is stored under `docs/benchmark/graph-db-testcontainers-2026-05-21.json`, normalized into `docs/benchmark/graph-benchmark-baseline.json`, and rendered as paired README chart assets under `docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.{svg,png}`.

Independent benchmark modules under `benchmark/` should remain independent unless there is a stronger build reason to merge them. Put readable result tables and chart links in each module README instead of forcing all benchmark code into `graph-benchmark`.

## Verification

Compile, parser validation, chart rendering, `:graph-benchmark:test`, and an actual Testcontainers JMH run are the required gates before PR.

## Future Guard

Do not start self-improve optimization until a fresh baseline JSON exists and sealed-file validation is passing from committed HEAD. A deliberate baseline refresh will fail the sealed validator until that refresh is committed.
