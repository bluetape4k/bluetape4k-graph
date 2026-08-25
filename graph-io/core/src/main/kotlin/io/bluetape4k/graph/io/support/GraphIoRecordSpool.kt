package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections

private const val DEFAULT_MAX_RECORD_BYTES = 128 * 1024 * 1024
private const val INITIAL_PAYLOAD_BUFFER_BYTES = 8 * 1024

private data class SpoolResources(
    val vertexFile: Path,
    val edgeFile: Path,
    val vertexOutput: DataOutputStream,
    val edgeOutput: DataOutputStream,
    val maxRecordBytes: Int,
)

private fun createSpoolFile(prefix: String, suffix: String): Path = Files.createTempFile(prefix, suffix)

private fun openSpoolOutput(file: Path): DataOutputStream =
    DataOutputStream(BufferedOutputStream(Files.newOutputStream(file)))

@Suppress("TooGenericExceptionCaught")
private fun openSpoolResources(
    maxRecordBytes: Int,
    createTempFile: (String, String) -> Path,
    openOutput: (Path) -> DataOutputStream,
): SpoolResources {
    require(maxRecordBytes > 0) { "maxRecordBytes must be positive" }

    var vertexFile: Path? = null
    var edgeFile: Path? = null
    var vertexOutput: DataOutputStream? = null
    var edgeOutput: DataOutputStream? = null
    try {
        vertexFile = createTempFile("bluetape4k-graph-io-vertices-", ".spool")
        vertexOutput = openOutput(vertexFile)
        edgeFile = createTempFile("bluetape4k-graph-io-edges-", ".spool")
        edgeOutput = openOutput(edgeFile)
        return SpoolResources(
            vertexFile = vertexFile,
            edgeFile = edgeFile,
            vertexOutput = vertexOutput,
            edgeOutput = edgeOutput,
            maxRecordBytes = maxRecordBytes,
        )
    } catch (error: Throwable) {
        listOf(edgeOutput, vertexOutput).forEach { output ->
            try {
                output?.close()
            } catch (cleanupFailure: Throwable) {
                error.addSuppressed(cleanupFailure)
            }
        }
        listOf(edgeFile, vertexFile).forEach { file ->
            try {
                file?.let(Files::deleteIfExists)
            } catch (cleanupFailure: Throwable) {
                error.addSuppressed(cleanupFailure)
            }
        }
        throw error
    }
}

private class CappedByteArrayOutputStream(
    private val maxBytes: Int,
) : ByteArrayOutputStream(minOf(maxBytes, INITIAL_PAYLOAD_BUFFER_BYTES)) {

    override fun write(value: Int) {
        requireCapacity(1)
        super.write(value)
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        requireCapacity(length)
        super.write(buffer, offset, length)
    }

    private fun requireCapacity(incomingBytes: Int) {
        require(incomingBytes <= maxBytes - size()) {
            "A graph-io spool record exceeds the ${maxBytes} byte limit"
        }
    }
}

/**
 * 대용량 graph-io export를 위한 디스크 기반 레코드 spool이다.
 *
 * 입력은 한 번만 소비하고, 출력 헤더 계산과 본문 쓰기에서는 같은 불변
 * 레코드를 반복 재생한다. 속성 값은 기존 CSV/GraphML writer와 같은
 * 문자열 표현으로 정규화하므로 임의의 직렬화 가능 여부에 의존하지 않는다.
 * 각 레코드는 128 MiB 한도 내의 단일 payload buffer로 인코딩하고 직접 기록하며,
 * 초기화 중 실패하면 이미 획득한 임시 파일과 stream을 정리한다.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
class GraphIoRecordSpool private constructor(
    resources: SpoolResources,
    private val payloadFactory: (Int) -> ByteArrayOutputStream,
) : AutoCloseable {

    constructor() : this(
        openSpoolResources(DEFAULT_MAX_RECORD_BYTES, ::createSpoolFile, ::openSpoolOutput),
        ::CappedByteArrayOutputStream,
    )

    internal constructor(maxRecordBytes: Int) : this(
        openSpoolResources(maxRecordBytes, ::createSpoolFile, ::openSpoolOutput),
        ::CappedByteArrayOutputStream,
    )

    internal constructor(
        maxRecordBytes: Int,
        payloadFactory: (Int) -> ByteArrayOutputStream,
    ) : this(
        openSpoolResources(maxRecordBytes, ::createSpoolFile, ::openSpoolOutput),
        payloadFactory,
    )

    internal constructor(
        maxRecordBytes: Int,
        createTempFile: (String, String) -> Path,
        openOutput: (Path) -> DataOutputStream,
        payloadFactory: (Int) -> ByteArrayOutputStream = ::CappedByteArrayOutputStream,
    ) : this(
        openSpoolResources(maxRecordBytes, createTempFile, openOutput),
        payloadFactory,
    )

    private val maxRecordBytes = resources.maxRecordBytes
    private val vertexFile = resources.vertexFile
    private val edgeFile = resources.edgeFile
    private var vertexOutput: DataOutputStream? = resources.vertexOutput
    private var edgeOutput: DataOutputStream? = resources.edgeOutput
    private val vertexKeys = linkedSetOf<String>()
    private val edgeKeys = linkedSetOf<String>()
    private var finished = false
    private var closed = false
    private val replayInputs = Collections.synchronizedSet(mutableSetOf<DataInputStream>())

    /** 입력 정점에서 관찰된 속성 키를 발견 순서대로 반환한다. */
    val vertexPropertyKeys: Set<String>
        get() = vertexKeys.toSet()

    /** 입력 간선에서 관찰된 속성 키를 발견 순서대로 반환한다. */
    val edgePropertyKeys: Set<String>
        get() = edgeKeys.toSet()

    /** 정점 레코드를 spool에 추가한다. 호출 시점에 속성 값을 문자열로 고정한다. */
    fun appendVertices(records: Iterable<GraphIoVertexRecord>) {
        ensureWritable()
        val output = requireNotNull(vertexOutput)
        records.forEach { record ->
            val normalized = normalize(record.properties, vertexKeys)
            writeRecord(output) {
                writeString(record.externalId)
                writeString(record.label)
                writeProperties(normalized)
            }
        }
    }

    /** 간선 레코드를 spool에 추가한다. 호출 시점에 속성 값을 문자열로 고정한다. */
    fun appendEdges(records: Iterable<GraphIoEdgeRecord>) {
        ensureWritable()
        val output = requireNotNull(edgeOutput)
        records.forEach { record ->
            val normalized = normalize(record.properties, edgeKeys)
            writeRecord(output) {
                writeNullableString(record.externalId)
                writeString(record.label)
                writeString(record.fromExternalId)
                writeString(record.toExternalId)
                writeProperties(normalized)
            }
        }
    }

    /** 모든 입력 스트림을 닫고 replay 가능한 읽기 상태로 전환한다. */
    fun finish() {
        if (closed) error("GraphIoRecordSpool is already closed")
        if (finished) return
        finished = true
        var failure: Throwable? = null
        failure = closeAndCapture(failure) { vertexOutput = closeOutput(vertexOutput) }
        failure = closeAndCapture(failure) { edgeOutput = closeOutput(edgeOutput) }
        if (failure != null) throw failure
    }

    /** 정점 레코드를 새 파일 스트림으로부터 매번 처음부터 재생한다. */
    fun vertexRecords(): Sequence<GraphIoVertexRecord> {
        ensureReadable()
        return records(vertexFile) {
            GraphIoVertexRecord(
                externalId = readString(),
                label = readString(),
                properties = readProperties(),
            )
        }
    }

    /** 간선 레코드를 새 파일 스트림으로부터 매번 처음부터 재생한다. */
    fun edgeRecords(): Sequence<GraphIoEdgeRecord> {
        ensureReadable()
        return records(edgeFile) {
            GraphIoEdgeRecord(
                externalId = readNullableString(),
                label = readString(),
                fromExternalId = readString(),
                toExternalId = readString(),
                properties = readProperties(),
            )
        }
    }

    /** 열린 스트림과 임시 파일을 멱등적으로 정리한다. */
    override fun close() {
        if (closed) return
        closed = true
        var failure: Throwable? = null
        failure = closeAndCapture(failure) { closeReplayInputs() }
        failure = closeAndCapture(failure) { vertexOutput = closeOutput(vertexOutput) }
        failure = closeAndCapture(failure) { edgeOutput = closeOutput(edgeOutput) }
        failure = closeAndCapture(failure) { Files.deleteIfExists(vertexFile) }
        failure = closeAndCapture(failure) { Files.deleteIfExists(edgeFile) }
        if (failure != null) throw failure
    }

    /** 원래 작업 실패를 보존하면서 spool 정리 실패를 suppressed exception으로 연결한다. */
    fun closeSuppressing(primaryFailure: Throwable?) {
        try {
            close()
        } catch (closeFailure: Throwable) {
            if (primaryFailure == null) {
                throw closeFailure
            }
            if (closeFailure !== primaryFailure) {
                primaryFailure.addSuppressed(closeFailure)
            }
        }
    }

    private fun ensureWritable() {
        check(!closed) { "GraphIoRecordSpool is already closed" }
        check(!finished) { "GraphIoRecordSpool has already been finished" }
    }

    private fun ensureReadable() {
        check(!closed) { "GraphIoRecordSpool is already closed" }
        check(finished) { "GraphIoRecordSpool must be finished before replay" }
    }

    private fun normalize(
        properties: Map<String, Any?>,
        keys: MutableSet<String>,
    ): Map<String, String?> {
        val normalized = LinkedHashMap<String, String?>(properties.size)
        properties.forEach { (key, value) ->
            keys += key
            normalized[key] = value?.toString()
        }
        return normalized
    }

    private fun writeRecord(output: DataOutputStream, writePayload: DataOutputStream.() -> Unit) {
        val payload = payloadFactory(maxRecordBytes)
        DataOutputStream(payload).use(writePayload)
        val size = payload.size()
        require(size <= maxRecordBytes) {
            "A graph-io spool record exceeds the ${maxRecordBytes} byte limit"
        }
        output.writeInt(size)
        payload.writeTo(output)
    }

    private fun <T> records(
        file: Path,
        readPayload: DataInputStream.() -> T,
    ): Sequence<T> = sequence {
        val input = DataInputStream(BufferedInputStream(Files.newInputStream(file)))
        if (!registerReplayInput(input)) {
            input.close()
            error("GraphIoRecordSpool is already closed")
        }
        try {
            input.use {
                while (true) {
                    val length = try {
                        it.readInt()
                    } catch (_: EOFException) {
                        break
                    }
                    require(length in 0..maxRecordBytes) {
                        "Invalid graph-io spool record length: $length"
                    }
                    val bytes = ByteArray(length)
                    it.readFully(bytes)
                    yield(readPayload(DataInputStream(ByteArrayInputStream(bytes))))
                }
            }
        } finally {
            replayInputs.remove(input)
        }
    }

    private fun registerReplayInput(input: DataInputStream): Boolean = synchronized(replayInputs) {
        if (closed) {
            false
        } else {
            replayInputs += input
            true
        }
    }

    private fun closeReplayInputs() {
        val inputs = synchronized(replayInputs) {
            replayInputs.toList().also { replayInputs.clear() }
        }
        var failure: Throwable? = null
        inputs.forEach { input ->
            failure = closeAndCapture(failure) { input.close() }
        }
        if (failure != null) throw failure
    }

    private fun closeOutput(output: DataOutputStream?): DataOutputStream? {
        output?.close()
        return null
    }

    private fun closeAndCapture(previous: Throwable?, block: () -> Unit): Throwable? =
        try {
            block()
            previous
        } catch (error: Throwable) {
            if (previous == null) error else previous.apply { addSuppressed(error) }
        }

    private companion object {
        fun DataOutputStream.writeString(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }

        fun DataOutputStream.writeNullableString(value: String?) {
            if (value == null) {
                writeInt(-1)
            } else {
                writeString(value)
            }
        }

        fun DataOutputStream.writeProperties(properties: Map<String, String?>) {
            writeInt(properties.size)
            properties.forEach { (key, value) ->
                writeString(key)
                writeNullableString(value)
            }
        }

    }

    private fun DataInputStream.readString(): String {
        val length = readInt()
        require(length >= 0) { "A required spool string cannot be null" }
        return readStringBytes(length)
    }

    private fun DataInputStream.readNullableString(): String? {
        val length = readInt()
        return if (length < 0) null else readStringBytes(length)
    }

    private fun DataInputStream.readStringBytes(length: Int): String {
        require(length <= maxRecordBytes) { "Invalid graph-io spool string length: $length" }
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(StandardCharsets.UTF_8)
    }

    private fun DataInputStream.readProperties(): Map<String, String?> {
        val count = readInt()
        require(count >= 0) { "Invalid graph-io spool property count: $count" }
        val properties = LinkedHashMap<String, String?>(count)
        repeat(count) {
            properties[readString()] = readNullableString()
        }
        return properties
    }
}
