# 2026-08-01 graph P1 contract review

## Context

`0.6.0`의 #440 Epic과 #441-#444 하위 이슈를 대상으로 7-Tier Kotlin
review를 이어 갔다. 기존 구현에는 FalkorDB `dropGraph`의 광범위한 예외
무시, logical graph 선택과 삭제 사이의 TOCTOU, GraphPath serialization
문서와 실제 property 계약의 불일치, graph-core published ABI의 외부 소비자
검증 공백이 남아 있었다.

## Decision or Finding

- FalkorDB는 확인된 absent-graph 오류만 idempotent 성공으로 취급하고, transport/
  server 오류와 coroutine cancellation은 호출자에게 전파한다.
- Neo4j, Memgraph, TinkerGraph는 같은 operations 인스턴스 안에서 graph 선택,
  존재 확인, 삭제를 하나의 lifecycle critical section으로 직렬화한다.
- `GraphPath`의 Java serialization은 nested map/collection을 포함한 모든
  non-null property 값이 `Serializable`일 때만 보장한다. 임의의 `Any` 값은
  자동 변환하지 않는다.
- `graph-core` published POM에는 coroutine Flow API의 compile dependency가
  명시되어야 하며, 독립 consumer compile smoke가 이 계약을 검증한다.

## Outcome

P1 회귀 테스트와 계약 문서를 보강했다. 현재 `0.6.0`의 #437/#440 및
#438/#439/#441-#444는 모두 OPEN 상태이며, 이 review에서 새 Epic이나
subissue를 추가할 필요는 없었다.

## Verification

- 전체 Gradle test: `1609` tests, `0` failures, `0` errors, `1` skipped
  (Testcontainers 환경에서 `TESTCONTAINERS_RYUK_DISABLED=true`).
- 영향 모듈과 examples를 포함한 `./gradlew test`: `BUILD SUCCESSFUL`.
- `detekt`: `17 actionable tasks`, 성공.
- `./gradlew build -x test`: 성공.
- publication POM audit: `15` files, `1705` dependencies, `0` failures.
- graph-core external consumer compile smoke: 성공.
- `actionlint .github/workflows/*.yml` 및 Ruby audit tests: 성공.

## Future Guidance

Colima에서 기본 Testcontainers Ryuk가 `/Users/debop/.colima/default/docker.sock`
mount를 거부할 수 있다. 이를 제품 코드 실패로 오인하지 말고, 원인 로그를
보존한 뒤 Ryuk 비활성화 환경에서 전체 테스트를 별도로 확인한다. 다음
진행자는 exact-head/CI/merge를 별도 승인 경계로 유지해야 한다.
