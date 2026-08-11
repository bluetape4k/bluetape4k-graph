# graph-io-micrometer

[English](README.md) | 한국어

graph-io 진행 이벤트를 Micrometer로 연결하는 선택 모듈입니다. `graph-io-core`에
의존하지만 core의 기본 의존성 표면에는 Micrometer를 추가하지 않습니다.

## 사용법

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-io-micrometer:<version>")
}

val metricsListener = GraphIoMicrometerProgressListener(meterRegistry)
val listener = GraphIoCompositeProgressListener.of(userListener, metricsListener)
importer.importGraph(source, graphOps, options, listener)
```

bridge가 생성하는 meter는 다음과 같습니다.

| Meter | 타입 | Tag |
|---|---|---|
| `graph.io.runs` | Counter | `operation`, `format`, `status` |
| `graph.io.records` | Counter | `operation`, `format`, `kind` |
| `graph.io.bytes` | Counter | `operation`, `format` |
| `graph.io.duration` | Timer | `operation`, `format`, `status` |
| `graph.io.phase.duration` | Timer | `operation`, `format`, `phase` |
| `graph.io.active` | Gauge | `operation`, `format` |

tag는 `Locale.ROOT`로 소문자화한 고정 enum 값만 사용합니다. dataset 경로,
record ID, run ID, exception message는 기록하지 않습니다.
