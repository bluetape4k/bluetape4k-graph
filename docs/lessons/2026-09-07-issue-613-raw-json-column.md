# CSV RawJsonColumn 왕복 보존

## 결정

`CsvPropertyMode.RawJsonColumn`은 정점·간선의 전체 속성 맵을 설정한 단일
컬럼에 JSON object로 기록하고, 같은 codec으로 임포트한다. `GraphIoRecordSpool`이
일반 CSV 속성을 문자열로 정규화하는 경계를 넘기 전에
`Jackson.defaultJsonMapper`로 payload를 고정해 중첩 map/list와 null이
문자열 표현으로 손실되지 않도록 했다.

## 검증

- sync와 suspend exporter/importer가 custom column 이름을 포함한 헤더를 만든다.
- scalar, null, 중첩 map, list, 따옴표, 쉼표, 개행 값을 양방향 왕복한다.
- 빈 속성은 `{}`로 기록한다.
- malformed JSON과 object가 아닌 JSON은 `IllegalArgumentException`으로 실패한다.
- 새 테스트는 `io.bluetape4k.assertions.assertFailsWith`와 Bluetape matcher를 사용한다.

## 주의점

RawJson spool payload는 내부 제어 키로 한 번 감싸진다. 이 키는 최종 CSV
헤더에 노출되지 않으며, 사용자 속성 키가 내부 예약 키와 충돌하면 export를
명시적으로 거부한다.

## 추적

- GitHub issue: [#613](https://github.com/bluetape4k/bluetape4k-graph/issues/613)
- Stacked base: PR [#619](https://github.com/bluetape4k/bluetape4k-graph/pull/619)
