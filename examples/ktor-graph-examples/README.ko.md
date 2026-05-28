# ktor-graph-examples

> 🇺🇸 [English](README.md)

`graph-ktor`의 실행 가능한 Ktor 예시입니다. TinkerGraph를 사용하므로 Docker나 외부 graph database 없이 실행할 수 있습니다.

## 예제 시나리오

애플리케이션은 `GraphPlugin`을 설치하고, 작은 city graph를 HTTP로 노출한다. Route handler는
`GraphOperations`와 `GraphSuspendOperations`를 plugin facade로 조회한다. `POST /demo/reset`은
`Seoul -> Daejeon -> Busan` 그래프를 다시 만들고, `GET /cities/count`, `GET /cities/path`는 그 그래프를
다시 읽는다. 별도 FalkorDB module은 caller-owned driver로 같은 route surface를 보여준다.

## Architecture Diagram

![ktor graph examples architecture](../../docs/images/readme-diagrams/examples-ktor-graph-examples-architecture-01.png)

## ERD

![ktor graph examples ERD](../../docs/images/readme-diagrams/examples-ktor-graph-examples-erd-02.png)

## Data Flow

![ktor graph examples data flow](../../docs/images/readme-diagrams/examples-ktor-graph-examples-data-flow-03.png)

## Routes

| Route | Method | 설명 |
|---|---|---|
| `/health` | GET | `UP`을 반환합니다. |
| `/demo/reset` | POST | demo city graph를 다시 생성합니다. |
| `/cities/count` | GET | `City` vertex 수를 반환합니다. |
| `/cities/path` | GET | Seoul에서 Busan까지 shortest path를 반환합니다. |

## 실행

```bash
./gradlew :ktor-graph-examples:run
```

```bash
curl -X POST http://localhost:8080/demo/reset
curl http://localhost:8080/cities/count
curl http://localhost:8080/cities/path
```

## Expected Output

| 요청 | 예상 응답 |
|---|---|
| `GET /health` | `UP` |
| `POST /demo/reset` | `reset` |
| `GET /cities/count` | `3` |
| `GET /cities/path` | `Seoul -> Daejeon -> Busan` |

## 테스트

```bash
./gradlew :ktor-graph-examples:test
```
