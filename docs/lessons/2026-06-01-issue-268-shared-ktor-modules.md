# 이슈 #268 - Shared Ktor Module Adoption

## 맥락

- Issue: #268 `chore(graph-ktor): adopt shared bluetape4k Ktor modules`
- 범위: `ktor/graph-ktor`, `examples/ktor-graph-examples`
- `bluetape4k-projects` 1.10.0 published shared Ktor modules, so graph needed to remove local generic Ktor helper/test duplication where the shared surface already owned it.

## 결정

- Keep `graph-ktor` production code backend-neutral and graph-specific.
- Reuse `bluetape4k-ktor-testing` in `graph-ktor` tests for common response status assertions.
- Reuse `bluetape4k-ktor-core` in `ktor-graph-examples` for standard health/readiness routes and JSON defaults.
- Keep demo graph routes explicit so the example still teaches `GraphPlugin` route accessors.

## 결과

- `GET /health` and `GET /readyz` now return the standard bluetape4k health JSON body in the example app.
- Demo routes still return the same graph-specific text responses.
- Example README files were updated together for the new dependency behavior and response shape.

## 검증

- `./gradlew -q projects --no-daemon | rg "graph-ktor|ktor-graph-examples"`
- `./gradlew :bluetape4k-graph-ktor:compileTestKotlin :ktor-graph-examples:compileTestKotlin --no-daemon`
- `./gradlew :bluetape4k-graph-ktor:test :ktor-graph-examples:test --no-daemon --no-parallel`
- Code-review graph incremental update and review context over the 11 changed files.

## 향후 가드

When adopting shared Ktor modules, separate generic Ktor behavior from graph-specific route/plugin behavior. Shared health, JSON, and response-test helpers should come from `bluetape4k-ktor-*`; graph routes and backend lifecycle rules should stay local.
