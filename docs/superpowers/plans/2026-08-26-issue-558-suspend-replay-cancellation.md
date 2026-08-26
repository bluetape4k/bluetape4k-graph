# #558 suspend replay cancellation checkpoint 및 output lifecycle TCK 실행 계획

## 기준

- live 이슈: [#558](https://github.com/bluetape4k/bluetape4k-graph/issues/558)
- 선행 stacked PR: [#582](https://github.com/bluetape4k/bluetape4k-graph/pull/582)
- base branch: `fix/issue-557-spool-peak-cleanup-stacked`
- base exact head: `5d5cd3f64bea1aedd7df66f64ca33a739970353c`
- target branch: `fix/issue-558-suspend-replay-cancellation-stacked`

## 실행 순서

1. CSV/GraphML suspend replay writer와 기존 sink/cleanup test helper를
   inventory한다.
2. fake sink가 첫 큰 record 뒤 job cancellation을 유도하고 후속 record가
   출력되는 TDD RED를 추가한다.
3. sequence `ensureActive` checkpoint를 구현하고 CSV/GraphML targeted GREEN을
   확인한다.
4. writer/session/output을 `NonCancellable` cleanup scope에서 닫도록 정렬하고,
   caller-owned/owned sink와 close failure suppressed TCK를 추가한다.
5. CSV/GraphML suspend 관련 전체 test, compile, Detekt, 금지 assertion scan,
   diff-check를 순차 실행한다.
6. README EN/KO, spec, 7-Tier review, lesson, WIP receipt를 작성하고 Lore
   commit으로 push한다.
7. PR을 #582 exact head 위에 만들고 exact base/head·labels·assignee·milestone와
   hosted CI/Examples terminal receipt를 read-back한다. 전체 train merge는
   마지막 승인 단계까지 보류한다.

## 실패 시 복구

- checkpoint가 후속 record를 계속 쓰면 sequence 경계를 보강하되 spool format은
  건드리지 않는다.
- close failure가 cancellation을 덮으면 cleanup 결과를 primary에 suppressed로
  연결하는 집계 순서와 outer catch의 primary identity 보존을 먼저 수정한다.
- hosted workflow-dispatch image gate가 입력 부재로 실패하면 run/log를 receipt에
  남기고 코드 수정과 별도 운영 이슈로 분리한다.
