# Changelog

이 프로젝트의 주요 변경 사항은 이 파일에 기록한다.

형식은 [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)를 기준으로
하며, 이 프로젝트는 [Semantic Versioning](https://semver.org/spec/v2.0.0.html)을
따른다.

## [Unreleased]

### Added

- **Chunked graph export cursor API**: graph repository가 sync/coroutine
  chunked label lookup을 제공한다. TinkerGraph는 reference chunked traversal
  구현을 제공하고, Jackson3 NDJSON export는 `GraphExportOptions.exportChunkSize`를
  통해 chunked path를 사용한다
  ([#233](https://github.com/bluetape4k/bluetape4k-graph/issues/233)).

### Fixed

- **0.6.0 graph contract and publication review**: graph-core의 coroutine
  `Flow` API를 compile classpath에 고정하고, published POM consumer compile
  smoke와 compile-scope 감사를 추가했다 ([#440](https://github.com/bluetape4k/bluetape4k-graph/issues/440),
  [#441](https://github.com/bluetape4k/bluetape4k-graph/issues/441)).
- **Named graph lifecycle safety**: FalkorDB 삭제 실패를 fail-closed로
  전파하고, Neo4j/Memgraph/TinkerGraph의 logical graph 선택과 삭제를
  lifecycle critical section으로 보호했다 ([#442](https://github.com/bluetape4k/bluetape4k-graph/issues/442)).
- **GraphPath serialization contract**: 중첩 property의 Java serialization
  조건과 지원하지 않는 값의 실패 동작을 테스트와 KDoc으로 명시했다
  ([#444](https://github.com/bluetape4k/bluetape4k-graph/issues/444)).

### Changed

- `0.5.0` 안정 릴리스 이후 `0.6.0` 개발 라인을 열었다.
- 로컬 `bluetape4k-bom` 참조를 `1.11.1-SNAPSHOT`에 맞췄다.
- backend-native graph-io bulk loader 가능성을 문서화하고, backend별 fast path를
  `0.6.0` 구현 lane에서 보류했다
  ([#234](https://github.com/bluetape4k/bluetape4k-graph/issues/234)).

## [0.5.0] - 2026-06-01

### Added

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

### Changed

- **0.5.0 release line dependency alignment**: graph build가
  `io.github.bluetape4k:bluetape4k-bom:1.10.0`을 사용하도록 바꾸고, shared
  dependency catalog는 `catalog/2026-05-26-01`에 맞췄다.

### Fixed

## [0.4.2] - 2026-05-27

### Fixed

- **Tag-triggered release의 catalog 선택을 결정적으로 고정**: release workflow의
  tag run은 오래된 repository catalog variable을 무시하고 checked-in catalog
  default를 사용한다. manual dispatch는 계속 `catalogRef` override를 허용한다
  ([#227](https://github.com/bluetape4k/bluetape4k-graph/issues/227)).
- **TinkerGraph shortest-path facade 경고 정리**: 0.4.x 라인에서 안정화한
  shortest-path 동작은 유지하면서 facade-size 경고만 억제했다.

### Changed

- **0.4.2 release line dependency alignment**: graph build가
  `bluetape4k-projects` 1.9.2 BOM과 `catalog/2026-05-26-01` shared catalog
  reference를 사용하도록 갱신했다.
- **GitHub Actions token hardening**: CI, Nightly, Examples, Benchmark,
  Snapshot publish, Release workflow가 명시적인 read-only 기본
  `GITHUB_TOKEN` permission을 선언하고, 필요한 job에서만 좁은 write/read
  override를 사용한다
  ([#243](https://github.com/bluetape4k/bluetape4k-graph/issues/243)).

### Tests

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

### Added

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

### Fixed

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

### Changed

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

### Added

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

### Fixed

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

### Changed

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

### Added

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

### Changed

- **`graph-servers` 모듈 삭제**: `bluetape4k-testcontainers`의
  `io.bluetape4k.testcontainers.graphdb` package
  (`Neo4jServer.Launcher.neo4j`, `MemgraphServer.Launcher.memgraph`,
  `PostgreSQLAgeServer.Launcher.postgresqlAge`)로 대체했다. 모든 backend test
  (`graph-neo4j`, `graph-memgraph`, `graph-age`, `examples`,
  `spring-boot4 starter`)가 새 API로 migration되었다.
- **문서 / 예제 API 정합성 정리**: `AgeGraphOperations(graphName)` constructor
  pattern, `Database.connect(dataSource)` 선행 호출, `asVirtualThread` 실제
  package import 기준으로 README/KDoc example을 정리했다.

### Fixed

- **TinkerGraph `graphOperations()` 반환 타입**: `GraphOperations` ->
  `TinkerGraphOperations`로 수정했다. `graphSuspendOperations(ops:
  TinkerGraphOperations)` injection 불가 버그를 고쳤다.
- **Spring Boot AutoConfig test `withBean(Supplier)` pattern 제거**: AGE
  AutoConfig test에서 `HikariDataSource` 공유 instance가 context 소멸 시
  자동 close되는 문제를 `withUserConfiguration(DataSourceConfig::class.java)`
  pattern으로 수정했다.

---

## [0.1.0] - 2026-04-16

### Added

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
