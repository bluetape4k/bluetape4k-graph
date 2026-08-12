package io.bluetape4k.graph.io.csv.internal

import io.bluetape4k.csv.CsvRecordReader
import io.bluetape4k.csv.Record
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoReadException
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.support.GraphIoPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.trySendBlocking
import java.io.IOException
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.Charset

/** CSV 입력을 한 행씩 소비하면서 source 수명과 안전한 parse failure를 관리하는 내부 파서. */
internal class CsvRecordParser {

    fun records(
        source: GraphImportSource,
        phase: GraphIoPhase,
        fileRole: GraphIoFileRole,
    ): Flow<Record> = channelFlow {
        val producer = this
        withContext(Dispatchers.IO) {
            parse(
                source = source,
                phase = phase,
                fileRole = fileRole,
                onRecord = { record ->
                    val result = producer.trySendBlocking(record)
                    if (result.isFailure) {
                        if (!producer.coroutineContext.isActive) {
                            throw CancellationException("CSV record collection cancelled")
                        }
                        throw result.exceptionOrNull()
                            ?: IllegalStateException("CSV record channel closed")
                    }
                },
                onFailure = { failure -> throw GraphIoReadException(failure) },
            )
        }
    }.buffer(0)

    fun parseVertices(
        source: GraphImportSource,
        onRecord: (Record) -> Unit,
        onFailure: (GraphIoFailure) -> Unit,
    ) = parse(source, GraphIoPhase.READ_VERTEX, GraphIoFileRole.VERTICES, onRecord, onFailure)

    fun parseEdges(
        source: GraphImportSource,
        onRecord: (Record) -> Unit,
        onFailure: (GraphIoFailure) -> Unit,
    ) = parse(source, GraphIoPhase.READ_EDGE, GraphIoFileRole.EDGES, onRecord, onFailure)

    @Suppress("ThrowsCount", "TooGenericExceptionCaught")
    private fun parse(
        source: GraphImportSource,
        phase: GraphIoPhase,
        fileRole: GraphIoFileRole,
        onRecord: (Record) -> Unit,
        onFailure: (GraphIoFailure) -> Unit,
    ) {
        var rowNumber = 0L
        var parsedToEof = false
        try {
            GraphIoPaths.openInputStream(source).use { input ->
                CsvRecordReader().read(
                    input = NonClosingInputStream(input),
                    encoding = source.charset(),
                    skipHeaders = true,
                ).forEach { record ->
                    rowNumber = record.rowNumber
                    try {
                        onRecord(record)
                    } catch (error: Throwable) {
                        throw CallbackFailure(error)
                    }
                }
                parsedToEof = true
            }
        } catch (error: CallbackFailure) {
            throw error.error
        } catch (error: CancellationException) {
            throw error
        } catch (error: IOException) {
            if (parsedToEof) throw error
            onFailure(
                GraphIoFailure(
                    phase = phase,
                    fileRole = fileRole,
                    location = "row:${(rowNumber + 1).coerceAtLeast(1)}",
                    message = "Malformed CSV input",
                ),
            )
        } catch (error: RuntimeException) {
            if (parsedToEof) throw error
            onFailure(
                GraphIoFailure(
                    phase = phase,
                    fileRole = fileRole,
                    location = "row:${(rowNumber + 1).coerceAtLeast(1)}",
                    message = "Malformed CSV input",
                ),
            )
        }
    }

    private fun GraphImportSource.charset(): Charset = when (this) {
        is GraphImportSource.PathSource -> charset
        is GraphImportSource.InputStreamSource -> charset
    }

    private class CallbackFailure(
        val error: Throwable,
    ) : RuntimeException(error)

    /** CsvRecordReader가 lexer를 닫을 때 source 소유권을 중복 반납하지 않도록 한다. */
    private class NonClosingInputStream(
        input: InputStream,
    ) : FilterInputStream(input) {
        override fun close() {
            // GraphIoPaths.openInputStream(source).use가 유일한 소유자다.
        }
    }
}
