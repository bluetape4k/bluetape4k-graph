# #538 graph-io-core 구현 계획

## 순서

1. **기준선과 계약 고정**
   - live issue, workflow source/store 구현, 두 writer와 기존 options/test를
     읽고 Bluetape helper 사용 위치를 대조한다.
   - graph-io-core targeted test 기준선을 기록한다.
2. **회귀 테스트를 먼저 추가**
   - 같은 store를 공유하는 두 workflow 인스턴스의 겹친 transition에서 한
     전이만 성공하는 race 테스트를 추가한다.
   - sync/suspend writer의 0·음수 `batchSize` 생성 거부 테스트를 추가한다.
   - 예외 assertion은 `io.bluetape4k.assertions.assertFailsWith`만 사용한다.
3. **최소 구현**
   - state store에 load/transform/save 원자 경계를 추가하고 workflow의
     validate/transition을 그 경계로 수렴시킨다.
   - 두 writer 생성자에 `requirePositiveNumber`를 적용한다.
4. **문서·정적 검증**
   - 설계/KDoc 또는 graph-io-core README에 JVM store 원자성, durable override,
     batchSize 계약을 기록한다.
   - `git diff --check`, detekt, compile, 금지 assertion 정적 검색을 실행한다.
5. **7-Tier review와 후속 이슈**
   - exact HEAD에서 독립 architecture/code review를 수행한다.
   - P0/P1은 수정하고 남는 P2/P3는 Korean GitHub issue로 생성한다.
6. **receipt·커밋·DoD**
   - required checks와 fresh main verification을 workflow receipt에 붙인다.
   - review/lesson을 read-back하고 Lore trailers를 포함한 Korean commit을
     만든다. PR/merge/push/issue close는 수행하지 않는다.

## 롤백

- store atomic helper와 writer constructor validation만 되돌리면 기존 public
  호출 흐름과 buffer semantics를 복원할 수 있다.
- Gradle 실패는 동일 명령을 반복하기 전에 실패 원인을 분리하고, graph-io-core
  외부 container는 이 이슈 범위의 검증으로 취급하지 않는다.
