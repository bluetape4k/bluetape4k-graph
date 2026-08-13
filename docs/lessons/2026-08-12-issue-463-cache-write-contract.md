# 2026-08-12 issue #463 캐시 생성 계약

## Context

AGE, Neo4j, Memgraph 캐시 데코레이터가 `createVertex`와 `createEdge`의 동일
인자 호출을 write-result memoization으로 합쳐 실제 생성 요청을 누락하고 있었다.
읽기 결과 캐시와 생성 연산의 부작용을 같은 캐시 정책으로 처리한 것이 원인이었다.

## Decision or Finding

- `createVertex`와 `createEdge`는 인자가 같아도 매 호출을 backend delegate에 위임한다.
- 캐시 데코레이터는 읽기 결과만 캐시하고, 생성·갱신·삭제 후 읽기 캐시를 무효화한다.
- 캐시 용량과 TTL 문제는 생성 의미 수정과 분리하여 후속 #464 slice에서 다룬다.

## Outcome

세 backend wrapper에서 write-key와 write-result map을 제거하고, 동일 인자 반복 생성과
읽기 캐시 무효화를 검증하는 회귀 테스트 및 영문/국문 문서를 정렬했다.

## Verification

- 기존 baseline: AGE/Neo4j/Memgraph cache test 각 24개 통과.
- RED: 새 계약 테스트가 세 모듈에서 각각 4개 실패하여 write memoization 원인을 재현.
- GREEN: targeted cache test 각 21개 통과.
- 전체 모듈: AGE 176개, Neo4j 116개, Memgraph 111개 통과.
- 세 모듈 `detekt`, `dokkaGenerateModuleHtml`, `git diff --check` 통과.

## Future Guidance

캐시 decorator를 추가하거나 수정할 때 읽기 캐시와 부작용 있는 write 연산을 먼저
분리해 검토한다. 동일 인자 deduplication은 `create*` 계약을 바꾸므로 명시적인
idempotent API가 없는 한 공용 decorator에 도입하지 않는다.
