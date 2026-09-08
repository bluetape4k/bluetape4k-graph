# CSV classpath 자원 공통화의 취소 경계

## 배경과 결정

8개 예제 loader가 같은 classloader 탐색과 stream 소유 코드를 가지고 있었다. graph-io-csv에 sync/suspend callback helper를 두고 각 loader의 fallback classloader와 기존 누락 메시지를 전달한다. 두 입력의 소유권은 helper에 남기고 InputStreamSource에는 closeInput=false를 전달한다. 기존 공개 importCsv/importCsvSuspending과 기본 인자는 유지한다.

## 실패에서 확인한 점

미구현 stub에서 새 테스트 8개가 모두 실패했다. nested use와 withContext만 사용한 첫 구현은 82개 중 81개를 통과했으나 실제 Job 취소의 terminal cause에는 stream close 실패가 남지 않았다. callback에서 받은 취소 예외와 호출자 Job의 원인이 달랐기 때문이다. CancellationException 경계에서 callerContext.ensureActive()로 호출자 원인을 사용한 후 닫으면 최종 취소 예외에 edge, vertex close 실패가 보존된다. 호출자가 활성 상태라면 callback 자체의 취소 예외를 그대로 전파한다.

## 검증과 향후 규칙

637-helper-green2: CSV 전체 82개 테스트와 detekt 통과. 실제 cancelAndJoin 후 예외 identity, suppressed 순서, close 횟수를 함께 검증했다. IO dispatcher의 열기·닫기와 호출자 dispatcher의 callback을 구분하는 테스트도 통과했다. classloader의 null 반환만 fallback으로 처리하고 SecurityException은 우회하지 않는다.

정적 설계 리뷰는 nested context와 최종 Job cause의 차이를 놓쳤다. coroutine 자원 helper에는 단순 throw CancellationException 테스트와 별개로 실제 Job 취소의 terminal cause를 확인하는 회귀 테스트를 유지한다. 8개 호출자 검증과 최종 리뷰는 PR 검증 기록에 따로 남긴다. 새 lesson은 현재 GNO collection의 worktree 제외 정책에 따라 merge 후 canonical checkout에서 색인한다.

활성 Job에서 callback 자체가 CancellationException을 던지는 추가 회귀 테스트는 dispatcher 복귀 중 예외 identity 손실도 재현했다. callback과 IO 자원 처리 결과를 Result로 경계 너머에 전달하고, 자원을 소유한 use 내부 및 최종 호출자 위치에서 getOrThrow하여 같은 예외와 suppressed를 보존한다. 실제 Job 취소는 ensureActive로 먼저 전파한다. 637-helper-green4에서 동작 테스트 83개가 통과했으며, Throwable 전달 경계의 제한된 detekt suppression에는 원래 예외를 다시 던지는 목적을 명시했다.

## Pre-PR 안정성 P1의 재현과 수정

독립 architect(gpt-5.6-sol/high)는 callback 정상 반환 뒤 close/dispatcher 복귀 시점의 취소가 이미 발생한 close 실패를 버릴 수 있다고 지적했다. edge close 진입을 latch로 고정하고 Job 취소 후 edge/vertex close 실패를 발생시키는 테스트에서 suppressed 누락을 재현했다(637-close-race-red). IO 종료 전에 failure를 외부 지역 변수에 기록하고 바깥 withContext의 취소 경계에서 별도 failure를 원래 취소 예외에 보존한다. nested use의 edge→vertex 예외 연결은 그대로 유지한다. captureFailure의 실패 기록은 ensureActive보다 먼저 수행하므로 close가 CancellationException을 던지는 경우에도 기록을 잃지 않는다. 637-close-race-green의 동작 테스트 84개는 통과했고, 두 줄의 스타일 경고를 정리한 뒤 637-final-green에서 전체 검증을 진행한다.

최종 로컬 결과: 637-final-green은 279개 테스트(84+195) 및 detekt PASS, git diff --check PASS다. retry로 넘긴 실패는 없으며 모든 동작 실패와 정적 검사 경고의 원인을 수정했다.
