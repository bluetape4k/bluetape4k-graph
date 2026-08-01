# WIP - bluetape4k-graph

스냅샷: 2026-08-01 KST
범위: `debop`에게 배정된 열린 GitHub 이슈
열린 배정 이슈 수: 10개

## 현재 방향

`0.5.0` 안정 라인은 유지보수 기준으로 보존한다.
현재 개발 라인은 `0.6.0`이며, 이 WIP 큐는 진행 중인 7-Tier 리뷰 흐름과
아직 열린 이전 backlog 항목을 추적한다. 이번 review train에서는 #298/#398
작업과 #437/#440 Epic 및 관련 subissue를 닫고, #400 문서 Epic도 완료했다.
세 PR을 `develop`에 병합했으며 post-merge CI/Examples와 graph-core 검증도
통과했다. 별도 Epic이나 subissue는 등록하지 않았다.

## 완료된 `0.6.0` review train

| 구분 | 이슈 | 마일스톤 | 라벨 | 메모 |
|---|---|---|---|---|
| CI | [#298](https://github.com/bluetape4k/bluetape4k-graph/issues/298) gitleaks release asset install hardening | 0.6.0 | ci, github_actions | CLOSED; PR #448이 `8e1c13e`로 병합됐다. |
| API | [#398](https://github.com/bluetape4k/bluetape4k-graph/issues/398) typed graph endpoint validation helpers | 0.6.0 | enhancement | CLOSED; PR #449가 `5682e56`으로 병합됐다. |
| Docs | [#400](https://github.com/bluetape4k/bluetape4k-graph/issues/400) Korean documentation and KDoc rewrite Epic | 0.6.0 | documentation, Epic | CLOSED; 문서/KDoc 작업 train이 완료됐다. |
| CI | [#437](https://github.com/bluetape4k/bluetape4k-graph/issues/437) CI quality and security gates Epic | 0.6.0 | Epic, ci | CLOSED; PR #447이 `8c8bac0`으로 병합됐다. |
| CI | [#438](https://github.com/bluetape4k/bluetape4k-graph/issues/438) CodeQL Kotlin catalog pin | 0.6.0 | bug, ci, github_actions | CLOSED; #437 train에서 형식 허용 범위를 고정했다. |
| CI | [#439](https://github.com/bluetape4k/bluetape4k-graph/issues/439) Detekt gate recovery | 0.6.0 | bug, documentation, ci | CLOSED; #437 train에서 gate 복구를 검증했다. |
| Contracts | [#440](https://github.com/bluetape4k/bluetape4k-graph/issues/440) graph contract and ABI Epic | 0.6.0 | bug, test, Epic | CLOSED; graph contract train이 완료됐다. |
| Contracts | [#441](https://github.com/bluetape4k/bluetape4k-graph/issues/441) graph-core coroutine Flow compile contract | 0.6.0 | bug, test | CLOSED; 외부 consumer compile smoke를 통과했다. |
| Contracts | [#442](https://github.com/bluetape4k/bluetape4k-graph/issues/442) named graph deletion safety | 0.6.0 | bug, test | CLOSED; fail-closed 및 lifecycle lock 회귀를 고정했다. |
| Contracts | [#443](https://github.com/bluetape4k/bluetape4k-graph/issues/443) sync/suspend graphExists failure contract | 0.6.0 | bug, test | CLOSED; failure/cancellation 전파 계약을 고정했다. |
| Contracts | [#444](https://github.com/bluetape4k/bluetape4k-graph/issues/444) GraphPath serialization contract | 0.6.0 | bug, test | CLOSED; 중첩 property와 실패 계약을 고정했다. |
| Docs | [#445](https://github.com/bluetape4k/bluetape4k-graph/issues/445) Korean `Fixed` terminology review | 0.6.0 | — | CLOSED; canonical English changelog에는 source change가 필요하지 않았다. |

## 이전 큐 스냅샷 (2026-07-04)

| 우선순위 | 이슈 | 마일스톤 | 라벨 | 메모 |
|---|---|---|---|---|
| P0 | [#319](https://github.com/bluetape4k/bluetape4k-graph/issues/319) bug(graph-ktor): do not close caller-owned backend resources | 0.6.0 | bug | PR #343 열림. 스택의 루트 이슈다. |
| P0 | [#320](https://github.com/bluetape4k/bluetape4k-graph/issues/320) bug(graph-spring-boot): qualify Memgraph Driver beans | 0.6.0 | bug | PR #344 열림. |
| P0 | [#321](https://github.com/bluetape4k/bluetape4k-graph/issues/321) bug(graph-spring-boot): guard optional auto-config classpath boundaries | 0.6.0 | bug | PR #345 열림. |
| P0 | [#322](https://github.com/bluetape4k/bluetape4k-graph/issues/322) bug(graph-io-okio): abort atomic writes when wrapper setup fails | 0.6.0 | bug | PR #346 열림. |
| P0 | [#323](https://github.com/bluetape4k/bluetape4k-graph/issues/323) bug(graph-io-ndjson): stream suspend imports and capture envelope validation failures | 0.6.0 | bug | PR #347 열림. |
| P1 | [#324](https://github.com/bluetape4k/bluetape4k-graph/issues/324) refactor(graph-io): keep suspend graph operations off Dispatchers.IO in CSV and GraphML | 0.6.0 | refactoring | PR #348 열림. |
| P0 | [#325](https://github.com/bluetape4k/bluetape4k-graph/issues/325) bug(graph-io-graphml): report invalid typed GraphML values | 0.6.0 | bug | PR #349 열림. |
| P1 | [#326](https://github.com/bluetape4k/bluetape4k-graph/issues/326) refactor(graph-tinkerpop): replace synchronized graph critical sections | 0.6.0 | refactoring | PR #350 열림. |
| P1 | [#327](https://github.com/bluetape4k/bluetape4k-graph/issues/327) test(examples): make suspend cleanup and lifecycle patterns consistent | 0.6.0 | test, example | PR #351 열림. |
| P1 | [#328](https://github.com/bluetape4k/bluetape4k-graph/issues/328) test(graph): migrate exception assertions to bluetape4k helpers | 0.6.0 | test, refactoring | PR #352 열림. |
| P2 | [#329](https://github.com/bluetape4k/bluetape4k-graph/issues/329) docs(graph-io): add README language switches | 0.6.0 | documentation | PR #353 열림. |
| P2 | [#330](https://github.com/bluetape4k/bluetape4k-graph/issues/330) docs(graph-spring-boot): fix README switch and English public KDoc | 0.6.0 | documentation | PR #354 열림. |
| P0 | [#331](https://github.com/bluetape4k/bluetape4k-graph/issues/331) bug(graph-backends): do not mask traversal and cycle-detection failures | 0.6.0 | bug | PR #355 열림. |
| P0 | [#332](https://github.com/bluetape4k/bluetape4k-graph/issues/332) bug(graph-backends): do not report graphExists=false on infrastructure failures | 0.6.0 | bug | PR #356 열림. |
| P0 | [#333](https://github.com/bluetape4k/bluetape4k-graph/issues/333) bug(graph-age): narrow createGraph duplicate handling | 0.6.0 | bug | PR #357 열림. |
| P0 | [#334](https://github.com/bluetape4k/bluetape4k-graph/issues/334) bug(graph-core): enforce nonblank GraphElementId invariant | 0.6.0 | bug | PR #358 열림. |
| P1 | [#335](https://github.com/bluetape4k/bluetape4k-graph/issues/335) test(graph-falkordb): move raw GenericContainer fixture behind shared launcher | 0.6.0 | test, refactoring | PR #359 열림. |
| P2 | [#336](https://github.com/bluetape4k/bluetape4k-graph/issues/336) docs(graph-core): convert public API KDoc to English | 0.6.0 | documentation | PR #360 열림. |
| P2 | [#337](https://github.com/bluetape4k/bluetape4k-graph/issues/337) docs(repo): refresh README commands and version references | 0.6.0 | documentation | PR #361 열림. |
| P2 | [#338](https://github.com/bluetape4k/bluetape4k-graph/issues/338) docs(repo): refresh WIP issue queue from live GitHub state | 0.6.0 | documentation | 현재 WIP 갱신 대상이다. |
| P1 | [#339](https://github.com/bluetape4k/bluetape4k-graph/issues/339) ci(graph): fail coverage aggregation when expected Kover reports are missing | 0.6.0 | bug, ci | 다음 CI hardening 항목이다. |
| P1 | [#340](https://github.com/bluetape4k/bluetape4k-graph/issues/340) ci(graph): include graph-io Kover XML tasks in nightly coverage | 0.6.0 | ci | #339 coverage signal 작업의 후속이다. |
| P1 | [#341](https://github.com/bluetape4k/bluetape4k-graph/issues/341) ci(benchmark): render all benchmark JSON outputs with chart artifacts | 0.6.0 | performance, ci | benchmark artifact 정리 항목이다. |
| P1 | [#342](https://github.com/bluetape4k/bluetape4k-graph/issues/342) build(repo): remove duplicated centrally governed catalog versions | 0.6.0 | build, dependencies | catalog governance 정리 항목이다. |
| P3 | [#298](https://github.com/bluetape4k/bluetape4k-graph/issues/298) ci: harden gitleaks release asset install | backlog | ci, github_actions | 이전 CI backlog 항목이다. |
| P3 | [#310](https://github.com/bluetape4k/bluetape4k-graph/issues/310) feat(graph-io): add checkpoint and resume support for large imports | backlog | enhancement, performance | 향후 graph-io 기능이다. |
| P3 | [#311](https://github.com/bluetape4k/bluetape4k-graph/issues/311) feat(graph-io): add bulk I/O progress listeners and Micrometer bridge | backlog | enhancement, performance | 향후 graph-io observability 기능이다. |
| P3 | [#312](https://github.com/bluetape4k/bluetape4k-graph/issues/312) feat(graph-io): define backend-native bulk loader SPI | backlog | enhancement, performance | 별도 SPI 설계가 필요하다. |
| P3 | [#313](https://github.com/bluetape4k/bluetape4k-graph/issues/313) feat(graph-io): add streaming import reader parity across formats | backlog | enhancement, performance | 향후 graph-io parity 항목이다. |
| P3 | [#314](https://github.com/bluetape4k/bluetape4k-graph/issues/314) test(graph): add cross-backend conformance suite for graph capabilities | backlog | enhancement, test | 향후 conformance suite 항목이다. |
| P3 | [#315](https://github.com/bluetape4k/bluetape4k-graph/issues/315) feat(graph-core): add schema drift planner for indexes and constraints | backlog | enhancement | 향후 schema planning 기능이다. |
| P3 | [#316](https://github.com/bluetape4k/bluetape4k-graph/issues/316) feat(spring-boot): add Actuator graph management endpoint | backlog | enhancement | 향후 Spring management 표면이다. |
| P3 | [#317](https://github.com/bluetape4k/bluetape4k-graph/issues/317) feat(graph-io): add multi-source import workflow for distributed graph datasets | backlog | enhancement, performance | 향후 graph-io workflow 기능이다. |
| P3 | [#215](https://github.com/bluetape4k/bluetape4k-graph/issues/215) research: revalidate Amazon Neptune backend feasibility | backlog | enhancement, research | #30을 되살리기 전에 필요하다. |
| P3 | [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) [Epic] Amazon Neptune graph DB backend implementation (graph-neptune) | backlog | invalid, Epic, research | invalid/research 상태인 동안 차단 상태로 둔다. mock만으로 구현하지 않는다. |

## 최근 완료

- `0.5.0` Ktor managed backend DSL 작업은 Neo4j, Memgraph, FalkorDB, Apache AGE
  DataSource 소유권 경로까지 완료되었다.
- `0.5.0` domain example suite는 observability, IAM access path, supply-chain
  impact, data lineage, network topology, security attack path까지 완료되었다.
- [#234](https://github.com/bluetape4k/bluetape4k-graph/issues/234)
  backend-native bulk loader 연구가 문서화되었다. 권장 사항은
  Neo4j/Memgraph/AGE/FalkorDB native fast path를 `0.6.0` 구현 lane에서
  제외하고, TinkerPop/TinkerGraph를 native-loader lane에서 배제하며,
  #233을 다음 별도 graph-io 구현 PR로 유지하는 것이다.
- [#233](https://github.com/bluetape4k/bluetape4k-graph/issues/233)
  chunked graph export cursor API가 TinkerGraph reference path와 Jackson3
  NDJSON exporter proof까지 구현되었다.
- 루트 README의 영어/한국어 module 목록과 example test 명령은 현재 Gradle
  project name 및 version catalog에 맞게 갱신되었다.

## 검증 증거

- 원본 명령:
  `gh issue list --assignee debop --state open --limit 100 --json number,title,url,labels,milestone`
- 열린 배정 이슈: 10개
- `0.6.0` milestone: 열린 issue 0개, 열린 PR 0개
- 병합된 PR: #447 (`8c8bac0`), #448 (`8e1c13e`), #449 (`5682e56`)
- hosted CI/Examples run `30697376313`/`30697376301`: 모두 `success`
- local graph-core test/compile: `335 passing`, `BUILD SUCCESSFUL`
- 이번 review에서 새 Epic/subissue를 등록하지 않았다.

## 범위 메모

- 현재 7-Tier review train과 가까운 CI/build hardening 작업에는 `0.6.0`을
  사용한다.
- backend-native loader SPI와 large-import workflow 항목은 별도 설계와 범위가
  잡히기 전까지 backlog에 둔다.
- Neptune 작업은 로컬 또는 신뢰 가능한 integration test 가능성이 입증될 때까지
  backlog에 둔다.
