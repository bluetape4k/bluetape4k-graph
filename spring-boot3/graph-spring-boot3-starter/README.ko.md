# graph-spring-boot3-starter

[bluetape4k-graph](../../README.ko.md)용 Spring Boot 3 Auto-configuration 스타터.

단일 설정 프로퍼티로 원하는 그래프 백엔드를 선택하면, `GraphOperations`, `GraphSuspendOperations`,
`GraphVirtualThreadOperations` 빈이 자동으로 등록된다.

## 지원 백엔드

| 백엔드 | 프로퍼티 값 | 필요한 런타임 의존성 |
|--------|------------|-------------------|
| TinkerGraph (인메모리, 기본값) | `tinkergraph` | `graph-tinkerpop` |
| Neo4j | `neo4j` | `graph-neo4j` |
| Memgraph | `memgraph` | `graph-memgraph` |
| Apache AGE (PostgreSQL) | `age` | `graph-age` |

## 시작하기

### 1. 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.bluetape4k:graph-spring-boot3-starter:<version>")

    // 런타임에 사용할 백엔드 하나만 추가
    runtimeOnly("io.bluetape4k:graph-neo4j:<version>")   // 또는 graph-memgraph / graph-age / graph-tinkerpop
}
```

### 2. `application.yml` 설정

**TinkerGraph (인메모리 — 별도 설정 불필요):**
```yaml
bluetape4k:
  graph:
    backend: tinkergraph
```

**Neo4j:**
```yaml
bluetape4k:
  graph:
    backend: neo4j
    neo4j:
      uri: bolt://localhost:7687
      username: neo4j
      password: secret
      database: neo4j
      register-suspend: true
      register-virtual-thread: true
```

**Memgraph:**
```yaml
bluetape4k:
  graph:
    backend: memgraph
    memgraph:
      uri: bolt://localhost:7687
      username: ""
      password: ""
      database: memgraph
      register-suspend: true
      register-virtual-thread: true
```

**Apache AGE (PostgreSQL):**
```yaml
bluetape4k:
  graph:
    backend: age
    age:
      graph-name: my_graph
      auto-create-graph: true
      register-suspend: true
      register-virtual-thread: true
```

> AGE는 JDBC `DataSource` 빈이 필요하다 (예: `spring-boot-starter-jdbc`).

### 3. 주입 후 사용

```kotlin
@Service
class MyGraphService(
    private val ops: GraphOperations,
    private val suspendOps: GraphSuspendOperations,   // 선택적
) {
    fun createPerson(name: String): GraphVertex =
        ops.createVertex("Person", mapOf("name" to name))

    suspend fun findPerson(id: GraphElementId): GraphVertex? =
        suspendOps.findVertexById("Person", id)
}
```

## 등록되는 빈

| 빈 타입 | 등록 조건 |
|--------|---------|
| `GraphOperations` | 백엔드 활성화 시 항상 등록 |
| `GraphSuspendOperations` | `register-suspend=true` (기본값) |
| `GraphVirtualThreadOperations` | `register-virtual-thread=true` (기본값) |
| `HealthIndicator` (Neo4j/Memgraph) | `spring-boot-actuator` 클래스패스 존재 시 |

모든 빈은 `@ConditionalOnMissingBean`을 사용하므로, 직접 빈을 등록하면 자동 구성이 건너뛰어진다.

## 설정 프로퍼티

### 공통

| 프로퍼티 | 기본값 | 설명 |
|---------|-------|------|
| `bluetape4k.graph.backend` | *(없음 — TinkerGraph가 기본 활성화)* | 활성 백엔드: `tinkergraph` \| `neo4j` \| `memgraph` \| `age` |

### Neo4j (`bluetape4k.graph.neo4j.*`)

| 프로퍼티 | 기본값 | 설명 |
|---------|-------|------|
| `uri` | `bolt://localhost:7687` | Bolt URI |
| `username` | `neo4j` | 사용자명 |
| `password` | *(빈 문자열)* | 비밀번호 (빈 문자열이면 인증 없음) |
| `database` | `neo4j` | 대상 데이터베이스 |
| `register-suspend` | `true` | `GraphSuspendOperations` 등록 여부 |
| `register-virtual-thread` | `true` | `GraphVirtualThreadOperations` 등록 여부 |

### Memgraph (`bluetape4k.graph.memgraph.*`)

Neo4j와 동일한 프로퍼티 구조, 프리픽스는 `bluetape4k.graph.memgraph`. 기본 데이터베이스: `memgraph`, 기본 사용자명: *(빈 문자열)*.

### Apache AGE (`bluetape4k.graph.age.*`)

| 프로퍼티 | 기본값 | 설명 |
|---------|-------|------|
| `graph-name` | `bluetape4k_graph` | AGE 그래프 이름 |
| `auto-create-graph` | `true` | 그래프가 없으면 자동 생성 |
| `register-suspend` | `true` | `GraphSuspendOperations` 등록 여부 |
| `register-virtual-thread` | `true` | `GraphVirtualThreadOperations` 등록 여부 |

## Auto-Configuration 클래스

| 클래스 | 활성화 조건 |
|-------|-----------|
| `GraphAutoConfiguration` | 항상 — `GraphProperties` 로딩 및 실행 순서 보장 |
| `GraphTinkerGraphAutoConfiguration` | `backend=tinkergraph` 또는 프로퍼티 미지정 시 |
| `GraphNeo4jAutoConfiguration` | `backend=neo4j` |
| `GraphMemgraphAutoConfiguration` | `backend=memgraph` |
| `GraphAgeAutoConfiguration` | `backend=age` |
