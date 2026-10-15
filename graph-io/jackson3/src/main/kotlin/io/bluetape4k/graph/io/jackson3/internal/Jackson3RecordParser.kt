package io.bluetape4k.graph.io.jackson3.internal

import io.bluetape4k.graph.io.options.NdJsonReadOptions
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.support.openReader
import io.bluetape4k.io.LineLimitExceededException
import io.bluetape4k.io.boundedLineReader
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import tools.jackson.core.JacksonException
import java.io.IOException

internal data class Jackson3ParsedRecord(
    val lineNumber: Int,
    val envelope: NdJsonEnvelope,
)

/** Jackson3 NDJSON를 한 줄씩 해석하고 source ownership을 관리하는 내부 parser. */
internal class Jackson3RecordParser(
    private val codec: Jackson3EnvelopeCodec = Jackson3EnvelopeCodec.Default,
    private val readOptions: NdJsonReadOptions = NdJsonReadOptions.Default,
) {

    companion object: KLogging() {
        val Default = Jackson3RecordParser()
    }

    fun records(
        source: GraphImportSource,
        phase: GraphIoPhase = GraphIoPhase.READ_VERTEX,
    ): Flow<Jackson3ParsedRecord> = channelFlow {
        val producer = this
        parse(
            source = source,
            onRecord = { record ->
                val result = producer.trySendBlocking(record)
                if (result.isFailure) {
                    if (!producer.coroutineContext.isActive) {
                        throw CancellationException("Jackson3 NDJSON collection cancelled")
                    }
                    throw result.exceptionOrNull()
                        ?: IllegalStateException("Jackson3 NDJSON channel closed")
                }
            },
            onFailure = { failure -> throw GraphIoReadException(failure) },
            phase = phase,
            ensureActive = { producer.coroutineContext.ensureActive() },
        )

    }
        .buffer(0)
        .flowOn(Dispatchers.IO)

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements", "TooGenericExceptionCaught", "ThrowsCount")
    fun parse(
        source: GraphImportSource,
        onRecord: (Jackson3ParsedRecord) -> Unit,
        onFailure: (GraphIoFailure) -> Unit,
        phase: GraphIoPhase = GraphIoPhase.READ_VERTEX,
        ensureActive: () -> Unit = {},
    ) {
        var lineNumber = 0
        var parsing = true
        try {
            source.openReader().use { reader ->
                val boundedReader = reader.boundedLineReader(readOptions.maxLineChars)
                while (parsing) {
                    ensureActive()
                    val raw = try {
                        boundedReader.readLine()
                    } catch (_: LineLimitExceededException) {
                        onFailure(lineLimitFailure(phase, lineNumber + 1))
                        parsing = false
                        continue
                    } ?: break

                    lineNumber++
                    val line = raw.trim()
                    if (line.isBlank()) continue

                    val envelope = try {
                        codec.parseLine(line)
                    } catch (_: JacksonException) {
                        onFailure(malformedFailure(phase, lineNumber))
                        parsing = false
                        continue
                    }
                    try {
                        onRecord(Jackson3ParsedRecord(lineNumber, envelope))
                    } catch (error: Throwable) {
                        throw CallbackFailure(error)
                    }
                }
            }
        } catch (error: CallbackFailure) {
            throw error.error
        } catch (error: CancellationException) {
            throw error
        } catch (error: GraphIoReadException) {
            throw error
        } catch (_: IOException) {
            onFailure(malformedFailure(phase, lineNumber + 1))
        } catch (_: RuntimeException) {
            onFailure(malformedFailure(phase, lineNumber + 1))
        }
    }

    private fun malformedFailure(phase: GraphIoPhase, lineNumber: Int): GraphIoFailure = GraphIoFailure(
        phase = phase,
        fileRole = GraphIoFileRole.UNIFIED,
        location = "line:$lineNumber",
        message = "Malformed JSON",
    )

    private fun lineLimitFailure(phase: GraphIoPhase, lineNumber: Int): GraphIoFailure = GraphIoFailure(
        phase = phase,
        fileRole = GraphIoFileRole.UNIFIED,
        location = "line:$lineNumber",
        message = "NDJSON line exceeds maxLineChars=${readOptions.maxLineChars}",
    )

    private class CallbackFailure(
        val error: Throwable,
    ): RuntimeException(error)
}
