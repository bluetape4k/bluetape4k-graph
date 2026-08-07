# Ktor 그래프 연동

## 문제와 백엔드

그래프 초기화, 개수 조회, 경로 탐색을 Ktor 플러그인과 HTTP 경계로 노출합니다. **TinkerGraph**를 써서 저장소 모델과 웹 계층을 나누어 검증합니다. 먼저 [핵심 모델](../architecture/core-model.md), [TinkerPop](../backends/tinkerpop.md), [Ktor 연동](../frameworks/ktor.md)을 읽습니다.

## 그래프 모델

- 정점: City
- 간선: ROAD
- 주요 속성: name, distance

## 준비와 릴리스 경계

JDK 21, 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`, 저장소의 Gradle Wrapper가 필요합니다. 예제는 배포되지 않으므로 릴리스 소스를 체크아웃하고 Gradle 프로젝트로 실행합니다. 소비자 애플리케이션에서는 `bluetape4k-dependencies:<ecosystem-version>`만 선택하고 필요한 그래프 모듈은 개별 버전 없이 추가합니다.

## 실행과 관찰

```bash
./gradlew :ktor-graph-examples:test --tests "io.bluetape4k.graph.examples.ktor.KtorGraphAppTest"
```

라우트 테스트는 초기화 응답이 `reset`인지, 도시가 세 개인지, 경로가 `Seoul -> Daejeon -> Busan`인지 검증합니다. 실패하면 플러그인 초기화, 응답 직렬화, 그래프 고정 데이터와 탐색을 나누어 진단합니다.

## 코드 읽는 순서

1. [스키마](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/ktor-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/ktor/KtorGraphAppMain.kt)
2. [서비스](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/ktor-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/ktor/KtorGraphAppMain.kt)
3. [완전한 실행 테스트](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/ktor-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/ktor/KtorGraphAppTest.kt)
4. [빌드 파일](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/ktor-graph-examples/build.gradle.kts)

[supply-chain-graph](./supply-chain-graph.md) 다음에 읽고 [code-graph](./code-graph.md)로 이어가십시오. [동기·코루틴 API](../architecture/paired-apis.md), [테스트](../guides/testing.md), [운영](../guides/operations.md)도 함께 보십시오.

## 확장과 운영 진단

결과를 바꾸는 간선과 단언을 하나 추가하고 suspend API로 반복하십시오. 영속 백엔드 테스트는 직렬로 실행하고 끊어진 경로와 잘못된 입력도 검증하십시오. 이 고정 데이터는 처리량, 군집, 권한, 테넌트 격리, 마이그레이션, 백업, 원격 드라이버 제한 시간, 인덱스 품질을 증명하지 않습니다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### ktor graph examples 아키텍처

[![ktor graph examples 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-ktor-graph-examples-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-ktor-graph-examples-architecture-01.svg)

_배포본 README: [`examples/ktor-graph-examples/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/ktor-graph-examples/README.ko.md)_

### ktor graph examples data 흐름

[![ktor graph examples data 흐름](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-ktor-graph-examples-data-flow-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-ktor-graph-examples-data-flow-03.svg)

_배포본 README: [`examples/ktor-graph-examples/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/ktor-graph-examples/README.ko.md)_

### ktor graph examples ERD

[![ktor graph examples ERD](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-ktor-graph-examples-erd-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-ktor-graph-examples-erd-02.svg)

_배포본 README: [`examples/ktor-graph-examples/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/ktor-graph-examples/README.ko.md)_

<!-- release-readme-diagrams:end -->
