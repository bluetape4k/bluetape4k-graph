# TinkerPop 최단 경로 Edge

## 맥락

TinkerPop shortest-path traversals based on `both()` can return vertex-only
Gremlin paths.

## 결정

Rebuild `GraphPath` results by inserting the connecting edge between consecutive
vertices so `GraphPath.length` reflects the hop count.

## 결과

`shortestPath` and `allShortestPaths` now return paths with edge steps for
TinkerGraph traversal results.

## 검증

Added sync and suspend shortest-path assertions for edge count and path length.

## 향후 메모

When mapping Gremlin `Path` values, verify both vertex order and edge steps;
vertex-only assertions are not enough for path APIs.
