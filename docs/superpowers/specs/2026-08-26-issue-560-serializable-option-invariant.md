# #560 Serializable option 역직렬화 invariant 설계

## 목표

`GraphTraversalOptions`와 `GraphAlgorithmOptions`의 Java serialization 경계가
생성자 `init` 검증을 우회하지 않도록 한다. 정상 옵션은 public property와
`serialVersionUID` 계약을 유지한 채 round-trip하고, 변조된 payload는 안전하게
거부한다.

## 범위

- traversal: `NeighborOptions`, `PathOptions`, `BfsDfsOptions`, `CycleOptions`
- algorithm: `PageRankOptions`, `DegreeOptions`, `ComponentOptions`
- 중첩 정책: `MissingWeightPolicy.UseDefault`
- public constructor/signature와 기존 `serialVersionUID = 1L`은 유지한다.

## 결정

각 concrete serializable class에 private `readObject(ObjectInputStream)`를 둔다.
`defaultReadObject()` 직후 constructor와 동일한 invariant를 검사하고, 실패하면
`InvalidObjectException`에 기존 invariant 메시지를 담아 반환하지 않는다. 정상
생성 경계는 기존처럼 `IllegalArgumentException`을 사용한다.

검사 정책은 다음과 같다.

| 모델 | 역직렬화 invariant |
| --- | --- |
| `NeighborOptions` | `maxDepth >= 0`, `direction != null` |
| `PathOptions` | `maxDepth >= 0`, `maxVisited > 0`, `direction != null`, `missingWeightPolicy != null` |
| `BfsDfsOptions` | `maxDepth >= 0`, `maxVertices > 0`, `direction != null` |
| `CycleOptions` | `maxDepth >= 0`, `maxCycles > 0` |
| `PageRankOptions` | `iterations > 0`, `dampingFactor ∈ [0,1]`, `tolerance >= 0`이고 finite, `topK > 0` |
| `DegreeOptions` | `direction != null` |
| `ComponentOptions` | `minSize > 0` |
| `UseDefault` | `value > 0.0`이고 finite |

`PageRankOptions.tolerance`와 `ComponentOptions.minSize`, `NeighborOptions`의
`maxDepth`는 기존 구현이 명시적으로 보장하지 않았지만, public KDoc의 안전한
option 계약과 계산기의 유효 입력을 맞추기 위해 생성자와 역직렬화 양쪽에 같은
검사를 둔다. tolerance는 `0.0`을 허용해 계산기의 전체 iteration 모드를 보존한다.

## 예외 계약

- 생성자 입력 오류: `IllegalArgumentException`, 기존 메시지 유지
- 변조된 serialized payload: `InvalidObjectException`, 동일 field명·값을 포함한
  기존 메시지 유지
- nested `UseDefault` 오류도 outer `PathOptions`가 소비하기 전에 같은 방식으로
  거부한다.

## 보안·호환성 경계

Java serialization은 신뢰할 수 없는 입력을 위한 포맷이 아니므로 allow-list
`ObjectInputFilter`를 대체하지 않는다. 이번 변경은 이미 public
`Serializable`로 노출된 option의 invariant 복구에 한정하며, public ABI와 native
backend query semantics는 바꾸지 않는다.
