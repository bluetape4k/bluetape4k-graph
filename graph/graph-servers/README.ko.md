# graph-servers

bluetape4k-graph 백엔드 모듈의 통합 테스트를 위한 Testcontainers 기반 싱글턴 서버 팩토리.

> 🇺🇸 [English](README.md)

## 개요

이 모듈은 `graph-neo4j`, `graph-memgraph`, `graph-age` 모듈의 통합 테스트가 단일 컨테이너 인스턴스를 여러 테스트 클래스에서 공유할 수 있도록 재사용 가능한 Testcontainers 싱글턴을 제공한다. 테스트 클래스마다 컨테이너를 새로 생성하는 방식 대비 테스트 실행 시간이 크게 단축된다.

## 제공 서버

| 객체 | 대상 | 이미지 | 용도 |
|------|------|--------|------|
| `Neo4jServer` | Neo4j 5.x | `neo4j:5` | Neo4j 통합 테스트 |
| `MemgraphServer` | Memgraph | `memgraph/memgraph:latest` | Memgraph 통합 테스트 |
| `PostgreSQLAgeServer` | PostgreSQL + Apache AGE | `apache/age:PG16_latest` | Apache AGE 통합 테스트 |

각 객체는 lazy 초기화된 싱글턴이다 — 첫 접근 시 컨테이너가 시작되고 JVM 수명 동안 재사용된다.

## 사용법

### Neo4j

```kotlin
import io.bluetape4k.graph.servers.Neo4jServer
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.AuthTokens

val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
val ops = Neo4jGraphOperations(driver)
```

### Memgraph

```kotlin
import io.bluetape4k.graph.servers.MemgraphServer

val driver = GraphDatabase.driver(MemgraphServer.boltUrl, AuthTokens.none())
val ops = MemgraphGraphOperations(driver)
```

### PostgreSQL + Apache AGE

```kotlin
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

val hikariConfig = HikariConfig().apply {
    jdbcUrl = PostgreSQLAgeServer.jdbcUrl
    username = PostgreSQLAgeServer.username
    password = PostgreSQLAgeServer.password
    connectionInitSql = """LOAD 'age'; SET search_path = ag_catalog, "${'$'}user", public"""
}
val database = Database.connect(HikariDataSource(hikariConfig))
val ops = AgeGraphOperations(database, graphName = "test_graph")
```

## 의존성

```kotlin
dependencies {
    // Apache AGE (PostgreSQL extension)
    api(Libs.testcontainers_postgresql)
    api(Libs.postgresql_driver)
    api(Libs.hikaricp)

    // Neo4j
    api(Libs.testcontainers_neo4j)
    api(Libs.neo4j_java_driver)

    // Memgraph (generic container + neo4j driver)
    api(Libs.testcontainers)
    api(Libs.bluetape4k_testcontainers)
}
```

## 요구 사항

- Docker가 로컬에서 실행 중이어야 함 (Testcontainers 요구 사항)
- 이 모듈은 테스트 코드용이다 — 프로덕션에서 사용하지 말 것
