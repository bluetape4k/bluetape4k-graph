# graph-okio

## 선택 기준

`graph-okio`는 graph 형식을 OkIO `Source`, `Sink`, `Path`, `FileSystem`에 연결한다. 압축 연결, 원자적 path 쓰기, FakeFileSystem 테스트, 단일 stream 형식의 deterministic AEAD chunk 암호화를 제공한다. OkIO pipeline이 필요할 때 선택하고 단순 NIO path로 충분하면 추가하지 않는다. 근거는 [GraphIoOkioPaths.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt)다.

## 의존성과 실행

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

예상 순서는 graph bytes → gzip → DAEAD chunk → 임시 path → 원자적 이동이며, 읽을 때는 거꾸로 처리한다.

## 자원과 보안 경계

path 형식은 library가 닫는다. `SourceBased`와 `SinkBased`는 기본적으로 호출자가 닫고, `ownsSource` 또는 `ownsSink`가 true일 때만 library가 닫는다. associated data는 정확히 같아야 한다. deterministic AEAD는 같은 key와 context에서 같은 chunk의 동일성을 드러낼 수 있다. ciphertext와 압축 해제 크기 한도를 설정한다.

CSV는 두 파일 형식이므로 상위 DAEAD helper가 거부한다. 두 파일의 key, associated data, 이름, 공개 순서를 먼저 설계하지 않고 낮은 단계 wrapper를 쓰면 안 된다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-okio:test --tests '*GraphIoOkioPathsTest' --tests '*NegativePathTest' --tests '*OkioRoundTripTest'
```

예상 결과는 잘못된 associated data와 잘린 암호문이 record 처리 전에 실패하고, 크기 한도가 지켜지며, 쓰기 실패 시 이전 파일이 남는 것이다. key와 평문은 log에 남기지 않는다.

## 관련 문서와 하지 않는 일

[OkIO 보안](../graph-io/okio-security.md), [파일 형식과 외부 ID](../graph-io/formats.md), [운영](../guides/operations.md)을 참고한다. 이 모듈은 key를 관리하거나 암호화된 CSV 묶음 형식을 정의하지 않는다.
