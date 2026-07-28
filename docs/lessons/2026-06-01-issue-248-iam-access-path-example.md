# 이슈 248 IAM access-path example

## 맥락

The 0.5.0 example suite needs security-oriented graph examples that show why graph traversal is useful outside generic social or recommendation domains.

## 결정

Add a focused IAM access-path module rather than a full policy engine. The example models identity, groups, roles, policies, permissions, resources, and temporary grants as graph reachability, with explicit paths for direct grants, inherited grants, deny policy paths, break-glass grants, risky nested admin chains, and least-privilege drift.

## 결과

The module follows the existing examples pattern: sync and suspend services, abstract backend-independent tests, concrete TinkerGraph/Neo4j/Memgraph/AGE/FalkorDB adapters, English/Korean README files, root README registration, Examples workflow coverage, and changelog entry.

## 검증

- `./gradlew :iam-access-graph-examples:test --tests '*TinkerGraph*' --no-daemon`
- `./gradlew :iam-access-graph-examples:test --no-daemon`
- `./gradlew :iam-access-graph-examples:build --no-daemon`
- `./gradlew projects --no-daemon`
- `git diff --check`

The example module does not currently expose a module-level `detekt` task; rely on build/test locally and PR-level CI for the repository quality gate.

## 향후 메모

Keep the example as path explanation and graph reachability. Do not grow it into a complete IAM policy evaluator without a separate design issue.
