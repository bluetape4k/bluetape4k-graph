# graph-io-micrometer

English | [한국어](README.ko.md)

Optional Micrometer bridge for graph-io progress events. The module depends on
`graph-io-core` and does not change the core module's dependency surface.

## Usage

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-io-micrometer:<version>")
}

val metricsListener = GraphIoMicrometerProgressListener(meterRegistry)
val listener = GraphIoCompositeProgressListener.of(userListener, metricsListener)
importer.importGraph(source, graphOps, options, listener)
```

The bridge emits these bounded meters:

| Meter | Type | Tags |
|---|---|---|
| `graph.io.runs` | Counter | `operation`, `format`, `status` |
| `graph.io.records` | Counter | `operation`, `format`, `kind` |
| `graph.io.bytes` | Counter | `operation`, `format` |
| `graph.io.duration` | Timer | `operation`, `format`, `status` |
| `graph.io.phase.duration` | Timer | `operation`, `format`, `phase` |
| `graph.io.active` | Gauge | `operation`, `format` |

Tags are fixed enum values lowercased with `Locale.ROOT`. Dataset paths,
record IDs, run IDs, and exception messages are never included.
