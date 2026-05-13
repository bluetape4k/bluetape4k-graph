package io.bluetape4k.graph.io.okio

import io.bluetape4k.graph.io.okio.bridge.toInputStream
import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.okio.tink.DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH
import io.bluetape4k.tink.daead.TinkDeterministicAead
import okio.BufferedSource
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.IOException

/**
 * Synchronous graph bulk importer backed by OkIO sources.
 *
 * The caller must pass [GraphIoFormat] explicitly. File-extension sniffing is intentionally unsupported.
 *
 * ### CSV constraint
 * CSV requires separate vertex and edge files, so this facade supports CSV only for
 * [OkioGraphImportSource.PathSource]. The source stem derives `{stem}_vertices.csv` and `{stem}_edges.csv`.
 * Stream-backed sources throw [UnsupportedOperationException].
 *
 * ### Delegation
 * Format-specific importers for CSV, Jackson 2, Jackson 3, and GraphML receive an adapted input stream.
 * OkIO-specific behavior such as compression chaining and [okio.FileSystem] support lives in [GraphIoOkioPaths].
 */
class OkioGraphBulkImporter(
    private val csvImporter: CsvGraphBulkImporter = CsvGraphBulkImporter(),
    private val jackson2Importer: Jackson2NdJsonBulkImporter = Jackson2NdJsonBulkImporter(),
    private val jackson3Importer: Jackson3NdJsonBulkImporter = Jackson3NdJsonBulkImporter(),
    private val graphmlImporter: GraphMlBulkImporter = GraphMlBulkImporter(),
) : GraphBulkImporter<OkioGraphImportSource> {

    companion object : KLogging()

    /**
     * Imports a graph from an OkIO source using [GraphIoFormat.NDJSON_JACKSON3].
     *
     * @param source OkIO import source
     * @param operations target graph operations
     * @param options duplicate handling, missing endpoint handling, default labels, and batch size
     * @throws IOException when an I/O error occurs
     * @throws UnsupportedOperationException when CSV is used with a stream-backed source
     */
    override fun importGraph(
        source: OkioGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraph(source, GraphIoFormat.NDJSON_JACKSON3, operations, options)

    /**
     * Imports a graph from an OkIO source using the explicit [format].
     *
     * @param format import format; no extension-based inference is performed
     */
    @Throws(IOException::class)
    fun importGraph(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport {
        log.debug { "Starting OkIO import: format=$format, source=${describeSource(source)}" }
        return when (format) {
            GraphIoFormat.CSV -> importCsv(source, operations, options)
            GraphIoFormat.NDJSON_JACKSON2 -> importSingleStream(source) { is_ ->
                jackson2Importer.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
            }
            GraphIoFormat.NDJSON_JACKSON3 -> importSingleStream(source) { is_ ->
                jackson3Importer.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
            }
            GraphIoFormat.GRAPHML -> importSingleStream(source) { is_ ->
                graphmlImporter.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
            }
        }
    }

    /**
     * Imports a single-stream graph format through DAEAD chunk decryption.
     *
     * CSV is intentionally unsupported because it is a paired-file format. Use low-level DAEAD helpers directly
     * for custom CSV file pairs.
     */
    @Throws(IOException::class)
    fun importGraphDaead(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        associatedData: ByteArray = ByteArray(0),
        maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
    ): GraphImportReport {
        requireSingleStreamFormat(format)
        log.debug { "Starting OkIO DAEAD import: format=$format, source=${describeSource(source)}" }
        return GraphIoOkioPaths.openDaeadDecryptedSource(source, daead, associatedData, maxCiphertextLength).use { bs ->
            importSingleStream(bs, format, operations, options)
        }
    }

    /**
     * Imports a single-stream graph format using decrypt-then-inflate DAEAD chunk + GZip source.
     *
     * The input must have been written by [OkioGraphBulkExporter.exportGraphGzipDaead].
     */
    @Throws(IOException::class)
    fun importGraphDaeadGzip(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        daead: TinkDeterministicAead,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        associatedData: ByteArray = ByteArray(0),
        maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
        maxDecompressedBytes: Long = GraphIoOkioPaths.DEFAULT_MAX_DECOMPRESSED_BYTES,
    ): GraphImportReport {
        requireSingleStreamFormat(format)
        log.debug { "Starting OkIO DAEAD+gzip import: format=$format, source=${describeSource(source)}" }
        return GraphIoOkioPaths.openDaeadDecryptedGzipSource(
            source = source,
            daead = daead,
            associatedData = associatedData,
            maxCiphertextLength = maxCiphertextLength,
            maxDecompressedBytes = maxDecompressedBytes,
        ).use { bs ->
            importSingleStream(bs, format, operations, options)
        }
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    /** Imports a single-stream format after adapting the OkIO source to an input stream. */
    private inline fun importSingleStream(
        source: OkioGraphImportSource,
        block: (java.io.InputStream) -> GraphImportReport,
    ): GraphImportReport {
        return GraphIoOkioPaths.openSource(source).use { bs ->
            bs.toInputStream().use { is_ -> block(is_) }
        }
    }

    private fun importSingleStream(
        source: BufferedSource,
        format: GraphIoFormat,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport =
        source.toInputStream().use { is_ ->
            when (format) {
                GraphIoFormat.NDJSON_JACKSON2 ->
                    jackson2Importer.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
                GraphIoFormat.NDJSON_JACKSON3 ->
                    jackson3Importer.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
                GraphIoFormat.GRAPHML ->
                    graphmlImporter.importGraph(GraphImportSource.InputStreamSource(is_, closeInput = false), operations, options)
                GraphIoFormat.CSV -> unsupportedCsvEncrypted()
            }
        }

    /**
     * Imports CSV by deriving `{stem}_vertices.csv` and `{stem}_edges.csv` from a [OkioGraphImportSource.PathSource].
     *
     * CSV is a paired-file format, so stream-backed sources throw [UnsupportedOperationException].
     */
    private fun importCsv(
        source: OkioGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport {
        return when (source) {
            is OkioGraphImportSource.PathSource -> {
                require(source.fileSystem == FileSystem.SYSTEM) {
                    "CSV import supports only FileSystem.SYSTEM. " +
                        "Use CsvGraphBulkImporter directly for custom FileSystem instances. " +
                        "Provided FileSystem: ${source.fileSystem}"
                }
                val stem = source.path.toString().removeSuffix(".csv")
                val verticesPath = "${stem}_vertices.csv".toPath()
                val edgesPath = "${stem}_edges.csv".toPath()
                val csvSource = CsvGraphImportSource(
                    vertices = GraphImportSource.PathSource(java.nio.file.Paths.get(verticesPath.toString())),
                    edges = GraphImportSource.PathSource(java.nio.file.Paths.get(edgesPath.toString())),
                )
                csvImporter.importGraph(csvSource, operations, options)
            }
            else -> throw UnsupportedOperationException(
                "CSV requires two files for vertices and edges, so OkioGraphBulkImporter supports only PathSource. " +
                    "Use CsvGraphBulkImporter directly for stream-backed sources."
            )
        }
    }

    private fun describeSource(source: OkioGraphImportSource): String = when (source) {
        is OkioGraphImportSource.PathSource -> source.path.toString()
        is OkioGraphImportSource.SourceBased -> "<Source>"
        is OkioGraphImportSource.InputStreamBased -> "<InputStream>"
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
