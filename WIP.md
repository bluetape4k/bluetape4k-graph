# WIP - bluetape4k-graph

스냅샷: 2026-08-26 KST
범위: `1.0.0` milestone closeout 및 `backlog` 후속 큐
현재 live 상태: #543은 병합 완료, #544(PR #565)는 stacked train에서 검증 대기,
#545(PR #566)는 #544 exact head 위에서 로컬 검증을 완료하고 hosted 검증을 대기 중이며,
#546(PR #567)은 #545 exact head 위에서 7개 예제 모듈의 suspend teardown을 정렬하고 hosted 검증을 대기 중이며,
#547(PR #568)은 #546 exact head 위에서 catalog ownership과 retry-only CI evidence를 정렬했고 hosted exact-head checks가 모두 통과했으며 review·최종 merge를 대기 중이다.
#536은 #547 exact head 위에 bounded capability contract를 적층한 PR #569의 hosted checks가 모두 통과했으며 review·최종 merge를 대기 중이다. #548은 #536 exact head 위의 PR #570에서 close-aware TinkerGraph lifecycle과 cursor README를 정렬했고 hosted checks·독립 review를 대기 중이다. #549는 #548 exact head 위의 PR #571에서 enum compatibility policy를 정비했다. #535는 #549 PR #571 exact head 위의 PR #572에서 AGE suspend Flow 선행 계약을 적층했고 hosted 검증·리뷰를 대기 중이다. 후속 `1.0.0` issue는 dependency 순서를 따라 같은 train에
순차적으로 쌓고 최종 일괄 merge 승인 전까지 병합하지 않는다. 현재 train은
#550까지 이어지며, #550은 #535 PR #572 exact head 위의 PR #573에서 fault-injection
검증을 적층했고 hosted 검증·리뷰를 대기 중이다.
#551은 #550 PR #573 exact head `186ea8af18192d8fe1e8024bc78cc80b7f235bc1` 위에
suspend transaction 중첩 `Flow` 결과 계약을 적층한 PR #574의 live head
`130532a2c2f0be2e9c87572ed6876bbb688afa06`에서 hosted 검증·리뷰를 대기 중이다.
#552는 이 exact head 위에 AGE JDBC `Statement.cancel()` 수명주기와
`executeQuery()`/`ResultSet.next()` blocking 취소 회귀를 적층한 PR #575에서
검증을 완료했다. 최신 source commit `5a21d911`은 실행 전 cancellation race와
cancel 실패 관찰을 보정했고, targeted 3/3·AGE 전체 198/198·Detekt와 PR #575
exact-head CI·Examples checks를 통과했다.
표준 JDBC의 `IDLE → IN_QUERY` handoff는 positive `defaultQueryTimeout` 또는
vendor API가 필요한 조건부 계약으로 기록했으며, 최종 일괄 merge 승인 전에는
모든 PR을 독립 병합하지 않는다.
#538은 PR #575 exact head `941c822e40f670ae8d856fad893f0922ae5d8a0d` 위에
GraphImportJobStateStore atomic update와 sync/suspend writer `batchSize`
검증을 적층한 PR #576의 exact head
`112703d4752fa5dad6f25cef5a53328cd6712bfa`에서 hosted 검증을 완료했다.
#553은 이 exact head 위에서 report payload 보존을 적층 중이며 candidate는
implementation commit은 `88cc9676dfbbd48c7b700c51854f7380ee6fe07a`,
설계·리뷰·WIP receipt는 `09ceee92`다.
최신 GitHub release: `0.6.0` (2026-08-05); 현재 개발 기준선은 `1.0.0`이다.

## 최근 완료 및 현재 `1.0.0` stacked train

| 이슈 | 상태 | 메모 |
|---|---|---|
| [#527](https://github.com/bluetape4k/bluetape4k-graph/issues/527) | 병합 완료 | stacked train 첫 slice인 PR #530이 `a234d7cfd0cd41381982720d06395e5b51226702`로 병합되고 이슈가 닫혔다. |
| [#525](https://github.com/bluetape4k/bluetape4k-graph/issues/525) | 병합 완료 | PR #531이 `a74d735a7eee98ebba258d2ad909290f183dc041`로 병합되고 이슈가 닫혔다. 선택적 Spring bridge와 기존 graph property alias를 적용했다. |
| [#526](https://github.com/bluetape4k/bluetape4k-graph/issues/526) | 병합 완료 | PR #532가 `57d3348acb2b5cad1ff3b5e03737cba704fc567a`로 병합되고 이슈가 닫혔다. `neo4j`, `memgraph`, `age`, `falkordb` manifest 기반 changed/full startup·workload gate와 fail-closed release gate를 완성했다. |
| [#543](https://github.com/bluetape4k/bluetape4k-graph/issues/543) | 병합 완료 | PR #564가 `a553a423271eb99cd69a6ce8bb88d034b27923d2`로 병합되고 이슈가 닫혔다. |
| [#544](https://github.com/bluetape4k/bluetape4k-graph/issues/544) | PR #565 검증 대기 | `fix/issue-544-neo4j-weighted-assertions` exact head 위에 #545를 쌓는다. 최종 merge 승인 전에는 병합하지 않는다. |
| [#545](https://github.com/bluetape4k/bluetape4k-graph/issues/545) | PR #566 검증 대기 | graph management 상태 요약, AGE initializer 예외 경계, Spring assertion migration을 한 slice로 정렬했고 로컬 test/Detekt/Kover를 통과했다. |
| [#546](https://github.com/bluetape4k/bluetape4k-graph/issues/546) | PR #567 검증 대기 | 7개 suspend backend teardown에서 CancellationException을 재전파하고 일반 drop 실패만 기록하며 driver close를 finally에서 보장했다. 7개 예제 테스트 311개가 순차 통과했다. exact head는 `75e45556f22994bf46b8aaab297747845669e0e4`이다. |
| [#547](https://github.com/bluetape4k/bluetape4k-graph/issues/547) | PR #568 hosted 통과, review·merge 대기 | #567 exact head `75e45556f22994bf46b8aaab297747845669e0e4` 위에서 local `bluetape4k` alias를 제거하고 examples/core retry helper가 첫 실패·retry-only 상태를 보존하도록 정렬했다. evidence root·`tee`·output write failure도 fail-closed로 고정하고 helper/test path routing, `fix/**` stacked PR trigger와 필수 `retry-helper` CI job을 추가했다. PR #568 live head `eda9c433a7004ab91e96e0f8ea8ecade0e1fa68a`에서 CI·Examples checks가 모두 통과했다. 최종 train merge는 마지막 승인 단계에서 수행한다. |
| [#536](https://github.com/bluetape4k/bluetape4k-graph/issues/536) | PR #569 hosted 통과, review·merge 대기 | `BOUNDED_CHUNKED_READ/EXPORT`와 `GraphBoundedChunkOperations` marker를 추가해 API chunking과 source bounded 실행을 분리하고 TinkerGraph만 bounded capability를 광고하도록 정렬했다. graph-core 351개, graph-tinkerpop 114개, GraphML 44개 테스트와 세 모듈 Detekt, AGE→Neo4j→Memgraph→FalkorDB 순차 conformance가 통과했으며 PR #569 hosted checks가 모두 통과했다. #548이 이 exact head 위에 쌓였다. |
| [#548](https://github.com/bluetape4k/bluetape4k-graph/issues/548) | PR #570 검증 대기 | #536 exact head 위에서 기존 `Sequence` ABI를 유지한 additive close-aware vertex/edge cursor와 suspend Flow `finally` close를 구현했다. TinkerGraph sync/suspend targeted 67개, compile, Detekt, public `javap` ABI 확인과 AGE→Neo4j→Memgraph→FalkorDB 순차 conformance가 통과했고 internal factory JVM surface는 `@JvmSynthetic`으로 숨겼다. 모듈 EN/KO README에 cursor use와 legacy Sequence 제한을 기록했으며 hosted checks·독립 review와 최종 train merge를 대기 중이다. #549가 이 exact head 위에 쌓였다. |
| [#549](https://github.com/bluetape4k/bluetape4k-graph/issues/549) | PR #571 검증 대기 | graph-core와 주요 소비 모듈의 `GraphCapability` exhaustive consumer를 inventory하고, enum ordinal/name 보존·외부 `else` unknown policy·`fromSerializedNameOrNull` forward-compatible parser를 추가했다. graph-core 352개 test, Detekt, 순차 capability conformance와 문서·release evidence를 완료했다. 전체 train merge는 마지막 승인 단계에서만 수행한다. |
| [#535](https://github.com/bluetape4k/bluetape4k-graph/issues/535) | PR #572 검증 대기 | #549 PR #571 exact head 위에서 AGE suspend direct Flow의 `Dispatchers.IO`·cursor·positive fetch size·`maxAttempts=1`·cancellation cleanup 계약을 고정했다. AGE targeted/full test 29/191, compile, Detekt, 문서·7-Tier evidence를 재검증했고 PR #572 hosted 검증·리뷰를 대기 중이다. #550은 이 PR의 exact head 위에 쌓는다. |
| [#550](https://github.com/bluetape4k/bluetape4k-graph/issues/550) | PR #573 검증 대기 | #535 PR #572 exact head 위에서 실제 `PreparedStatement.fetchSize` `8`/fallback `100`과 late `SQLException` 단일 시도·prefix 비중복을 JDBC proxy fault injection으로 관찰했다. AGE targeted/full 32/194, compile, Detekt, 7-Tier review·lesson evidence를 완료했고 PR #573 hosted 검증·리뷰를 대기 중이다. |
| [#551](https://github.com/bluetape4k/bluetape4k-graph/issues/551) | PR #574 hosted 검증·리뷰 대기 | #550 PR #573 exact head 위에서 `suspendTransaction` 최상위 `Flow` materialization과 표준 컨테이너 중첩 `Flow` 거부 계약을 graph-core 공통 helper 및 AGE·Neo4j·Memgraph·TinkerPop에 정렬했다. PR #574 live head `130532a2c2f0be2e9c87572ed6876bbb688afa06`, core 355개, TinkerPop 119개, Neo4j 132개, Memgraph 124개, AGE 195개 전체 테스트와 다섯 모듈 Detekt를 순차 통과했다. 최종 train merge는 마지막 승인 단계에서만 수행한다. |
| [#552](https://github.com/bluetape4k/bluetape4k-graph/issues/552) | PR #575 hosted 검증·리뷰 대기 | #551 PR #574 exact head 위에서 AGE active JDBC statement를 cancellation 시작 시 한 번 cancel하고, 실행 전 취소·`executeQuery()`·`ResultSet.next()` blocking 및 Exposed cleanup ownership을 deterministic proxy로 검증했다. targeted 3/3, AGE 198/198, Detekt를 통과했으며 실제 driver의 `Statement.cancel()`/`IDLE → IN_QUERY` handoff 한계와 positive `defaultQueryTimeout`/vendor API 책임을 EN/KO README와 7-Tier evidence에 기록했다. source implementation commit은 `35a9bef41daf5176a16695ee48cb15d7584e5344`, race 보정 commit은 `5a21d911`, PR read-back docs commit은 `093f2a7cd09a3191965a869acc34ff5883d92379`이며, 최종 train merge는 마지막 승인 단계에서만 수행한다. |
| [#538](https://github.com/bluetape4k/bluetape4k-graph/issues/538) | PR #576 hosted 통과, review·merge 대기 | PR #575 exact head 위에서 state-store `update` 원자 경계와 `GraphIoBatchWriter`/`SuspendGraphIoBatchWriter`의 shared `requirePositiveNumber` 검증을 추가했다. graph-io-core targeted 11/11, 전체 142개, Detekt, forbidden assertion scan, Korean terminology audit를 통과했고 PR #576 exact head `112703d4752fa5dad6f25cef5a53328cd6712bfa`에서 CI·Examples hosted checks가 모두 성공했다. #553은 이 exact head 위에 적층 중이다. 최종 train merge는 마지막 승인 단계에서만 수행한다. |
| [#553](https://github.com/bluetape4k/bluetape4k-graph/issues/553) | PR 생성 전 candidate 검증 완료 | #538 PR #576 exact head 위에서 workflow transition이 기존 `sources`·`elapsed`·`checkpoint` payload를 `copy(state = ...)`로 보존하도록 수정했다. TDD RED 후 targeted 4/4, graph-io-core 143/143, Detekt, 금지 assertion scan과 diff-check를 통과했다. implementation commit은 `88cc9676dfbbd48c7b700c51854f7380ee6fe07a`, 문서 receipt는 `09ceee92`이며 PR 생성·hosted 검증 후 #554를 다음 slice로 적층한다. |

이전 완료 순서는 `#527 → #525 → #526`이다. 현재 train은 `#543 → #544 → #545 → #546 → #547 → #536 → #548 → #549 → #535 → #550 → #551 → #552 → #538 → #553`
순서이며, 각 PR은 이전 exact head를 base로 삼고 최종 일괄 merge 승인 전에는
독립 병합하지 않는다.

## 현재 방향

`0.5.0` 안정 라인은 유지보수 기준으로 보존한다.
현재 작업 라인 `1.0.0`에는 #544, #545, #546, #547, #536이 남아 있다. 최근 stacked train은
Spring/Testcontainers 통합 테스트 계약과 네 graph image family의
startup/workload gate를 고정했고, 현재는 Spring Boot management·initializer
계약을 PR #566으로 추가한 뒤 7개 예제 suspend teardown의 cancellation-safe cleanup을 #546 slice로
쌓고 catalog/retry evidence를 PR #568의 #547 slice로 추가한 뒤 bounded capability contract를 #536 slice로 적층했다. hosted 검증과 review는 각 PR에 대해 대기 중이다. 아래 `0.7.0` 표와 이전 큐는 historical
snapshot으로 보존한다.

이전 train의 merge tip은 PR #530의 `a234d7cfd0cd41381982720d06395e5b51226702`,
PR #531의 `a74d735a7eee98ebba258d2ad909290f183dc041`, PR #532의
`57d3348acb2b5cad1ff3b5e03737cba704fc567a`이다. 현재 train의 기준은 PR #564의
`a553a423271eb99cd69a6ce8bb88d034b27923d2`이며, #544와 #545는 그 위에
순차적으로 쌓인다. #546은 PR #566의 `96496c612e26b8a3eafa55c401bf8073703972bd` exact head를
base로 사용한다.

## 다음 backlog

| 이슈 | 상태 | 메모 |
|---|---|---|
| [#215](https://github.com/bluetape4k/bluetape4k-graph/issues/215) | backlog | Amazon Neptune backend feasibility를 local 또는 신뢰 가능한 integration test 가능성부터 재검증한다. |
| [#30](https://github.com/bluetape4k/bluetape4k-graph/issues/30) | backlog | #215의 feasibility 결과 전까지 `graph-neptune` 구현을 시작하지 않는다. |

## 이전 `0.7.0` 진행 기록

이 절의 상태와 서술은 해당 release line의 당시 기록을 보존한 historical
snapshot이다. 현재 live 상태는 위의 `1.0.0` closeout과 `backlog` 표를 따른다.

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

- live GitHub 조회(2026-08-23 KST): `1.0.0` milestone 열린 issue 0개,
  열린 PR 0개; `backlog` 배정 issue는 #215와 #30이다.
- stacked train merge tip: PR #530 → `a234d7cfd0cd41381982720d06395e5b51226702`,
  PR #531 → `a74d735a7eee98ebba258d2ad909290f183dc041`, PR #532 →
  `57d3348acb2b5cad1ff3b5e03737cba704fc567a`.
- PR #532 hosted CI run `32594015661`: 전체 check와 graph image family gate가
  성공했다.

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
