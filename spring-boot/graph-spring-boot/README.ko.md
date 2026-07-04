# graph-spring-boot

[English](README.md) | 한국어

[bluetape4k-graph](../../README.ko.md)용 Spring Boot 4 Auto-configuration 통합 모듈.

단일 설정 프로퍼티로 원하는 그래프 백엔드를 선택하면, `GraphOperations`, `GraphSuspendOperations`,
`GraphVirtualThreadOperations` 빈이 자동으로 등록된다.

> **Spring Boot 4 전용:** 이 모듈은 Spring Boot 4.x를 대상으로 한다. Spring Boot 3.x 지원은 제거되었다.

## 아키텍처

![graph-spring-boot architecture](../../docs/images/readme-diagrams/spring-boot-graph-spring-boot-architecture-01.png)

`graph-spring-boot`는 Spring Boot auto-configuration 조건으로 정확히 하나의 graph backend를 활성화한다:

- `GraphAutoConfiguration`은 공통 `GraphProperties`를 로드하고 backend별 auto-configuration 실행 순서를 정한다.
- `bluetape4k.graph.backend`가 backend를 선택하며, 값이 없으면 TinkerGraph만 기본으로 매칭된다.
- Backend auto-configuration은 backend property 값과 필요한 runtime class 조건을 모두 만족할 때만 동작한다.
- 각 backend는 `@ConditionalOnMissingBean`으로 `GraphOperations`, optional `GraphSuspendOperations`, optional `GraphVirtualThreadOperations`를 등록한다.
- AGE는 단일 Spring `DataSource`를 재사용하고, AGE operation 생성 전에 Exposed를 연결한다.
- Health indicator는 Spring Boot 4 health class가 classpath에 있을 때만 로드된다.

## 지원 백엔드

| 백엔드 | 프로퍼티 값 | 필요한 런타임 의존성 |
|--------|------------|-------------------|
| TinkerGraph (인메모리, 기본값) | `tinkergraph` | `graph-tinkerpop` |
| Neo4j | `neo4j` | `graph-neo4j` |
| Memgraph | `memgraph` | `graph-memgraph` |
| Apache AGE (PostgreSQL) | `age` | `graph-age` |
| FalkorDB (Redis 모듈) | `falkordb` | `graph-falkordb` |

## 시작하기

### 1. 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-spring-boot:<version>")

    // 런타임에 사용할 백엔드 하나만 추가
    runtimeOnly("io.github.bluetape4k.graph:bluetape4k-graph-neo4j:<version>")   // 또는 graph-memgraph / graph-age / graph-tinkerpop
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

> AGE는 JDBC `DataSource` 빈이 필요하다. 의존성에 `spring-boot-jdbc`를 추가하라.

**FalkorDB (Redis 기반 그래프 데이터베이스):**
```yaml
bluetape4k:
  graph:
    backend: falkordb
    falkordb:
      host: localhost
      port: 6379
      username: ""        # 인증 없이 접속할 경우 빈 문자열
      password: ""
      graph-name: my_graph
      register-suspend: true
      register-virtual-thread: true
```

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
| `HealthIndicator` (Neo4j/Memgraph/AGE/TinkerGraph/FalkorDB) | `spring-boot-health` 클래스패스 존재 시 |

모든 빈은 `@ConditionalOnMissingBean`을 사용하므로, 직접 빈을 등록하면 자동 구성이 건너뛰어진다.

## 설정 프로퍼티

### 공통

| 프로퍼티 | 기본값 | 설명 |
|---------|-------|------|
| `bluetape4k.graph.backend` | *(없음 — TinkerGraph가 기본 활성화)* | 활성 백엔드: `tinkergraph` \| `neo4j` \| `memgraph` \| `age` \| `falkordb` |

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

### FalkorDB (`bluetape4k.graph.falkordb.*`)

| 프로퍼티 | 기본값 | 설명 |
|---------|-------|------|
| `host` | `localhost` | FalkorDB 호스트 주소 |
| `port` | `6379` | FalkorDB Redis 포트 |
| `username` | *(빈 문자열)* | 인증 사용자명 (빈 문자열이면 인증 없음) |
| `password` | *(빈 문자열)* | 인증 비밀번호 (빈 문자열이면 인증 없음) |
| `graph-name` | `bluetape4k` | 대상 그래프 이름 |
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
| `GraphFalkorDBAutoConfiguration` | `backend=falkordb` |

## Spring Boot 4 참고 사항

Spring Boot 4에서는 일부 모듈이 분리되어 명시적으로 추가해야 한다.

| 아티팩트 | 필요한 경우 |
|---------|-----------|
| `spring-boot-health` | `HealthIndicator` 지원 (`org.springframework.boot.health.contributor`) |
| `spring-boot-jdbc` | JDBC/DataSource 자동 구성 (AGE 백엔드에 필요) |
| `spring-boot-restclient` | `RestClient` / `RestTemplate` |
| `spring-boot-resttestclient` | 테스트용 `TestRestTemplate` |
| `spring-boot-webtestclient` | 테스트용 `WebTestClient` |

`HealthIndicator` 패키지 변경:
- Boot 3: `org.springframework.boot.actuate.health.HealthIndicator`
- Boot 4: `org.springframework.boot.health.contributor.HealthIndicator`
