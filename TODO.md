# bluetape4k-graph — 향후 작업 목록

> 우선순위는 **임팩트 대비 구현 비용** 기준으로 분류했다.

---

## 1순위 — 핵심 기능 확장

### [x] 그래프 알고리즘 확장 (`graph-core` + 각 백엔드) — 2026-04-16 완료

현재 `GraphTraversalRepository`는 `neighbors`, `shortestPath`, `allPaths` 3개뿐.

| 대상 | 설명 | 백엔드 구현 방식 |
|------|------|----------------|
| `pageRank(label, iterations)` | 중요도 순위 — 추천/지식그래프 필수 | Neo4j/Memgraph 내장, AGE/Tinkerpop 직접 구현 |
| `degreeCentrality(vertexId)` | 연결 중심성 — 허브 노드 탐지 | 모든 백엔드 Cypher/Gremlin |
| `connectedComponents(label)` | 연결 컴포넌트 — 고립 클러스터 탐지 | Neo4j/Memgraph GDS, 나머지 직접 구현 |
| `bfs(startId, depth)` | 너비 우선 탐색 | 모든 백엔드 |
| `dfs(startId, depth)` | 깊이 우선 탐색 | 모든 백엔드 |
| `cycles(label)` | 순환 탐지 — 의존성 분석에 필요 | Cypher/Gremlin 직접 구현 |

### [x] `graph-spring-boot-starter` 신규 모듈 — 2026-04-17 완료

설계 문서:

- `docs/superpowers/specs/2026-04-17-spring-boot-starter-design.md`
- `docs/superpowers/plans/2026-04-17-spring-boot-starter-plan.md`

구현 범위:

- `spring-boot3/graph-spring-boot3-starter` — Spring Boot 3.5.x
- `spring-boot4/graph-spring-boot4-starter` — Spring Boot 4.0.x (분리된 모듈 패키지 대응)

Spring Boot 4 패키지 변경 사항:

- `DataSourceAutoConfiguration`: `boot.autoconfigure.jdbc` → `boot.jdbc.autoconfigure` (`spring-boot-jdbc`)
- `HealthIndicator`/`Health`: `boot.actuate.health` → `boot.health.contributor` (`spring-boot-health`)
- `TestRestTemplate`: `boot.test.web.client` → `boot.resttestclient` + `@AutoConfigureTestRestTemplate` 필수
- `WebTestClient`: `@AutoConfigureWebTestClient` 필수 (`spring-boot-webtestclient`)

테스트 결과: boot3 16 passing, boot4 16 passing

### [x] Streaming API — `Flow<T>` 반환 — 완료

`findVerticesByLabel` / `findEdgesByLabel`이 이미 `Flow<T>`를 반환한다.
별도 `stream*` 메서드 추가 불필요.

---

## 1순위 (추가) — Virtual Threads 전체 확장

### [x] Vertex/Edge/Traversal 전체에 Virtual Threads API 적용 — 2026-04-17 완료

**배경**: 그래프 알고리즘 확장(1순위)에서 `GraphVirtualThreadAlgorithmRepository` 브릿지 어댑터를 도입.
동일 패턴을 `GraphVertexRepository`, `GraphEdgeRepository`, `GraphTraversalRepository` 전체로 확대.

**방식**: 각 동기 Repository를 `Executors.newVirtualThreadPerTaskExecutor()`로 감싸는 브릿지 어댑터 + 확장 함수.
신규 인터페이스: `GraphVirtualThreadVertexRepository`, `GraphVirtualThreadEdgeRepository`, `GraphVirtualThreadTraversalRepository`
최종 합성: `GraphVirtualThreadOperations` (Java 코드 진입점).

**선행 조건**: 그래프 알고리즘 확장(1순위) 완료 후 진행.

---

## 2순위 — 생산성 향상

### [x] 문서 / 예제 API 정합성 정리 — 2026-04-18 완료

현재 코드와 일부 README 예제가 어긋난다. 신규 starter 문서화 전에 먼저 정리한다.

- `AgeGraphOperations(database, graphName = "...")` 예제를 현재 생성자 `AgeGraphOperations(graphName)` +
  `Database.connect(dataSource)` 선행 호출 패턴으로 수정
- `AgeGraphSuspendOperations` 예제도 동일하게 `AgeGraphSuspendOperations(graphName)` 기준으로 정리
- `asVirtualThread` import 예제를 실제 패키지 `io.bluetape4k.graph.vt.asVirtualThread` 기준으로 정리
- 루트 `README.md`, `README.ko.md`, `graph-age/README*.md`, `graph-servers/README*.md`,
  `graph-core/README*.md`에서 오래된 코드 조각 검색 후 수정
- README 코드 조각과 실제 테스트 코드가 컴파일 가능한 형태인지 샘플 테스트 또는 문서 스니펫 점검으로 확인

### [ ] `graph-io` 모듈 — 벌크 임포트/익스포트

| 포맷 | 방향 |
|------|------|
| CSV (정점/엣지 분리 파일) | import / export |
| JSON Lines (NDJSON) | import / export |
| GraphML | import / export |

### [ ] 트랜잭션 DSL

```kotlin
ops.transaction {
    val alice = createVertex("Person", mapOf("name" to "Alice"))
    val bob   = createVertex("Person", mapOf("name" to "Bob"))
    createEdge("KNOWS", alice.id, bob.id)
} // 실패 시 롤백
```

### [ ] `graph-benchmark` 모듈 — JMH 벤치마크

- 정점 삽입 1만건, 최단경로 100쌍, 이웃 탐색 1홉/3홉 — 백엔드별 비교
- `docs/graphdb-tradeoffs.md` 실측 수치 보강

### [ ] 동기 / Virtual Threads / Coroutines API 벤치마크

알고리즘 확장으로 추가된 3가지 실행 모델의 처리량·지연 시간 비교 (JMH, TinkerGraph 인메모리 기준).

| 측정 항목 | 설명 |
|-----------|------|
| `pageRank` throughput | 동기 vs `asVirtualThread()` vs `Flow` 수집 |
| `bfs` latency (depth=5) | 3가지 API 왕복 시간 |
| 동시 요청 처리량 | VT 100-way 병렬 vs 코루틴 `async` 100-way |
| 스레드/메모리 사용량 | Virtual Thread 생성 비용 측정 |

- 측정 대상: `TinkerGraphOperations` (Docker 불필요)
- 결과 기록: `docs/graphdb-tradeoffs.md` API 모델 비교 섹션 추가

---

## 2순위 (추가) — 빌드 / CI 개선

### [x] GitHub Actions CI 파이프라인 구성 — 2026-04-18 완료

`.github/workflows/` 아래에 워크플로우 파일 추가.

| 워크플로우 파일 | 트리거 | 역할 |
|----------------|--------|------|
| `ci.yml` | `push` / `pull_request` → `main`, `develop` | 전체 빌드 + 단위 테스트 (Docker 없는 모듈만) |
| `integration.yml` | `push` → `main`, 수동 `workflow_dispatch` | Testcontainers 통합 테스트 (Neo4j·Memgraph·AGE) |
| `release.yml` | `push` → `v*` 태그 | Maven Central 배포 (`publishAggregationToCentralPortal`) |
| `benchmark.yml` | 수동 `workflow_dispatch` | JMH 벤치마크 실행 + 결과 아티팩트 업로드 |

**공통 설정:**

- JDK: `temurin` 25 (preview 기능 포함)
- Gradle: `gradle/wrapper/gradle-wrapper.properties` 버전 고정 + `--build-cache` 활용
- Testcontainers: `testcontainers.reuse.enable=true` + Docker Layer Caching
- Gradle 캐시: `actions/cache` → `~/.gradle/caches`, `~/.gradle/wrapper`
- 실패 시 `--continue` 옵션으로 모듈별 결과 분리 리포트

**시크릿 관리 (GitHub Secrets):**

| 시크릿 키 | 용도 |
|-----------|------|
| `MAVEN_CENTRAL_USERNAME` | Sonatype OSSRH 계정 |
| `MAVEN_CENTRAL_PASSWORD` | Sonatype OSSRH 비밀번호 |
| `GPG_SIGNING_KEY` | 배포 서명용 GPG 키 |
| `GPG_SIGNING_PASSPHRASE` | GPG 키 패스프레이즈 |

### [ ] 빌드 캐시 최적화

- `gradle.properties`에 `org.gradle.caching=true` 활성화
- `buildSrc` 결과물 캐시 구성 (`--build-cache` CI 플래그)
- 모듈별 `test` 태스크에 `outputs.upToDateWhen { false }` 제거 → 점진적 빌드 활용
- Testcontainers 이미지 pull을 CI 캐시 레이어로 분리 (Docker layer cache)

### [ ] 코드 품질 게이트 CI 연동

| 도구 | 역할 | CI 단계 |
|------|------|---------|
| `detekt` | 정적 분석 (코틀린 코드 스멜) | `ci.yml` — PR 블로킹 |
| `ktlint` | 코드 스타일 검사 | `ci.yml` — PR 블로킹 |
| Codecov / JaCoCo | 테스트 커버리지 리포트 | `ci.yml` — 80% 미만 시 경고 |
| `dependency-check` (OWASP) | 취약 의존성 스캔 | `release.yml` — 배포 전 필수 통과 |

### [ ] Dependabot / Renovate 자동 의존성 업데이트

- `.github/dependabot.yml` 추가 — Gradle 의존성 주간 업데이트 PR 자동 생성
- `buildSrc/Libs.kt` 버전 상수와 연동되도록 Renovate `customManagers` 설정 검토
- `spring-boot3` / `spring-boot4` BOM 업데이트 PR 분리 관리

---

## 3순위 — 생태계 확장

### [ ] 추가 예시 모듈

| 예시 | 핵심 알고리즘 |
|------|-------------|
| `fraud-detection-examples` | 순환 탐지, 커뮤니티 탐지 |
| `recommendation-examples` | 공통 이웃, 협업 필터링 |
| `knowledge-graph-examples` | 엔티티 링킹, 경로 추론 |

### [ ] 추가 백엔드

| 백엔드 | 이유 |
|--------|------|
| Amazon Neptune | AWS 환경, Bolt 호환으로 구현 비용 낮음 |
| FalkorDB (구 RedisGraph) | Redis 기반 인메모리, Bolt 지원 |

---

## 완료

- [x] 문서/예제 API 정합성 정리 — AgeGraphOperations 생성자 패턴 + asVirtualThread import 수정 (2026-04-18)
- [x] GitHub Actions CI (`ci.yml` + `publish-snapshot.yml`) — push마다 전체 테스트, nightly SNAPSHOT 배포 (2026-04-18)
- [x] Spring Boot 3/4 AutoConfiguration 스타터 — boot3 16 passing, boot4 16 passing (2026-04-17)
- [x] 그래프 알고리즘 확장 — 6 알고리즘 × 4 백엔드 + VT bridge (2026-04-16)
- [x] Virtual Threads 전체 확장 — Vertex/Edge/Traversal/Operations 어댑터 + 테스트 (2026-04-17)
- [x] 0.1.0 Maven Central 배포 (2026-04-16)
- [x] GitHub Release v0.1.0 작성
- [x] 전체 public API KDoc 커버리지 (199개 예제)
- [x] 추상 테스트 클래스 패턴 (백엔드 전환 용이)
- [x] code-graph-examples, linkedin-graph-examples 통합 모듈
