# #637 모듈별 pre-PR 코드 리뷰

기준: develop `156097ebbc9e856e69349133b462a5a86a5ed58f` 대비 helper·8개 caller·테스트와 관련 문서. 각 관점은 독립 native agent가 검토하고 리더가 통합한다. 리더 통합은 별도 subagent로 대체하지 않았다.

## 관점과 실제 provenance

| 관점 | 실행 역할·모델·effort | 결과 |
|---|---|---|
| 성능 | code-reviewer / gpt-5.6-luna / max | 9개 module P0/P1/P2/P3=0. import당 두 리소스, record별 lookup/할당 증가 없음 |
| 안정성 | architect / gpt-5.6-sol / high | helper P1 1건 → 회귀 RED/수정/재검토 CLEAR. 8개 caller는 지적 없음 |
| 보안 | code-reviewer / gpt-5.6-luna / max | 9개 module 지적 없음. 신뢰된 classpath 설정, null에만 fallback, SecurityException 우회 없음 |
| 운영 | verifier / gpt-5.6-luna / max | 구현 계약 P0/P1=0. 아래 두 P2는 후속 PR 단계에서 확인할 증거 |
| 개발자/API | code-reviewer / gpt-5.6-luna / max; 최종 delta architect / gpt-5.6-sol / high | 8개 공개 선언/default와 JVM descriptor 확인. 최신 private close-race delta도 API 계약 변경 없음 |
| 사용자/호출자 | writer / gpt-5.6-luna / max | helper와 8개 caller/test, README 양언어 모두 지적 없음 |
| 통합 | 현재 리더 세션 | source·호출자·테스트·API·문서·CI·위험 증거 통합, pre-PR 차단 지적 없음 |

## P1 수렴

최초 P1은 callback 정상 반환 뒤 close/dispatcher 복귀 경합의 취소가 이미 발생한 close 실패를 버리는 경우다. `CsvGraphClasspathResourcesTest`의 close latch 회귀에서 누락을 재현했다(`637-close-race-red`). IO 종료 전 `completedFailure`를 기록하고 외부 취소 경계에서 다른 failure를 원래 취소 예외에 suppressed로 연결했다. edge→vertex close 예외의 중첩 연결을 유지한다.

architect 재검토는 현재 helper/test에서 P0/P1/P2/P3=0으로 판정했다. 최초 재검토가 읽은 중간 로그에는 스타일 경고가 있었으나 리더가 이후 `637-final-green` exit0과 detekt 결과를 확인했고 최종 API delta 검토에도 이 결과를 전달했다. P1은 재현·수정·재검토·현재 검증으로 해소했다.

## 운영 P2의 단계별 처리

1. implementation commit이 아직 없어서 helper와 8개 caller의 단일 rollback 단위를 확인할 수 없음: PR 생성 직전 하나의 implementation commit을 만들고 변경 경로를 확인한다. 설계/계획 commit은 별도로 보존한다. 실제 배포나 rollback 실행을 요구하는 변경은 아니다.
2. exact-head hosted CI가 아직 없음: PR 생성 뒤 기존 CI와 Examples workflow의 terminal 상태와 artifact/review/thread를 확인한다. pre-PR 단계에 존재할 수 없는 증거를 통과로 표시하지 않는다.

두 항목은 코드 결함이나 추가 범위가 아니며 A-10/CG-14 후속 검증으로 추적한다. merge 전 확인 책임은 리더에게 있다.

## 검증과 문서

`637-final-green`: CSV84 + 예제195 =279개 테스트, detekt PASS. `--no-parallel --max-workers=1`과 queue lock으로 로컬 DB 테스트를 직렬화했다. `git diff --check` PASS. production 9개 파일에서 GlobalScope/runBlocking/Thread.sleep/delay/synchronized/runCatching quick scan 0건이다. 공개 선언/default 비교8/8, javap9클래스를 확인했다.

CSV README 양언어와 한국어 helper KDoc는 source 수명, fallback, dispatcher와 예외 계약을 설명한다. README dependency 좌표는 실제 `io.bluetape4k:bluetape4k-graph-io-csv`와 맞췄다. WIP/CHANGELOG/lesson을 갱신했다. 새 module/dependency/workflow/BOM/catalog/publication 변경은 없다. GNO의 worktree 제외 정책에 따라 새 문서 색인은 merge 후 canonical checkout에서 반영한다.

## 모듈별 최종 코드 지적

각 셀은 P0/P1/P2/P3 건수다. 운영의 공통 후속 증거 두 건은 위 단계별 처리 항목으로 별도 추적한다.

| 모듈 | 성능 | 안정성 | 보안 | 운영 | API | 호출자 |
|---|---|---|---|---|---|---|
| graph-io/csv | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 |
| fraud-detection-examples | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 |
| knowledge-graph-examples | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 |
| recommendation-examples | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 |
| observability-graph-examples | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 |
| supply-chain-graph-examples | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 |
| data-lineage-examples | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 |
| network-topology-examples | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 |
| security-attack-path-examples | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 | 0/0/0/0 |

## 리더 통합 판정

최종 P0=0, P1=0. 6개 독립 관점과 리더 통합을 완료했다. 안정성 P1을 실제 실패로 재현하고 수정·재검토했으며, API 마지막 delta는 architect 보완 검토로 누락 범위를 닫았다. 운영 P2 두 건은 commit/PR 후 가능한 증거이므로 A-10/CG-14에서 확인한다. 검증 범위를 넘어 merge/CI 완료를 주장하지 않는다.

SPW-01~05: 리더가 한국어 문서의 구조·수치·API 식별자·현재/과거 실패 구분·Markdown을 readback했다. 호출자 writer는 helper/각 loader/test/README를 읽었고 WIP/CHANGELOG/plan/lesson/verifier의 최종 통합은 리더가 맡았다. source와 현재 테스트 결과가 일치한다.
