# WIP - bluetape4k-graph

기준 시점: 2026-08-26 KST
범위: `1.0.0` milestone closeout 및 `backlog` 후속 큐

## 현재 상태

- local `develop`와 `origin/develop`은 `06f8f256e2f303556b9ca101d6778d7222dd33a5`에서 일치한다.
- 열린 PR은 0개이며, 열린 issue는 backlog인 [#215](https://github.com/bluetape4k/bluetape4k-graph/issues/215)와 [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30)뿐이다.
- `1.0.0` closeout 범위의 [#534](https://github.com/bluetape4k/bluetape4k-graph/issues/534)~[#563](https://github.com/bluetape4k/bluetape4k-graph/issues/563)와 선행 [#525](https://github.com/bluetape4k/bluetape4k-graph/issues/525)~[#527](https://github.com/bluetape4k/bluetape4k-graph/issues/527)은 모두 종료되었다. 대응 PR #530~#532와 #564~#593도 모두 MERGED 상태다.
- graph-core, graph-io, AGE, TinkerGraph, Spring Boot, examples, CI 계약을 포함한 repository-side stacked train은 완료되었다. 완료된 worktree와 local train branch는 정리했으며 루트 worktree만 남아 있다.
- 최신 GitHub release는 `0.6.0`(2026-08-05)이다. `baseVersion=1.0.0`은 개발 기준선이며 `1.0.0` tag/release는 아직 만들지 않았다.
- [#563](https://github.com/bluetape4k/bluetape4k-graph/issues/563)의 upstream 지원 [bluetape4k-projects#1523](https://github.com/bluetape4k/bluetape4k-projects/pull/1523)은 MERGED 되었지만, 새 upstream artifact를 소비하는 graph downstream 검증은 해당 artifact가 배포될 때까지 PENDING이다.

## 다음 backlog

| 이슈 | 상태 | 메모 |
|---|---|---|
| [#215](https://github.com/bluetape4k/bluetape4k-graph/issues/215) | backlog | Amazon Neptune backend feasibility를 local 또는 신뢰 가능한 integration test 가능성부터 재검증한다. |
| [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) | backlog | #215의 feasibility 결과 전까지 `graph-neptune` 구현을 시작하지 않는다. |

<details>
<summary>병합 전 stacked train 기록 (historical)</summary>

2026-08-25~26의 closeout train은 issue dependency order에 따라 적층하고
각 slice의 exact-head·CI·7-Tier evidence를 확인한 뒤 병합했다. 당시의 상세
receipt와 review 기록은 Git history 및 연결된 GitHub issue/PR에서 확인할 수
있다.

</details>

## 이전 `0.7.0` 진행 기록

이 절의 상태와 서술은 해당 release line의 당시 기록을 보존한 historical
기록이다. 현재 live 상태는 위의 `1.0.0` closeout과 `backlog` 표를 따른다.

| 이슈 | 상태 | 메모 |
|---|---|---|
| [#463](https://github.com/bluetape4k/bluetape4k-graph/issues/463) | 병합 완료 | PR #497이 `d5366325dd2a126a3f52f55800911776e64c9de6`으로 병합됐고, 병합 후 AGE/Neo4j/Memgraph targeted 회귀 테스트를 통과했다. |
| [#464](https://github.com/bluetape4k/bluetape4k-graph/issues/464) | 구현·검증 완료, PR 준비 | 세 wrapper의 여섯 read cache를 Caffeine bounded/expiring cache로 전환하고 `maxSize`/`expireAfterWrite` 양수 검증, eviction/expiration 회귀 테스트와 영문/국문 문서를 정렬했다. |
| [#499](https://github.com/bluetape4k/bluetape4k-graph/issues/499) | 구현·검증 완료, PR 준비 | 세 wrapper의 generation guard, `dropGraph` 무효화, transaction commit/rollback 경계를 추가하고 동시 miss/write 회귀 테스트와 영문/국문 문서를 정렬했다. 세 모듈 전체 테스트·detekt·Dokka도 통과했다. |
| [#470](https://github.com/bluetape4k/bluetape4k-graph/issues/470) | 구현·검증 완료, PR 준비 | Virtual Thread graph-io bulk adapter가 동기 delegate의 `close()`를 wrapper에서 최대 한 번 전파하도록 고정하고 source/sink 소유권 및 비동기 작업 중 close 정책을 KDoc과 회귀 테스트로 명시했다. graph-io core 131개 테스트·detekt·Kover check를 통과했다. |
| [#312](https://github.com/bluetape4k/bluetape4k-graph/issues/312) | PR #481 검증 대기 | `graph-io-core` additive native loader SPI와 128개 core 테스트(21개 targeted) 통과. TinkerPop/TinkerGraph는 서버 native command/staging semantics가 없어 제외하며, 실제 backend adapter/Testcontainers는 후속 범위다. |
| [#313](https://github.com/bluetape4k/bluetape4k-graph/issues/313) | PR #482 CI 성공, 병합 승인 대기 | CSV/Jackson2/Jackson3/GraphML/OkIO streaming reader와 ownership·cancellation·safe failure contract를 추가했다. 여섯 graph-io module의 fresh test/compile/detekt와 hosted graph/Examples CI가 통과했으며, P0/P1=0·P2=4 후속 범위를 review 문서에 기록했다. |

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

## 이전 큐 기준 시점 (2026-07-04)

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
| P2 | [#338](https://github.com/bluetape4k/bluetape4k-graph/issues/338) docs(repo): refresh WIP issue queue from live GitHub state | 0.6.0 | documentation | 당시 WIP 갱신 대상이었다. |
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

## 과거 완료 기록

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

## 최신 검증 증거

- live GitHub 조회(2026-08-26 KST): 열린 PR 0개; 열린 issue는 backlog인 #215와
  #30뿐이다.
- local `develop`와 `origin/develop`은 `06f8f256e2f303556b9ca101d6778d7222dd33a5`에서
  일치하며, 마지막 docs cleanup PR #593도 이 head에 병합되었다.
- `1.0.0` closeout train의 graph PR #564~#592는 모두 MERGED이고, 선행 PR
  #530~#532도 병합 상태다. 해당 범위의 issue는 모두 CLOSED다.
- upstream `bluetape4k-projects#1523`은 `2026-08-26`에 MERGED 되었지만,
  새 artifact 배포 전 graph downstream 소비 검증은 PENDING이다.
- worktree 정리 후 linked worktree는 0개이고 루트 worktree 1개만 남아 있으며,
  완료 train의 local branch는 제거했다.

## 당시 검증 증거 (`0.6.0` review)

- 원본 명령:
  `gh issue list --assignee debop --state open --limit 100 --json number,title,url,labels,milestone`
- 열린 배정 이슈: 10개
- `0.6.0` milestone: 열린 issue 0개, 열린 PR 0개
- 병합된 PR: #447 (`8c8bac0`), #448 (`8e1c13e`), #449 (`5682e56`)
- hosted CI/Examples run `30697376313`/`30697376301`: 모두 `success`
- local graph-core test/compile: `335 passing`, `BUILD SUCCESSFUL`
- 이번 review에서 새 Epic/subissue를 등록하지 않았다.

## 현재 및 과거 범위 메모

- 현재 `1.0.0` closeout 이후 작업은 위 `backlog` 표의 #215 feasibility와
  #30 blocked Epic을 기준으로 시작한다.
- Neptune 작업은 로컬 또는 신뢰 가능한 integration test 가능성이 입증될 때까지
  backlog에 둔다.
- `0.6.0` review train의 CI/build hardening, backend-native loader SPI,
  large-import workflow 항목은 당시 범위와 후속 backlog 기록으로 보존한다.
