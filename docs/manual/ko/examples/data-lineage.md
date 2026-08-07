# 데이터 계보 그래프

## 문제와 백엔드

데이터셋, 작업, 보고서 사이에서 상류 원본과 하류 영향 범위를 추적합니다. **TinkerGraph**를 써서 컨테이너와 네트워크 편차를 빼고 모델부터 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md)과 [TinkerPop](../backends/tinkerpop.md)을 읽고, 운영 전에는 [선택 가이드](../backends/selection-guide.md)를 적용합니다.

## 그래프 모델

- 정점: Dataset/Table/Column/PipelineJob/Dashboard/Owner/QualityCheck
- 간선: CONTAINS_TABLE/CONTAINS_COLUMN/INPUT_TO_JOB/OUTPUTS_TABLE/FEEDS_DASHBOARD/OWNS_JOB/VALIDATES_COLUMN
- 주요 속성: datasetId, tableId, columnId, jobId, dashboardId, ownerId, checkId

## 준비와 릴리스 경계

JDK 21, 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 소스를 체크아웃하고 Gradle 프로젝트로 실행합니다. 소비자 애플리케이션에서는 `bluetape4k-dependencies:<ecosystem-version>`만 선택하고 필요한 그래프 모듈은 개별 버전 없이 추가합니다.

## 실행과 관찰

```bash
./gradlew :data-lineage-examples:test --tests "io.bluetape4k.graph.examples.datalineage.TinkerGraphDataLineageImpactTest"
```

테스트는 하류 영향 범위에 `exec-revenue`와 `ops-quality`가 들어가는지, 상류 탐색이 예상 원본 테이블까지 닿는지 검증합니다. 실패하면 계보 방향, 변환 간선 누락, 탐색 범위를 차례로 살펴봅니다.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/src/main/kotlin/io/bluetape4k/graph/examples/datalineage/schema/DataLineageGraphSchema.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/src/main/kotlin/io/bluetape4k/graph/examples/datalineage/service/DataLineageImpactService.kt)
3. [공통 실행 계약](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/src/test/kotlin/io/bluetape4k/graph/examples/datalineage/AbstractDataLineageImpactTest.kt)
4. [TinkerGraph 구체 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/src/test/kotlin/io/bluetape4k/graph/examples/datalineage/DataLineageBackendTests.kt)
5. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/build.gradle.kts)

[observability-graph](./observability-graph.md) 다음에 읽고 [supply-chain-graph](./supply-chain-graph.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.
