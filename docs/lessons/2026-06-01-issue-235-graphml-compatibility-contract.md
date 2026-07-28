# 이슈 235 GraphML compatibility contract

## 맥락

GraphML supports undirected graphs, nested graphs, ports, hyperedges, and XML extension payloads. The current graph IO model is a backend-neutral property graph with directed `GraphEdge` records, so the next compatibility slice needed an explicit support matrix before broadening import behavior.

## 결정

Keep the implemented GraphML subset focused on directed property graphs with scalar keys and data. Treat undirected graph defaults, edge-level undirected flags, ports, nested graphs, hyperedges, and extension payloads as explicit unsupported constructs with documented `SKIP` and `FAIL` behavior.

## 결과

Representative GraphML fixtures now lock the supported property-graph subset and the unsupported-construct policy. README files document the compatibility matrix, and the design note records why undirected and nested graph support should wait for explicit mapping rules.

## 검증

- `./gradlew :bluetape4k-graph-io-graphml:test --tests 'io.bluetape4k.graph.io.graphml.internal.StaxGraphMlReaderWriterTest' --no-daemon`
- `./gradlew :bluetape4k-graph-io-graphml:test --no-daemon`
- `./gradlew :bluetape4k-graph-io-graphml:detekt --no-daemon`

## 향후 메모

Do not silently flatten nested GraphML or auto-create reverse edges for undirected input. Choose a named mapping policy first, then add round-trip fixtures before changing importer behavior.
