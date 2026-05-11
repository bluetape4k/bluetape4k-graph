# ktor-graph-examples

> 🇰🇷 [한국어 문서](README.ko.md)

Runnable Ktor example for `graph-ktor`. It uses TinkerGraph so the application can run without Docker or an external graph database.

## Routes

| Route | Method | Description |
|---|---|---|
| `/health` | GET | Returns `UP`. |
| `/demo/reset` | POST | Recreates the demo city graph. |
| `/cities/count` | GET | Returns the number of `City` vertices. |
| `/cities/path` | GET | Returns the shortest path from Seoul to Busan. |

## Run

```bash
./gradlew :ktor-graph-examples:run
```

```bash
curl -X POST http://localhost:8080/demo/reset
curl http://localhost:8080/cities/count
curl http://localhost:8080/cities/path
```

## Test

```bash
./gradlew :ktor-graph-examples:test
```
