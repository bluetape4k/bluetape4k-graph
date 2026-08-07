# bluetape4k-graph-age


실행 방식: **release test fixture 연계형**이다. snippet은 핵심 서비스 설정만 보인다. 완전한 fixture가 PostgreSQL AGE를 시작하고 `DataSource`, Exposed 상태, `ops`를 만든 뒤 operations, DataSource, container 순서로 닫는다.

## 실행 전 준비

AGE는 PostgreSQL 안에 그래프 데이터를 두고 SQL 경계에서 Cypher를 실행한다. PostgreSQL의 backup, 권한, 트랜잭션 운영 체계를 그대로 써야 할 때 선택한다. Bolt 동작이나 Neo4j 전용 procedure가 필요하면 피한다. 시작점은 [AgeGraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt)다.

## 실행

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

## 기대 결과

예상 결과는 숫자형 AGE ID가 생기고 Alice에서 Bob으로 이동하는 것이다.

## 동작과 자원

`transaction { }`은 Exposed/JDBC와 같은 PostgreSQL 트랜잭션 경계를 쓴다. pool에서 빌린 모든 connection에 `LOAD 'age'`와 `search_path`가 적용돼야 한다. merge는 [AgeGraphMergeOperationsTest.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphMergeOperationsTest.kt)로 고정한다. schema 기능은 [AgeGraphSchemaManager.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphSchemaManager.kt)가 제공하는 범위만 쓴다.

DataSource는 호출자가 닫는다.

## 운영 점검

- 서버/image 버전과 선택한 그래프/database를 기록한다.
- connection pool 대기와 query latency를 확인한다.
- 트랜잭션 rollback과 schema 지원을 따로 검증한다.
- operations를 먼저 닫고 호출자 소유 Driver/DataSource를 닫는다.

## 실패와 복구

증상: 그래프 검증 전에 SQL/agtype 해석이 실패한다. 잘못 초기화된 pool connection을 버리고 `LOAD 'age'`와 `search_path`를 복구한 뒤 그래프 존재를 확인하고 새 connection에서 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphOperationsTest' --tests '*AgeGraphMergeOperationsTest'
```

예상 결과는 AGE container에서 생성, merge, traversal, rollback이 통과하는 것이다. 그래프 없음, extension 누락, 잘못된 `search_path`, connection 초기화 누락은 보통 domain 검증보다 먼저 SQL/agtype 오류로 나타난다. PostgreSQL log, SQLSTATE, pool 상태, 그래프 이름을 차례로 본다.

## 완전한 release 예제

고정된 [AgeGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt)가 fixture 값을 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphOperationsTest'
```

예상 결과는 fixture가 시작되고 검증이 통과하며 소유 자원이 문서에 적은 순서로 닫히는 것이다.

## 하지 않는 일과 관련 문서

[Apache AGE](../backends/apache-age.md), [구현 선택](../backends/selection-guide.md), [schema와 트랜잭션](../architecture/schema-and-transactions.md)을 참고한다. 이 모듈은 PostgreSQL을 운영하거나 Bolt/Cypher 호환성을 보장하지 않는다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Bluetape4k Graph age 아키텍처

[![Bluetape4k Graph age 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-architecture-01.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### Apache AGE 다이어그램

[![Apache AGE 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-architecture-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-architecture-02.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### agtype 다이어그램

[![agtype 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-architecture-10.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-architecture-10.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### Bluetape4k Graph age 아키텍처 12 다이어그램

[![Bluetape4k Graph age 아키텍처 12 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-architecture-12.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-architecture-12.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### AgeGraphOperations 다이어그램

[![AgeGraphOperations 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-class-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-class-03.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### AgeSql 다이어그램

[![AgeSql 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-class-04.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-class-04.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### AgeTypeParser 다이어그램

[![AgeTypeParser 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-class-05.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-class-05.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### createVertex 다이어그램

[![createVertex 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-06.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-06.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### createEdge 다이어그램

[![createEdge 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-07.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-07.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### shortestPath 다이어그램

[![shortestPath 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-08.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-08.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### neighbors () 다이어그램

[![neighbors () 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-09.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-09.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

### HikariCP 다이어그램

[![HikariCP 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-11.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-age-sequence-11.svg)

_배포본 README: [`graph/graph-age/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/README.ko.md)_

<!-- release-readme-diagrams:end -->
