# Issue #527 — Testcontainers 이미지 계약을 공용 launcher와 고정

## Context

그래프 저장소 README와 `.github/testcontainers-images.txt`가 공용
`bluetape4k-testcontainers` launcher의 실제 기본 이미지 및 중앙 catalog와
어긋나 있었다. 이미지 태그가 바뀌면 문서, 캐시 manifest, backend 테스트가 서로
다른 런타임을 가리킬 수 있고, #1337의 startup/workload 검증으로 전달할 이미지
family 경계도 명시되어 있지 않았다.

## Decision or Finding

현재 공용 launcher의 `IMAGE`·`TAG`를 네 backend의 manifest 기준으로 삼고,
family와 image repository의 대응은 별도 `.github/testcontainers-image-families.txt`에
고정한다. 태그 자체는 기존 캐시 스크립트와의 호환성을 위해 flat manifest에
그대로 둔다. Ruby validator가 manifest, family map, 중앙 catalog ref, Java/Kotlin
baseline, 루트·backend EN/KO README와 관련 KDoc를 fail-closed 방식으로 검사하고,
Spring Boot 테스트가 manifest와 launcher 상수를 직접 비교한다. 이 변경은 이미지 family가 바뀌었음을
감지하는 경계만 제공하며 실제 startup/workload gate는 #1337에서 수행한다.

## Outcome

Neo4j `5.26.29`, Memgraph `3.12.0`, Apache AGE
`release_PG18_1.7.0`, FalkorDB `v4.20.2`와 PostgreSQL JDBC `42.7.13`,
Neo4j Java Driver `6.2.1`, `jfalkordb 0.8.0`, Java 25/Kotlin `2.4.10`,
`bluetape4k 2.0.0-SNAPSHOT`를 루트·모듈 README와 backend 선택 가이드에 반영했다.
EN/KO README의 계약 토큰도 동일하게 유지하고, FalkorDB KDoc의 `jfalkordb 0.8.0`
설명도 실제 catalog와 맞췄다.

## Verification

```text
ruby scripts/manual/testcontainers_image_contract_test.rb
2 runs, 3 assertions, 0 failures, 0 errors, 0 skips

ruby scripts/manual/testcontainers_image_contract.rb --repository-root . --catalog <central catalog>
Testcontainers image contract valid

./gradlew :bluetape4k-graph-spring-boot:test --tests '*TestcontainersImageLauncherContractTest' --no-daemon
BUILD SUCCESSFUL — 1 test

./gradlew :bluetape4k-graph-spring-boot:test --no-daemon
BUILD SUCCESSFUL — 44 tests, 1 pre-existing environment-gated pending test
```

전용 workflow는 `actionlint`를 통과했다. FalkorDB live integration은 기존
`BLUETAPE4K_GRAPH_SPRING_FALKORDB_INTEGRATION=true` guard가 비활성화되어 이번
계약 변경의 검증 범위에서 실행하지 않았다.

## Future Guidance

공용 launcher의 image family 또는 tag를 변경할 때는 manifest, family map,
EN/KO README, 중앙 catalog 근거를 함께 갱신하고 전용 contract를 먼저 통과시킨다.
family 변경의 실제 컨테이너 startup/workload 판정은 #1337 gate에 연결하며,
문서 계약을 통과한 것만으로 backend 호환성이나 live integration 성공을 주장하지
않는다.
