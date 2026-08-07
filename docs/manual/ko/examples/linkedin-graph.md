# 직업 관계망

## 문제와 백엔드

사람, 회사, 기술, 추천 관계를 연결해 여러 형태의 사회 관계 조회를 연습합니다. **TinkerGraph**를 써서 컨테이너와 네트워크 편차를 빼고 모델부터 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md)과 [TinkerPop](../backends/tinkerpop.md)을 읽고, 운영 전에는 [선택 가이드](../backends/selection-guide.md)를 적용합니다.

## 그래프 모델

- 정점: Person/Company/Skill
- 간선: KNOWS/WORKS_AT/FOLLOWS/HAS_SKILL/ENDORSES
- 주요 속성: name, title, company, skills, strength, role, level

## 준비와 릴리스 경계

JDK 21, 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 소스를 체크아웃하고 Gradle 프로젝트로 실행합니다. 소비자 애플리케이션에서는 `bluetape4k-dependencies:<ecosystem-version>`만 선택하고 필요한 그래프 모듈은 개별 버전 없이 추가합니다.

## 실행과 관찰

```bash
./gradlew :linkedin-graph-examples:test --tests "io.bluetape4k.graph.examples.linkedin.TinkerGraphLinkedInGraphTest"
```

테스트는 직접 인맥, 여러 단계를 거치는 인맥 경로, 2촌 인맥, 회사별 직원, 팔로워 조회 결과를 각각 검증합니다. 실패한 검증 조건이 가리키는 관계를 확인한 뒤, 해당 간선 방향과 조회 조건을 살펴봅니다.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/schema/LinkedInSchema.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/service/LinkedInGraphService.kt)
3. [공통 실행 계약](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/AbstractLinkedInGraphTest.kt)
4. [TinkerGraph 구체 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/TinkerGraphLinkedInGraphTest.kt)
5. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/build.gradle.kts)

[recommendation](./recommendation.md) 다음에 읽고 [iam-access-graph](./iam-access-graph.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### linkedin graph examples 아키텍처

[![linkedin graph examples 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-architecture-01.svg)

_배포본 README: [`examples/linkedin-graph-examples/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/README.ko.md)_

### linkedin graph examples data 흐름

[![linkedin graph examples data 흐름](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-data-flow-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-data-flow-03.svg)

_배포본 README: [`examples/linkedin-graph-examples/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/README.ko.md)_

### linkedin graph examples ERD

[![linkedin graph examples ERD](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-erd-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-linkedin-graph-examples-erd-02.svg)

_배포본 README: [`examples/linkedin-graph-examples/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/linkedin-graph-examples/README.ko.md)_

<!-- release-readme-diagrams:end -->
