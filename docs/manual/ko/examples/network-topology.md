# 네트워크 토폴로지 그래프

## 문제와 백엔드

네트워크 토폴로지에서 도달 가능성, 서비스 영향, 고립된 구성 요소를 구분합니다. **TinkerGraph**를 써서 컨테이너와 네트워크 편차를 빼고 모델부터 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md)과 [TinkerPop](../backends/tinkerpop.md)을 읽고, 운영 전에는 [선택 가이드](../backends/selection-guide.md)를 적용합니다.

## 그래프 모델

- 정점: Site/Device/Segment/Service
- 간선: CONTAINS_DEVICE/CONNECTED_TO/MEMBER_OF_SEGMENT/HOSTS_SERVICE
- 주요 속성: siteId, deviceId, segmentId, serviceId, cidr, tier, status

## 준비와 릴리스 경계

JDK 21, 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 소스를 체크아웃하고 Gradle 프로젝트로 실행합니다. 소비자 애플리케이션에서는 `bluetape4k-dependencies:<ecosystem-version>`만 선택하고 필요한 그래프 모듈은 개별 버전 없이 추가합니다.

## 실행과 관찰

```bash
./gradlew :network-topology-examples:test --tests "io.bluetape4k.graph.examples.networktopology.TinkerGraphNetworkTopologyImpactTest"
```

테스트는 도달 가능한 장비, 영향을 받는 서비스, 고립된 구성 요소가 토폴로지 고정 데이터와 일치하는지 검증합니다. 실패하면 연결 간선의 방향, 고립 관계 누락, 영향 탐색의 경계를 확인합니다.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/src/main/kotlin/io/bluetape4k/graph/examples/networktopology/schema/NetworkTopologyGraphSchema.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/src/main/kotlin/io/bluetape4k/graph/examples/networktopology/service/NetworkTopologyImpactService.kt)
3. [공통 실행 계약](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/src/test/kotlin/io/bluetape4k/graph/examples/networktopology/AbstractNetworkTopologyImpactTest.kt)
4. [TinkerGraph 구체 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/src/test/kotlin/io/bluetape4k/graph/examples/networktopology/NetworkTopologyBackendTests.kt)
5. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/build.gradle.kts)

[security-attack-path](./security-attack-path.md) 다음에 읽고 [observability-graph](./observability-graph.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.
