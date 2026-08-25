# #557 spool record serialization peak memory·constructor cleanup 실행 계획

## 기준

- live 이슈: [#557](https://github.com/bluetape4k/bluetape4k-graph/issues/557)
- 선행 stacked PR: [#581](https://github.com/bluetape4k/bluetape4k-graph/pull/581)
- base branch: `test/issue-556-backend-bounded-chunk-stacked`
- base exact head: `534aed0111d062450d5d6a3958d3cb0294e34bba`
- target branch: `fix/issue-557-spool-peak-cleanup-stacked`

## 실행 순서

1. `GraphIoRecordSpoolTest`에 no-second-copy, max guard, second file/output
   initialization failure 회귀를 추가하고 compile RED를 확인한다.
2. capped payload buffer와 direct `writeTo`를 구현한다.
3. resource factory와 fail-clean cleanup을 구현하고 targeted GREEN을 확인한다.
4. graph-io-core 전체 test, Detekt, 금지 assertion scan, diff-check를 실행한다.
5. README EN/KO, 7-Tier review, lesson, WIP receipt를 exact base/head와 함께
   작성한다.
6. Lore commit으로 push하고 PR을 #581 exact head 위에 생성한다. 자동 PR check가
   base branch filter에 걸리면 workflow_dispatch receipt를 같은 head에서
   별도로 확인한다.

## 실패 시 복구

- payload format이 깨지면 implementation commit만 되돌리고 기존 spool file
  format을 유지한다.
- constructor cleanup 실패가 primary error를 덮으면 resource factory의
  suppressed 처리만 되돌려 primary identity를 우선한다.
- local/hosted 검증이 실패하면 PR merge 없이 해당 slice에서 원인을 수정한다.
