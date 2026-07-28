# 이슈 247 Observability Graph Example

## 맥락

Milestone 0.5.0 needed the first observability-oriented graph example under the #253 example epic.

## 결정

Create a focused `observability-graph-examples` module that teaches incident graph traversal through service
dependencies, public APIs, alerts, incidents, and ownership, while reusing the existing example backend matrix pattern.

## 결과

The module includes sync/suspend services, graph-io CSV fixtures, TinkerGraph loader smoke tests, backend portability
tests, English/Korean README files, and a shared architecture diagram asset.

## 검증 증거

- `./gradlew :observability-graph-examples:compileKotlin :observability-graph-examples:compileTestKotlin --no-daemon`
  passed.
- `./gradlew :observability-graph-examples:test --no-daemon` passed with 34 tests.
- `./gradlew :observability-graph-examples:build --no-daemon` passed.
- `./gradlew projects --no-daemon` listed `:observability-graph-examples`.
- `actionlint .github/workflows/examples.yml` passed.
- `git diff --check` passed.

## 향후 가드

For new example modules, treat README scenario, Architecture Diagram, graph model, traversal goals, sample dataset, and
expected output as mandatory DoD items, not optional documentation polish.
