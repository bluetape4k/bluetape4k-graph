# #637 CSV classpath 입력 소유권 공통화

## 목표와 범위

8개 예제 loader의 동일한 classpath 탐색·두 입력 스트림 종료 코드를 graph-io-csv의 callback helper로 옮긴다. 이슈 #637과 사용자 #641 개별 PR 구현 지시에 따른 Type A 작업이다. 기존 loader 공개 API, dataset 경로, 옵션, 메시지, sync/suspend 결과는 유지한다. 신규 의존성·모듈·workflow 변경은 없다.

## 근거와 대안

`CsvRecordParser`는 소비한 입력만 닫고 importer는 정점 오류로 간선 단계 이전에 반환할 수 있다. 따라서 두 stream을 eager-open한 source factory만 반환하면 소비되지 않은 stream이 누수된다. AutoCloseable owner를 별도로 노출하면 호출자마다 use 책임과 객체가 늘어난다. callback이 전체 import 동안 소유하는 현재 nested use 형태를 선택한다.

기존 `ClassLoaderSupport.getClassLoader`는 클래스 loader를 먼저 선택하고 `Resourcex.getInputStream`은 단일 loader 계약이다. TCCL 우선 후 fallback이라는 기존 예제 계약을 보존하기 위해 표준 getResourceAsStream을 helper 내부에 한정한다. 새 dependency는 사용하지 않는다.

## 공개 계약

새 CsvGraphClasspathResources.kt에 `withClasspathCsvGraphImportSource`와 `withClasspathCsvGraphImportSourceSuspending`을 추가한다. 두 함수는 verticesResource, edgesResource, fallbackClassLoader, missingResourceMessage, block을 받는다. fallbackClassLoader는 `ClassLoader?`이며 null이면 TCCL만 탐색한다. 둘 다 null이면 지정된 missing-resource IllegalArgumentException을 사용한다. 반환 타입은 callback 결과 T다. 기본 누락 메시지는 `CSV classpath resource not found: <path>`이며 예제는 기존 `Sample dataset resource not found: <path>` 메시지 함수를 전달한다.

각 리소스는 호출 시점 TCCL에서 먼저 탐색하고 찾지 못할 때 전달된 fallbackClassLoader를 사용한다. suspend helper는 호출 시점 TCCL과 coroutine context를 dispatcher 전환 전에 캡처한다. `withContext(Dispatchers.IO)` 안에서 두 stream을 nested `use`로 열고, 안쪽 callback만 `withContext(callerContext)`로 실행한다. 따라서 callback은 호출자 context를 유지하고, 동기 close는 IO의 `use` finally에서 별도 suspend 지점 없이 실행한다. 취소된 context로 cleanup dispatcher 진입을 시도하지 않으며, 실제 취소와 close suppressed 순서는 Kotlin use로 보존한다. open과 callback 사이 취소 및 callback 종료 시 dispatcher 복귀 취소도 동일한 use 경계 안에 포함한다.

callback에는 closeInput=false인 두 InputStreamSource를 제공한다. helper가 두 stream을 소유하며 정상·실패·취소 모두 edge 다음 vertex 순서로 각 1회 닫는다. 두 번째 open 실패는 첫 stream을 닫는다. 기존 Kotlin use의 primary/suppressed 예외 순서를 보존하며 callback 결과로 source를 반환해도 소유 수명은 늘어나지 않음을 KDoc에 명시한다.

resource 이름과 ClassLoader는 신뢰된 애플리케이션 설정을 받는 계약이다. 외부 요청 값을 직접 연결하는 호출자는 자신의 허용 namespace를 검증한다. 기존 TCCL 우선 탐색은 유지하며 별도 sandbox 또는 namespace resolver API를 추가하지 않는다.

모든 읽기는 block이 반환되기 전에 완료해야 한다. source, 지연 Flow, Deferred 또는 비동기 작업으로 소비를 밖으로 미루는 사용은 지원하지 않는다. resource 이름은 ClassLoader 기준 상대 경로이며 선행 `/`를 정규화하지 않고 그대로 탐색하여 누락 오류를 유지한다.

## 수용 기준

- AC1: TCCL과 fallback이 모두 null인 경계를 포함하며, TCCL 성공은 fallback을 호출하지 않고 TCCL miss는 fallback을 사용한다.
- AC2: vertex/edge 누락과 예외에서 기존 메시지·원인·close 횟수를 유지한다.
- AC3: callback의 호출자 dispatcher 유지, 정상 callback 반환, 실패, 실제 job 취소에 대해 두 stream의 소유권·역순 close와 suppressed 순서를 검증한다.
- AC4: 8개 loader는 기존 공개 signature와 dataset import 결과를 유지한다.
- AC5: 새 API의 한국어 KDoc·README 양 locale·CHANGELOG·WIP·lesson을 제공한다.
- AC6: 기존 API descriptor를 제거하지 않는 additive 변경으로 검증하고 예제·CI/BOM 기존 coverage가 유지됨을 확인한다.

## 위험과 검증

주요 위험은 source 수명 escape, dispatcher 이동에 따른 TCCL 변경, cleanup 예외가 취소를 덮어씀이다. 수명 문서화, 전환 전 TCCL 캡처, use의 suppressed 규칙과 실제 cancelAndJoin 테스트로 고정한다. 경로를 operational log에 기록하거나 run별 tag를 만들지 않는다. 동시 테스트는 TCCL을 finally에서 복원하며 공유 전역 loader를 변경하지 않는다.

rollback은 helper와 8개 호출부 commit을 함께 되돌린다. 이미 게시된 artifact는 회수하지 않고 후속 patch release로 수정한다. CI task와 JVM descriptor 확인은 실행 계획에서 구체화한다. 이 PR만으로 기존 애플리케이션에 마이그레이션을 요구하지 않는다. 테스트/ABI/독립 리뷰 결과는 후속 review artifact에 기록한다. 설계 문서 자체는 통과 증거가 아니다.

## Writer DoD

SPW-01~05: 한국어 설계, source-to-claim 대조, 대안·수명·예외·AC 기록, Markdown readback 완료. 코드 토큰·정확한 메시지는 유지한다.

Loader 탐색은 null 반환만 miss로 취급한다. SecurityException/RuntimeException은 같은 예외를 전파하고 fallback으로 우회하지 않는다. SecurityException을 던지는 TCCL fixture에서 예외 identity와 fallback 호출 0회를 검증한다.

## 구현 검증에서 확인한 취소 예외 보존

첫 구현은 CSV 82개 중 81개를 통과했지만 실제 Job 취소의 terminal cause에 close 실패가 남지 않았다. 중첩 withContext가 전달한 취소 예외와 호출자 Job의 원래 원인이 달랐다. callback 경계에서 CancellationException만 잡고 callerContext.ensureActive()로 호출자의 취소 원인을 전파한 다음 nested use가 닫기 실패를 suppressed로 붙이도록 보완했다. 호출자 Job이 취소되지 않은 callback 자체의 CancellationException은 그대로 다시 던진다. 자원 열기/닫기 dispatcher와 공개 계약은 유지한다. 재검증 637-helper-green2에서 82개 테스트와 detekt가 통과했다.

활성 Job에서 callback 자체가 CancellationException을 던지는 추가 회귀 테스트는 dispatcher 복귀 중 예외 identity 손실도 재현했다. callback과 IO 자원 처리 결과를 Result로 경계 너머에 전달하고, 자원을 소유한 use 내부 및 최종 호출자 위치에서 getOrThrow하여 같은 예외와 suppressed를 보존한다. 실제 Job 취소는 ensureActive로 먼저 전파한다. 637-helper-green4에서 동작 테스트 83개가 통과했으며, Throwable 전달 경계의 제한된 detekt suppression에는 원래 예외를 다시 던지는 목적을 명시했다.

## Pre-PR 안정성 P1의 재현과 수정

독립 architect(gpt-5.6-sol/high)는 callback 정상 반환 뒤 close/dispatcher 복귀 시점의 취소가 이미 발생한 close 실패를 버릴 수 있다고 지적했다. edge close 진입을 latch로 고정하고 Job 취소 후 edge/vertex close 실패를 발생시키는 테스트에서 suppressed 누락을 재현했다(637-close-race-red). IO 종료 전에 failure를 외부 지역 변수에 기록하고 바깥 withContext의 취소 경계에서 별도 failure를 원래 취소 예외에 보존한다. nested use의 edge→vertex 예외 연결은 그대로 유지한다. captureFailure의 실패 기록은 ensureActive보다 먼저 수행하므로 close가 CancellationException을 던지는 경우에도 기록을 잃지 않는다. 637-close-race-green의 동작 테스트 84개는 통과했고, 두 줄의 스타일 경고를 정리한 뒤 637-final-green에서 전체 검증을 진행한다.
