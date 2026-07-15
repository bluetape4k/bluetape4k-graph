# 이상 거래 탐지 그래프

## 문제와 백엔드

이 예제는 도메인 질문을 경로, 개수, 순위, 진단 집합으로 확인합니다. **TinkerGraph**를 써서 컨테이너와 네트워크 편차를 빼고 모델부터 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md)과 [TinkerPop](../backends/tinkerpop.md)을 읽고, 운영 전에는 [선택 가이드](../backends/selection-guide.md)를 적용하십시오.

## 그래프 모델

- 정점: Account
- 간선: TRANSFERRED_TO
- 주요 속성: accountId, ownerName, riskTier, amount, occurredAt

## 준비와 릴리스 경계

JDK 21, 커밋 `3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 고정 데이터로 실행합니다. 배포 모듈 사용자는 BOM을 가져오고 개별 모듈 버전을 생략합니다.

```kotlin
dependencies {
    implementation(platform("io.bluetape4k:bluetape4k-graph-bom:0.5.1"))
    implementation("io.bluetape4k:bluetape4k-graph-core")
}
```

## 실행과 관찰

```bash
./gradlew :fraud-detection-examples:test --tests "io.bluetape4k.graph.examples.fraud.TinkerGraphFraudDetectionTest"
```

`BUILD SUCCESSFUL`과 함께 지정 테스트가 통과해야 합니다. 점수에 acct-bob, 순환 경로, 도착점 acct-sink가 포함됩니다. 결과가 다르면 고정 데이터, 간선 방향, 탐색 깊이를 확인하십시오.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/fraud-detection-examples/src/main/kotlin/io/bluetape4k/graph/examples/fraud/schema/FraudDetectionSchema.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/fraud-detection-examples/src/main/kotlin/io/bluetape4k/graph/examples/fraud/service/FraudDetectionService.kt)
3. [완전한 실행 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/fraud-detection-examples/src/test/kotlin/io/bluetape4k/graph/examples/fraud/AbstractFraudDetectionTest.kt)
4. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/fraud-detection-examples/build.gradle.kts)

[iam-access-graph](./iam-access-graph.md) 다음에 읽고 [security-attack-path](./security-attack-path.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.
