# 추천 그래프

## 문제와 백엔드

이 예제는 도메인 질문을 경로, 개수, 순위, 진단 집합으로 확인합니다. **TinkerGraph**를 써서 컨테이너와 네트워크 편차를 빼고 모델부터 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md)과 [TinkerPop](../backends/tinkerpop.md)을 읽고, 운영 전에는 [선택 가이드](../backends/selection-guide.md)를 적용하십시오.

## 그래프 모델

- 정점: User/Product
- 간선: PURCHASED/FOLLOWS
- 주요 속성: userId, productId, category, quantity, purchasedAt

## 준비와 릴리스 경계

JDK 21, 커밋 `3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 소스를 체크아웃하고 Gradle 프로젝트로 실행합니다. 소비자 애플리케이션에서는 `bluetape4k-dependencies:<ecosystem-version>`만 선택하고 필요한 그래프 모듈은 개별 버전 없이 추가합니다.

## 실행과 관찰

```bash
./gradlew :recommendation-examples:test --tests "io.bluetape4k.graph.examples.recommendation.TinkerGraphRecommendationTest"
```

`BUILD SUCCESSFUL`과 함께 지정 테스트가 통과해야 합니다. p-tripod과 u-carol이 추천되고 p-camera가 상위 3개에 포함됩니다. 결과가 다르면 고정 데이터, 간선 방향, 탐색 깊이를 확인하십시오.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/src/main/kotlin/io/bluetape4k/graph/examples/recommendation/schema/RecommendationSchema.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/src/main/kotlin/io/bluetape4k/graph/examples/recommendation/service/RecommendationService.kt)
3. [데이터 로더 계약](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/RecommendationSampleDatasetLoaderTest.kt)
4. [TinkerGraph 구체 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/RecommendationBackendTests.kt)
5. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/build.gradle.kts)

[knowledge-graph](./knowledge-graph.md) 다음에 읽고 [linkedin-graph](./linkedin-graph.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.
