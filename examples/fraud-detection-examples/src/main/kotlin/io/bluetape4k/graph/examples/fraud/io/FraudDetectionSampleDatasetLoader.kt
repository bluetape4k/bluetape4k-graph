package io.bluetape4k.graph.examples.fraud.io

import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.csv.CsvGraphIoOptions
import io.bluetape4k.graph.io.csv.SuspendCsvGraphBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import java.io.InputStream

/**
 * Imports the fraud detection sample CSV dataset with graph-io.
 *
 * The default resources model accounts as vertices and transfers as directed edges, then persists them through
 * [GraphOperations] or [GraphSuspendOperations].
 *
 * ## Behavior / Contract
 *
 * - Default resources are resolved from the current thread context class loader first, then this loader's class loader.
 * - Missing resource names are caller input errors and throw [IllegalArgumentException].
 * - The loader owns and closes the resource input streams, including failure paths before graph-io consumes both files.
 * - [GraphImportOptions] and [CsvGraphIoOptions] default to graph-io defaults unless callers override them.
 * - The loader does not create or clear graphs; callers should initialize the target operations before import.
 *
 * ```kotlin
 * val report = FraudDetectionSampleDatasetLoader.importCsv(ops)
 * println("accounts=${report.verticesCreated}, transfers=${report.edgesCreated}")
 * ```
 */
object FraudDetectionSampleDatasetLoader {

    const val DEFAULT_VERTICES_RESOURCE: String = "sample-data/fraud/vertices.csv"
    const val DEFAULT_EDGES_RESOURCE: String = "sample-data/fraud/edges.csv"

    fun importCsv(
        operations: GraphOperations,
        verticesResource: String = DEFAULT_VERTICES_RESOURCE,
        edgesResource: String = DEFAULT_EDGES_RESOURCE,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport =
        withImportSource(verticesResource, edgesResource) { source ->
            CsvGraphBulkImporter().importGraph(source, operations, options, csvOptions)
        }

    suspend fun importCsvSuspending(
        operations: GraphSuspendOperations,
        verticesResource: String = DEFAULT_VERTICES_RESOURCE,
        edgesResource: String = DEFAULT_EDGES_RESOURCE,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport =
        withImportSourceSuspending(verticesResource, edgesResource) { source ->
            SuspendCsvGraphBulkImporter().importGraphSuspending(source, operations, options, csvOptions)
        }

    private fun <T> withImportSource(
        verticesResource: String,
        edgesResource: String,
        block: (CsvGraphImportSource) -> T,
    ): T {
        val vertices = resourceStreamOrThrow(verticesResource)
        return vertices.use { vertexInput ->
            val edges = resourceStreamOrThrow(edgesResource)
            edges.use { edgeInput ->
                block(CsvGraphImportSource(vertexInput.toSource(), edgeInput.toSource()))
            }
        }
    }

    private suspend fun <T> withImportSourceSuspending(
        verticesResource: String,
        edgesResource: String,
        block: suspend (CsvGraphImportSource) -> T,
    ): T {
        val vertices = resourceStreamOrThrow(verticesResource)
        return vertices.use { vertexInput ->
            val edges = resourceStreamOrThrow(edgesResource)
            edges.use { edgeInput ->
                block(CsvGraphImportSource(vertexInput.toSource(), edgeInput.toSource()))
            }
        }
    }

    private fun InputStream.toSource(): GraphImportSource =
        GraphImportSource.InputStreamSource(
            input = this,
            closeInput = false,
        )

    private fun resourceStreamOrThrow(resourceName: String): InputStream =
        requireNotNull(
            listOfNotNull(
                Thread.currentThread().contextClassLoader,
                FraudDetectionSampleDatasetLoader::class.java.classLoader,
            ).asSequence()
                .mapNotNull { it.getResourceAsStream(resourceName) }
                .firstOrNull()
        ) {
            "Sample dataset resource not found: $resourceName"
        }
}
