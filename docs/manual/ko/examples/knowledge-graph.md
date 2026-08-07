# 지식 그래프

## 문제와 백엔드

문서, 개념, 개체를 연결해 관련 결과와 그 결과를 설명하는 경로를 함께 찾습니다. **TinkerGraph**를 써서 컨테이너와 네트워크 편차를 빼고 모델부터 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md)과 [TinkerPop](../backends/tinkerpop.md)을 읽고, 운영 전에는 [선택 가이드](../backends/selection-guide.md)를 적용합니다.

## 그래프 모델

- 정점: Document/Entity/Concept
- 간선: MENTIONS/RELATED_TO/IS_A
- 주요 속성: documentId, entityId, conceptId, confidence, relationType

## 준비와 릴리스 경계

JDK 21, 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 소스를 체크아웃하고 Gradle 프로젝트로 실행합니다. 소비자 애플리케이션에서는 `bluetape4k-dependencies:<ecosystem-version>`만 선택하고 필요한 그래프 모듈은 개별 버전 없이 추가합니다.

## 실행과 관찰

```bash
./gradlew :knowledge-graph-examples:test --tests "io.bluetape4k.graph.examples.knowledge.TinkerGraphKnowledgeGraphTest"
```

테스트는 문서가 예상 개체를 언급하는지, 개체가 예상 개념으로 분류되는지를 서로 나눠 검증합니다. 실패하면 언급 관계의 방향, 개념 분류, 탐색 범위를 확인합니다.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/knowledge-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/knowledge/schema/KnowledgeGraphSchema.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/knowledge-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/knowledge/service/KnowledgeGraphService.kt)
3. [공통 실행 계약](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/knowledge-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/knowledge/AbstractKnowledgeGraphTest.kt)
4. [TinkerGraph 구체 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/knowledge-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/knowledge/KnowledgeGraphBackendTests.kt)
5. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/knowledge-graph-examples/build.gradle.kts)

[code-graph](./code-graph.md) 다음에 읽고 [recommendation](./recommendation.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### knowledge graph examples 아키텍처

[![knowledge graph examples 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-knowledge-graph-examples-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-knowledge-graph-examples-architecture-01.svg)

_배포본 README: [`examples/knowledge-graph-examples/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/knowledge-graph-examples/README.ko.md)_

### UML 다이어그램

[![UML 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-knowledge-graph-examples-class-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-knowledge-graph-examples-class-02.svg)

_배포본 README: [`examples/knowledge-graph-examples/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/knowledge-graph-examples/README.ko.md)_

### knowledge graph examples 처리 순서 흐름 3 다이어그램

[![knowledge graph examples 처리 순서 흐름 3 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-knowledge-graph-examples-sequence-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-knowledge-graph-examples-sequence-03.svg)

_배포본 README: [`examples/knowledge-graph-examples/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/knowledge-graph-examples/README.ko.md)_

<!-- release-readme-diagrams:end -->
