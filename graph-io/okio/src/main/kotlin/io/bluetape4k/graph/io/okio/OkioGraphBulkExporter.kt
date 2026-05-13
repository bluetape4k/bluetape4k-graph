package io.bluetape4k.graph.io.okio

import io.bluetape4k.graph.io.okio.bridge.asClosingOutputStream
import io.bluetape4k.graph.io.contract.GraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.report.GraphExportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.okio.tink.DEFAULT_DAEAD_CHUNK_SIZE
import io.bluetape4k.tink.daead.TinkDeterministicAead
import okio.BufferedSink
import okio.FileSystem
import java.io.IOException

/**
 * Synchronous graph bulk exporter backed by OkIO sources and sinks.
 *
 * The caller must pass [GraphIoFormat] explicitly. File-extension sniffing is intentionally unsupported.
 *
 * ### CSV constraint
 * CSV requires separate vertex and edge files, so this facade supports CSV only for
 * [OkioGraphExportSink.PathSink]. The target stem produces `{stem}_vertices.csv` and `{stem}_edges.csv`.
 * Stream-backed sinks throw [UnsupportedOperationException].
 */
class OkioGraphBulkExporter(
    private val csvExporter: CsvGraphBulkExporter = CsvGraphBulkExporter(),
    private val jackson2Exporter: Jackson2NdJsonBulkExporter = Jackson2NdJsonBulkExporter(),
    private val jackson3Exporter: Jackson3NdJsonBulkExporter = Jackson3NdJsonBulkExporter(),
    private val graphmlExporter: GraphMlBulkExporter = GraphMlBulkExporter(),
) : GraphBulkExporter<OkioGraphExportSink> {

    companion object : KLogging()

    /**
     * Exports a graph to an OkIO sink using [GraphIoFormat.NDJSON_JACKSON3].
     */
    override fun exportGraph(
        sink: OkioGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport = exportGraph(sink, GraphIoFormat.NDJSON_JACKSON3, operations, options)

    /**
     * Exports a graph to an OkIO sink using the explicit [format].
     *
     * @param format export format; no extension-based inference is performed
     * @throws IOException when an I/O error occurs
     * @throws UnsupportedOperationException when CSV is used with a stream-backed sink
     */
    @Throws(IOException::class)
    fun exportGraph(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport {
        log.debug { "Starting OkIO export: format=$format, sink=${describeSink(sink)}" }
        return when (format) {
            GraphIoFormat.CSV -> exportCsv(sink, operations, options)
            GraphIoFormat.NDJSON_JACKSON2 -> exportSingleStream(sink) { os ->
                jackson2Exporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
            }
            GraphIoFormat.NDJSON_JACKSON3 -> exportSingleStream(sink) { os ->
                jackson3Exporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
            }
            GraphIoFormat.GRAPHML -> exportSingleStream(sink) { os ->
                graphmlExporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
            }
        }
    }

    /**
     * Exports a single-stream graph format through DAEAD chunk encryption.
     *
     * CSV is intentionally unsupported because it is a paired-file format. Use the low-level
     * [GraphIoOkioPaths.openDaeadEncryptedSink] helpers directly for custom CSV file pairs.
     *
     * @throws IOException I/O or encryption failure
     * @throws UnsupportedOperationException when [format] is [GraphIoFormat.CSV]
     */
    @Throws(IOException::class)
    fun exportGraphDaead(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        chunkSize: Int = DEFAULT_DAEAD_CHUNK_SIZE,
        associatedData: ByteArray = ByteArray(0),
    ): GraphExportReport {
        requireSingleStreamFormat(format)
        log.debug { "Starting OkIO DAEAD export: format=$format, sink=${describeSink(sink)}" }
        return GraphIoOkioPaths.openDaeadEncryptedSink(sink, daead, chunkSize, associatedData).use { bs ->
            exportSingleStream(bs, format, operations, options)
        }
    }

    /**
     * Exports a single-stream graph format using compress-then-encrypt GZip + DAEAD chunk encryption.
     *
     * The inverse import path is [OkioGraphBulkImporter.importGraphDaeadGzip].
     */
    @Throws(IOException::class)
    fun exportGraphGzipDaead(
        sink: OkioGraphExportSink,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
        chunkSize: Int = DEFAULT_DAEAD_CHUNK_SIZE,
        associatedData: ByteArray = ByteArray(0),
    ): GraphExportReport {
        requireSingleStreamFormat(format)
        log.debug { "Starting OkIO gzip+DAEAD export: format=$format, sink=${describeSink(sink)}" }
        return GraphIoOkioPaths.openGzipDaeadEncryptedSink(sink, daead, chunkSize, associatedData).use { bs ->
            exportSingleStream(bs, format, operations, options)
        }
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    /** Exports a single-stream format after adapting the OkIO sink to an output stream. */
    private inline fun exportSingleStream(
        sink: OkioGraphExportSink,
        block: (java.io.OutputStream) -> GraphExportReport,
    ): GraphExportReport {
        return GraphIoOkioPaths.openSink(sink).use { bs ->
            bs.asClosingOutputStream().use { os -> block(os) }
        }
    }

    private fun exportSingleStream(
        sink: BufferedSink,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport =
        sink.asClosingOutputStream().use { os ->
            when (format) {
                GraphIoFormat.NDJSON_JACKSON2 ->
                    jackson2Exporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
                GraphIoFormat.NDJSON_JACKSON3 ->
                    jackson3Exporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
                GraphIoFormat.GRAPHML ->
                    graphmlExporter.exportGraph(GraphExportSink.OutputStreamSink(os, closeOutput = false), operations, options)
                GraphIoFormat.CSV -> unsupportedCsvEncrypted()
            }
        }

    /**
     * Exports CSV by deriving `{stem}_vertices.csv` and `{stem}_edges.csv` from a [OkioGraphExportSink.PathSink].
     *
     * CSV is a paired-file format, so stream-backed sinks throw [UnsupportedOperationException].
     */
    private fun exportCsv(
        sink: OkioGraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport {
        return when (sink) {
            is OkioGraphExportSink.PathSink -> {
                require(sink.fileSystem == FileSystem.SYSTEM) {
                    "CSV export supports only FileSystem.SYSTEM. " +
                        "Use CsvGraphBulkExporter directly for custom FileSystem instances. " +
                        "Provided FileSystem: ${sink.fileSystem}"
                }
                val stem = sink.path.toString().removeSuffix(".csv")
                val verticesSink = GraphExportSink.PathSink(java.nio.file.Paths.get("${stem}_vertices.csv"))
                val edgesSink = GraphExportSink.PathSink(java.nio.file.Paths.get("${stem}_edges.csv"))
                csvExporter.exportGraph(CsvGraphExportSink(verticesSink, edgesSink), operations, options)
            }
            else -> throw UnsupportedOperationException(
                "CSV requires two files for vertices and edges, so OkioGraphBulkExporter supports only PathSink. " +
                    "Use CsvGraphBulkExporter directly for stream-backed sinks."
            )
        }
    }

    private fun describeSink(sink: OkioGraphExportSink): String = when (sink) {
        is OkioGraphExportSink.PathSink -> sink.path.toString()
        is OkioGraphExportSink.SinkBased -> "<Sink>"
        is OkioGraphExportSink.OutputStreamBased -> "<OutputStream>"
    }

    private fun requireSingleStreamFormat(format: GraphIoFormat) {
        if (format == GraphIoFormat.CSV) {
            unsupportedCsvEncrypted()
        }
    }

    private fun unsupportedCsvEncrypted(): Nothing =
        throw UnsupportedOperationException(
            "CSV is a paired-file format. Use low-level DAEAD helpers directly for custom CSV file pairs."
        )
}
