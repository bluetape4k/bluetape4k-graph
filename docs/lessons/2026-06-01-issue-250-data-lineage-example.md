# Issue 250 Data Lineage Example

## Context

0.5.0 milestone에 data lineage impact 예제를 추가했다. 기존 supply-chain, IAM 예제처럼 compact CSV fixture와 TinkerGraph sync/suspend smoke test를 우선 범위로 잡았다.

## Decision

Full catalog product 대신 dataset/table/column/job/dashboard/owner/quality-check 정점과 bounded traversal API만 구현했다. `data-lineage-examples` 모듈은 graph-io CSV loader를 사용하고, source table/column impact, upstream table lookup, broken job owner lookup, quality check impact, missing lineage path를 테스트한다.

## Outcome

Root README/README.ko, repo-local `AGENTS.md`, Examples workflow, CHANGELOG에 새 모듈을 등록했다. Stacked PR 구조에서는 이전 예제 모듈 등록 변경 위에 새 example module을 계속 쌓는 방식이 충돌을 줄인다.

## Verification

- `./gradlew :data-lineage-examples:test --no-daemon`: 6 passing
- `./gradlew :data-lineage-examples:build --no-daemon`: success
- `./gradlew projects --no-daemon`: `:data-lineage-examples` registered
- `git diff --check`: success

## Future Guidance

Data lineage 예제를 확장할 때도 catalog UI나 governance workflow를 붙이기보다, 한두 hop의 설명 가능한 traversal과 CSV fixture 테스트를 먼저 유지한다.
