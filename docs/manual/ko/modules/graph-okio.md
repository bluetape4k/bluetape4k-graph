# graph-okio


실행 방식: **release test fixture 연계형**이다. `sourceOps`, `targetOps`, 임시 OkIO path, 테스트 key는 fixture가 소유한다. `NegativePathTest`와 `OkioRoundTripTest`가 종료와 임시 파일 정리를 검증한다.

## 실행 전 준비

`graph-okio`는 그래프 형식을 OkIO `Source`, `Sink`, `Path`, `FileSystem`에 연결한다. 압축 연결, 원자적 path 쓰기, FakeFileSystem 테스트, 단일 stream 형식의 deterministic AEAD chunk 암호화를 제공한다. OkIO pipeline이 필요할 때 선택하고 단순 NIO path로 충분하면 추가하지 않는다. 근거는 [GraphIoOkioPaths.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-okio")
}
```

```kotlin
val path = "graph.ndjson.gz.daead".toPath()
val context = "tenant=acme;format=graph-0.5".encodeToByteArray()
val daead = TinkDaeads.AES256_SIV
val out = OkioGraphBulkExporter().exportGraphGzipDaead(
    OkioGraphExportSink.PathSink(path, FileSystem.SYSTEM, atomicWrite = true),
    GraphIoFormat.NDJSON_JACKSON3, daead, sourceOps, associatedData = context,
)
val input = OkioGraphBulkImporter().importGraphDaeadGzip(
    OkioGraphImportSource.PathSource(path, FileSystem.SYSTEM),
    GraphIoFormat.NDJSON_JACKSON3, daead, targetOps, associatedData = context,
)
check(out.verticesWritten == input.verticesCreated)
```

예상 순서는 그래프 bytes → gzip → DAEAD chunk → 임시 path → 원자적 이동이며, 읽을 때는 거꾸로 처리한다.

## 자원과 보안 경계

path 형식은 library가 닫는다. `SourceBased`와 `SinkBased`는 기본적으로 호출자가 닫고, `ownsSource` 또는 `ownsSink`가 true일 때만 library가 닫는다. associated data는 정확히 같아야 한다. deterministic AEAD는 같은 key와 context에서 같은 chunk의 동일성을 드러낼 수 있다. ciphertext와 압축 해제 크기 한도를 설정한다.

CSV는 두 파일 형식이므로 상위 DAEAD helper가 거부한다. 두 파일의 key, associated data, 이름, 공개 순서를 먼저 설계하지 않고 낮은 단계 wrapper를 쓰면 안 된다.

## 운영 점검

- report 수와 실제 그래프 수를 비교한다.
- 파일 형식, batch 크기, 정책, 실패 단계를 기록한다.
- 입력 크기와 buffer 상한을 둔다.
- 문서에 정한 경계에서 library 소유 path와 호출자 소유 stream을 닫는다.

## 실패와 복구

증상: 인증, 압축 해제, 원자적 이동이 실패한다. 손상된 입력을 재시도하지 않는다. 이전 파일을 보존하고 확인된 임시 파일만 지운 뒤 key/context 또는 제한값을 고쳐 깨끗한 입력으로 다시 시작한다.

```bash
./gradlew :bluetape4k-graph-okio:test --tests '*GraphIoOkioPathsTest' --tests '*NegativePathTest' --tests '*OkioRoundTripTest'
```

## 기대 결과

예상 결과는 잘못된 associated data와 잘린 암호문이 record 처리 전에 실패하고, 크기 한도가 지켜지며, 쓰기 실패 시 이전 파일이 남는 것이다. key와 평문은 log에 남기지 않는다.

## 완전한 release 예제

고정된 [NegativePathTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/NegativePathTest.kt)가 모든 fixture 변수를 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-okio:test --tests '*NegativePathTest'
```

예상 결과는 왕복 또는 실패 경로 검증이 통과하고 fixture 소유 자원이 모두 닫히는 것이다.

## 하지 않는 일과 관련 문서

[OkIO 보안](../graph-io/okio-security.md), [파일 형식과 외부 ID](../graph-io/formats.md), [운영](../guides/operations.md)을 참고한다. 이 모듈은 key를 관리하거나 암호화된 CSV 묶음 형식을 정의하지 않는다.
