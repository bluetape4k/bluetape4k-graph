# Issue #537 graph-io checkpoint/resume 7-Tier 리뷰

## 범위와 기준

CSV, Jackson2/3, GraphML, OkIO importer의 `GraphImportOptions` checkpoint/resume
lifecycle을 검토했다. 기준은 [#537](https://github.com/bluetape4k/bluetape4k-graph/issues/537)의
load/validate/save/delete 및 duplicate/conflict 수용 기준이며, core contract와
각 format의 sync/suspend 경계를 exact stacked head에서 대조했다.

## 7-Tier 결과

| Tier | 판정 | 근거 |
| --- | --- | --- |
| T1 계약·범위 | PASS | 기본 `checkpointStore=null`은 one-attempt 동작을 유지하고, resume 시 source identity·version·phase를 검증한 뒤 compatible state만 재개한다. |
| T2 API·ABI | PASS | 기존 importer entry point와 option 필드를 유지하면서 checkpoint session/identity를 추가하고, 지원 format 모두 같은 lifecycle contract를 사용한다. |
| T3 구현·패턴 | PASS | 공통 `GraphImportCheckpointSession`/external-ID mapping을 재사용하고, cancellation·실패 경계에서 claim을 명시적으로 release해 stale writer를 fencing한다. |
| T4 테스트 | PASS | core contract와 CSV/Jackson2/Jackson3/GraphML/OkIO sync·suspend 회귀가 checkpoint load/save, conflict, duplicate, cancellation 경계를 검증한다. |
| T5 backend 영향 | PASS / WATCH | graph-io format 모듈 전체 test와 Detekt가 통과했다. backend graph database matrix는 importer contract 밖의 별도 gate다. |
| T6 문서·사용성 | PASS | graph-io core EN/KO README에 resume source identity, duplicate policy, shared store의 atomic claim 요구를 기록했다. |
| T7 검증·운영 | PASS / WATCH | compile/test/Detekt와 diff 검증을 통과했다. distributed checkpoint store의 원자성은 구현체 책임으로 남긴다. |

## 잔여 위험과 후속 범위

- non-atomic backend에서 exactly-once를 보장한다고 주장하지 않으며, 재개 시
  duplicate policy와 importer별 batch semantics를 계속 검증한다.
- container-backed graph backend와 hosted workflow 결과는 PR exact-head receipt에서
  별도로 갱신한다.
- graph-age SQL identifier 경계는 [#534](https://github.com/bluetape4k/bluetape4k-graph/issues/534)에서
  독립적으로 다룬다.

## 최종 판정

**PASS / WATCH** — #537 checkpoint/resume lifecycle 수용 기준을 충족한다. merge는
전체 stacked train의 최종 승인 단계까지 보류한다.
