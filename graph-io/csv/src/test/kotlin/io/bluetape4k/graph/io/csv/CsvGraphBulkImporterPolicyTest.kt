package io.bluetape4k.graph.io.csv

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class CsvGraphBulkImporterPolicyTest {

    @Test
    fun `reader preserves duplicate vertices and missing endpoints for importer policy`() = runSuspendIO {
        val source = CsvGraphImportSource(
            vertices = GraphImportSource.InputStreamSource(
                ByteArrayInputStream("id,label\nv1,Person\nv1,Person\n".toByteArray()),
            ),
            edges = GraphImportSource.InputStreamSource(
                ByteArrayInputStream("id,label,from,to\ne1,KNOWS,v1,missing\n".toByteArray()),
            ),
        )
        val reader = CsvGraphRecordFlowReader()

        reader.readVertices(source).toList().map { it.externalId } shouldBeEqualTo listOf("v1", "v1")
        reader.readEdges(source).toList().single().let { edge ->
            (edge.fromExternalId to edge.toExternalId) shouldBeEqualTo ("v1" to "missing")
        }
    }
}
