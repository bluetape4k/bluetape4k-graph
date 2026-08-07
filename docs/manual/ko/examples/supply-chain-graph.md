# 공급망 영향 그래프

## 문제와 백엔드

주문, 운송 경로, 부품, 순환 의존성을 공급망 그래프에서 추적합니다. **TinkerGraph**를 써서 컨테이너와 네트워크 편차를 빼고 모델부터 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md)과 [TinkerPop](../backends/tinkerpop.md)을 읽고, 운영 전에는 [선택 가이드](../backends/selection-guide.md)를 적용합니다.

## 그래프 모델

- 정점: Supplier/Part/Warehouse/Route/Carrier/CustomerOrder
- 간선: SUPPLIES/REQUIRED_BY/STOCKED_AT/USES_ROUTE/DELIVERS_TO/OPERATES_ROUTE/ALTERNATE_PART
- 주요 속성: supplierId, partId, routeId, carrierId, orderId, criticality, status

## 준비와 릴리스 경계

JDK 21, 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 소스를 체크아웃하고 Gradle 프로젝트로 실행합니다. 소비자 애플리케이션에서는 `bluetape4k-dependencies:<ecosystem-version>`만 선택하고 필요한 그래프 모듈은 개별 버전 없이 추가합니다.

## 실행과 관찰

```bash
./gradlew :supply-chain-graph-examples:test --tests "io.bluetape4k.graph.examples.supplychain.TinkerGraphSupplyChainImpactTest"
```

테스트는 `order-1001`, `route-air-express`, `gps-module`을 찾고 배터리 의존 순환을 감지하는지 검증합니다. 실패하면 공급 관계 누락, 의존 방향, 순환 탐지 범위를 확인합니다.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/supplychain/schema/SupplyChainGraphSchema.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/supplychain/service/SupplyChainImpactService.kt)
3. [공통 실행 계약](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/supplychain/AbstractSupplyChainImpactTest.kt)
4. [TinkerGraph 구체 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/supplychain/SupplyChainBackendTests.kt)
5. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/build.gradle.kts)

[data-lineage](./data-lineage.md) 다음에 읽고 [ktor-graph](./ktor-graph.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.
