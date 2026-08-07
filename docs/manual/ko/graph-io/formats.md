# 파일 형식과 외부 ID

![graph-io 처리 흐름](../../assets/graph-io/graph-io-pipeline.png)

| 형식 | 파일 경계 | 알맞은 용도 | 검증 근거 |
|---|---|---|---|
| CSV | 정점·간선 파일 한 쌍 | 표 형태 교환과 육안 확인 | [`CsvRoundTripTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvRoundTripTest.kt) |
| Jackson 2 NDJSON | 레코드 단일 스트림 | Jackson 2 애플리케이션 | [`Jackson2RoundTripTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2RoundTripTest.kt) |
| Jackson 3 NDJSON | 레코드 단일 스트림 | Jackson 3 애플리케이션 | [`Jackson3RoundTripTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3RoundTripTest.kt) |
| GraphML | XML 그래프 문서 | 그래프 도구 간 교환 | [`GraphMlRoundTripTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlRoundTripTest.kt) |

외부 ID는 가져오기 과정에서 정점과 간선을 연결하는 값이지, 백엔드의 `GraphElementId` 형식을 보장하는 값이 아니다. importer가 외부 ID와 새 정점 ID를 연결한 뒤 간선 끝점을 찾는다. [`GraphIoExternalIdMap.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMap.kt)와 [`GraphIoExternalIdMapTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMapTest.kt)를 확인한다.

전송 전에 속성 형식 변환, 중복 외부 ID 처리, 간선 순서, 문자셋, 잘못된 레코드 정책을 정한다. 전송 뒤에는 report 수치, 속성 표본, 찾지 못한 끝점, 다른 형식과의 왕복 결과를 본다. NDJSON은 정점보다 먼저 나온 간선을 잠시 보관하므로 한도 초과 실패도 시험해야 한다. [`Jackson3EdgeBufferOverflowTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3EdgeBufferOverflowTest.kt)가 이 경계를 보여 준다.

## 두 CSV 파일을 만든다

```csv
id,label,name
u1,Person,Alice
u2,Person,Bob
```

```csv
id,label,from,to,since
e1,KNOWS,u1,u2,2024
```

```kotlin
val source = CsvGraphImportSource(
    GraphImportSource.PathSource(Paths.get("vertices.csv")),
    GraphImportSource.PathSource(Paths.get("edges.csv")),
)
val report = CsvGraphBulkImporter().importGraph(
    source, ops,
    GraphImportOptions(
        onDuplicateVertexId = DuplicateVertexPolicy.SKIP,
        onMissingEdgeEndpoint = MissingEndpointPolicy.FAIL,
        preserveExternalIdProperty = "sourceId",
    ),
)
check(report.verticesCreated == 2L && report.edgesCreated == 1L)
```

`CsvGraphBulkExporter().exportGraph(CsvGraphExportSink(...), ops, GraphExportOptions(setOf("Person"), setOf("KNOWS")))`로 다시 내보낸다. header와 정점 2·간선 1의 기록 수를 확인한다.

## 실패를 넣고 원인을 찾는다

`u1`을 한 번 더 넣으면 `SKIP` 정책에서 `PARTIAL`과 `skippedVertices`가 보여야 한다. 간선의 `to`를 `missing`으로 바꾸면 `FAIL` 정책은 `READ_EDGE` 단계에서 실패한다. NDJSON에서 정점보다 먼저 나온 간선을 `maxEdgeBufferSize`보다 많이 넣으면 메모리를 계속 쓰지 않고 overflow로 실패해야 한다. 재시도 전에 policy와 failure phase를 확인하고, 이미 만든 정점은 보존한 외부 ID로 대조한다.
