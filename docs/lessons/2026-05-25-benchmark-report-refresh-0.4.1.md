# Benchmark Report Refresh — 0.4.1 Patch (Issue #214)

**Date**: 2026-05-25
**Issue**: [#214](https://github.com/bluetape4k/bluetape4k-graph/issues/214)
**Milestone**: 0.4.1

## Context

0.4.0 added a comprehensive benchmark evidence program (`graph-db` backend comparison,
sustained write ingestion, domain workloads, 10k write ingestion, and API model benchmarks)
with results stored under `docs/benchmark/2026-05-21-*` and a full decision guide at
`benchmark/README.md`.

After the 0.4.0 release, the root `README.md` and `README.ko.md` only linked the
original `2026-04-18-graph-io-bulk-results.md` from the graph-io section and had no
pointer to the broader benchmark decision guide.

## Decision

1. Updated the graph-io section benchmark link in `README.md` and `README.ko.md` to
   clarify it is graph-io-specific and to add a pointer to `benchmark/README.md` for
   the full result set.
2. Added a `benchmark/README.md` entry to the `## Documentation` / `## 문서` sections
   in both README files so library users can discover the decision guide from the root.

## What Was NOT Changed

- No benchmark implementation changes.
- `docs/benchmark/2026-04-18-graph-io-bulk-results.md` is still valid; it remains the
  most recent graph-io bulk I/O report (no graph-io benchmark was re-run in 0.4.0).
- `benchmark/README.md` already contained the full 0.4.0 result set and required no edits.

## Outcome

- `git diff --check`: passes (no trailing whitespace or line-ending issues).
- All referenced files verified to exist.
- README locale set (`README.md` + `README.ko.md`) synchronized.

## Future Guidance

When a new benchmark run is completed, update the following together:
1. Add new `docs/benchmark/YYYY-MM-DD-*.md` and `*.json` result files.
2. Update `benchmark/README.md` evidence base table and raw artifact list.
3. Update root `README.md` and `README.ko.md` links if the graph-io-specific link
   or the Documentation section pointer becomes stale.
4. If new chart images are generated, follow `benchmark/README.md` image embedding
   conventions and store assets under `docs/images/readme-charts/`.
