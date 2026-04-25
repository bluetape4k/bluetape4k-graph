# code-graph-examples

bluetape4k-graph의 백엔드 독립 API를 사용해 모듈, 클래스, 함수를 프로퍼티 그래프로 모델링하는 방법을 보여주는 코드 의존성 그래프 예시 모듈.

> 🇺🇸 [English](README.md)

## 개요

이 예시는 소프트웨어 코드베이스를 프로퍼티 그래프로 모델링하고, 추상 테스트 클래스 패턴을 통해 동일한 코드가 Neo4j, Memgraph, Apache AGE, TinkerGraph 백엔드에서 동일하게 동작하는지를 검증한다.

- **공통 로직**: `CodeGraphService` / `CodeGraphSuspendService`가 그래프 연산을 담고 있음
- **추상 테스트**: `AbstractCodeGraphTest` / `AbstractCodeGraphSuspendTest`가 공유 테스트 시나리오를 캡슐화
- **구체 테스트**: 각 백엔드(Neo4j, Memgraph, AGE, TinkerGraph)는 `GraphOperations` 팩토리만 제공

## 도메인 모델

### 정점 라벨

| 라벨 | 설명 | 주요 속성 |
|------|------|-----------|
| `Module` | 빌드 모듈 / 패키지 | `name`, `path`, `version`, `language` |
| `Class` | 클래스 또는 인터페이스 | `name`, `qualifiedName`, `module`, `isAbstract`, `isInterface` |
| `Function` | 함수 또는 메서드 | `name`, `signature`, `className`, `module`, `lineCount` |

### 간선 라벨

| 라벨 | From → To | 설명 |
|------|-----------|------|
| `DEPENDS_ON` | `Module → Module` | 모듈 의존성 (compile / runtime / test) |
| `IMPORTS` | `Class → Class` | 클래스 import 관계 |
| `EXTENDS` | `Class → Class` | 클래스 상속 |
| `IMPLEMENTS` | `Class → Class` | 인터페이스 구현 |
| `CALLS` | `Function → Function` | 함수 호출 (호출 횟수, 재귀 플래그) |
| `BELONGS_TO` | `Class → Module` | 클래스 → 모듈 소속 |

## 추상 테스트 클래스 패턴

공통 테스트 시나리오는 `AbstractCodeGraphTest`에, 각 구체 클래스는 백엔드 연결만 담당한다.

```kotlin
// 추상 클래스 (공통 로직)
abstract class AbstractCodeGraphTest {
    abstract val ops: GraphOperations
    // ... 공유 테스트 케이스 ...
}

// Neo4j 구현
class Neo4jCodeGraphTest : AbstractCodeGraphTest() {
    private val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll fun teardown() { driver.close() }
}

// Memgraph / Apache AGE / TinkerGraph 테스트 클래스도 동일한 패턴
```

| 추상 클래스 | 구체 클래스 |
|------------|------------|
| `AbstractCodeGraphTest` | `Neo4jCodeGraphTest`, `MemgraphCodeGraphTest`, `AgeCodeGraphTest`, `TinkerGraphCodeGraphTest` |
| `AbstractCodeGraphSuspendTest` | `Neo4jCodeGraphSuspendTest`, `MemgraphCodeGraphSuspendTest`, `AgeCodeGraphSuspendTest`, `TinkerGraphCodeGraphSuspendTest` |

## 테스트 실행

```bash
# 전체 테스트
./gradlew :code-graph-examples:test

# 특정 백엔드
./gradlew :code-graph-examples:test --tests "io.bluetape4k.graph.examples.code.Neo4jCodeGraphTest"
./gradlew :code-graph-examples:test --tests "io.bluetape4k.graph.examples.code.AgeCodeGraphSuspendTest"
```

통합 테스트에는 Docker가 필요하다(Neo4j, Memgraph, Apache AGE). TinkerGraph는 인메모리로 실행된다.

## 모듈 참고 사항

- 예시 모듈은 Maven Central에 배포 **대상이 아니다**
- 스키마 DSL과 서비스 계층은 `src/main`에, 테스트는 `src/test`에 위치
- bluetape4k-graph 기반 애플리케이션을 구축할 때 참고 자료로 활용할 것
