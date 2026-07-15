# bluetape4k-graph-age

## 선택 기준

AGE는 PostgreSQL 안에 graph 데이터를 두고 SQL 경계에서 Cypher를 실행한다. PostgreSQL의 backup, 권한, transaction 운영 체계를 그대로 써야 할 때 선택한다. Bolt 동작이나 Neo4j 전용 procedure가 필요하면 피한다. 시작점은 [AgeGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt)다.

## 의존성과 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-age")
}
```

```kotlin
val dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
    username = "postgres"
    password = "password"
    connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
})
Database.connect(dataSource)
val ops = AgeGraphOperations("social")
ops.createGraph("social")
val a = ops.createVertex("Person", mapOf("name" to "Alice"))
val b = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
ops.createEdge(a.id, b.id, "KNOWS")
check(ops.neighbors(a.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == b.id)
```

예상 결과는 숫자형 AGE ID가 생기고 Alice에서 Bob으로 이동하는 것이다.

## 동작과 자원

`transaction { }`은 Exposed/JDBC와 같은 PostgreSQL transaction 경계를 쓴다. pool에서 빌린 모든 connection에 `LOAD 'age'`와 `search_path`가 적용돼야 한다. merge는 [AgeGraphMergeOperationsTest.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphMergeOperationsTest.kt)로 고정한다. schema 기능은 [AgeGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManager.kt)가 제공하는 범위만 쓴다.

DataSource는 호출자가 닫는다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphOperationsTest' --tests '*AgeGraphMergeOperationsTest'
```

예상 결과는 AGE container에서 생성, merge, traversal, rollback이 통과하는 것이다. graph 없음, extension 누락, 잘못된 `search_path`, connection 초기화 누락은 보통 domain 검증보다 먼저 SQL/agtype 오류로 나타난다. PostgreSQL log, SQLSTATE, pool 상태, graph 이름을 차례로 본다.

## 관련 문서와 하지 않는 일

[Apache AGE](../backends/apache-age.md), [구현 선택](../backends/selection-guide.md), [schema와 transaction](../architecture/schema-and-transactions.md)을 참고한다. 이 모듈은 PostgreSQL을 운영하거나 Bolt/Cypher 호환성을 보장하지 않는다.
