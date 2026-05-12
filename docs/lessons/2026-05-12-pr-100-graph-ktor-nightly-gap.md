# PR #100 graph-ktor Nightly Gap Lessons

## Context

- PR #100 added `ktor/graph-ktor` and `examples/ktor-graph-examples`.
- The PR verified `:graph-ktor:test` and `:ktor-graph-examples:test` locally and in the PR body.
- CI/Nightly workflow updates missed the new module-specific test task.

## Decision or Finding

- Lesson: adding a module is not complete until CI and Nightly task lists include it.
  - Evidence: `.github/workflows/nightly.yml` did not contain `:graph-ktor:test` or `:ktor-graph-examples:test` after PR #100 was merged.
  - Future guard: every new module plan must include a workflow grep for the module path and Gradle task name.

- Lesson: integration module tests belong in their own workflow job when their runtime cost differs from surrounding modules.
  - Evidence: `graph-ktor:test` includes lightweight Ktor tests plus backend Testcontainers smoke for Neo4j, Memgraph, AGE, and FalkorDB. Putting it into the daily in-memory core job would make smoke Nightly unexpectedly pull containers.
  - Future guard: keep `graph-ktor` in a dedicated Ktor Graph Testcontainers job and run it on full Nightly / relevant CI changes.

## Outcome

- `AGENTS.md` now explicitly requires CI and Nightly updates when a new module is added.
- `AGENTS.md` now requires explicit `workflow_dispatch` execution when Nightly workflow changes.
- CI path filtering now has a `graph-ktor` category for:
  - `ktor/graph-ktor/**`
  - `examples/ktor-graph-examples/**`
- CI now has `Test / Ktor Graph (Testcontainers)` for `:graph-ktor:test` and `:ktor-graph-examples:test`.
- Nightly full now has the same Ktor Graph Testcontainers job.
- Coverage aggregation now includes `coverage-ktor`.

## Verification

- `git diff --check`
- `./gradlew :graph-ktor:test :ktor-graph-examples:test --no-daemon`
- Nightly full dispatch for branch `ci/graph-ktor-nightly` completed successfully:
  - https://github.com/bluetape4k/bluetape4k-graph/actions/runs/25705115469
  - `Test / Ktor Graph (Testcontainers)` passed.
  - `Nightly Status` passed.

## Future Guidance

- For every new module, search workflow coverage before PR close:
  - `rg "<module-name>|:<module-name>:test" .github/workflows`
- If a module contains Testcontainers smoke, do not hide it inside an in-memory smoke job.
- If `nightly.yml` changes, trigger Nightly manually and wait for the relevant jobs before reporting DoD.
- Add a lesson immediately when workflow coverage was missed.
