# Abuser Detection Smoke Benchmark - 2026-05-28

실행 조건: macOS arm64, GraalVM JDK 25.0.3, kotlinx-benchmark/JMH 1.37, fork 1회, warmup iteration 1회, 1초 measurement iteration 1회, PostgreSQL AGE Testcontainer, 120 account 규모의 `smoke` fixture, `shared` scenario, 2026-05-28. 모든 latency 값은 `ms/op`이며 낮을수록 좋다. Benchmark 전에 smoke test로 detection quality를 검증했고, AGE + Exposed, Exposed JDBC, JPA/Hibernate 모두 precision `1.0`, recall `1.0`, F1 `1.0`을 기록했다.

명령:

```bash
./gradlew :graph-benchmark:abuserDetectionSmokeBenchmark --no-build-cache
```

Raw JSON:

- [2026-05-28-abuser-detection-smoke-main.json](2026-05-28-abuser-detection-smoke-main.json)

## 결과

| Benchmark | AGE + Exposed | Exposed JDBC | JPA/Hibernate |
|---|---:|---:|---:|
| `detectCandidates` | 13.002 | **0.199** | 0.210 |
| `detectF1BasisPoints` | 11.533 | **0.197** | 0.202 |

## 해석

Smoke fixture는 release-grade performance ranking이 아니라 contract와 execution surface를 검증한다. 이 첫 slice에서 AGE는 graph abstraction과 traversal 비용을 부담하는 반면, 두 relational baseline은 shared recursive SQL query를 직접 실행한다. Benchmark는 이제 `shared`, `transfer`, `noisy-dense`, `wide-fanout` scenario를 지원하므로 더 큰 dataset과 더 많은 inspection volume에서 tradeoff를 더 분명하게 볼 수 있다.
