# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - Unreleased

### Added

### Changed

### Fixed

## [0.4.2] - 2026-05-27

### Fixed

- **Release catalog selection is deterministic for tag-triggered releases**:
  release workflow tag runs now ignore stale repository catalog variables and
  use the checked-in catalog default, while manual dispatch can still override
  `catalogRef` explicitly ([#227](https://github.com/bluetape4k/bluetape4k-graph/issues/227)).
- **TinkerGraph shortest-path facade warning cleanup**: suppressed the
  facade-size warning without changing the shortest-path behavior stabilized in
  the 0.4.x line.

### Changed

- **Dependency alignment for the 0.4.2 release line**: updated the graph build to
  consume the `bluetape4k-projects` 1.9.2 BOM and the
  `catalog/2026-05-26-01` shared catalog reference.
- **GitHub Actions token hardening**: CI, Nightly, Examples, Benchmark,
  Snapshot publish, and Release workflows now declare explicit read-only default
  `GITHUB_TOKEN` permissions, with narrow job-level write/read overrides only
  where required ([#243](https://github.com/bluetape4k/bluetape4k-graph/issues/243)).

### Tests

- **GraphML unsupported element policy matrix**: locked reader behavior for
  unsupported GraphML elements so future parser changes preserve the documented
  skip/fail policy ([#231](https://github.com/bluetape4k/bluetape4k-graph/issues/231)).
- **Graph-io skipped-record failure accounting**: added coverage for skipped
  record accounting on graph-io failure paths ([#239](https://github.com/bluetape4k/bluetape4k-graph/issues/239)).
- **Benchmark evidence contracts**: added lightweight coverage for benchmark
  wrapper JSON stdout, benchmark SVG rendering, and graph-io benchmark smoke
  execution paths ([#236](https://github.com/bluetape4k/bluetape4k-graph/issues/236),
  [#237](https://github.com/bluetape4k/bluetape4k-graph/issues/237),
  [#238](https://github.com/bluetape4k/bluetape4k-graph/issues/238)).
- **Suspend graph test harness alignment**: remaining IO/Testcontainers-backed
  suspend graph tests now use `runSuspendIO` and bluetape4k assertion helpers
  instead of virtual-time or Kotlin test assertions ([#241](https://github.com/bluetape4k/bluetape4k-graph/issues/241)).

## [0.4.0] - 2026-05-22

### Added

- **graph-io backed domain example sample loaders**: fraud detection, recommendation, and knowledge graph examples now include bundled CSV fixtures plus sync/suspend sample dataset loaders, with TinkerGraph smoke coverage and English/Korean README import flows ([#111](https://github.com/bluetape4k/bluetape4k-graph/issues/111)).
- **graph benchmark evidence program**: added backend, graph-io, runtime-model, workload-shape, sustained-write, production API model, and weighted shortest-path benchmark lanes with normalized reports and README chart assets ([#14](https://github.com/bluetape4k/bluetape4k-graph/issues/14), [#15](https://github.com/bluetape4k/bluetape4k-graph/issues/15), [#41](https://github.com/bluetape4k/bluetape4k-graph/issues/41), [#188](https://github.com/bluetape4k/bluetape4k-graph/issues/188), [#189](https://github.com/bluetape4k/bluetape4k-graph/issues/189), [#190](https://github.com/bluetape4k/bluetape4k-graph/issues/190), [#191](https://github.com/bluetape4k/bluetape4k-graph/issues/191), [#192](https://github.com/bluetape4k/bluetape4k-graph/issues/192), [#193](https://github.com/bluetape4k/bluetape4k-graph/issues/193), [#196](https://github.com/bluetape4k/bluetape4k-graph/issues/196), [#197](https://github.com/bluetape4k/bluetape4k-graph/issues/197), [#198](https://github.com/bluetape4k/bluetape4k-graph/issues/198), [#199](https://github.com/bluetape4k/bluetape4k-graph/issues/199), [#201](https://github.com/bluetape4k/bluetape4k-graph/issues/201)).
- **graph-spring-boot FalkorDB nightly coverage**: full-nightly CI now covers the FalkorDB Spring Boot auto-configuration path with a live container smoke test ([#126](https://github.com/bluetape4k/bluetape4k-graph/issues/126)).

### Fixed

- **FalkorDB and Memgraph schema manager DDL fallbacks preserve cancellation**: schema manager create/drop index helper paths now rethrow `CancellationException` before applying already-exists or missing-resource message fallbacks ([#157](https://github.com/bluetape4k/bluetape4k-graph/issues/157)).
- **FalkorDB suspend graph existence checks propagate coroutine cancellation**: `FalkorDBGraphSuspendOperations.graphExists()` now rethrows `CancellationException` instead of converting cancellation into `false`, while preserving the existing fallback for ordinary driver failures ([#156](https://github.com/bluetape4k/bluetape4k-graph/issues/156)).
- **Neo4j suspend transactions no longer bridge through `runBlocking`**: `Neo4jGraphSuspendOperations.suspendTransaction()` now uses Neo4j reactive transactions, preserves rollback/cleanup semantics, and materializes returned transaction `Flow` values before commit ([#158](https://github.com/bluetape4k/bluetape4k-graph/issues/158)).
- **AGE, Memgraph, and TinkerGraph suspend transactions no longer bridge through `runBlocking`**: AGE now uses Exposed suspended transactions with a native suspend transaction scope, Memgraph uses reactive Bolt transactions, and TinkerGraph uses a suspend-aware rollback snapshot path. Cancellation rollback and returned transaction `Flow` materialization are covered by targeted tests ([#160](https://github.com/bluetape4k/bluetape4k-graph/issues/160)).
- **FalkorDB Ktor example teardown closes its driver**: the example test now closes the caller-owned driver after the PER_CLASS test lifecycle completes ([#135](https://github.com/bluetape4k/bluetape4k-graph/issues/135)).

### Changed

- Aligned release dependencies with `io.github.bluetape4k:bluetape4k-bom:1.9.0` and the latest published `io.github.bluetape4k:bluetape4k-dependencies:1.0.0`.
- Kept leaf Dependabot scoped to GitHub Actions and made Detekt the PR quality gate while Kover remains report-only ([#18](https://github.com/bluetape4k/bluetape4k-graph/issues/18), [#19](https://github.com/bluetape4k/bluetape4k-graph/issues/19)).
- Audited `graph-ktor` and examples against Ktor 3.5.0 with no API changes required ([#127](https://github.com/bluetape4k/bluetape4k-graph/issues/127)).
- Added FalkorDB Ktor example discoverability to English and Korean README module tables and converted public FalkorDB auto-configuration KDoc to English ([#133](https://github.com/bluetape4k/bluetape4k-graph/issues/133), [#134](https://github.com/bluetape4k/bluetape4k-graph/issues/134)).

---

## [0.3.0] - 2026-05-16

### Added

- **Root README refresh**: Added the generated graph workbench hero image, a fuller project overview, Mermaid architecture diagrams, supported database guidance, graph-io DAEAD notes, and the current example module/workflow surface ([PR #116](https://github.com/bluetape4k/bluetape4k-graph/pull/116)).
- **Dedicated `Examples` workflow**: Added a daily and path-triggered GitHub Actions workflow for all example modules, kept separate from Nightly coverage ([PR #112](https://github.com/bluetape4k/bluetape4k-graph/pull/112)).
- **Domain example modules**: Added fraud detection, recommendation, and knowledge graph examples on top of the stable graph API and existing backend test pattern ([#10](https://github.com/bluetape4k/bluetape4k-graph/issues/10), [PR #110](https://github.com/bluetape4k/bluetape4k-graph/pull/110)).
- **Public API KDoc examples**: Added callable English Kotlin examples across public APIs ([#16](https://github.com/bluetape4k/bluetape4k-graph/issues/16), [PR #109](https://github.com/bluetape4k/bluetape4k-graph/pull/109)).
- **`graph-okio` DAEAD streaming**: Added DAEAD chunk encryption/decryption for OkIO graph streams, including gzip+DAEAD chaining and negative-path tests for wrong associated data and truncated ciphertext ([#49](https://github.com/bluetape4k/bluetape4k-graph/issues/49), [PR #114](https://github.com/bluetape4k/bluetape4k-graph/pull/114), [PR #115](https://github.com/bluetape4k/bluetape4k-graph/pull/115)).
- **`graph-ktor`**: Added a Ktor 3.x `GraphPlugin` module and TinkerGraph-based `ktor-graph-examples`. `Application`/`ApplicationCall` extensions provide access to `GraphOperations` and `GraphSuspendOperations`, and backend helpers cover TinkerGraph, Neo4j, Memgraph, AGE, and FalkorDB ([#96](https://github.com/bluetape4k/bluetape4k-graph/issues/96)).
- **`graph-bom` README**: Documented BOM usage in English and Korean READMEs ([PR #70](https://github.com/bluetape4k/bluetape4k-graph/pull/70)).
- **`graph-okio`**: Added an OkIO-based streaming graph I/O layer with `Source`/`Sink` entry points for CSV, GraphML, and Jackson family formats ([PR #48](https://github.com/bluetape4k/bluetape4k-graph/pull/48)).
- **Weighted graph support**: Added Dijkstra/A* shortest path APIs ([PR #39](https://github.com/bluetape4k/bluetape4k-graph/pull/39)).
- **`graph-core` model builder utilities** (`graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/`)
  - `graphElementIdOf(Any)`: creates a `GraphElementId` from any type — `String`, `Long`, `Int`, and `GraphElementId` all convert safely.
  - `graphVertexOf(Any, label, properties)`: builder utility that creates a `GraphVertex` from an id, label, and property map.
  - `graphPathOf(vertices, edges)` / `graphPathOf(vertices)`: `GraphPath` builder overloads.
  - `emptyGraphPath()`: creates an empty path (replaces the former `emptyGraphPathOf()`).
  - `GraphPath.toCycle()`: extension that converts a path to a `GraphCycle`.
- **`graph-core` model test classes** (`graph/graph-core/src/test/`)
  - `GraphElementIdTest`: 4 cases for `graphElementIdOf`, including double-conversion guard.
  - `GraphVertexTest`: 6 cases for `graphVertexOf`.
  - `GraphPathTest`: 8 cases for `graphPathOf` + `emptyGraphPath`.
  - `GraphCycleTest`: 7 cases covering `toCycle()`, `length`, and equality.
- **`graph-core` README — model builder utilities section** (`graph/graph-core/README.md`, `README.ko.md`).
- **Transaction DSL first slice**: Added the `GraphOperations.transaction { }` extension and capability contract, wired to Neo4j, Memgraph, AGE, and TinkerGraph sync backends. The suspend transaction capability is wired to the same backends. FalkorDB is explicitly unsupported because its repository DSL must return intermediate results immediately.
- **`graph-core` capability docs**: Synchronized `SchemaManager`, `GraphMergeOperations`, and `GraphTransactionScope` / `GraphSuspendTransactionScope` usage examples across the root README, backend READMEs, and KDoc.
- **FalkorDB Ktor example**: Added `FalkorDBKtorGraphApp` to `ktor-graph-examples` with a full `GraphPlugin` integration test covering city-graph reset, count, and shortest-path routes ([#123](https://github.com/bluetape4k/bluetape4k-graph/issues/123), [PR #130](https://github.com/bluetape4k/bluetape4k-graph/pull/130)).
- **`graph-spring-boot` FalkorDB health indicator test**: Added `ApplicationContextRunner` test for the FalkorDB `HealthIndicator` bean and updated both `README.md` and `README.ko.md` to list `falkordb` as a supported backend value ([#125](https://github.com/bluetape4k/bluetape4k-graph/issues/125), [PR #131](https://github.com/bluetape4k/bluetape4k-graph/pull/131)).
- **License text alignment**: Aligned MIT license text across all README files ([PR #117](https://github.com/bluetape4k/bluetape4k-graph/pull/117)).
- **Test infrastructure**: Added `junit-platform.properties` and `logback-test.xml` to benchmark and graph-io modules ([PR #119](https://github.com/bluetape4k/bluetape4k-graph/pull/119)).

### Docs

- **`graph-ktor` KDoc and messages in English**: Translated all KDoc, log messages, and error strings in `GraphPlugin`, `GraphPluginConfig`, `GraphPluginState`, `ApplicationExt`, and all five backend config helpers ([#122](https://github.com/bluetape4k/bluetape4k-graph/issues/122), [PR #129](https://github.com/bluetape4k/bluetape4k-graph/pull/129)).
- **`graph-okio` README rewrite to English**: Replaced the Korean README with a full English canonical version; Korean section headers updated ([#118](https://github.com/bluetape4k/bluetape4k-graph/issues/118), [PR #129](https://github.com/bluetape4k/bluetape4k-graph/pull/129)).

### Fixed

- **`graphElementIdOf(Any)` double-`toString()` conversion bug**: passing a `GraphElementId` value back into `graphElementIdOf` previously produced `"GraphElementId(value=x)"` string corruption. Fixed with an `is GraphElementId` early-return check.
- Reinforced `AStarRunner` performance and invariant validation; cleaned up related tests and KDoc ([PR #62](https://github.com/bluetape4k/bluetape4k-graph/pull/62)).
- Fixed `ConcurrentModificationException` on concurrent `FakeFileSystem` access ([PR #53](https://github.com/bluetape4k/bluetape4k-graph/pull/53)).
- Fixed CI artifact paths and per-module Kover report paths to match the current build layout ([PR #55](https://github.com/bluetape4k/bluetape4k-graph/pull/55), [PR #56](https://github.com/bluetape4k/bluetape4k-graph/pull/56)).

### Changed

- Refreshed `WIP.md`, `AGENTS.md`, and `CLAUDE.md` to reflect the current open issue queue, Java 21 runtime, version catalog dependencies, renamed Spring Boot module, graph-okio DAEAD support, examples workflow, and active module layout.
- Renamed the Spring Boot integration from `graph-spring-boot4-starter` (`spring-boot4/graph-spring-boot4-starter`, package `io.bluetape4k.graph.spring.boot4`) to `graph-spring-boot` (`spring-boot/graph-spring-boot`, package `io.bluetape4k.graph.spring.boot`) to publish a stable version-neutral module contract ([#99](https://github.com/bluetape4k/bluetape4k-graph/issues/99)).
- Renamed `graph-io-okio` Gradle project/artifact to `graph-okio` ([#76](https://github.com/bluetape4k/bluetape4k-graph/issues/76)).
- Gradle dependency declarations migrated from `buildSrc/Libs.kt` to Version Catalog (`gradle/libs.versions.toml`) ([PR #63](https://github.com/bluetape4k/bluetape4k-graph/pull/63)).
- CI now uses paths-filter, Docker-specific jobs, and retry configuration for container-heavy workflows ([PR #68](https://github.com/bluetape4k/bluetape4k-graph/pull/68)).
- Removed the `tanvd.kosogor` plugin from the build ([PR #57](https://github.com/bluetape4k/bluetape4k-graph/pull/57)).
- `graph-io-core` test coverage raised from 65% to 93% ([PR #58](https://github.com/bluetape4k/bluetape4k-graph/pull/58)).
- Renamed `emptyGraphPathOf()` → `emptyGraphPath()` to follow Kotlin factory function conventions.
- `graphVertexOf(Any, label)` → `graphVertexOf(Any, label, properties)`: added `properties` parameter and eliminated the double-conversion path via `graphElementIdOf`.
- Removed the single-arg duplicate `graphPathOf` overload; only explicit-parameter overloads remain.
- Added missing space before colon in `AStarRunner` companion object to satisfy ktlint rules.
- Migrated test-code assertions dependency to `bluetape4k-assertions` and replaced Gradle test dependencies accordingly. Verified with `./gradlew compileTestKotlin --no-daemon` in PR #69; issue #66 closed.

---

## [0.2.0] - 2026-04-28

### Added

- **그래프 알고리즘 확장** (`graph-core` + 백엔드 구현): `pageRank`, `degreeCentrality`, `connectedComponents`, `bfs`, `dfs`, `cycles` API를 추가하고 Neo4j/Memgraph/AGE/TinkerPop 계열 구현을 정리했습니다.
- **Virtual Threads API 확장**: Vertex/Edge/Traversal repository 전체에 virtual-thread bridge adapter와 `GraphVirtualThreadOperations` 합성 API를 적용했습니다.
- **FalkorDB 백엔드**: `jfalkordb` 기반 `graph-falkordb` 구현, Spring Boot 4 auto-configuration, examples 통합을 추가했습니다.
- **`graph-io` 벌크 임포트/익스포트** (`graph-io/` 4개 모듈): 포맷별 대용량 I/O (Sync / VirtualThread / Coroutine)
  - `graph-io-core`: 공유 계약(`GraphBulkExporter`, `GraphBulkImporter`), 모델(`GraphIoVertexRecord`, `GraphIoEdgeRecord`), 옵션, 헬퍼(`GraphIoPaths`) — `BufferedOutputStream/InputStream` 래핑으로 StAX 성능 확보
  - `graph-io-csv`: CSV 임포터/익스포터 (univocity-parsers 기반) × Sync/VT/Suspend
  - `graph-io-jackson2`: Jackson 2.x NDJSON 임포터/익스포터 × Sync/VT/Suspend — 간선 버퍼링(`maxEdgeBufferSize`) 지원
  - `graph-io-jackson3`: Jackson 3.x NDJSON 임포터/익스포터 × Sync/VT/Suspend — Jackson2 NDJSON 호환
  - `graph-io-graphml`: GraphML (XML/StAX) 임포터/익스포터 × Sync/VT/Suspend — `XMLInputFactory`/`XMLOutputFactory` 싱글턴 캐싱
  - 크로스-포맷 round-trip 테스트: CSV ↔ Jackson2 ↔ Jackson3 ↔ GraphML
- **`graph-io-benchmark`** (`benchmark/graph-io-benchmark`): JMH 벤치마크 36개 메서드 (4 포맷 × 3 API × 3 연산)
  - 결과: CSV export 1.0ms, GraphML export 2.6ms, import 18–22ms (TinkerGraph in-memory 기준)
  - 결과 리포트: `docs/benchmark/2026-04-18-graph-io-bulk-results.md`

- **`graph-spring-boot3-starter` 제거**: Spring Boot 4 전용으로 정리하면서 Spring Boot 3 starter 모듈을 제거했습니다.
  - `GraphAutoConfiguration`: 루트 자동 설정 (공통 프로퍼티 바인딩)
  - `GraphNeo4jAutoConfiguration`: `@ConditionalOnClass(Neo4jGraphOperations::class)` 기반 Neo4j 빈 자동 등록 + HealthIndicator
  - `GraphMemgraphAutoConfiguration`: Memgraph 빈 자동 등록 + HealthIndicator
  - `GraphAgeAutoConfiguration`: AGE DataSource 조건부 빈 등록 + HealthIndicator
  - `GraphTinkerGraphAutoConfiguration`: TinkerGraph 인메모리 빈 자동 등록 + HealthIndicator
  - `GraphProperties`, `Neo4jGraphProperties`, `MemgraphGraphProperties`, `AgeGraphProperties`, `TinkerGraphGraphProperties`: `@ConfigurationProperties` 바인딩
  - 테스트: `ApplicationContextRunner` 기반 단위 테스트 4종 + `TinkerGraphWebMvcTest` (Virtual Threads) + `TinkerGraphWebFluxTest` (코루틴) — 총 16 passing
- **`graph-spring-boot4-starter`** (`spring-boot4/graph-spring-boot4-starter`): Spring Boot 4.0.x AutoConfiguration 스타터 신규 추가
  - Spring Boot 4 모듈 분리 대응:
    - `DataSourceAutoConfiguration`: `boot.autoconfigure.jdbc` → `boot.jdbc.autoconfigure` (`spring-boot-jdbc` 모듈)
    - `HealthIndicator`/`Health`: `boot.actuate.health` → `boot.health.contributor` (`spring-boot-health` 모듈)
    - `TestRestTemplate`: `boot.test.web.client` → `boot.resttestclient` + `@AutoConfigureTestRestTemplate` 필수 (`spring-boot-resttestclient` 모듈)
    - `WebTestClient`: `@AutoConfigureWebTestClient` 필수 (`spring-boot-webtestclient` 모듈)
  - Spring Boot 4 전용 5종 AutoConfiguration + 5종 Properties 클래스 (패키지: `boot4`)
  - 테스트: `ApplicationContextRunner` 기반 단위 테스트 4종 + `TinkerGraphWebMvcTest` + `TinkerGraphWebFluxTest` — 총 16 passing
- **GitHub Actions CI 파이프라인**: CI, integration, release, benchmark 워크플로우를 추가하고 Java 25 preview, Gradle cache, Testcontainers 기반 통합 테스트 경로를 구성했습니다.

### Changed

- **`graph-servers` 모듈 삭제**: `bluetape4k-testcontainers`의 `io.bluetape4k.testcontainers.graphdb` 패키지(`Neo4jServer.Launcher.neo4j`, `MemgraphServer.Launcher.memgraph`, `PostgreSQLAgeServer.Launcher.postgresqlAge`)로 대체. 모든 백엔드 테스트(`graph-neo4j`, `graph-memgraph`, `graph-age`, `examples`, `spring-boot4 starter`)가 새 API로 마이그레이션됨.
- **문서 / 예제 API 정합성 정리**: `AgeGraphOperations(graphName)` 생성자 패턴, `Database.connect(dataSource)` 선행 호출, `asVirtualThread` 실제 패키지 import 기준으로 README/KDoc 예제를 정리했습니다.

### Fixed

- **TinkerGraph `graphOperations()` 반환 타입**: `GraphOperations` → `TinkerGraphOperations` — `graphSuspendOperations(ops: TinkerGraphOperations)` 주입 불가 버그 수정
- **Spring Boot AutoConfig 테스트 `withBean(Supplier)` 패턴 제거**: AGE AutoConfig 테스트에서 `HikariDataSource` 공유 인스턴스가 컨텍스트 소멸 시 자동 close되는 문제를 `withUserConfiguration(DataSourceConfig::class.java)` 패턴으로 수정

---

## [0.1.0] - 2026-04-16

### Added

- **BOM 모듈** (`bluetape4k-graph-bom`): 의존성 버전 통합 관리용 Bill of Materials 추가
- **`code-graph-examples`**: 코드 의존성 그래프 예시 통합 모듈
  - `AbstractCodeGraphTest` / `AbstractCodeGraphSuspendTest`: 공통 테스트 추상 클래스
  - 백엔드별 구체 클래스: `Neo4j`, `Memgraph`, `TinkerGraph`, `AGE` × sync/suspend (총 8개)
  - `CodeGraphService` / `CodeGraphSuspendService`: 모듈 의존성, 클래스 상속, 함수 호출 체인 관리
- **`linkedin-graph-examples`**: LinkedIn 소셜 그래프 예시 통합 모듈
  - `AbstractLinkedInGraphTest` / `AbstractLinkedInGraphSuspendTest`: 공통 테스트 추상 클래스
  - 백엔드별 구체 클래스: `Neo4j`, `Memgraph`, `TinkerGraph`, `AGE` × sync/suspend (총 8개)
  - `LinkedInGraphSuspendService`: suspend/Flow 기반 LinkedIn 그래프 서비스 신규 작성
- **추상 테스트 클래스 패턴**: `ops` (`GraphOperations` / `GraphSuspendOperations`) 오버라이드만으로 모든 백엔드에서 동일한 테스트 실행 가능

### Changed

- 기존 8개 분리 모듈(`code-graph-{age,neo4j,memgraph,tinkerpop}`, `linkedin-graph-{age,neo4j,memgraph,tinkerpop}`)을 2개 통합 모듈로 합침
- `settings.gradle.kts`: `examples/` 하위 디렉토리 자동 탐색으로 모듈 등록

### Fixed

- **TinkerGraph 경로 호환성**: `shortestPath` 결과에서 `path.length` (= `edges.size`) 대신 `path.vertices.size > 1` 사용 — TinkerGraph의 경로 탐색이 정점만 반환하는 특성에 대응

---

## [0.0.1] - 2026-03-25 (Initial)

### Added

- `graph-core`: 백엔드 독립 추상 모델 및 인터페이스
  - 이중 API 패턴: `GraphOperations` (동기) + `GraphSuspendOperations` (코루틴/Flow)
  - 도메인 모델: `GraphVertex`, `GraphEdge`, `GraphPath`, `GraphElementId`
  - 스키마 DSL: `VertexLabel`, `EdgeLabel` (Exposed Table 스타일)
- `graph-age`: Apache AGE (PostgreSQL 그래프 확장) 구현
- `graph-neo4j`: Neo4j Java Driver 기반 Cypher 구현
- `graph-memgraph`: Memgraph (Neo4j 프로토콜 호환) 구현
- `graph-tinkerpop`: Apache TinkerPop / TinkerGraph 인메모리 구현
- `graph-servers`: Testcontainers 기반 테스트 서버 팩토리 (Neo4j, Memgraph, PostgreSQL+AGE)
