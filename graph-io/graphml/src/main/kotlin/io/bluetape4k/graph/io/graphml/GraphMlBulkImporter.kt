package io.bluetape4k.graph.io.graphml

import io.bluetape4k.graph.io.contract.GraphBulkImporter
import io.bluetape4k.graph.io.graphml.internal.StaxGraphMlReader
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord
import io.bluetape4k.graph.io.report.GraphIoFailure
import io.bluetape4k.graph.io.report.GraphIoFailureSeverity
import io.bluetape4k.graph.io.report.GraphIoFileRole
import io.bluetape4k.graph.io.report.GraphIoFormat
import io.bluetape4k.graph.io.report.GraphIoOperation
import io.bluetape4k.graph.io.report.GraphIoPhase
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressReporter
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.io.support.GraphIoBatchWriter
import io.bluetape4k.graph.io.support.GraphIoExternalIdMap
import io.bluetape4k.graph.io.support.GraphIoPaths
import io.bluetape4k.graph.io.support.GraphIoStopwatch
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.warn

/**
 * Blocking bulk importer for GraphML.
 *
 * The importer reads the XML document with StAX, creates all vertices first,
 * and then connects edges once endpoint ids are known.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
 * import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
 * import io.bluetape4k.graph.io.options.GraphImportOptions
 * import io.bluetape4k.graph.io.source.GraphImportSource
 * import java.nio.file.Paths
 *
 * val importer = GraphMlBulkImporter()
 * val report = importer.importGraph(
 *     source = GraphImportSource.PathSource(Paths.get("graph.graphml")),
 *     operations = graphOps,
 *     options = GraphImportOptions(batchSize = 500),
 *     graphMlOptions = GraphMlImportOptions(defaultVertexLabel = "Entity"),
 * )
 * check(report.verticesCreated >= 0)
 * ```
 */
class GraphMlBulkImporter : GraphBulkImporter<GraphImportSource> {

    private val reader = StaxGraphMlReader()

    override fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport = importGraph(source, operations, options, GraphMlImportOptions())

    override fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
        listener: GraphIoProgressListener,
    ): GraphImportReport = importGraph(source, operations, options, GraphMlImportOptions(), listener)

    fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
        listener: GraphIoProgressListener,
    ): GraphImportReport {
        val reporter = GraphIoProgressReporter(
            operation = GraphIoOperation.IMPORT,
            format = GraphIoFormat.GRAPHML,
            listener = listener,
            bytesProvider = { GraphIoPaths.sizeOf(source) },
        )
        return reporter.run(
            block = { importGraph(source, operations, options, graphMlOptions) },
            onCompleted = { report -> reporter.completed(report) },
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
    fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
    ): GraphImportReport {
        log.debug { "Starting GRAPHML import: defaultVertexLabel=${graphMlOptions.defaultVertexLabel}" }
        val watch = GraphIoStopwatch()
        val idMap = GraphIoExternalIdMap(options.onDuplicateVertexId)
        val batchWriter = GraphIoBatchWriter(operations, options.batchSize)
        val failures = mutableListOf<GraphIoFailure>()
        val bufferedEdges = ArrayDeque<GraphIoEdgeRecord>()
        var vr = 0L
        var vc = 0L
        var er = 0L
        var ec = 0L
        var sv = 0L
        var se = 0L
        var status = GraphIoStatus.COMPLETED

        val sink = object : StaxGraphMlReader.GraphMlRecordSink {
            override fun onVertex(record: GraphIoVertexRecord) {
                vr++
                if (status == GraphIoStatus.FAILED) return
                val props = options.preserveExternalIdProperty
                    ?.let { record.properties + (it to record.externalId) } ?: record.properties
                when (idMap.putFirstOrFail(record.externalId, GraphElementId(record.externalId))) {
                    GraphIoExternalIdMap.PutResult.CREATED -> {
                        vc += batchWriter.addVertex(record.externalId, record.label, props, idMap)
                    }
                    GraphIoExternalIdMap.PutResult.SKIPPED -> {
                        sv++
                        status = GraphIoStatus.PARTIAL
                        failures += GraphIoFailure(
                            phase = GraphIoPhase.CREATE_VERTEX,
                            severity = GraphIoFailureSeverity.WARN,
                            fileRole = GraphIoFileRole.UNIFIED,
                            recordId = record.externalId,
                            message = "Duplicate vertex skipped: ${record.externalId}",
                        ).also { log.warn { "Duplicate vertex skipped: ${record.externalId}" } }
                    }
                }
            }

            override fun onEdge(record: GraphIoEdgeRecord) {
                er++
                if (status == GraphIoStatus.FAILED) return
                bufferedEdges += record
                if (bufferedEdges.size > options.maxEdgeBufferSize) {
                    failures += GraphIoFailure(
                        phase = GraphIoPhase.READ_EDGE,
                        fileRole = GraphIoFileRole.UNIFIED,
                        location = "edge-buffer:${bufferedEdges.size}",
                        message = "Edge buffer exceeded maxEdgeBufferSize=${options.maxEdgeBufferSize}; " +
                            "verticesCreated=$vc remain in graph as partial state",
                    )
                    status = GraphIoStatus.FAILED
                    throw StopImport
                }
            }

            override fun onFailure(failure: GraphIoFailure) {
                failures += failure
                status = when {
                    failure.severity == GraphIoFailureSeverity.ERROR -> GraphIoStatus.FAILED
                    status == GraphIoStatus.COMPLETED -> GraphIoStatus.PARTIAL
                    else -> status
                }
            }
        }

        try {
            GraphIoPaths.openInputStream(source).use { input ->
                reader.read(input, graphMlOptions, sink)
            }
        } catch (_: StopImport) {
            // Stop parsing after a terminal edge-buffer failure.
        }

        if (status == GraphIoStatus.FAILED) {
            return GraphImportReport(status, GraphIoFormat.GRAPHML, vr, vc, er, ec, sv, se, watch.elapsed(), failures)
        }

        vc += batchWriter.flushVertices(idMap)

        while (bufferedEdges.isNotEmpty()) {
            val e = bufferedEdges.removeFirst()
            val from = idMap.resolve(e.fromExternalId)
            val to = idMap.resolve(e.toExternalId)
            if (from == null || to == null) {
                when (options.onMissingEdgeEndpoint) {
                    MissingEndpointPolicy.FAIL -> {
                        ec += batchWriter.flushEdges()
                        val failure = GraphIoFailure(
                            phase = GraphIoPhase.READ_EDGE,
                            fileRole = GraphIoFileRole.UNIFIED,
                            recordId = e.externalId,
                            message = "Unresolved endpoint from=${e.fromExternalId} to=${e.toExternalId}",
                        )
                        log.warn { failure.message }
                        failures += failure
                        status = GraphIoStatus.FAILED
                        break
                    }
                    MissingEndpointPolicy.SKIP_EDGE -> {
                        se++
                        status = GraphIoStatus.PARTIAL
                        failures += GraphIoFailure(
                            phase = GraphIoPhase.READ_EDGE,
                            severity = GraphIoFailureSeverity.WARN,
                            fileRole = GraphIoFileRole.UNIFIED,
                            recordId = e.externalId,
                            message = "Missing endpoint skipped from=${e.fromExternalId} to=${e.toExternalId}",
                        )
                        continue
                    }
                }
            }
            val props = e.externalId?.let { eid ->
                options.preserveExternalIdProperty?.let { key -> e.properties + (key to eid) } ?: e.properties
            } ?: e.properties
            ec += batchWriter.addEdge(e.label, from, to, props)
        }

        if (status != GraphIoStatus.FAILED) {
            ec += batchWriter.flushEdges()
        }

        return GraphImportReport(status, GraphIoFormat.GRAPHML, vr, vc, er, ec, sv, se, watch.elapsed(), failures)
            .also { log.debug { "Import completed: vertices=$vc/$vr, edges=$ec/$er, skipped=$sv/$se, status=$status, elapsed=${watch.elapsed()}" } }
    }

    private object StopImport : RuntimeException()

    companion object : KLogging()
}
