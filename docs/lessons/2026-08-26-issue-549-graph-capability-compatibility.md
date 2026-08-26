# 이슈 #549 GraphCapability enum 호환성 교훈

## 배경

#536에서 `GraphCapability`에 `BOUNDED_CHUNKED_READ`와
`BOUNDED_CHUNKED_EXPORT`를 추가하면서 기존 enum ordinal은 보존했지만,
외부 Kotlin 소비자의 exhaustive `when`은 새 값과 함께 재컴파일 경계에 도달한다.
이름 기반 설정·저장·remote capability 목록도 구버전 binary가 새 이름을 읽을 때
`Enum.valueOf` 예외를 만들 수 있다.

## 결정

graph-core 내부의 유일한 exhaustive `when`은
`GraphCapabilities.defaultConstraints`로 inventory하고 enum과 함께 유지한다.
외부 소비자에는 다음 정책을 공개한다.

- 새 enum 값은 마지막에만 추가해 기존 ordinal을 보존한다.
- ordinal은 저장·전송하지 않고 enum `name`을 serialization key로 사용한다.
- capability를 `when`으로 처리할 때는 `else`를 두고 unknown을 unsupported로
  취급하며, 확인하지 않은 연산을 호출하지 않는다.
- 이름 입력은 `GraphCapability.fromSerializedNameOrNull`로 해석하고 알 수 없는
  미래 이름은 `null`로 받아 관찰·무시·fail-closed 중 소비자 정책을 적용한다.

이 helper는 신규 capability를 구버전에서 실행 가능하게 만드는 compatibility
shim이 아니다. source/binary compatibility 한계를 명시적으로 드러내는 parsing
경계다.

## 결과와 검증

- 기존 capability ordinal 0–8과 새 bounded capability ordinal 9–10을 회귀 테스트로
  고정했다.
- known name, 미래 이름, 대소문자 불일치 이름을 `bluetape4k.assertions`로 검증했다.
- graph-core EN/KO README와 CHANGELOG/WIP에 외부 소비자·release guidance를 추가했다.
- exhaustive inventory에서 backend/Spring/graph-io는 enum 전체 switch가 아닌
  `supports` 조회임을 확인했다.
- graph-core 352개 전체 test와 Detekt를 통과했다. TinkerGraph 5개,
  AGE·Neo4j·Memgraph·FalkorDB 각각 4개의 capability conformance를 현재
  stacked head에서 순차 검증했고 모두 0 skipped/0 failure였다.

## 후속 guard

새 `GraphCapability`를 추가할 때는 enum 마지막 배치, ordinal/name 회귀 테스트,
`defaultConstraints` exhaustive branch, EN/KO README와 release note를 같은 PR에서
갱신한다. 외부 예제나 adapter에서 enum 전체 `when`이 생기면 `else` unknown policy와
호환성 테스트를 먼저 추가한다. capability 지원 여부만 확인하는 코드는
`supports`를 사용하고, bounded 실행을 요구하는 caller는
`BOUNDED_CHUNKED_*`를 별도로 확인한다.
