# GraphDB Adoption 결정 보고서 - 2026-05-28

## 요약

일반적인 authorization, fraud, CRUD, 짧은 join, shallow traversal workload만으로는 GraphDB adoption을 정당화할 수 없다.

이 slice에서 확인된 유일한 positive adoption signal은 **길고 선택적인 path-shaped traversal**이다.

| Workload | 판단 |
|---|---|
| PostgreSQL AGE/Cypher | 이 benchmark에서는 viable하지 않다. 두 large long-path scenario 모두 로컬에서 timeout되었다. |
| PostgreSQL recursive CTE | 강한 baseline이다. `deep-wide`와 여러 small/medium scenario에서 이긴다. |
| PostgreSQL iterative traversal | 강한 baseline이다. 여러 medium scenario에서 이기고 예측 가능성을 유지한다. |
| Neo4j Cypher | long selective traversal의 viable candidate다. `large + long-chain`에서 이긴다. |
| Memgraph | failed adoption evidence로 포함한다. Smoke parity는 통과했지만, 로컬 large load가 reportable latency 전에 실패했다. |
| TinkerGraph | in-memory이므로 이 adoption decision에서만 제외한다. 기존 in-memory benchmark track은 API/contract 작업에는 계속 유효하다. |

Adoption 권고:

> 측정한 workload에는 Apache AGE를 채택하지 않는다. persistent GraphDB가 필요하다면 long, selective traversal use case에서 Neo4j 중심 검증을 계속한다. PostgreSQL CTE/iterative traversal은 기본 relational baseline으로 유지한다.

## 배경

원래 이슈는 abuser/fraud detection에서 시작했다.

- AGE + Exposed, Exposed, JPA를 비교한다.
- Exposed/JPA relational baseline을 recursive CTE와 iterative traversal로 분리한다.
- latency와 detection correctness를 함께 측정한다.

Scenario review 중 fraud detection이 all paths나 unbounded traversal을 요구하면 쉽게 나쁜 benchmark가 될 수 있어 범위가 바뀌었다. 더 강한 adoption question은 다음과 같았다.

- relationship depth가 variable일 때 GraphDB가 도움이 되는가?
- path shape 자체가 query일 때 도움이 되는가?
- data size와 traversal length가 커지면 결과가 달라지는가?

따라서 primary scenario를 authorization inheritance로 바꿨다.

```text
user -> group -> group... -> role -> resource
```

active edge, deny-overrides-allow, public-resource filtering, bounded traversal, cycle-safe semantics를 함께 적용한다.

## 완료한 작업

| 영역 | 완료한 작업 |
|---|---|
| Shared contracts | deterministic authz fixture, result metric, oracle, engine interface, smoke parity test를 추가했다. |
| PostgreSQL AGE | AGE/Cypher authz traversal을 추가하고 명백한 path explosion을 줄이도록 Cypher query shape를 최적화했다. |
| PostgreSQL baselines | recursive CTE와 iterative traversal engine을 분리해 추가했다. |
| Native GraphDB baseline | Neo4j/Memgraph 호환 native Cypher engine과 benchmark parameter를 추가했다. |
| Fraud benchmark | bounded fraud/abuser benchmark를 더 엄격히 하고 CTE vs iterative relational split을 유지했다. |
| Long-path adoption surface | `large` data에 `long-chain` 10-hop 및 `deep-wide` 12-hop scenario를 추가했다. |
| TinkerGraph scope | TinkerGraph는 persistent adoption decision에서만 제외했다. 무관한 in-memory track은 그대로 둔다. |
| Evidence | raw JMH JSON, timeout/failure log, README table, chart asset을 commit했다. |

## Benchmark 표면

주요 명령:

```bash
./gradlew :graph-benchmark:authzInheritanceSmokeBenchmark --no-build-cache
./gradlew :graph-benchmark:authzInheritanceBenchmark --no-build-cache
./gradlew :graph-benchmark:authzInheritanceAdoptionBenchmark --no-build-cache
```

AGE/Memgraph가 complete matrix execution을 막은 뒤에는 backend를 분리하기 위해 diagnostic JMH run도 사용했다.

## Scenario

| Scenario | Size | 형태 | 목적 |
|---|---:|---|---|
| `shallow` | small/medium | 짧은 user/group/role/resource path | shallow traversal의 negative control |
| `deep-inheritance` | small/medium | cycle을 포함한 더 깊은 inheritance | mid-depth variable traversal |
| `deny-heavy` | small/medium | 많은 deny grant edge | correctness 및 deny-overrides-allow semantics |
| `wide-groups` | small/medium | 더 넓은 membership fan-out | fan-out pressure |
| `long-chain` | large | 강제된 10-hop target chain | long selective traversal adoption probe |
| `deep-wide` | large | 더 넓은 fan-out/cycle을 포함한 12-hop traversal | long + wider traversal stress |

## 정확성

Benchmark 해석 전에 smoke test가 구현된 engine들의 result-set parity와 F1 `1.0`을 검증했다.

정확성 benchmark인 `resolveF1BasisPoints`는 같은 resource를 resolve하고 F1을 basis point로 변환한다. 이는 별도 ranking axis가 아니라 correctness-metric overhead를 확인하는 guard다.

## Small/Medium PostgreSQL AGE 기준선

`resolveResources`, `ms/op`, 낮을수록 좋다.

| Scenario | Size | AGE/Cypher | PostgreSQL CTE | PostgreSQL iterative | Winner |
|---|---:|---:|---:|---:|---|
| `shallow` | `small` | 6.337 | **0.572** | 0.576 | PostgreSQL CTE |
| `shallow` | `medium` | 57.382 | 12.085 | **1.056** | PostgreSQL iterative |
| `deep-inheritance` | `small` | 93.848 | **0.726** | 1.449 | PostgreSQL CTE |
| `deep-inheritance` | `medium` | 604.833 | 9.385 | **2.102** | PostgreSQL iterative |
| `deny-heavy` | `small` | 67.670 | **0.993** | 1.803 | PostgreSQL CTE |
| `deny-heavy` | `medium` | 448.263 | 9.450 | **4.310** | PostgreSQL iterative |
| `wide-groups` | `small` | 29.445 | **0.551** | 1.801 | PostgreSQL CTE |
| `wide-groups` | `medium` | 250.083 | **1.521** | 3.658 | PostgreSQL CTE |

결론:

- AGE/Cypher는 어떤 small/medium row에서도 이기지 못했다.
- PostgreSQL recursive CTE와 iterative traversal은 둘 다 viable하며 별도 baseline으로 유지해야 한다.
- 이 결과만으로는 GraphDB adoption을 정당화할 수 없다.

## Large Long-Path Adoption Probe

`resolveResources`, `large` fixture, `ms/op`, 낮을수록 좋다.

| Scenario | Neo4j Cypher | Memgraph Cypher | AGE/Cypher | PostgreSQL CTE | PostgreSQL iterative | Winner |
|---|---:|---:|---:|---:|---:|---|
| `long-chain` | **12.731** | load failure | timeout >75s | 55.364 | 47.568 | Neo4j Cypher |
| `deep-wide` | 56.467 | load failure | timeout >75s | **11.596** | 27.836 | PostgreSQL CTE |

![Authorization inheritance adoption latency](../images/readme-charts/authz-inheritance-adoption-latency-chart-01.png)

해석:

- `long-chain`은 측정된 유일한 positive GraphDB signal이다.
- `large + long-chain`에서 Neo4j Cypher는 PostgreSQL iterative보다 3.74배, PostgreSQL recursive CTE보다 4.35배 빠르다.
- `deep-wide`는 여전히 PostgreSQL CTE에 유리하므로 GraphDB는 blanket replacement가 아니다.
- AGE/Cypher는 75초 로컬 diagnostic timeout 안에서 `large + long-chain`과 `large + deep-wide` 어느 쪽도 완료하지 못했다.
- Memgraph는 smoke parity를 통과했지만, local large adoption diagnostic run에서 load 중 Bolt connection이 종료되었다. 따라서 reportable latency가 아니라 failed adoption evidence로 포함한다.

## AGE를 권장하지 않는 이유

AGE는 PostgreSQL을 storage engine으로 유지하면서 Cypher syntax를 추가하므로 매력적으로 보였다. 그러나 benchmark 결과는 이 use case에 AGE를 채택하는 결정을 뒷받침하지 않는다.

| 기준 | AGE 결과 |
|---|---|
| 표현력 | 좋다. Cypher는 variable-depth path를 자연스럽게 표현한다. |
| Small/medium latency | 나쁘다. 측정된 모든 row에서 PostgreSQL CTE 또는 iterative traversal에 졌다. |
| Large long-path latency | reportable하지 않다. 두 adoption scenario 모두 timeout되었다. |
| 운영 단순성 | 혼재되어 있다. 두 번째 database는 피하지만 AGE extension/query semantics와 Exposed connection setup 제약이 추가된다. |
| Adoption verdict | 이 benchmark에는 채택하지 않는다. |

실무 결론:

> AGE는 PostgreSQL 안에서 Cypher syntax를 제공하지만, 이 benchmark에는 측정 가능한 traversal performance와 예측 가능한 execution이 필요하다. PostgreSQL CTE/iterative traversal이 AGE보다 안전하며, Neo4j는 positive long-path signal을 보인 유일한 측정된 persistent GraphDB candidate다.

## 그래도 GraphDB가 의미 있는 경우

다음 조건이 모두 참이면 GraphDB는 여전히 평가할 가치가 있다.

- Traversal depth가 variable이고 고정 2-3 join보다 자주 깊다.
- path 자체가 query result 또는 key filter다.
- query가 bounded, selective, path-shaped다.
- workload를 simple join, aggregate table, materialized projection으로 줄일 수 없다.
- native graph engine을 production infrastructure로 운영할 수 있다.

좋은 후보:

- 깊은 exception chain이 있는 permission/organization/group inheritance.
- dependency 또는 impact-radius analysis.
- relationship-type filter가 있는 long selective recommendation path.
- path existence 또는 shortest path가 중요한 network/topology reachability.

나쁜 후보:

- 단순 ID lookup.
- 1-hop join.
- 고정 2-3 table join.
- CRUD-heavy OLTP.
- star-schema aggregation.
- unbounded all-path search.

## 최종 권고

| 결정 | 권고 |
|---|---|
| 기본 구현 | query shape에 따라 PostgreSQL recursive CTE 또는 iterative traversal을 선택한다. |
| AGE | 이 benchmark의 adoption candidate에서 제외한다. |
| Neo4j | long selective traversal을 위한 persistent GraphDB candidate로 계속 검증한다. |
| Memgraph | large fixture load stability를 해결한 뒤에만 재검토한다. 현재 adoption evidence는 latency가 아니라 load failure다. |
| TinkerGraph | adoption evidence가 아니라 in-memory API/contract benchmark용으로 유지한다. |

다음 benchmark 방향:

1. dependency/impact-radius workload를 추가한다. 이 workload는 자연스럽게 long-path이면서 path-shaped다.
2. Neo4j vs PostgreSQL CTE/iterative를 primary decision table로 유지한다.
3. 새 query shape 또는 indexing strategy가 reportable latency를 만들기 전까지 AGE는 제외된 것으로 다룬다.
4. 각 backend가 현재 `large` probe를 안정적으로 완료한 뒤에만 더 큰 data를 추가한다.

## 증거 산출물

| 산출물 | 목적 |
|---|---|
| [2026-05-28-authz-inheritance-main.json](2026-05-28-authz-inheritance-main.json) | Small/medium AGE vs PostgreSQL raw JMH result |
| [2026-05-28-authz-inheritance-results.md](2026-05-28-authz-inheritance-results.md) | Small/medium result table 및 해석 |
| [2026-05-28-authz-inheritance-adoption-neo4j.json](2026-05-28-authz-inheritance-adoption-neo4j.json) | Large Neo4j adoption probe |
| [2026-05-28-authz-inheritance-adoption-postgres.json](2026-05-28-authz-inheritance-adoption-postgres.json) | Large PostgreSQL CTE/iterative adoption probe |
| [2026-05-28-authz-inheritance-adoption-f1.json](2026-05-28-authz-inheritance-adoption-f1.json) | 정확성 metric benchmark probe |
| [2026-05-28-authz-inheritance-adoption-age-timeout.txt](2026-05-28-authz-inheritance-adoption-age-timeout.txt) | AGE `large + long-chain` timeout evidence |
| [2026-05-28-authz-inheritance-adoption-age-deep-wide-timeout.txt](2026-05-28-authz-inheritance-adoption-age-deep-wide-timeout.txt) | AGE `large + deep-wide` timeout evidence |
| [2026-05-28-authz-inheritance-adoption-memgraph-failure.txt](2026-05-28-authz-inheritance-adoption-memgraph-failure.txt) | Memgraph large load failure evidence |
| [authz-inheritance-adoption-latency-chart-01.png](../images/readme-charts/authz-inheritance-adoption-latency-chart-01.png) | Adoption chart PNG |
| [authz-inheritance-adoption-latency-chart-01.svg](../images/readme-charts/authz-inheritance-adoption-latency-chart-01.svg) | Adoption chart SVG source |

## DoD

| 항목 | 상태 | 증거 |
|---|---|---|
| Workload shift documented | Done | 이 보고서와 issue #260 update |
| AGE included in comparison | Done | timeout row와 timeout log |
| TinkerGraph scope clarified | Done | persistent adoption decision에서만 제외 |
| Native GraphDB candidate measured | Done | Neo4j adoption JSON과 table |
| Memgraph adoption evidence included | Done | smoke parity와 large load failure row/log |
| PostgreSQL baselines separated | Done | CTE와 iterative row를 별도로 유지 |
| 정확성 captured | Done | smoke parity test와 F1 benchmark artifact |
| Chart and raw evidence linked | Done | PNG/SVG 및 JSON/log artifact |
| Recommendation stated | Done | AGE 제외, long selective traversal에는 Neo4j 유지 |
