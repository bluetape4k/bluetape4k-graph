# Abuser Detection Smoke Benchmark - 2026-05-28

Run conditions: macOS arm64, GraalVM JDK 25.0.3, kotlinx-benchmark/JMH 1.37, one fork, one warmup iteration, one one-second measurement iteration, PostgreSQL AGE Testcontainer, `smoke` fixture with 120 accounts, `shared` scenario, May 28, 2026. All latency values are `ms/op`; lower is better. Detection quality was verified by smoke tests before the benchmark: precision `1.0`, recall `1.0`, and F1 `1.0` for AGE + Exposed, Exposed JDBC, and JPA/Hibernate.

Command:

```bash
./gradlew :graph-benchmark:abuserDetectionSmokeBenchmark --no-build-cache
```

Raw JSON:

- [2026-05-28-abuser-detection-smoke-main.json](2026-05-28-abuser-detection-smoke-main.json)

## Results

| Benchmark | AGE + Exposed | Exposed JDBC | JPA/Hibernate |
|---|---:|---:|---:|
| `detectCandidates` | 13.002 | **0.199** | 0.210 |
| `detectF1BasisPoints` | 11.533 | **0.197** | 0.202 |

## Interpretation

The smoke fixture validates the contract and execution surface rather than a release-grade performance ranking. AGE pays the graph abstraction and traversal cost in this first slice, while the two relational baselines execute the shared recursive SQL query directly. The benchmark now supports `shared`, `transfer`, `noisy-dense`, and `wide-fanout` scenarios so larger datasets and heavier inspection volume can make the tradeoff more visible.
