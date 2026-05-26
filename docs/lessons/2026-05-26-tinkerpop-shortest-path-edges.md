# TinkerPop Shortest Path Edges

## Context

TinkerPop shortest-path traversals based on `both()` can return vertex-only
Gremlin paths.

## Decision

Rebuild `GraphPath` results by inserting the connecting edge between consecutive
vertices so `GraphPath.length` reflects the hop count.

## Outcome

`shortestPath` and `allShortestPaths` now return paths with edge steps for
TinkerGraph traversal results.

## Verification

Added sync and suspend shortest-path assertions for edge count and path length.

## Future Notes

When mapping Gremlin `Path` values, verify both vertex order and edge steps;
vertex-only assertions are not enough for path APIs.
