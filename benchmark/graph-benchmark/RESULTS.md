# graph-benchmark 결과

## WeightedShortestPathBench

Issue: <https://github.com/bluetape4k/bluetape4k-graph/issues/41>

### 범위

- 백엔드: TinkerGraph in-memory operations.
- 데이터셋 크기: 정점 100개, 1,000개, 10,000개.
- 비교 알고리즘: `shortestPath`를 통한 Dijkstra와 `aStarPath`를 통한 A*.
- 가중치 속성: `cost`.
- A* heuristic: zero heuristic. 실행을 admissible 상태로 유지해 Dijkstra와 직접 비교할 수 있게 한다.

### 명령

```bash
./gradlew :graph-benchmark:test --tests "io.bluetape4k.graph.benchmark.WeightedShortestPathBenchTest"
./gradlew :graph-benchmark:mainBenchmark
```

### 기준선

최신 로컬 실행: 2026-05-22 21:21 KST.

결과 JSON:
`benchmark/graph-benchmark/build/reports/benchmarks/main/2026-05-22T20.48.53.553841/main.json`

| Benchmark | 정점 수 | Score |
|---|---:|---:|
| `WeightedShortestPathBench.aStar` | 100 | 1.258 +/- 0.022 ms/op |
| `WeightedShortestPathBench.aStar` | 1,000 | 12.961 +/- 0.210 ms/op |
| `WeightedShortestPathBench.aStar` | 10,000 | 139.581 +/- 2.023 ms/op |
| `WeightedShortestPathBench.dijkstra` | 100 | 0.699 +/- 0.018 ms/op |
| `WeightedShortestPathBench.dijkstra` | 1,000 | 8.072 +/- 0.664 ms/op |
| `WeightedShortestPathBench.dijkstra` | 10,000 | 86.092 +/- 4.554 ms/op |

`:graph-benchmark:mainBenchmark`를 실행하면 `benchmark/graph-benchmark/build/reports/benchmarks/main/` 아래의 kotlinx-benchmark JSON report를 다시 생성한다. checked-in benchmark definition은 deterministic하므로, 로컬 하드웨어 비교의 source of truth는 생성된 JSON으로 둔다.

### Optimization Pass #192

차트:
![Weighted shortest path Dijkstra optimization](../../docs/images/readme-charts/weighted-shortest-path-dijkstra-optimization-chart-01.png)

후보: `DijkstraRunner` priority queue entry를 `Pair<Double, GraphElementId>`와 comparator dispatch 조합에서 전용 `DijkstraNode` comparable entry로 교체한다.

실행: 2026-05-22 21:54 KST, `./gradlew :graph-benchmark:mainBenchmark` 사용.

| Benchmark | 정점 수 | 기준선 | 후보 | 변화 |
|---|---:|---:|---:|---:|
| `WeightedShortestPathBench.dijkstra` | 100 | 0.699 ms/op | 0.710 ms/op | -1.6% |
| `WeightedShortestPathBench.dijkstra` | 1,000 | 8.072 ms/op | 7.501 ms/op | +7.1% |
| `WeightedShortestPathBench.dijkstra` | 10,000 | 86.092 ms/op | 83.417 ms/op | +3.1% |

판정: accepted. primary metric은 정점 10,000개의 `WeightedShortestPathBench.dijkstra`이며, 낮을수록 좋고 후보가 1% threshold 이상 개선했다. 정점 100개의 regression은 5% regression guard 안에 있으며, 목표인 더 큰 graph path workload를 대표하지 않는다.
