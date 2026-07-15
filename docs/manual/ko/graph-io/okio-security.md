# OkIO 압축과 파일 보안

`graph-okio`는 graph 형식을 OkIO `Source`, `Sink`, `Path`, `FileSystem`에 연결한다. [`Compressor.kt`](../../../../graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/Compressor.kt)에 정의된 GZIP, DEFLATE, LZ4, SNAPPY, ZSTD, BZIP2를 streaming 방식으로 처리한다.

단일 스트림인 NDJSON과 GraphML은 DAEAD chunk 도우미로 인증·암호화할 수 있다. 내보낼 때는 먼저 압축하고 암호화하며, 가져올 때는 복호화한 다음 압축을 푼다. associated data는 양쪽이 같아야 한다. 결정적 암호화는 같은 키와 문맥에서 같은 평문 chunk의 동일성을 드러낼 수 있으므로 사용 전에 이 특성을 받아들일지 결정한다. 정확한 순서와 크기 제한은 [`GraphIoOkioPaths.kt`](../../../../graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt)에 있다.

고수준 DAEAD 함수는 파일 두 개가 필요한 CSV를 거부한다. 두 파일의 키, associated data, 이름, 원자적 공개 방식을 직접 정한 경우에만 저수준 wrapper를 조합한다. [`OkioRoundTripTest.kt`](../../../../graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/OkioRoundTripTest.kt)가 이 실패 경계를 검증한다.

잘못된 associated data, 잘린 암호문과 압축 스트림, 압축 해제 한도, XXE 차단, source/sink 소유권, atomic write 정리를 반드시 시험한다. 릴리스 근거는 [`GraphIoOkioPathsTest.kt`](../../../../graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPathsTest.kt)와 [`NegativePathTest.kt`](../../../../graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/NegativePathTest.kt)다.
