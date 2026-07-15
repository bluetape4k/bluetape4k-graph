# Bluetape4k Graph 0.5 매뉴얼

이 매뉴얼은 커밋 `3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907`에서 출시한 안정 버전 `0.5.1`을 설명한다. 공통 모델, 동기·코루틴 API, 지원 백엔드 다섯 개, graph-io, 프레임워크 연동이 범위다. Amazon Neptune은 0.5.1에서 **지원하지 않는다**. 백로그 이슈도 현재 기능으로 다루지 않는다.

## 무엇부터 결정할까

1. [시작하기](getting-started.md)에서 생태계 BOM을 불러오고 첫 연산을 실행한다.
2. 드라이버를 고르기 전에 [백엔드 선택 가이드](backends/selection-guide.md)를 읽는다.
3. [핵심 모델](architecture/core-model.md), [동기·코루틴 API](architecture/paired-apis.md), [트랜잭션 경계](architecture/schema-and-transactions.md)를 익힌다.
4. [학습 경로](guides/learning-path.md)를 따라가고, 운영 전에 테스트와 운영 가이드를 적용한다.

<!-- diagram: repository learning map -->

API의 중심은 [`GraphOperations`](../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt)와 [`GraphSuspendOperations`](../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendOperations.kt)다. 두 API 모두 [`graph-core`의 공통 모델](../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphVertex.kt)을 반환한다.

## 매뉴얼 지도

- 아키텍처: 저장소 구성, 모델, API 조합, 스키마, merge·batch, 순회, 트랜잭션
- 백엔드: Neo4j, Memgraph, Apache AGE, TinkerPop/TinkerGraph, FalkorDB
- graph-io: 파일 경계, 실행 모델, OkIO 압축과 인증 암호화
- 프레임워크: Ktor plugin과 Spring Boot auto-configuration의 생명주기
- 가이드: 단계별 학습, 테스트, 운영, 취소, benchmark 해석

소비자가 선택할 버전은 개별 graph 라이브러리나 graph BOM 버전이 아니라 `bluetape4k-dependencies` 버전 하나다. 이 매뉴얼의 의존성 예제는 생태계 BOM을 불러오고 모듈 좌표에는 버전을 쓰지 않는다.
