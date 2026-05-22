# graph-benchmark Results

## WeightedShortestPathBench

Issue: <https://github.com/bluetape4k/bluetape4k-graph/issues/41>

### Scope

- Backend: TinkerGraph in-memory operations.
- Dataset sizes: 100, 1,000, and 10,000 vertices.
- Compared algorithms: Dijkstra via `shortestPath` and A* via `aStarPath`.
- Weight property: `cost`.
- A* heuristic: zero heuristic, which keeps the run admissible and directly comparable with Dijkstra.

### Commands

```bash
./gradlew :graph-benchmark:test --tests "io.bluetape4k.graph.benchmark.WeightedShortestPathBenchTest"
./gradlew :graph-benchmark:mainBenchmark
```

### Baseline

Latest local run: 2026-05-22 21:21 KST.

Result JSON:
`benchmark/graph-benchmark/build/reports/benchmarks/main/2026-05-22T20.48.53.553841/main.json`

| Benchmark | Vertex count | Score |
|---|---:|---:|
| `WeightedShortestPathBench.aStar` | 100 | 1.258 +/- 0.022 ms/op |
| `WeightedShortestPathBench.aStar` | 1,000 | 12.961 +/- 0.210 ms/op |
| `WeightedShortestPathBench.aStar` | 10,000 | 139.581 +/- 2.023 ms/op |
| `WeightedShortestPathBench.dijkstra` | 100 | 0.699 +/- 0.018 ms/op |
| `WeightedShortestPathBench.dijkstra` | 1,000 | 8.072 +/- 0.664 ms/op |
| `WeightedShortestPathBench.dijkstra` | 10,000 | 86.092 +/- 4.554 ms/op |

Run `:graph-benchmark:mainBenchmark` to regenerate the kotlinx-benchmark JSON report under
`benchmark/graph-benchmark/build/reports/benchmarks/main/`. The checked-in benchmark definition is deterministic;
use the generated JSON as the source of truth for local hardware comparisons.

### Optimization Pass #192

Chart:
![Weighted shortest path Dijkstra optimization](../../docs/images/readme-charts/weighted-shortest-path-dijkstra-optimization-chart-01.png)

Candidate: replace `DijkstraRunner` priority queue entries from `Pair<Double, GraphElementId>` plus comparator
dispatch to a dedicated `DijkstraNode` comparable entry.

Run: 2026-05-22 21:54 KST with `./gradlew :graph-benchmark:mainBenchmark`.

| Benchmark | Vertex count | Baseline | Candidate | Delta |
|---|---:|---:|---:|---:|
| `WeightedShortestPathBench.dijkstra` | 100 | 0.699 ms/op | 0.710 ms/op | -1.6% |
| `WeightedShortestPathBench.dijkstra` | 1,000 | 8.072 ms/op | 7.501 ms/op | +7.1% |
| `WeightedShortestPathBench.dijkstra` | 10,000 | 86.092 ms/op | 83.417 ms/op | +3.1% |

Decision: accepted. The primary metric is `WeightedShortestPathBench.dijkstra` at 10,000 vertices, where lower is
better and the candidate improved beyond the 1% threshold. The 100-vertex regression is within the 5% regression guard
and is not representative of the target larger graph path workload.
