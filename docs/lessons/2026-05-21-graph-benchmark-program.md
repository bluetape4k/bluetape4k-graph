# Graph Benchmark Program

## Context

Issue #193 adds a single benchmark program for graph DB and graph-io performance comparison, with self-improve evidence gates.

## Decision

Use `benchmark/graph-benchmark` as the unified kotlinx-benchmark entry point instead of scattering comparison logic across backend-specific benchmark modules.

## Outcome

The benchmark harness now has DB and graph-io comparison classes plus a normalized JMH report script for before/after scoring.

## Verification

Compile and parser validation are the required gates before PR.

## Future Guard

Do not start self-improve optimization until a fresh baseline JSON exists and sealed-file validation is passing.
