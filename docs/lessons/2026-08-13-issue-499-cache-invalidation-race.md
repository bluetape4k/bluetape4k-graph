# 2026-08-13 issue #499 cache invalidation race

## Context

AGE, Neo4j, Memgraph 캐시 데코레이터는 cache miss에서 delegate를 읽은 뒤 결과를
무조건 캐시에 저장했다. 그 사이 wrapper를 통한 쓰기가 성공해 캐시를 비워도, 먼저
시작한 읽기가 이전 결과를 다시 저장할 수 있었다. `GraphOperations by delegate`는
`dropGraph`를 직접 위임했고, Kotlin의 선택적 `GraphTransactionalOperations`도
delegation만으로는 wrapper에 노출되지 않았다.

## Decision or Finding

- 각 wrapper가 `AtomicLong` generation을 소유하고, cache miss 시작 시 generation을
  캡처한 뒤 delegate 읽기 후에도 같은 경우에만 결과를 저장한다.
- 기존 쓰기 무효화와 `dropGraph` 성공 경로는 generation 증가와 여섯 read cache 전체
  무효화를 함께 수행한다.
- 세 wrapper가 `GraphTransactionalOperations`를 명시적으로 구현하고 backend transaction을
  전달한다. 정상 commit 후에는 무효화하고, 예외로 rollback된 경우에는 기존 cache를
  유지한다.

## Outcome

동시 miss가 쓰기 완료 뒤 stale 결과를 캐시에 재적재하지 않으며, `dropGraph`와 commit된
transaction은 다음 읽기를 cache miss로 만든다. 이미 쓰기 전에 시작한 호출이 반환하는
값 자체는 직렬화하지 않고, 다른 delegate 인스턴스에서 직접 수행한 쓰기는 wrapper의
무효화 경계 밖으로 문서화했다.

## Miss or Surprise

선택적 capability는 Kotlin `by delegate`에 자동으로 포함되지 않는다. 초기 회귀 테스트가
transaction extension의 `UnsupportedOperationException`을 재현해 이 경계를 명시적
forwarding 계약으로 고정했다. race 테스트에는 프로젝트의 기존 `MultithreadingTester`가
이 모듈에 없어 `CountDownLatch`와 단일 executor로 read/write 순서를 결정적으로 고정했다.

## Verification

- RED: AGE, Neo4j, Memgraph targeted cache suite가 각각 동시 miss/write, `dropGraph`,
  transaction 경계에서 3개 실패를 재현했다.
- GREEN: 동일한 세 targeted suite가 각각 28개 테스트를 모두 통과했다.
- Module GREEN: AGE 183개, Neo4j 123개, Memgraph 118개 테스트가 모두 통과했다.
- Static GREEN: 세 모듈 `detekt`와 `dokkaGenerateModuleHtml`이 모두 성공했다. 명시적
  transaction capability로 함수 수가 늘어난 wrapper에는 기존 operation class 관례에
  맞춰 `TooManyFunctions` suppression을 명시했다.
- `git diff --check`가 통과했다. 독립 review와 hosted CI는 PR 단계에서 완료한다.

## Future Guidance

새로운 cache invalidation source를 추가할 때는 generation 증가와 실제 cache clear를
하나의 성공 경로로 묶고, in-flight read의 반환값과 cache 재적재 여부를 분리해 테스트한다.
선택적 repository capability를 decorator에 제공할 때는 Kotlin delegation만 의존하지 말고
명시적 forwarding과 commit/rollback 후속 효과를 검증한다.
