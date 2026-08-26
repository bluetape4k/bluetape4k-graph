# #560 Serializable option invariant TCK lesson

## 결정

Java serialization은 Kotlin 생성자와 `init`을 우회할 수 있으므로,
`Serializable` option을 선언하는 것만으로는 public invariant가 보장되지 않는다.
각 concrete option의 private `readObject`에서 `defaultReadObject` 뒤 생성자와 같은
조건을 다시 검사하고, malformed payload는 `InvalidObjectException`으로 거부한다.

## 적용한 패턴

- constructor guard와 deserialization guard의 조건·메시지를 같은 표로 관리한다.
- non-null Kotlin property도 serialization payload에서는 `null`이 될 수 있으므로
  `direction`, `missingWeightPolicy` 같은 필드를 명시적으로 확인한다.
- `MissingWeightPolicy.UseDefault`를 nested payload로 검증해 outer
  `PathOptions`만 검사하는 실수를 막는다.
- `serialVersionUID = 1L`은 `ObjectStreamClass` TCK로 고정하고 public property
  round-trip equality를 함께 확인한다.
- malformed payload 재현은 test-only `Unsafe` forge로 격리하고 production code에는
  unsafe API를 추가하지 않는다.

## 검증에서 배운 점

1. 정상 round-trip만 통과하면 constructor bypass 결함을 발견할 수 없다. 먼저
   invalid serialized payload RED test를 작성해야 한다.
2. `readObject`가 private이어야 Java serialization hook으로 동작하며 public API/ABI에
   노출되지 않는다. `javap`와 runtime TCK를 함께 사용하면 descriptor와 실제 호출을
   분리해서 확인할 수 있다.
3. `tolerance = 0.0`은 full-iteration 모드로 허용할 수 있으므로 finite/non-negative
   정책과 positive-only 정책을 혼동하지 않는다.
4. invariant 오류 타입은 constructor의 `IllegalArgumentException`과 stream 경계의
   `InvalidObjectException`을 구분해야 호출자가 원인을 정확히 분류할 수 있다.

## 후속 규칙

- 새 Serializable data class를 추가할 때 constructor, `readObject`, round-trip,
  forged payload, UID, EN/KO 문서를 같은 change slice에 포함한다.
- untrusted stream의 허용 여부를 이 invariant TCK의 성공으로 간주하지 않는다.
  호출자는 `ObjectInputFilter`를 별도로 구성해야 한다.
- PR receipt는 exact head와 hosted terminal run을 함께 기록하고, stacked train의
  최종 merge 전까지 선행 PR을 독립 병합하지 않는다.
