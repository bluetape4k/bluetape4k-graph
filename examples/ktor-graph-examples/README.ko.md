# ktor-graph-examples

> 🇺🇸 [English](README.md)

`graph-ktor`의 실행 가능한 Ktor 예시입니다. TinkerGraph를 사용하므로 Docker나 외부 graph database 없이 실행할 수 있습니다.

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

## 테스트

```bash
./gradlew :ktor-graph-examples:test
```
