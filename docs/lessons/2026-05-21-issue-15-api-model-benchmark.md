# 이슈 15 API Model Benchmark

## 맥락

Issue #15 required a Docker-free `graph-benchmark` comparison of sync, virtual-thread, and coroutine API models on the same TinkerGraph fixture.

## 결정

Added `ApiModelBenchmark` instead of extending legacy `AlgorithmBenchmark` so API-model semantics, 100-way concurrency, and virtual-thread creation cost stay isolated from existing operation benchmarks.

## 결과

Measured PageRank throughput, BFS depth=5 latency, 100-way BFS latency, and 100-way launch/create cost with JMH GC profiler output. Updated `graph-benchmark` README files and `docs/graphdb-tradeoffs.md` with result tables plus SVG/PNG chart artifacts.

Added benchmark-level decision guide READMEs that map current evidence to backend/API/graph-io recommendations by service scale, data scale, and domain. Follow-up benchmark gaps were registered as issues #196-#199 instead of expanding this PR scope.

## 검증

- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:compileTestKotlin :graph-benchmark:test --no-build-cache`
- `./gradlew :graph-benchmark:compileKotlin :graph-benchmark:mainBenchmarkJar --no-build-cache`
- `java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-0.3.1-SNAPSHOT-JMH.jar '.*ApiModelBenchmark.*' -wi 1 -i 3 -r 1s -w 1s -f 1 -prof gc -rf json -rff docs/benchmark/2026-05-21-api-model-jmh.json`
- `python3 benchmark/graph-benchmark/scripts/render_api_model_chart.py docs/benchmark/2026-05-21-api-model-jmh.json`
- `git diff --check`

## 향후 지침

Keep short benchmark runs labeled as smoke-scale evidence. For release-grade claims, rerun with longer warmup and measurement windows and compare confidence intervals before stating a stable ranking.
