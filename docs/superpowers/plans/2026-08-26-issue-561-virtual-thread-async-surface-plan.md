# #561 Virtual Thread optional async surface 구현 계획

## 순서

1. **기준선·선행 head 확인**
   - live #561과 PR #585를 조회하고, #585 current head를 stacked base로 고정한다.
   - graph-core의 Virtual Thread facade, Bluetape4k future helper, sync optional
     marker, TinkerGraph capability와 기존 TCK를 inventory한다.
2. **TDD RED**
   - supported/unsupported capability routing, optional method, transaction thread
     affinity, exception identity, cancellation/timeout을 먼저 실패하도록 작성한다.
   - 예외 검증은 `io.bluetape4k.assertions.assertFailsWith`를 사용한다.
3. **최소 구현**
   - merge/schema/transaction/chunked optional interface를 additive로 추가한다.
   - focused adapter와 facade extension을 Bluetape4k `virtualFutureOf` helper로
     구현하고, chunk source close와 delegate ownership을 고정한다.
   - `capabilities()`와 `delegateCapabilities()`를 분리해 marker 기반 surface를
     계산한다.
4. **문서·리뷰**
   - graph-core EN/KO README와 public KDoc에 실행 thread, executor, 예외, 취소,
     timeout, close ownership, materialized chunk 제한과 migration note를 기록한다.
   - 7-Tier review와 lesson에서 P0/P1 및 후속 P2/P3를 분리한다.
5. **검증**
   - focused TCK, graph-core 전체 test, compile, Detekt, 금지 assertion scan,
     `git diff --check`를 fresh rerun한다.
   - graph-core 변경만 포함하므로 기존 Core & TinkerGraph hosted job과 Examples
     workflow를 exact head에서 확인한다.
6. **receipt·stacked PR**
   - Lore commit으로 implementation과 문서 receipt를 기록하고 #585 exact head 위에
     PR을 생성한다.
   - PR/issue metadata와 body를 read-back하고, merge·issue close는 전체 train 최종
     승인 단계까지 보류한다.

## 롤백

- optional interface와 adapter 파일을 제거하면 기존 sync/algorithm-only facade로
  돌아갈 수 있다.
- capability projection 문제가 발견되면 delegate mapping과 surface mapping을
  독립적으로 되돌려 기존 `delegateCapabilities()` 호환성을 먼저 보존한다.
- chunk close 또는 cancellation 회귀가 실패하면 async materialization 경계를
  유지한 채 source lifecycle helper만 격리해 수정한다.
