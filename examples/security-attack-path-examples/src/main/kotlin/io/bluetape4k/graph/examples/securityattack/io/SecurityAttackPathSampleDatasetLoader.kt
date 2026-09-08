package io.bluetape4k.graph.examples.securityattack.io

import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphIoOptions
import io.bluetape4k.graph.io.csv.SuspendCsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.withClasspathCsvGraphImportSource
import io.bluetape4k.graph.io.csv.withClasspathCsvGraphImportSourceSuspending
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.report.GraphImportReport
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations

/**
 * graph-io로 security attack-path sample CSV dataset을 import한다.
 */
object SecurityAttackPathSampleDatasetLoader {

    const val DEFAULT_VERTICES_RESOURCE: String = "sample-data/security-attack-path/vertices.csv"
    const val DEFAULT_EDGES_RESOURCE: String = "sample-data/security-attack-path/edges.csv"

    fun importCsv(
        operations: GraphOperations,
        verticesResource: String = DEFAULT_VERTICES_RESOURCE,
        edgesResource: String = DEFAULT_EDGES_RESOURCE,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport =
        withClasspathCsvGraphImportSource(
            verticesResource = verticesResource,
            edgesResource = edgesResource,
            fallbackClassLoader = SecurityAttackPathSampleDatasetLoader::class.java.classLoader,
            missingResourceMessage = { "Sample dataset resource not found: $it" },
        ) { source ->
            CsvGraphBulkImporter().importGraph(source, operations, options, csvOptions)
        }

    suspend fun importCsvSuspending(
        operations: GraphSuspendOperations,
        verticesResource: String = DEFAULT_VERTICES_RESOURCE,
        edgesResource: String = DEFAULT_EDGES_RESOURCE,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport =
        withClasspathCsvGraphImportSourceSuspending(
            verticesResource = verticesResource,
            edgesResource = edgesResource,
            fallbackClassLoader = SecurityAttackPathSampleDatasetLoader::class.java.classLoader,
            missingResourceMessage = { "Sample dataset resource not found: $it" },
        ) { source ->
            SuspendCsvGraphBulkImporter().importGraphSuspending(source, operations, options, csvOptions)
        }
}
