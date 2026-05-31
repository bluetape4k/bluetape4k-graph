# Issue #268 - Shared Ktor Module Adoption

## Context

- Issue: #268 `chore(graph-ktor): adopt shared bluetape4k Ktor modules`
- Scope: `ktor/graph-ktor`, `examples/ktor-graph-examples`
- `bluetape4k-projects` 1.10.0 published shared Ktor modules, so graph needed to remove local generic Ktor helper/test duplication where the shared surface already owned it.

## Decision

- Keep `graph-ktor` production code backend-neutral and graph-specific.
- Reuse `bluetape4k-ktor-testing` in `graph-ktor` tests for common response status assertions.
- Reuse `bluetape4k-ktor-core` in `ktor-graph-examples` for standard health/readiness routes and JSON defaults.
- Keep demo graph routes explicit so the example still teaches `GraphPlugin` route accessors.

## Outcome

- `GET /health` and `GET /readyz` now return the standard bluetape4k health JSON body in the example app.
- Demo routes still return the same graph-specific text responses.
- Example README files were updated together for the new dependency behavior and response shape.

## Verification

- `./gradlew -q projects --no-daemon | rg "graph-ktor|ktor-graph-examples"`
- `./gradlew :bluetape4k-graph-ktor:compileTestKotlin :ktor-graph-examples:compileTestKotlin --no-daemon`
- `./gradlew :bluetape4k-graph-ktor:test :ktor-graph-examples:test --no-daemon --no-parallel`
- Code-review graph incremental update and review context over the 11 changed files.

## Future Guard

When adopting shared Ktor modules, separate generic Ktor behavior from graph-specific route/plugin behavior. Shared health, JSON, and response-test helpers should come from `bluetape4k-ktor-*`; graph routes and backend lifecycle rules should stay local.
