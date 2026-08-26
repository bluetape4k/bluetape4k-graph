# 변경 이력

이 프로젝트의 주요 변경 사항은 이 파일에 기록한다.

형식은 [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)를 기준으로
하며, 이 프로젝트는 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)을
따른다.

## [Unreleased]

### 추가

- **Format streaming import reader parity**: CSV, Jackson2/3 NDJSON, GraphML,
  OkIO에 순차 `GraphRecordFlowReader`를 추가하고 source ownership, cancellation,
  safe parse failure, bounded edge staging 계약을 포맷 간에 정렬했다
  ([#313](https://github.com/bluetape4k/bluetape4k-graph/issues/313)).
- graph-io 모든 실행 모델에 ordered progress listener lifecycle과 phase
  snapshot을 추가하고, 선택적 `graph-io-micrometer` bridge 및 Spring Boot
  조건부 자동설정을 제공한다 ([#311](https://github.com/bluetape4k/bluetape4k-graph/issues/311)).
- Virtual Thread 벌크 I/O future가 `cancel(false)` 상태 취소와
  `cancel(true)` worker interrupt를 구분하고, Micrometer tag cardinality를
  고정한다.
- **Backend-native bulk loader SPI**: `graph-io-core`에 raw `R`/validated `V`
  source 경계, capability·progress·report 계약, bounded cancellation/cleanup,
  secret-free lifecycle diagnostic을 추가했다 (#312). 실제 backend adapter와
  URI/file I/O는 후속 이슈 범위다.

### 변경

- **Serializable option invariant TCK**: graph-core의 Serializable traversal·algorithm
  options와 `MissingWeightPolicy.UseDefault`가 Java deserialization으로 constructor
  invariant를 우회하지 못하도록 `readObject` 재검증과 `InvalidObjectException` 계약을
  추가했다. public property, `serialVersionUID = 1L`, 정상 round-trip과 malformed
  payload 거부를 Bluetape assertion TCK로 고정했다 ([#560](https://github.com/bluetape4k/bluetape4k-graph/issues/560)).
- **Weighted path `maxDepth` conformance**: Dijkstra/A* JVM fallback이
  `(vertexId, depth)` 상태와 predecessor를 사용해 inclusive hop bound를
  적용하도록 고쳤다. `maxDepth=0` source-only 경계와 cheaper-deep/shallow
  경로를 graph-core 및 Neo4j, Memgraph, AGE, FalkorDB, TinkerGraph의
  sync/suspend/virtual-thread 공통 TCK로 검증했다 ([#559](https://github.com/bluetape4k/bluetape4k-graph/issues/559)).
- **Catalog ownership and retry-only CI evidence**: local `bluetape4k` version
  alias를 중앙 immutable `bt4k` catalog ownership으로 정리하고, examples
  build와 graph-core test의 bounded retry가 첫 실패 log·attempt count·
  `success_after_retry`를 별도 evidence로 보존하도록 공통 helper와 governance
  계약을 추가했다 ([#547](https://github.com/bluetape4k/bluetape4k-graph/issues/547)).
- **Bounded chunk capability contract**: `CHUNKED_READ`/`CHUNKED_EXPORT` API
  chunking과 source bounded 실행을 `BOUNDED_CHUNKED_READ`/
  `BOUNDED_CHUNKED_EXPORT`로 분리하고, 실제 traversal bounded 보장을 증명한
  TinkerGraph만 marker와 capability를 광고하도록 정렬했다. AGE, Neo4j,
  Memgraph, FalkorDB synchronous fallback은 API chunking만 유지하며 GraphML과
  graph-core 문서에 heap-bound 한계를 명시했다
  ([#536](https://github.com/bluetape4k/bluetape4k-graph/issues/536)).
- **GraphCapability enum compatibility policy**: 기존 enum ordinal과
  serialization name을 보존하고, 새 capability를 enum 마지막에만 추가하는
  규칙과 외부 exhaustive `when`의 `else`/unknown handling을 graph-core
  EN/KO README와 release guidance에 명시했다. 이름 기반 입력은
  `fromSerializedNameOrNull`로 future capability를 `null`로 격리한다
  ([#549](https://github.com/bluetape4k/bluetape4k-graph/issues/549)).
- **AGE suspend Flow streaming boundary**: direct suspend 조회가
  `Dispatchers.IO`의 JDBC cursor와 channel backpressure를 사용하고,
  `DatabaseConfig.defaultFetchSize` 또는 양수 기본값 100을 적용하도록 정렬했다.
  이미 방출한 prefix가 늦은 JDBC 오류에서 중복되지 않도록 streaming transaction의
  재시도를 끄고(`maxAttempts=1`), 취소·collector 예외 시 cursor와 transaction을
  닫는 계약을 검증했다
  ([#535](https://github.com/bluetape4k/bluetape4k-graph/issues/535)).
- **AGE streaming fetch/retry fault injection**: 실제 AGE JDBC 경계를 감싼
  test-only proxy로 configured fetch size `8`, positive fallback `100`, late
  `SQLException`, emitted prefix 1건과 streaming attempt 1회를 관찰하는 회귀를
  추가했다. production API는 변경하지 않고 #552의 driver stall 취소 범위는
  별도 유지한다 ([#550](https://github.com/bluetape4k/bluetape4k-graph/issues/550)).
- **suspendTransaction 중첩 Flow 결과 계약**: graph-core 공통 helper가
  transaction commit 전에 최상위 `Flow`를 materialize하고, `Pair`, `Triple`,
  `Map`, `Collection`, 배열 내부의 중첩 `Flow`는 `IllegalArgumentException`으로
  거부하도록 AGE·Neo4j·Memgraph·TinkerPop에 정렬했다. 중첩 값을 반환해야 하는
  호출자는 transaction block 안에서 `toList()` 등으로 명시적으로 materialize해야
  하며, 임의 사용자 wrapper의 내부 구조는 검사하지 않는다
  ([#551](https://github.com/bluetape4k/bluetape4k-graph/issues/551)).
- **AGE JDBC statement 취소 수명주기**: direct suspend `Flow`가 취소되면 active
  JDBC statement에 `Statement.cancel()`을 최대 한 번 전달하고, Exposed cleanup이
  statement와 `ResultSet`을 닫도록 정렬했다. `executeQuery()`와
  `ResultSet.next()` blocking 경계를 실제 AGE JDBC proxy로 검증하며, driver가
  statement 취소를 지원하지 않는 경우에는 자체 timeout/vendor API가 필요하다는
  제한을 문서화했다 ([#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552)).
- **Graph image/document contract alignment**: 공용 graph launcher와 현재
  중앙 catalog에 맞춰 Neo4j `5.26.29`, Memgraph `3.12.0`, Apache AGE
  `release_PG18_1.7.0`, FalkorDB `v4.20.2` 및 Java 25/Kotlin 2.4.10 기준을
  루트·backend EN/KO README, AGE 선택 가이드, FalkorDB manual/KDoc에 정렬하고,
  manifest/image family drift를 fail-closed로 검증했다
  ([#527](https://github.com/bluetape4k/bluetape4k-graph/issues/527)).
- **Spring Testcontainers DynamicPropertyRegistry bridge**: 선택적
  `bluetape4k-testcontainers-spring`을 graph-spring-boot 테스트에 연결하고,
  공용 `testcontainers.*` key와 기존 `bluetape4k.graph.*` 설정 alias의 lazy 계약을
  FalkorDB live 통합 테스트 및 backend mapping 회귀 테스트로 고정했다
  ([#525](https://github.com/bluetape4k/bluetape4k-graph/issues/525)).
- **Graph Testcontainers image family gate**: Neo4j, Memgraph, Apache AGE,
  FalkorDB manifest가 변경 범위를 결정하고, 각 backend의 startup readiness와
  대표 `GraphCapability` workload를 순차 실행하도록 CI·Nightly·release gate를
  연결했다. readiness timeout, image pull/rate-limit, infrastructure,
  application failure를 분류하고 retry 성공도 release gate를 열지 않으며,
  image digest·container inspect/logs/events를 artifact로 보존한다
  ([#526](https://github.com/bluetape4k/bluetape4k-graph/issues/526)).

### 버그 수정

- **TinkerGraph chunk cursor lifecycle**: sync vertex/edge chunk 경로에
  close-aware cursor를 추가하고 suspend Flow가 조기 `take`, cancellation,
  iterator 예외에서 traversal을 닫도록 정렬했다. 기존 repository `Sequence`
  ABI는 유지하며 graph-io와 remote driver backend는 변경하지 않았다
  ([#548](https://github.com/bluetape4k/bluetape4k-graph/issues/548)).
- **Suspend example teardown cancellation contract**: code, fraud, IAM,
  knowledge, LinkedIn, observability, recommendation 예제의 suspend backend
  teardown이 `CancellationException`을 재전파하고 일반 graph drop 실패만
  기록하도록 정렬했다. owned driver close는 `finally`에서 보장하며 sync-only
  teardown은 변경하지 않았다 ([#546](https://github.com/bluetape4k/bluetape4k-graph/issues/546)).
- **Spring Boot graph management contract**: Actuator snapshot이 backend별
  graph/database 설정과 실제 `GraphOperations.capabilities()`·graph-io
  classpath/bean 상태를 반영하도록 고쳤다. AGE graph initializer는
  operations의 typed duplicate predicate에만 중복을 위임하고 일반 예외를
  다시 던지며, backend auto-configuration 테스트는
  `bluetape4k.assertions.assertFailsWith`를 사용한다
  ([#545](https://github.com/bluetape4k/bluetape4k-graph/issues/545)).
- **Graph cache create contract**: AGE, Neo4j, Memgraph 캐시 데코레이터가
  `createVertex`와 `createEdge`를 동일 인자라는 이유로 합치지 않고 매번 backend에
  위임하도록 고쳤다. 생성 후 읽기 캐시 무효화와 영문/국문 문서 계약도 정렬했다
  ([#463](https://github.com/bluetape4k/bluetape4k-graph/issues/463)).
- **Graph cache bounds and TTL**: AGE, Neo4j, Memgraph 캐시 데코레이터가
  `maxSize`와 `expireAfterWrite`를 모든 읽기 캐시에 실제로 적용하도록 Caffeine
  bounded/expiring cache로 전환했다. 양수 파라미터 검증과 eviction/expiration
  회귀 테스트, 영문/국문 문서를 추가했다
  ([#464](https://github.com/bluetape4k/bluetape4k-graph/issues/464)).
- **Graph cache invalidation race**: AGE, Neo4j, Memgraph 캐시 데코레이터가
  generation guard로 동시 cache miss의 stale 재적재를 차단하고, `dropGraph`와
  transaction commit/rollback 이후의 읽기 캐시 경계를 명시하도록 정렬했다
  ([#499](https://github.com/bluetape4k/bluetape4k-graph/issues/499)).
- **Virtual Thread bulk adapter lifecycle**: graph-io의 `wrapImporter`와
  `wrapExporter`가 동기 delegate의 `close()`를 wrapper에서 최대 한 번 전파하고,
  반복 close·source/sink 소유권·비동기 작업 중 close 정책을 KDoc과 회귀 테스트로
  명시하도록 고쳤다 ([#470](https://github.com/bluetape4k/bluetape4k-graph/issues/470)).

## [0.6.0] - 2026-08-06

### 추가

- **Chunked graph export cursor API**: graph repository가 sync/coroutine
  chunked label lookup을 제공한다. TinkerGraph는 reference chunked traversal
  구현을 제공하고, Jackson3 NDJSON export는 `GraphExportOptions.exportChunkSize`를
  통해 chunked path를 사용한다
  ([#233](https://github.com/bluetape4k/bluetape4k-graph/issues/233)).
- **Typed graph endpoint validation helpers**: graph-core의 sync, suspend,
  virtual-thread vertex repository에 `requireEndpoint` 계열 확장을 추가해
  endpoint 누락과 label 불일치를 호출 지점에서 즉시 검증한다
  ([#398](https://github.com/bluetape4k/bluetape4k-graph/issues/398)).

### 버그 수정

- **CodeQL Kotlin catalog pin**: 중앙 version catalog의 정렬된 주석과 공백을
  허용하면서도 immutable ref와 checksum 검증을 유지하도록 고쳤다
  ([#437](https://github.com/bluetape4k/bluetape4k-graph/issues/437)).
- **Gitleaks release asset resolution**: 고정된 도구 버전과 인증된 release
  metadata를 사용하고, 모호하거나 누락된 asset 및 SHA-256 불일치를 fail-closed로
  처리하도록 고쳤다 ([#298](https://github.com/bluetape4k/bluetape4k-graph/issues/298)).
- **0.6.0 graph contract review**: graph-core의 coroutine `Flow` API를
  compile classpath에 고정하고, 외부 consumer compile smoke와 compile-scope
  감사를 추가했다 ([#440](https://github.com/bluetape4k/bluetape4k-graph/issues/440),
  [#441](https://github.com/bluetape4k/bluetape4k-graph/issues/441)).
- **Named graph lifecycle safety**: FalkorDB 삭제 실패를 fail-closed로
  전파하고, Neo4j/Memgraph/TinkerGraph의 logical graph 선택과 삭제를
  lifecycle critical section으로 보호했다 ([#442](https://github.com/bluetape4k/bluetape4k-graph/issues/442)).
- **Graph existence failure contract**: sync와 suspend `graphExists`가
  infrastructure failure와 cancellation을 `false`로 숨기지 않고 호출자에게
  전파하도록 정렬했다
  ([#443](https://github.com/bluetape4k/bluetape4k-graph/issues/443)).
- **GraphPath serialization contract**: 중첩 property의 Java serialization
  조건과 지원하지 않는 값의 실패 동작을 테스트와 KDoc으로 명시했다
  ([#444](https://github.com/bluetape4k/bluetape4k-graph/issues/444)).

### 변경

- **Korean documentation and KDoc consistency**: README와 LLM-facing 문서는
  유지하면서 single-language 문서와 Kotlin KDoc/comments를 한국어로 정리했다
  ([#400](https://github.com/bluetape4k/bluetape4k-graph/issues/400)).
- `0.5.0` 안정 릴리스 이후 `0.6.0` 개발 라인을 열었다.
- 로컬 `bluetape4k-bom` 참조를 `1.11.1-SNAPSHOT`에 맞췄다.
- backend-native graph-io bulk loader 가능성을 문서화하고, backend별 fast path를
  `0.6.0` 구현 lane에서 보류했다
  ([#234](https://github.com/bluetape4k/bluetape4k-graph/issues/234)).

## [0.5.0] - 2026-06-01

### 추가

- **Ktor managed backend DSL**: `graph-ktor`가 `neo4j { ... }`,
  `memgraph { ... }`, `falkorDB { ... }`를 통해 Neo4j, Memgraph, FalkorDB
  driver를 직접 생성하고 소유할 수 있다. 기존 caller-owned helper overload는
  유지한다 ([#232](https://github.com/bluetape4k/bluetape4k-graph/issues/232)).
- **Ktor managed Apache AGE DataSource DSL**: `graph-ktor`가
  `ageDataSource { ... }`를 통해 Hikari 기반 AGE pool을 생성하고,
  `Database.connect(...)`로 Exposed에 연결한 뒤 Ktor shutdown 시 plugin-owned
  pool만 닫을 수 있다
  ([#254](https://github.com/bluetape4k/bluetape4k-graph/issues/254)).
- **Observability incident graph example**: `observability-graph-examples`
  module을 추가했다. service dependency blast-radius, alert-boundary
  correlation, affected API lookup, owner traversal, bundled CSV fixture import,
  sync/suspend backend test를 포함한다
  ([#247](https://github.com/bluetape4k/bluetape4k-graph/issues/247)).
- **IAM access-path graph example**: `iam-access-graph-examples` module을
  추가했다. direct grant, inherited group grant, explicit deny path,
  temporary break-glass grant, risky nested privilege-chain detection,
  sync/suspend backend test를 포함한다
  ([#248](https://github.com/bluetape4k/bluetape4k-graph/issues/248)).
- **Supply-chain impact graph example**: `supply-chain-graph-examples` module을
  추가했다. graph-io CSV fixture, supplier/part impact query, route failure
  alternate, bottleneck part detection, substitution-cycle detection,
  sync/suspend TinkerGraph test를 포함한다
  ([#249](https://github.com/bluetape4k/bluetape4k-graph/issues/249)).
- **Data lineage impact graph example**: `data-lineage-examples` module을
  추가했다. graph-io CSV fixture, downstream dashboard impact query, upstream
  table lookup, broken job owner traversal, data quality impact explanation,
  sync/suspend TinkerGraph test를 포함한다
  ([#250](https://github.com/bluetape4k/bluetape4k-graph/issues/250)).
- **Network topology graph example**: `network-topology-examples` module을
  추가했다. graph-io CSV fixture, shortest active path query, service
  failure-impact check, isolated segment detection, redundant route discovery,
  sync/suspend TinkerGraph test를 포함한다
  ([#251](https://github.com/bluetape4k/bluetape4k-graph/issues/251)).
- **Security attack-path graph example**: `security-attack-path-examples`
  module을 추가했다. shortest attack-path query, risk-ranked path enumeration,
  credential-based privilege escalation, remediation-impact check,
  sync/suspend TinkerGraph test를 포함한다
  ([#252](https://github.com/bluetape4k/bluetape4k-graph/issues/252)).

### 변경

- **0.5.0 release line dependency alignment**: graph build가
  `io.github.bluetape4k:bluetape4k-bom:1.10.0`을 사용하도록 바꾸고, shared
  dependency catalog는 `catalog/2026-05-26-01`에 맞췄다.

### 버그 수정

## [0.4.2] - 2026-05-27

### 버그 수정

- **Tag-triggered release의 catalog 선택을 결정적으로 고정**: release workflow의
  tag run은 오래된 repository catalog variable을 무시하고 checked-in catalog
  default를 사용한다. manual dispatch는 계속 `catalogRef` override를 허용한다
  ([#227](https://github.com/bluetape4k/bluetape4k-graph/issues/227)).
- **TinkerGraph shortest-path facade 경고 정리**: 0.4.x 라인에서 안정화한
  shortest-path 동작은 유지하면서 facade-size 경고만 억제했다.

### 변경

- **0.4.2 release line dependency alignment**: graph build가
  `bluetape4k-projects` 1.9.2 BOM과 `catalog/2026-05-26-01` shared catalog
  reference를 사용하도록 갱신했다.
- **GitHub Actions token hardening**: CI, Nightly, Examples, Benchmark,
  Snapshot publish, Release workflow가 명시적인 read-only 기본
  `GITHUB_TOKEN` permission을 선언하고, 필요한 job에서만 좁은 write/read
  override를 사용한다
  ([#243](https://github.com/bluetape4k/bluetape4k-graph/issues/243)).

### 테스트

- **GraphML unsupported element policy matrix**: unsupported GraphML element에
  대한 reader 동작을 고정해, 이후 parser 변경이 문서화된 skip/fail policy를
  보존하도록 했다 ([#231](https://github.com/bluetape4k/bluetape4k-graph/issues/231)).
- **Graph-io skipped-record failure accounting**: graph-io failure path에서
  skipped record accounting을 검증하는 coverage를 추가했다
  ([#239](https://github.com/bluetape4k/bluetape4k-graph/issues/239)).
- **Benchmark evidence contracts**: benchmark wrapper JSON stdout, benchmark SVG
  rendering, graph-io benchmark smoke execution path의 lightweight coverage를
  추가했다
  ([#236](https://github.com/bluetape4k/bluetape4k-graph/issues/236),
  [#237](https://github.com/bluetape4k/bluetape4k-graph/issues/237),
  [#238](https://github.com/bluetape4k/bluetape4k-graph/issues/238)).
- **Suspend graph test harness alignment**: 남은 IO/Testcontainers 기반 suspend
  graph test가 virtual-time 또는 Kotlin test assertion 대신 `runSuspendIO`와
  bluetape4k assertion helper를 사용하도록 정렬했다
  ([#241](https://github.com/bluetape4k/bluetape4k-graph/issues/241)).

## [0.4.0] - 2026-05-22

### 추가

- **graph-io backed domain example sample loaders**: fraud detection,
  recommendation, knowledge graph example에 bundled CSV fixture와 sync/suspend
  sample dataset loader를 추가하고, TinkerGraph smoke coverage 및 영어/한국어
  README import flow를 제공했다
  ([#111](https://github.com/bluetape4k/bluetape4k-graph/issues/111)).
- **graph benchmark evidence program**: backend, graph-io, runtime-model,
  workload-shape, sustained-write, production API model, weighted shortest-path
  benchmark lane을 normalized report와 README chart asset으로 추가했다
  ([#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14),
  [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15),
  [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41),
  [#188](https://github.com/bluetape4k/bluetape4k-graph/issues/188),
  [#189](https://github.com/bluetape4k/bluetape4k-graph/issues/189),
  [#190](https://github.com/bluetape4k/bluetape4k-graph/issues/190),
  [#191](https://github.com/bluetape4k/bluetape4k-graph/issues/191),
  [#192](https://github.com/bluetape4k/bluetape4k-graph/issues/192),
  [#193](https://github.com/bluetape4k/bluetape4k-graph/issues/193),
  [#196](https://github.com/bluetape4k/bluetape4k-graph/issues/196),
  [#197](https://github.com/bluetape4k/bluetape4k-graph/issues/197),
  [#198](https://github.com/bluetape4k/bluetape4k-graph/issues/198),
  [#199](https://github.com/bluetape4k/bluetape4k-graph/issues/199),
  [#201](https://github.com/bluetape4k/bluetape4k-graph/issues/201)).
- **graph-spring-boot FalkorDB nightly coverage**: full-nightly CI가 live
  container smoke test로 FalkorDB Spring Boot auto-configuration path를
  검증한다 ([#126](https://github.com/bluetape4k/bluetape4k-graph/issues/126)).

### 버그 수정

- **FalkorDB/Memgraph schema manager DDL fallback이 cancellation을 보존**:
  schema manager create/drop index helper path는 already-exists 또는
  missing-resource message fallback을 적용하기 전에 `CancellationException`을
  다시 던진다 ([#157](https://github.com/bluetape4k/bluetape4k-graph/issues/157)).
- **FalkorDB suspend graph existence check가 coroutine cancellation을 전파**:
  `FalkorDBGraphSuspendOperations.graphExists()`는 cancellation을 `false`로
  변환하지 않고 `CancellationException`을 다시 던진다. 일반 driver failure에
  대한 기존 fallback은 유지한다
  ([#156](https://github.com/bluetape4k/bluetape4k-graph/issues/156)).
- **Neo4j suspend transaction이 더 이상 `runBlocking`을 경유하지 않음**:
  `Neo4jGraphSuspendOperations.suspendTransaction()`은 Neo4j reactive
  transaction을 사용하고 rollback/cleanup semantics를 보존하며, commit 전에
  반환된 transaction `Flow` 값을 materialize한다
  ([#158](https://github.com/bluetape4k/bluetape4k-graph/issues/158)).
- **AGE, Memgraph, TinkerGraph suspend transaction이 더 이상 `runBlocking`을
  경유하지 않음**: AGE는 Exposed suspended transaction과 native suspend
  transaction scope를 사용하고, Memgraph는 reactive Bolt transaction을 사용하며,
  TinkerGraph는 suspend-aware rollback snapshot path를 사용한다. Cancellation
  rollback과 반환된 transaction `Flow` materialization은 targeted test로
  검증한다 ([#160](https://github.com/bluetape4k/bluetape4k-graph/issues/160)).
- **FalkorDB Ktor example teardown이 driver를 닫음**: example test는 PER_CLASS
  test lifecycle 종료 후 caller-owned driver를 닫는다
  ([#135](https://github.com/bluetape4k/bluetape4k-graph/issues/135)).

### 변경

- 의존성을 `io.github.bluetape4k:bluetape4k-bom:1.9.0`과 최신 published
  `io.github.bluetape4k:bluetape4k-dependencies:1.0.0`에 맞췄다.
- leaf Dependabot은 GitHub Actions에 한정하고, Detekt를 PR quality gate로
  두며 Kover는 report-only로 유지했다
  ([#18](https://github.com/bluetape4k/bluetape4k-graph/issues/18),
  [#19](https://github.com/bluetape4k/bluetape4k-graph/issues/19)).
- Ktor 3.5.0 기준으로 `graph-ktor`와 examples를 점검했으며 API 변경은
  필요하지 않았다 ([#127](https://github.com/bluetape4k/bluetape4k-graph/issues/127)).
- English/Korean README module table에 FalkorDB Ktor example discoverability를
  추가하고, public FalkorDB auto-configuration KDoc을 영어로 변환했다
  ([#133](https://github.com/bluetape4k/bluetape4k-graph/issues/133),
  [#134](https://github.com/bluetape4k/bluetape4k-graph/issues/134)).

---

## [0.3.0] - 2026-05-16

### 추가

- **Root README refresh**: generated graph workbench hero image, 더 충실한 project
  overview, Mermaid architecture diagram, supported database guidance, graph-io
  DAEAD note, 현재 example module/workflow 표면을 추가했다
  ([PR #116](https://github.com/bluetape4k/bluetape4k-graph/pull/116)).
- **Dedicated `Examples` workflow**: 모든 example module에 대해 daily 및
  path-triggered GitHub Actions workflow를 추가하고 Nightly coverage와 분리했다
  ([PR #112](https://github.com/bluetape4k/bluetape4k-graph/pull/112)).
- **Domain example modules**: 안정화된 graph API와 기존 backend test pattern
  위에 fraud detection, recommendation, knowledge graph example을 추가했다
  ([#10](https://github.com/bluetape4k/bluetape4k-graph/issues/10),
  [PR #110](https://github.com/bluetape4k/bluetape4k-graph/pull/110)).
- **Public API KDoc examples**: public API 전반에 호출 가능한 English Kotlin
  example을 추가했다
  ([#16](https://github.com/bluetape4k/bluetape4k-graph/issues/16),
  [PR #109](https://github.com/bluetape4k/bluetape4k-graph/pull/109)).
- **`graph-okio` DAEAD streaming**: OkIO graph stream에 DAEAD chunk
  encryption/decryption을 추가했다. gzip+DAEAD chaining과 잘못된 associated
  data, truncated ciphertext에 대한 negative-path test를 포함한다
  ([#49](https://github.com/bluetape4k/bluetape4k-graph/issues/49),
  [PR #114](https://github.com/bluetape4k/bluetape4k-graph/pull/114),
  [PR #115](https://github.com/bluetape4k/bluetape4k-graph/pull/115)).
- **`graph-ktor`**: Ktor 3.x `GraphPlugin` module과 TinkerGraph 기반
  `ktor-graph-examples`를 추가했다. `Application`/`ApplicationCall` extension은
  `GraphOperations`와 `GraphSuspendOperations` 접근을 제공하고, backend helper는
  TinkerGraph, Neo4j, Memgraph, AGE, FalkorDB를 지원한다
  ([#96](https://github.com/bluetape4k/bluetape4k-graph/issues/96)).
- **`graph-bom` README**: BOM 사용법을 English/Korean README에 문서화했다
  ([PR #70](https://github.com/bluetape4k/bluetape4k-graph/pull/70)).
- **`graph-okio`**: CSV, GraphML, Jackson family format을 위한 `Source`/`Sink`
  entry point가 있는 OkIO 기반 streaming graph I/O layer를 추가했다
  ([PR #48](https://github.com/bluetape4k/bluetape4k-graph/pull/48)).
- **Weighted graph support**: Dijkstra/A* shortest path API를 추가했다
  ([PR #39](https://github.com/bluetape4k/bluetape4k-graph/pull/39)).
- **`graph-core` model builder utilities** (`graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/`)
  - `graphElementIdOf(Any)`: 모든 타입에서 `GraphElementId`를 만든다. `String`,
    `Long`, `Int`, `GraphElementId`는 안전하게 변환된다.
  - `graphVertexOf(Any, label, properties)`: id, label, property map으로
    `GraphVertex`를 만드는 builder utility다.
  - `graphPathOf(vertices, edges)` / `graphPathOf(vertices)`: `GraphPath`
    builder overload다.
  - `emptyGraphPath()`: 빈 path를 만든다. 이전 `emptyGraphPathOf()`를 대체한다.
  - `GraphPath.toCycle()`: path를 `GraphCycle`로 변환하는 extension이다.
- **`graph-core` model test classes** (`graph/graph-core/src/test/`)
  - `GraphElementIdTest`: `graphElementIdOf` 4개 case와 double-conversion guard.
  - `GraphVertexTest`: `graphVertexOf` 6개 case.
  - `GraphPathTest`: `graphPathOf`와 `emptyGraphPath` 8개 case.
  - `GraphCycleTest`: `toCycle()`, `length`, equality 7개 case.
- **`graph-core` README - model builder utilities section**
  (`graph/graph-core/README.md`, `README.ko.md`).
- **Transaction DSL first slice**: `GraphOperations.transaction { }` extension과
  capability contract를 추가하고 Neo4j, Memgraph, AGE, TinkerGraph sync backend에
  연결했다. suspend transaction capability도 같은 backend에 연결했다. FalkorDB는
  repository DSL이 중간 결과를 즉시 반환해야 하므로 명시적으로 unsupported다.
- **`graph-core` capability docs**: `SchemaManager`, `GraphMergeOperations`,
  `GraphTransactionScope` / `GraphSuspendTransactionScope` 사용 예제를 root
  README, backend README, KDoc 전반에 동기화했다.
- **FalkorDB Ktor example**: `FalkorDBKtorGraphApp`을 `ktor-graph-examples`에
  추가하고 city-graph reset/count/shortest-path route를 검증하는 전체
  `GraphPlugin` integration test를 제공했다
  ([#123](https://github.com/bluetape4k/bluetape4k-graph/issues/123),
  [PR #130](https://github.com/bluetape4k/bluetape4k-graph/pull/130)).
- **`graph-spring-boot` FalkorDB health indicator test**:
  FalkorDB `HealthIndicator` bean에 대한 `ApplicationContextRunner` test를
  추가하고, `README.md`와 `README.ko.md` 모두에 supported backend value로
  `falkordb`를 추가했다
  ([#125](https://github.com/bluetape4k/bluetape4k-graph/issues/125),
  [PR #131](https://github.com/bluetape4k/bluetape4k-graph/pull/131)).
- **License text alignment**: 모든 README 파일의 MIT license text를 맞췄다
  ([PR #117](https://github.com/bluetape4k/bluetape4k-graph/pull/117)).
- **Test infrastructure**: benchmark 및 graph-io module에
  `junit-platform.properties`와 `logback-test.xml`을 추가했다
  ([PR #119](https://github.com/bluetape4k/bluetape4k-graph/pull/119)).

### Docs

- **`graph-ktor` KDoc and messages in English**: `GraphPlugin`,
  `GraphPluginConfig`, `GraphPluginState`, `ApplicationExt`, 다섯 backend config
  helper의 모든 KDoc, log message, error string을 영어로 변환했다
  ([#122](https://github.com/bluetape4k/bluetape4k-graph/issues/122),
  [PR #129](https://github.com/bluetape4k/bluetape4k-graph/pull/129)).
- **`graph-okio` README rewrite to English**: Korean README를 완전한 English
  canonical version으로 교체하고 Korean section header를 갱신했다
  ([#118](https://github.com/bluetape4k/bluetape4k-graph/issues/118),
  [PR #129](https://github.com/bluetape4k/bluetape4k-graph/pull/129)).

### 버그 수정

- **`graphElementIdOf(Any)` double-`toString()` conversion bug**:
  `GraphElementId` 값을 다시 `graphElementIdOf`에 넘기면
  `"GraphElementId(value=x)"` 문자열로 손상되던 문제를 `is GraphElementId`
  early-return으로 수정했다.
- `AStarRunner` performance와 invariant validation을 강화하고 관련 test와 KDoc을
  정리했다 ([PR #62](https://github.com/bluetape4k/bluetape4k-graph/pull/62)).
- concurrent `FakeFileSystem` 접근 시 발생하던 `ConcurrentModificationException`을
  수정했다 ([PR #53](https://github.com/bluetape4k/bluetape4k-graph/pull/53)).
- 현재 build layout에 맞게 CI artifact path와 module별 Kover report path를
  수정했다 ([PR #55](https://github.com/bluetape4k/bluetape4k-graph/pull/55),
  [PR #56](https://github.com/bluetape4k/bluetape4k-graph/pull/56)).

### 변경

- 현재 열린 issue queue, Java 21 runtime, version catalog dependency, 변경된
  Spring Boot module, graph-okio DAEAD support, examples workflow, active module
  layout을 반영하도록 `WIP.md`, `AGENTS.md`, `CLAUDE.md`를 갱신했다.
- Spring Boot integration을 `graph-spring-boot4-starter`
  (`spring-boot4/graph-spring-boot4-starter`, package
  `io.bluetape4k.graph.spring.boot4`)에서 version-neutral stable contract인
  `graph-spring-boot` (`spring-boot/graph-spring-boot`, package
  `io.bluetape4k.graph.spring.boot`)로 이름을 바꿨다
  ([#99](https://github.com/bluetape4k/bluetape4k-graph/issues/99)).
- `graph-io-okio` Gradle project/artifact를 `graph-okio`로 이름을 바꿨다
  ([#76](https://github.com/bluetape4k/bluetape4k-graph/issues/76)).
- Gradle dependency declaration을 `buildSrc/Libs.kt`에서 Version Catalog
  (`gradle/libs.versions.toml`)로 이전했다
  ([PR #63](https://github.com/bluetape4k/bluetape4k-graph/pull/63)).
- CI는 paths-filter, Docker-specific job, container-heavy workflow retry
  configuration을 사용한다
  ([PR #68](https://github.com/bluetape4k/bluetape4k-graph/pull/68)).
- build에서 `tanvd.kosogor` plugin을 제거했다
  ([PR #57](https://github.com/bluetape4k/bluetape4k-graph/pull/57)).
- `graph-io-core` test coverage를 65%에서 93%로 높였다
  ([PR #58](https://github.com/bluetape4k/bluetape4k-graph/pull/58)).
- Kotlin factory function convention에 맞춰 `emptyGraphPathOf()`를
  `emptyGraphPath()`로 이름을 바꿨다.
- `graphVertexOf(Any, label)`을 `graphVertexOf(Any, label, properties)`로 바꾸고
  `properties` parameter를 추가했으며, `graphElementIdOf` 경유
  double-conversion path를 제거했다.
- single-arg duplicate `graphPathOf` overload를 제거하고 명시적 parameter
  overload만 남겼다.
- ktlint 규칙을 맞추기 위해 `AStarRunner` companion object의 colon 앞 공백 누락을
  수정했다.
- test-code assertion dependency를 `bluetape4k-assertions`로 이전하고 Gradle test
  dependency를 교체했다. PR #69에서 `./gradlew compileTestKotlin --no-daemon`으로
  검증했고 issue #66을 닫았다.

---

## [0.2.0] - 2026-04-28

### 추가

- **그래프 알고리즘 확장** (`graph-core` + 백엔드 구현): `pageRank`,
  `degreeCentrality`, `connectedComponents`, `bfs`, `dfs`, `cycles` API를
  추가하고 Neo4j/Memgraph/AGE/TinkerPop 계열 구현을 정리했다.
- **Virtual Threads API 확장**: Vertex/Edge/Traversal repository 전체에
  virtual-thread bridge adapter와 `GraphVirtualThreadOperations` 합성 API를
  적용했다.
- **FalkorDB 백엔드**: `jfalkordb` 기반 `graph-falkordb` 구현, Spring Boot 4
  auto-configuration, examples 통합을 추가했다.
- **`graph-io` 벌크 임포트/익스포트** (`graph-io/` 4개 모듈): 포맷별 대용량 I/O
  (Sync / VirtualThread / Coroutine)
  - `graph-io-core`: 공유 계약(`GraphBulkExporter`, `GraphBulkImporter`),
    모델(`GraphIoVertexRecord`, `GraphIoEdgeRecord`), 옵션, 헬퍼(`GraphIoPaths`)
    - `BufferedOutputStream/InputStream` wrapping으로 StAX 성능을 확보했다.
  - `graph-io-csv`: CSV importer/exporter (univocity-parsers 기반) x Sync/VT/Suspend.
  - `graph-io-jackson2`: Jackson 2.x NDJSON importer/exporter x Sync/VT/Suspend.
    edge buffering(`maxEdgeBufferSize`)을 지원한다.
  - `graph-io-jackson3`: Jackson 3.x NDJSON importer/exporter x Sync/VT/Suspend.
    Jackson2 NDJSON과 호환된다.
  - `graph-io-graphml`: GraphML (XML/StAX) importer/exporter x Sync/VT/Suspend.
    `XMLInputFactory`/`XMLOutputFactory` singleton caching을 사용한다.
  - cross-format round-trip test: CSV <-> Jackson2 <-> Jackson3 <-> GraphML.
- **`graph-io-benchmark`** (`benchmark/graph-io-benchmark`): JMH benchmark
  36개 method (4 format x 3 API x 3 operation).
  - 결과: CSV export 1.0ms, GraphML export 2.6ms, import 18-22ms
    (TinkerGraph in-memory 기준).
  - 결과 report: `docs/benchmark/2026-04-18-graph-io-bulk-results.md`.
- **`graph-spring-boot3-starter` 제거**: Spring Boot 4 전용으로 정리하면서
  Spring Boot 3 starter module을 제거했다.
  - `GraphAutoConfiguration`: root auto-configuration (공통 property binding).
  - `GraphNeo4jAutoConfiguration`: `@ConditionalOnClass(Neo4jGraphOperations::class)`
    기반 Neo4j bean auto-registration과 HealthIndicator.
  - `GraphMemgraphAutoConfiguration`: Memgraph bean auto-registration과 HealthIndicator.
  - `GraphAgeAutoConfiguration`: AGE DataSource conditional bean registration과 HealthIndicator.
  - `GraphTinkerGraphAutoConfiguration`: TinkerGraph in-memory bean auto-registration과 HealthIndicator.
  - `GraphProperties`, `Neo4jGraphProperties`, `MemgraphGraphProperties`,
    `AgeGraphProperties`, `TinkerGraphGraphProperties`: `@ConfigurationProperties` binding.
  - 테스트: `ApplicationContextRunner` 기반 unit test 4종,
    `TinkerGraphWebMvcTest` (Virtual Threads), `TinkerGraphWebFluxTest`
    (coroutine) - 총 16 passing.
- **`graph-spring-boot4-starter`** (`spring-boot4/graph-spring-boot4-starter`):
  Spring Boot 4.0.x AutoConfiguration starter를 추가했다.
  - Spring Boot 4 module split 대응:
    - `DataSourceAutoConfiguration`: `boot.autoconfigure.jdbc` ->
      `boot.jdbc.autoconfigure` (`spring-boot-jdbc` module).
    - `HealthIndicator`/`Health`: `boot.actuate.health` ->
      `boot.health.contributor` (`spring-boot-health` module).
    - `TestRestTemplate`: `boot.test.web.client` -> `boot.resttestclient` +
      `@AutoConfigureTestRestTemplate` 필수 (`spring-boot-resttestclient` module).
    - `WebTestClient`: `@AutoConfigureWebTestClient` 필수
      (`spring-boot-webtestclient` module).
  - Spring Boot 4 전용 5종 AutoConfiguration과 5종 Properties class
    (package: `boot4`).
  - 테스트: `ApplicationContextRunner` 기반 unit test 4종,
    `TinkerGraphWebMvcTest`, `TinkerGraphWebFluxTest` - 총 16 passing.
- **GitHub Actions CI 파이프라인**: CI, integration, release, benchmark workflow를
  추가하고 Java 25 preview, Gradle cache, Testcontainers 기반 integration test
  path를 구성했다.

### 변경

- **`graph-servers` 모듈 삭제**: `bluetape4k-testcontainers`의
  `io.bluetape4k.testcontainers.graphdb` package
  (`Neo4jServer.Launcher.neo4j`, `MemgraphServer.Launcher.memgraph`,
  `PostgreSQLAgeServer.Launcher.postgresqlAge`)로 대체했다. 모든 backend test
  (`graph-neo4j`, `graph-memgraph`, `graph-age`, `examples`,
  `spring-boot4 starter`)가 새 API로 migration되었다.
- **문서 / 예제 API 정합성 정리**: `AgeGraphOperations(graphName)` constructor
  pattern, `Database.connect(dataSource)` 선행 호출, `asVirtualThread` 실제
  package import 기준으로 README/KDoc example을 정리했다.

### 버그 수정

- **TinkerGraph `graphOperations()` 반환 타입**: `GraphOperations` ->
  `TinkerGraphOperations`로 수정했다. `graphSuspendOperations(ops:
  TinkerGraphOperations)` injection 불가 버그를 고쳤다.
- **Spring Boot AutoConfig test `withBean(Supplier)` pattern 제거**: AGE
  AutoConfig test에서 `HikariDataSource` 공유 instance가 context 소멸 시
  자동 close되는 문제를 `withUserConfiguration(DataSourceConfig::class.java)`
  pattern으로 수정했다.

---

## [0.1.0] - 2026-04-16

### 추가

- **BOM 모듈** (`bluetape4k-graph-bom`): dependency version 통합 관리를 위한
  Bill of Materials를 추가했다.
- **`code-graph-examples`**: code dependency graph example integration module.
  - `AbstractCodeGraphTest` / `AbstractCodeGraphSuspendTest`: 공통 test abstract class.
  - backend별 concrete class: `Neo4j`, `Memgraph`, `TinkerGraph`, `AGE` x
    sync/suspend (총 8개).
  - `CodeGraphService` / `CodeGraphSuspendService`: module dependency,
    class inheritance, function call chain을 관리한다.
- **`linkedin-graph-examples`**: LinkedIn social graph example integration module.
  - `AbstractLinkedInGraphTest` / `AbstractLinkedInGraphSuspendTest`: 공통 test
    abstract class.
  - backend별 concrete class: `Neo4j`, `Memgraph`, `TinkerGraph`, `AGE` x
    sync/suspend (총 8개).
  - `LinkedInGraphSuspendService`: suspend/Flow 기반 LinkedIn graph service를
    새로 작성했다.
- **추상 테스트 클래스 패턴**: `ops` (`GraphOperations` /
  `GraphSuspendOperations`) override만으로 모든 backend에서 같은 test를 실행할 수
  있다.
