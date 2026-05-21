# API Model Benchmark Results - 2026-05-21

Docker-free JMH smoke run for `ApiModelBenchmark` on the shared TinkerGraph fixture.

- Source: `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/ApiModelBenchmark.kt`
- Raw JSON: `docs/benchmark/2026-05-21-api-model-jmh.json`
- Chart PNG: `docs/images/readme-charts/graph-api-model-chart-01.png`
- Chart SVG: `docs/images/readme-charts/graph-api-model-chart-01.svg`
- JVM: GraalVM JDK 25.0.3
- JMH: 1 fork, 1 warmup iteration, 3 measurement iterations, 1 second per iteration, `-prof gc`

This run is intentionally short and suitable for smoke-scale comparison. Re-run with longer warmup and measurement windows before using the ranking as release-grade evidence.

## PageRank Throughput

Higher is better.

| API model | Score | Error | Allocation |
|---|---:|---:|---:|
| Sync | **138,943.484 ops/s** | ±40,362.146 | 28,451 B/op |
| Virtual Thread | 40,283.460 ops/s | ±9,678.720 | 29,456 B/op |
| Coroutine Flow | 36,879.554 ops/s | ±85,084.781 | 29,516 B/op |

## BFS And Concurrency Latency

Lower is better.

| Scenario | API model | Score | Error | Allocation |
|---|---|---:|---:|---:|
| BFS depth=5 | Sync | **4.724 us/op** | ±3.022 | 21,990 B/op |
| BFS depth=5 | Virtual Thread | 18.668 us/op | ±8.229 | 23,152 B/op |
| BFS depth=5 | Coroutine Flow | 20.244 us/op | ±11.268 | 23,455 B/op |
| BFS 100-way | Virtual Thread | **240.903 us/op** | ±167.502 | 2,318,801 B/op |
| BFS 100-way | Coroutine async | 279.828 us/op | ±329.942 | 2,367,754 B/op |
| 100-way launch/create | Virtual Thread | 51.042 us/op | ±173.745 | 61,464 B/op |
| 100-way launch/create | Coroutine async | **5.916 us/op** | ±3.127 | 28,373 B/op |

## Command

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-*-JMH.jar \
  '.*ApiModelBenchmark.*' \
  -wi 1 -i 3 -r 1s -w 1s -f 1 \
  -prof gc \
  -rf json \
  -rff docs/benchmark/2026-05-21-api-model-jmh.json
```
