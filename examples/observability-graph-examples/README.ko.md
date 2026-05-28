# observability-graph-examples

> 🇺🇸 [English](README.md)

서비스 의존성, public API, alert, incident, on-call ownership를 incident-response 그래프로 모델링하는 예제입니다.
bluetape4k-graph의 backend 독립 API로 "실패 서비스의 downstream은 무엇인가?", "어떤 public API가 영향받는가?",
"이 incident boundary의 담당 팀은 누구인가?" 같은 운영 질문을 풀어냅니다.

## 예제 시나리오

checkout 장애가 payment service latency alert에서 시작됩니다. Incident commander는 더 많은 팀을 호출하거나 고객 영향
API owner에게 알리기 전에 alert 신호와 runtime dependency graph를 함께 봐야 합니다.

샘플 그래프는 다음을 포함합니다.

- `checkout-service -> payment-service -> postgres-primary`,
- `edge-api`를 거치는 public `checkout-api`, `mobile-checkout-api`,
- `payment-latency`, `checkout-errors` alert,
- `payment-service`를 root cause로 표시하는 `Checkout payment incident`,
- 영향을 받는 서비스의 owner인 `payments-team`.

## Architecture Diagram

![observability graph examples architecture](../../docs/images/readme-diagrams/examples-observability-graph-examples-architecture-01.png)

## Graph Model

| 요소 | Label | 주요 속성 | 목적 |
|---|---|---|---|
| Runtime service | `Service` | `serviceId`, `name`, `tier`, `status` | 서비스와 인프라 의존성 그래프 노드입니다. |
| Public API | `Api` | `apiId`, `name`, `tier`, `status` | 고객에게 노출되는 entry point입니다. |
| On-call team | `Team` | `teamId`, `name`, `status` | escalation 대상입니다. |
| Monitoring alert | `Alert` | `alertId`, `name`, `severity`, `status` | 서비스 boundary에 연결되는 signal입니다. |
| Incident | `Incident` | `incidentId`, `name`, `severity`, `status` | incident-response record입니다. |
| Dependency | `DEPENDS_ON` | `kind` | caller -> callee 방향 runtime dependency입니다. |
| Ownership | `OWNED_BY` | `kind` | service -> team escalation path입니다. |
| Alert correlation | `ALERTS_ON` | `kind` | alert -> service signal edge입니다. |
| Root cause | `ROOT_CAUSE` | `kind` | incident -> service root-cause marker입니다. |

## Traversal Goals

| 질문 | API |
|---|---|
| 실패 서비스의 downstream은 무엇인가? | `downstreamDependencies(serviceId, maxDepth)` |
| 어떤 서비스가 upstream caller인가? | `upstreamImpactedServices(serviceId, maxDepth)` |
| 고객 영향 public API는 무엇인가? | `affectedApis(serviceId, maxDepth)` |
| alert가 가리키는 incident boundary는 어디까지인가? | `alertBoundary(alertIds, maxDepth)` |
| 실패 서비스를 owning하는 team은 누구인가? | `owningTeams(serviceId)` |

## Sample Dataset

모듈은 `src/main/resources/sample-data/observability/` 아래에 graph-io CSV fixture를 포함합니다.

| 파일 | 내용 |
|---|---|
| `vertices.csv` | service 4개, public API 2개, team 1개, alert 2개, incident 1개. |
| `edges.csv` | dependency, ownership, alert correlation, root-cause edge. |

`ObservabilitySampleDatasetLoader`는 이 fixture를 임의의 `GraphOperations` 또는 `GraphSuspendOperations` 구현체로
import합니다.

```kotlin
val ops = TinkerGraphOperations()
val service = ObservabilityIncidentService(ops)
service.initialize()

val report = ObservabilitySampleDatasetLoader.importCsv(ops)
check(report.status == GraphIoStatus.COMPLETED)

val affectedApis = service.affectedApis("payment-service")
val owners = service.owningTeams("payment-service")
```

## Expected Output

번들된 incident dataset 기준 예상 결과입니다.

| Query | Expected IDs |
|---|---|
| `downstreamDependencies("checkout-service", maxDepth = 2)` | `payment-service`, `postgres-primary` |
| `upstreamImpactedServices("payment-service", maxDepth = 3)` | `checkout-service`, `edge-api` |
| `affectedApis("payment-service", maxDepth = 5)` | `checkout-api`, `mobile-checkout-api` |
| `alertBoundary(listOf("payment-latency", "checkout-errors"))` | `payment-service`, `checkout-service`와 인접 service |
| `owningTeams("payment-service")` | `payments-team` |

## 테스트 읽는 법

`AbstractObservabilityIncidentTest`와 `AbstractObservabilityIncidentSuspendTest`가 학습 시나리오입니다. 구체 backend
class는 TinkerGraph, Neo4j, Memgraph, Apache AGE, FalkorDB용 `GraphOperations` 또는 `GraphSuspendOperations`
설정만 제공합니다.

`ObservabilitySampleDatasetLoaderTest`는 bundled CSV fixture에 대한 빠른 TinkerGraph smoke path입니다.

## 테스트 실행

```bash
./gradlew :observability-graph-examples:test
./gradlew :observability-graph-examples:test --tests "*TinkerGraph*"
```

TinkerGraph 테스트는 메모리에서 실행됩니다. Neo4j, Memgraph, Apache AGE, FalkorDB 테스트는 Docker/Testcontainers가 필요합니다.

## 의존성

```kotlin
implementation(project(":bluetape4k-graph-core"))
implementation(project(":bluetape4k-graph-neo4j"))
implementation(project(":bluetape4k-graph-memgraph"))
implementation(project(":bluetape4k-graph-age"))
implementation(project(":bluetape4k-graph-falkordb"))
implementation(project(":bluetape4k-graph-tinkerpop"))
implementation(project(":bluetape4k-graph-io-csv"))
```
