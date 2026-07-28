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
 * graph-io로 fraud detection sample CSV dataset을 import한다.
 *
 * 기본 resource는 account를 vertex로, transfer를 directed edge로 모델링한 뒤
 * [GraphOperations] 또는 [GraphSuspendOperations]를 통해 저장한다.
 *
 * ## 동작/계약
 *
 * - 기본 resource는 현재 thread context class loader를 먼저 사용하고, 그다음 이 loader의 class loader로 resolve한다.
 * - 누락된 resource name은 caller input error이며 [IllegalArgumentException]을 던진다.
 * - Loader는 graph-io가 두 file을 모두 consume하기 전의 failure path를 포함해 resource input stream을 소유하고 close한다.
 * - Caller가 override하지 않으면 [GraphImportOptions]와 [CsvGraphIoOptions]는 graph-io default를 따른다.
 * - Loader는 graph를 create하거나 clear하지 않는다. Caller는 import 전에 target operations를 initialize해야 한다.
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
