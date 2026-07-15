# Formats and external IDs

| Format | Boundary | Good fit | Evidence |
|---|---|---|---|
| CSV | paired vertex/edge files | tabular exchange and inspection | [`CsvRoundTripTest.kt`](../../../../graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvRoundTripTest.kt) |
| Jackson 2 NDJSON | single record stream | Jackson 2 applications | [`Jackson2RoundTripTest.kt`](../../../../graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2RoundTripTest.kt) |
| Jackson 3 NDJSON | single record stream | Jackson 3 applications | [`Jackson3RoundTripTest.kt`](../../../../graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3RoundTripTest.kt) |
| GraphML | XML graph document | interoperable graph tooling | [`GraphMlRoundTripTest.kt`](../../../../graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlRoundTripTest.kt) |

External IDs are import correlation keys, not backend `GraphElementId` promises. The importer maps source IDs to created vertex IDs so edges can be resolved; see [`GraphIoExternalIdMap.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMap.kt) and its [`tests`](../../../../graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMapTest.kt).

Before transfer, define property-type normalization, duplicate external-ID policy, edge ordering, charset, and malformed-record handling. After transfer, compare report counts, sampled properties, unresolved endpoints, and a cross-format round trip. NDJSON buffers edges that arrive before vertices, so size limits and overflow failures matter; release evidence is in [`Jackson3EdgeBufferOverflowTest.kt`](../../../../graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3EdgeBufferOverflowTest.kt).
