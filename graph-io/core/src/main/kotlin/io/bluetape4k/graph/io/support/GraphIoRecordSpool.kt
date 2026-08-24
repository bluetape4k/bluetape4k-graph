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

/**
 * 대용량 graph-io export를 위한 디스크 기반 레코드 spool이다.
 *
 * 입력은 한 번만 소비하고, 출력 헤더 계산과 본문 쓰기에서는 같은 불변
 * 레코드를 반복 재생한다. 속성 값은 기존 CSV/GraphML writer와 같은
 * 문자열 표현으로 정규화하므로 임의의 직렬화 가능 여부에 의존하지 않는다.
 */
@Suppress("TooManyFunctions", "TooGenericExceptionCaught")
class GraphIoRecordSpool : AutoCloseable {

    private val vertexFile: Path = Files.createTempFile("bluetape4k-graph-io-vertices-", ".spool")
    private val edgeFile: Path = Files.createTempFile("bluetape4k-graph-io-edges-", ".spool")
    private var vertexOutput: DataOutputStream? = openOutput(vertexFile)
    private var edgeOutput: DataOutputStream? = openOutput(edgeFile)
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
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use(writePayload)
        val bytes = payload.toByteArray()
        require(bytes.size <= MAX_RECORD_BYTES) {
            "A graph-io spool record exceeds the ${MAX_RECORD_BYTES} byte limit"
        }
        output.writeInt(bytes.size)
        output.write(bytes)
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
                    require(length in 0..MAX_RECORD_BYTES) {
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

    private fun openOutput(file: Path): DataOutputStream =
        DataOutputStream(BufferedOutputStream(Files.newOutputStream(file)))

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
        const val MAX_RECORD_BYTES = 128 * 1024 * 1024

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

        fun DataInputStream.readString(): String {
            val length = readInt()
            require(length >= 0) { "A required spool string cannot be null" }
            return readStringBytes(length)
        }

        fun DataInputStream.readNullableString(): String? {
            val length = readInt()
            return if (length < 0) null else readStringBytes(length)
        }

        fun DataInputStream.readStringBytes(length: Int): String {
            require(length <= MAX_RECORD_BYTES) { "Invalid graph-io spool string length: $length" }
            val bytes = ByteArray(length)
            readFully(bytes)
            return bytes.toString(StandardCharsets.UTF_8)
        }

        fun DataInputStream.readProperties(): Map<String, String?> {
            val count = readInt()
            require(count >= 0) { "Invalid graph-io spool property count: $count" }
            val properties = LinkedHashMap<String, String?>(count)
            repeat(count) {
                properties[readString()] = readNullableString()
            }
            return properties
        }
    }
}
