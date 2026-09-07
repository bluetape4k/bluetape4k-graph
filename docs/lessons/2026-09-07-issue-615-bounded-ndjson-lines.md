# NDJSON codec 이전 줄 길이 제한

## 배경

Jackson2와 Jackson3 NDJSON reader는 `bufferedReader().lineSequence()`로 한 줄
전체를 만든 뒤 codec에 전달했다. 개행이 없거나 비정상적으로 긴 입력은 JSON
파싱이 시작되기 전에 큰 문자열을 할당할 수 있었고, sync, suspend, Virtual
Thread, Flow 경로에 공통된 입력 경계가 없었다.

## 결정

`graph-io/core`에 immutable `NdJsonReadOptions`를 두고 `maxLineChars`를 양수로
검증한다. 기본값은 기존 호출자의 입력 호환성을 위해 `Int.MAX_VALUE`로 유지한다.
Jackson2와 Jackson3의 모든 importer 및 Flow reader는 bluetape4k-io의
`Reader.boundedLineReader`를 재사용해 Jackson codec 호출 전에 같은 제한을
적용한다. 길이는 JVM `String.length`와 같은 UTF-16 code unit 단위다.

설정값은 checkpoint identity에 포함한다. 다른 상한으로 시작한 실행은 기존
checkpoint를 이어받지 못하므로 재개 과정에서 입력 정책이 조용히 바뀌지 않는다.
제한 초과 오류에는 phase, file role, line 번호와 허용 상한만 기록하고 payload와
source path는 포함하지 않는다.

## 결과

LF, CRLF, CR과 종결 문자가 없는 마지막 줄에 동일한 경계가 적용된다. reader는
상한을 확인하는 데 필요한 범위를 넘어서 전체 source를 소비하지 않는다. Flow는
빈 줄만 연속되는 입력에서도 coroutine 취소를 관찰한다. owned source의 close가
함께 실패하면 줄 길이 오류를 primary로 유지하고 close 오류를 suppressed로
보존한다.

기존 no-arg importer constructor와 Flow reader의 `(String, String)` constructor는
그대로 유지하고, Java 호출자를 위한 `NdJsonReadOptions` constructor를 추가했다.

## 검증

- 경계값 `limit - 1`, `limit`, `limit + 1`과 Unicode/줄 종결자 조합을 검증했다.
- sync, suspend, Virtual Thread, Flow와 checkpoint mismatch 경로를 검증했다.
- Jackson2와 Jackson3의 제한 초과·취소·close suppression 회귀 테스트를 통과했다.
- 기존 public constructor와 method descriptor는 `javap`로 비교했다.
- 신규 예외 테스트는 `io.bluetape4k.assertions.assertFailsWith`를 사용한다.

## 향후 지침

레코드 단위 포맷의 입력 제한은 codec 안이나 importer 뒤쪽이 아니라 source와
codec 사이에 둔다. 실행 모델별 wrapper를 따로 구현하지 말고 공통 reader를
재사용하며, 제한 정책을 변경하는 옵션은 checkpoint identity에도 반영한다.

## 추적

- GitHub issue: [#615](https://github.com/bluetape4k/bluetape4k-graph/issues/615)
