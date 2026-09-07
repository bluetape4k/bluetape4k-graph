# GraphML property 타입 왕복 보존

## 배경

GraphML writer는 모든 property key를 `attr.type="string"`으로 선언하고 값을
`toString()`으로 기록했다. Reader에 scalar 타입 변환 기능이 있어도 exporter가
타입 정보를 버리므로 숫자와 불리언이 import 후 `String`으로 바뀌었다. 기존
typed round-trip 테스트도 생성된 element 수만 확인해 이 손실을 탐지하지 못했다.

## 결정

Node와 edge property는 서로 독립된 타입 표를 사용한다. 동일 key에서 관찰한
non-null 값이 `Int`, `Long`, `Float`, `Double`, `Boolean`, `String` 중 하나로
일관되면 해당 GraphML `attr.type`을 기록한다. Null 값은 추론에서 제외하되
`GraphExportOptions.includeEmptyProperties=true`이면 기존 호환성을 위해 빈
`<data>`를 기록하고, `false`이면 생략한다. 미지원 단일 타입은 `toString()`과
`string` 타입을 사용한다. 서로 다른 non-null JVM 타입이 한 key에 섞이면 XML
출력 전에 실패한다.

타입 추론은 exporter가 각 repository chunk를 disk spool에 기록하기 직전에
수행한다. 따라서 두 번째 backend 조회 없이 동일한 stage 시점 기준 데이터에서
GraphML header와 payload를 만든다는 기존 bounded export 계약을 유지한다.

## 결과

Sync, suspend, Virtual Thread exporter가 같은 writer 정책을 사용한다. 숫자와
불리언은 원래 JVM 타입으로 복원된다. Null은 `includeEmptyProperties`에 따라 빈
`<data>`를 기록하거나 생략하며, 기존 importer는 빈 data를 property 부재로 읽는다.
Mixed-type 오류에는 property scope, key, 타입 이름만 포함하고 실제 값은 노출하지
않는다.

## 검증

- 회귀 테스트 추가 직후 지원 타입, mixed 타입, null 처리 3건이 실패했다.
- 구현 후 `:bluetape4k-graph-io-graphml:test` 66개가 모두 통과했다.
- `:bluetape4k-graph-io-graphml:check`가 test, Detekt, Kover 검증을 통과했다.
- 신규 예외 테스트는 `io.bluetape4k.assertions.assertFailsWith`를 사용한다.

## 향후 지침

GraphML scalar 타입을 확장할 때는 reader coercion만 추가하지 말고 writer의 타입
추론과 sync/suspend bulk spool 경로를 함께 검증한다. Typed round-trip 테스트는
element 수뿐 아니라 property 값과 JVM 타입을 직접 비교해야 한다.

## 추적

- GitHub issue: [#614](https://github.com/bluetape4k/bluetape4k-graph/issues/614)
