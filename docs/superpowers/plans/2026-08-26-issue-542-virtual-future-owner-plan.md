# #542 graph-core Virtual Thread helper owner 구현 계획

## 순서

1. live #542, #586 exact head, graph-core dependency/catalog와 official helper jar를
   확인한다.
2. `VirtualThreadOfficialUtilityTest`를 추가해 graph-local generated owner가 남은
   상태의 RED와 official owner/nullable execution 계약을 고정한다.
3. graph-local duplicate source를 삭제하고 기존 adapter call site가 공식 helper를
   사용하는지 GREEN으로 확인한다.
4. graph-core EN/KO README에 package ownership, split-package 후속 범위, generated
   owner migration을 기록한다.
5. 7-Tier review와 lesson을 작성하고 P0/P1 blocker와 #562/#563 후속 경계를 분리한다.
6. graph-core full test·compile·Detekt·금지 assertion·diff-check를 실행하고 Lore
   commit, PR metadata, hosted receipt를 기록한다.

## 롤백

source 삭제로 공식 helper가 resolve되지 않으면 변경을 되돌리기보다 dependency
resolution과 generated owner를 먼저 확인한다. graph-local shim 복원은 split-package와
ABI 문제를 숨기므로 대안으로 사용하지 않는다.
