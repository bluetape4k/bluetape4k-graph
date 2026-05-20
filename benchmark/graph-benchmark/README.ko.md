# graph-benchmark

[English](README.md) | [한국어](README.ko.md)

인메모리 TinkerGraph 구현을 사용해 백엔드 독립 graph operation을 측정하는 JMH 벤치마크 모듈입니다.

## Architecture

![graph-benchmark Architecture diagram](../../docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.png)

## 측정 대상

`graph-benchmark`는 `TinkerGraphOperations` 위에서 동기 graph API와 virtual-thread adapter 경로를 비교합니다.

- 알고리즘: PageRank, BFS, DFS의 동기 및 virtual-thread 경로.
- 탐색: 이웃 조회, 최단 경로, 전체 경로.
- 정점 작업: 라벨 조회, ID 조회, 이웃 조회.
- 배치 삽입: 10k 정점/간선 루프 삽입과 배치 삽입 비교.

## 소스 근거

- `build.gradle.kts`는 `kotlinx.benchmark`를 적용하고 JMH `@State`를 all-open 처리하며, `graph-core`, `graph-tinkerpop`, coroutines, `bluetape4k-virtualthread-api`에 의존합니다.
- `GraphBenchmarkState`는 `TinkerGraphOperations`로 공유 인메모리 그래프를 구성합니다.
- `AlgorithmBenchmark`는 동기 알고리즘 호출과 `VirtualThreadAlgorithmAdapter`를 비교합니다.
- `TraversalBenchmark`, `ShortestPathBenchmark`, `NeighborsBenchmark`, `VertexOperationsBenchmark`는 동기 및 virtual-thread operation 경로를 비교합니다.
- `BatchInsertBenchmark`와 smoke test support는 10k 루프-vs-배치 정점/간선 삽입 시나리오를 검증합니다.

## 실행

```bash
./gradlew :graph-benchmark:benchmark
```

이 모듈은 인메모리 TinkerGraph 백엔드를 사용하므로 외부 graph database 컨테이너가 필요하지 않습니다.

## 참고

- core API 오버헤드를 확인할 때 가장 빠른 벤치마크 경로입니다.
- 네트워크나 데이터베이스 시작 비용 없이 동기 호출과 virtual-thread wrapper를 비교하는 데 적합합니다.
