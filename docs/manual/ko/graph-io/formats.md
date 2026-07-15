# 파일 형식과 외부 ID

| 형식 | 파일 경계 | 알맞은 용도 | 검증 근거 |
|---|---|---|---|
| CSV | 정점·간선 파일 한 쌍 | 표 형태 교환과 육안 확인 | [`CsvRoundTripTest.kt`](../../../../graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvRoundTripTest.kt) |
| Jackson 2 NDJSON | 레코드 단일 스트림 | Jackson 2 애플리케이션 | [`Jackson2RoundTripTest.kt`](../../../../graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2RoundTripTest.kt) |
| Jackson 3 NDJSON | 레코드 단일 스트림 | Jackson 3 애플리케이션 | [`Jackson3RoundTripTest.kt`](../../../../graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3RoundTripTest.kt) |
| GraphML | XML 그래프 문서 | 그래프 도구 간 교환 | [`GraphMlRoundTripTest.kt`](../../../../graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlRoundTripTest.kt) |

외부 ID는 가져오기 과정에서 정점과 간선을 연결하는 값이지, 백엔드의 `GraphElementId` 형식을 보장하는 값이 아니다. importer가 외부 ID와 새 정점 ID를 연결한 뒤 간선 끝점을 찾는다. [`GraphIoExternalIdMap.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMap.kt)와 [`GraphIoExternalIdMapTest.kt`](../../../../graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMapTest.kt)를 확인한다.

전송 전에 속성 형식 변환, 중복 외부 ID 처리, 간선 순서, 문자셋, 잘못된 레코드 정책을 정한다. 전송 뒤에는 report 수치, 속성 표본, 찾지 못한 끝점, 다른 형식과의 왕복 결과를 본다. NDJSON은 정점보다 먼저 나온 간선을 잠시 보관하므로 한도 초과 실패도 시험해야 한다. [`Jackson3EdgeBufferOverflowTest.kt`](../../../../graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3EdgeBufferOverflowTest.kt)가 이 경계를 보여 준다.
