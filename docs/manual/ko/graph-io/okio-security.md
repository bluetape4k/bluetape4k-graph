# OkIO 압축과 파일 보안

![graph-io 처리 흐름](../../assets/graph-io/graph-io-pipeline.png)

`graph-okio`는 graph 형식을 OkIO `Source`, `Sink`, `Path`, `FileSystem`에 연결한다. [`Compressor.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/Compressor.kt)에 정의된 GZIP, DEFLATE, LZ4, SNAPPY, ZSTD, BZIP2를 streaming 방식으로 처리한다.

단일 스트림인 NDJSON과 GraphML은 DAEAD chunk 도우미로 인증·암호화할 수 있다. 내보낼 때는 먼저 압축하고 암호화하며, 가져올 때는 복호화한 다음 압축을 푼다. associated data는 양쪽이 같아야 한다. 결정적 암호화는 같은 키와 문맥에서 같은 평문 chunk의 동일성을 드러낼 수 있으므로 사용 전에 이 특성을 받아들일지 결정한다. 정확한 순서와 크기 제한은 [`GraphIoOkioPaths.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt)에 있다.

고수준 DAEAD 함수는 파일 두 개가 필요한 CSV를 거부한다. 두 파일의 키, associated data, 이름, 원자적 공개 방식을 직접 정한 경우에만 저수준 wrapper를 조합한다. [`OkioRoundTripTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/OkioRoundTripTest.kt)가 이 실패 경계를 검증한다.

잘못된 연관 데이터, 잘린 암호문과 압축 스트림, 압축 해제 한도, XXE 차단, 입출력 소유권, 원자적 쓰기 정리를 반드시 시험한다. 릴리스 근거는 [`GraphIoOkioPathsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPathsTest.kt)와 [`NegativePathTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/NegativePathTest.kt)다.

## 압축하고 인증 암호화한다

```kotlin
val fs = FileSystem.SYSTEM
val path = "graph.ndjson.gz.daead".toPath()
val context = "tenant=acme;format=graph-0.5".encodeToByteArray()
val daead = TinkDaeads.AES256_SIV

val out = OkioGraphBulkExporter().exportGraphGzipDaead(
    OkioGraphExportSink.PathSink(path, fs, atomicWrite = true),
    GraphIoFormat.NDJSON_JACKSON3, daead, sourceOps,
    GraphExportOptions(setOf("Person"), setOf("KNOWS")), associatedData = context,
)
val input = OkioGraphBulkImporter().importGraphDaeadGzip(
    OkioGraphImportSource.PathSource(path, fs),
    GraphIoFormat.NDJSON_JACKSON3, daead, targetOps,
    associatedData = context, maxCiphertextLength = 64L * 1024 * 1024,
    maxDecompressedBytes = 256L * 1024 * 1024,
)
check(out.verticesWritten == input.verticesCreated)
```

쓰기 순서는 graph bytes → GZip → DAEAD chunk → 임시 파일 → atomic move다. 읽을 때는 반대로 처리한다.

## 음수 경로를 확인한다

연관 데이터를 `wrong`으로 바꾸면 레코드를 받기 전에 인증 실패가 나야 한다. 암호문이나 gzip을 자르면 I/O·인증 오류가 나야 하고, 두 크기 제한을 페이로드보다 작게 잡으면 메모리를 계속 늘리지 않고 실패해야 한다. `atomicWrite=true`에서 출력 쓰기 오류를 주입하면 기존 대상은 그대로이고 임시 파일은 없어야 한다. 결과가 다르면 DB 재시도부터 하지 말고 래퍼 순서와 자원 소유권을 확인한다.

```bash
./gradlew :bluetape4k-graph-okio:test --tests '*GraphIoOkioPathsTest' --tests '*NegativePathTest' --tests '*OkioRoundTripTest'
```
