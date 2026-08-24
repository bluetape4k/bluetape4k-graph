# Issue #540: graph-core option validation lesson

## 맥락

`NeighborOptions.maxDepth`와 `ComponentOptions.minSize`는 계산기 내부에서
늦게 검증되거나 검증되지 않아 invalid 상태를 public option 객체가 표현할 수
있었다. `PathOptions`와 `BfsDfsOptions`의 zero-depth 의미는 runner·weighted
path 실행 경로와 일치하도록 보존하고, `CycleOptions`는 zero-depth를 거부해야
했으며 PageRank의 `tolerance`도 `NaN`·무한대 경계를 명시하지 않았다.

## 결정

option 생성 시점에 계약을 고정했다. neighbor `maxDepth=0`은 이웃 미확장,
path `maxDepth=0`은 vertex-only path, BFS/DFS `maxDepth=0`은 시작 정점만
반환하는 의미를 유지한다. cycle depth와 양수 한도에는 `requirePositiveNumber`,
zero-depth가 유효한 depth에는 `requireZeroOrPositiveNumber`를 사용한다. 유한
실수 계약은 Bluetape `requireFinite`를 먼저 적용한다. 예외 회귀는
`io.bluetape4k.assertions.assertFailsWith`로 작성해 repository의 assertion
계약을 유지한다.

## 결과와 검증

- RED 테스트에서 `NeighborOptions(-1)`와 `ComponentOptions(0)`이 예외를
  던지지 않는 결함을 재현했고, cycle zero-depth serialization 경계를 확인했다.
- 수정 후 option·runner·serialization 회귀와 graph-core 전체 검증을 fresh run으로
  다시 확인한다.
- graph-core `compileKotlin`·`detekt`와 `git diff --check`를 다시 확인한다.
- 영문·한국어 README가 같은 validation 범위와 `IllegalArgumentException`
  계약을 설명한다.

## 놀라움과 다음 방어선

`requirePositiveNumber`는 일반적인 양수 검증에는 적합하지만 `Double.POSITIVE_INFINITY`
까지 허용할 수 있으므로 실수 option에는 finite 검사를 별도로 두어야 했다.
finite-number helper가 이미 Bluetape support에 있으므로 `requireFinite`와
`requireInRange`를 조합해 공용 계약을 재사용했다. weighted path depth와
backend conformance TCK는 [#559](https://github.com/bluetape4k/bluetape4k-graph/issues/559),
serialization invariant TCK는 [#560](https://github.com/bluetape4k/bluetape4k-graph/issues/560)으로
연결한다.

## SPW 게이트

SPW-01~SPW-05를 모두 통과했다. 근거는 #540 live issue, 현재 option source와
tests, graph-core README 두 locale, fresh Gradle 결과 및 7-Tier review다.
