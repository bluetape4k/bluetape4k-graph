# 이슈 249 supply-chain impact example

## 맥락

The 0.5.0 example suite needs an operational graph domain that differs from IAM, observability, fraud, and recommendation examples.

## 결정

Add a supply-chain module focused on deterministic impact analysis and alternate candidate discovery. Use graph-io CSV fixtures for the small sample dataset and keep optimization, routing solvers, and probabilistic risk scoring out of scope.

## 결과

The module adds sync and suspend TinkerGraph examples for supplier/part/route impact, alternate route discovery, bottleneck parts, and substitution cycles. Root docs, workflow coverage, and changelog now include the module.

## 검증

- `./gradlew :supply-chain-graph-examples:test --no-daemon`
- `./gradlew :supply-chain-graph-examples:build --no-daemon`
- `./gradlew projects --no-daemon`
- `git diff --check`

## 향후 메모

If this grows beyond bounded traversal, define a separate design issue for optimization semantics and expected solver behavior before adding algorithms.
