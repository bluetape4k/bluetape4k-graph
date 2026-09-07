# Issue #615 NDJSON 줄 길이 상한 설계

## 목표

Jackson2·Jackson3 NDJSON 입력이 JSON codec 호출 전에 한 줄 전체를 무제한 할당하지 않도록
`bluetape4k-io`의 `Reader.boundedLineReader`를 재사용한다. 기존 사용자는 설정하지 않으면
동일한 입력을 계속 처리할 수 있고, 제한을 선택한 사용자는 sync, suspend, Virtual Thread,
Flow 경로에서 같은 UTF-16 code unit 상한과 안전한 오류 계약을 얻는다.

## 현재 근거

- `Jackson2RecordParser`와 `Jackson3RecordParser`는 `BufferedReader.readLine()`으로 줄 전체를 읽는다.
- sync importer와 Flow reader는 각 parser를 직접 사용하고 suspend importer는 parser의 cold `Flow`를 사용한다.
- Virtual Thread importer는 sync importer를 감싼다.
- `graph-io-core`의 `bt4k.bluetape4k.io`는 현재
  `io.github.bluetape4k:bluetape4k-io:2.1.0-SNAPSHOT:20260906.172302-4`로 해석된다.
- `BoundedLineReader`는 LF, CRLF, CR, EOF를 처리하고 상한을 넘기기 위해 필요한 한 code unit까지만 요청한다.
- 기존 public JVM 표면에는 importer의 no-arg constructor와 Flow reader의
  `(String, String)`, default-mask, no-arg constructor가 있다.

## 검토한 접근

### 1. `GraphImportOptions` primary constructor 확장

모든 importer가 같은 옵션을 받는 장점은 있지만 data class의 constructor, `copy`, `componentN`,
default-mask ABI가 바뀐다. NDJSON 전용 제한 때문에 공통 옵션 ABI를 깨므로 채택하지 않는다.

### 2. 공통 `NdJsonReadOptions`와 호환 constructor 추가

`graph-io-core`에 immutable `NdJsonReadOptions(maxLineChars)`를 추가하고 Jackson2·3의 public
reader/importer에 주입한다. importer의 기존 no-arg constructor와 Flow reader의 기존 constructor는
그대로 남긴다. 두 codec adapter는 같은 `Reader.boundedLineReader`만 사용한다. 이 접근을 채택한다.

### 3. 내부 고정 상한

구현 범위는 가장 작지만 기존 입력을 예고 없이 거부하거나 사실상 제한이 없는 값만 선택해야 한다.
사용자가 위험도와 입력 계약에 맞춰 상한을 정할 수 없으므로 채택하지 않는다.

## API와 호환성

`NdJsonReadOptions`는 `Serializable` data class로 제공한다.

```kotlin
data class NdJsonReadOptions(
    val maxLineChars: Int = UNLIMITED_MAX_LINE_CHARS,
) : Serializable
```

`maxLineChars`는 양수이며 UTF-16 code unit 수를 뜻한다. 기본값 `Int.MAX_VALUE`는 기존 입력
호환성을 유지한다. data class이므로 constructor, `copy`, equality, serialization을 직접 검증한다.

Jackson2·3의 sync, suspend, Virtual Thread importer는 optional constructor parameter로 옵션을
받는다. 모든 parameter에 기본값이 있으므로 기존 public no-arg JVM constructor를 유지한다.
Flow reader는 기존 primary constructor를 유지하고, `NdJsonReadOptions`를 첫 parameter로 받는
`@JvmOverloads` secondary constructor를 추가한다. 이 방식은 기존 `(String, String)`, default-mask,
no-arg constructor를 바꾸지 않는다.

## 처리 흐름

1. public adapter가 immutable `NdJsonReadOptions` snapshot을 내부 parser에 전달한다.
2. parser가 `GraphIoPaths.openReader(source)`로 ownership-aware `Reader`를 연다.
3. parser가 이 reader를 `boundedLineReader(maxLineChars)`로 감싸고 한 줄씩 읽는다.
4. 제한 안의 줄만 trim 후 codec에 전달한다.
5. `LineLimitExceededException`은 현재 line과 caller phase를 가진 `GraphIoFailure`로 변환한다.
6. sync importer는 report를 `FAILED`로 끝내고, Flow/suspend 경로는 기존 `GraphIoReadException`
   redaction을 유지한다.
7. checkpoint identity에는 `maxLineChars`를 포함해 서로 다른 제한으로 만든 checkpoint 재개를 거부한다.

## 오류와 보안 계약

- 제한 초과 위치는 `line:N`, file role은 `UNIFIED`다.
- bulk report의 메시지는 설정 이름과 상한만 포함한다.
- `GraphIoReadException`은 기존 redaction을 거쳐 phase/location만 외부에 노출한다.
- payload, external ID, source path, codec 원문, `LineLimitExceededException` cause는 노출하지 않는다.
- reader와 input ownership은 `GraphIoPaths`에 남겨 caller-owned source를 닫지 않고 owned source를 한 번 닫는다.

## Coroutine과 자원 수명

Flow parser는 record를 방출할 때뿐 아니라 각 줄을 읽기 전에도 coroutine context의 active 상태를
확인한다. 따라서 빈 줄만 계속되는 입력에서도 취소가 관찰된다. `CancellationException`은 failure로
변환하지 않고 그대로 전파하며, `use`가 source close 계약을 수행한다.

## 검증 계약

- `NdJsonReadOptions`: 기본값, 양수 검증, `copy`, Java-visible constructor, serialization
- Jackson2·3: 상한-1, 상한, 상한+1
- generated no-newline 입력: 전체 source 소비 전에 제한 초과
- LF, CRLF, CR, Unicode: UTF-16 code unit 기준과 line number
- Flow: phase/location/fileRole redaction, caller-owned/owned close, 빈 줄 취소
- sync/suspend/Virtual Thread: 같은 제한 적용과 정상 vertex/edge round-trip
- checkpoint: 같은 제한 재개 성공, 다른 제한 재개 충돌
- ABI: 변경 전 `javap`에 있던 기존 constructor/method signature가 변경 후에도 모두 존재

## 범위 밖

- CSV/GraphML line 또는 element 제한
- byte 단위 상한
- 자동 상한 강제나 기본값 축소
- 새로운 JSON codec 또는 별도 bounded scanner

## 승인 근거

사용자가 milestone `1.1.0` 이슈 전체의 순차 stacked PR 실행을 승인했고, #615에 대해
기본 unlimited, format-specific immutable option, 기존 `GraphImportOptions` ABI 보존 설계가
선행 검토에서 확정됐다.
