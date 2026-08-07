# 보안 공격 경로

## 문제와 백엔드

공격 경로에 있는 exploit, 신뢰, 권한 상승, 차단 전이를 눈에 보이게 만듭니다. **TinkerGraph**를 써서 컨테이너와 네트워크 편차를 빼고 모델부터 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md)과 [TinkerPop](../backends/tinkerpop.md)을 읽고, 운영 전에는 [선택 가이드](../backends/selection-guide.md)를 적용합니다.

## 그래프 모델

- 정점: EntryAsset/Host/Principal/Credential/Vulnerability/Permission
- 간선: CAN_REACH/EXPLOITS/COMPROMISES/RUNS_AS/HAS_CREDENTIAL/GRANTS_ACCESS/HAS_PERMISSION/CONTROLS_ASSET
- 주요 속성: assetId, hostId, principalId, vulnerabilityId, severity, privilege, status

## 준비와 릴리스 경계

JDK 21, 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 소스를 체크아웃하고 Gradle 프로젝트로 실행합니다. 소비자 애플리케이션에서는 `bluetape4k-dependencies:<ecosystem-version>`만 선택하고 필요한 그래프 모듈은 개별 버전 없이 추가합니다.

## 실행과 관찰

```bash
./gradlew :security-attack-path-examples:test --tests "io.bluetape4k.graph.examples.securityattack.TinkerGraphSecurityAttackPathTest"
```

테스트는 권한 상승 경로가 `web-service`, `ci-admin-token`, `domain-admin`을 거치고 `customer-db` 접근은 차단되는지 검증합니다. 실패하면 exploit·신뢰 간선, 권한 전이, 차단 규칙을 확인합니다.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/src/main/kotlin/io/bluetape4k/graph/examples/securityattack/schema/SecurityAttackPathGraphSchema.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/src/main/kotlin/io/bluetape4k/graph/examples/securityattack/service/SecurityAttackPathService.kt)
3. [공통 실행 계약](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/src/test/kotlin/io/bluetape4k/graph/examples/securityattack/AbstractSecurityAttackPathTest.kt)
4. [TinkerGraph 구체 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/src/test/kotlin/io/bluetape4k/graph/examples/securityattack/SecurityAttackPathBackendTests.kt)
5. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/build.gradle.kts)

[fraud-detection](./fraud-detection.md) 다음에 읽고 [network-topology](./network-topology.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.
