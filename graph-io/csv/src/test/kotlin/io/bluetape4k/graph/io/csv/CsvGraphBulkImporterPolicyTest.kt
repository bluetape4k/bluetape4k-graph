package io.bluetape4k.graph.io.csv

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.graph.io.source.asSource
import io.bluetape4k.io.toInputStream
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.toUtf8Bytes
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.Test

class CsvGraphBulkImporterPolicyTest {

    companion object: KLogging()

    @Test
    fun `reader preserves duplicate vertices and missing endpoints for importer policy`() = runSuspendIO {
        val source = CsvGraphImportSource(
            "id,label\nv1,Person\nv1,Person\n".toUtf8Bytes().toInputStream().asSource(),
            "id,label,from,to\ne1,KNOWS,v1,missing\n".toUtf8Bytes().toInputStream().asSource(),
        )
        val reader = CsvGraphRecordFlowReader()

        reader.readVertices(source)
            .log("Vertices")
            .toList()
            .map { it.externalId } shouldBeEqualTo listOf("v1", "v1")

        reader.readEdges(source)
            .log("Edges")
            .toList()
            .single()
            .let { edge ->
                (edge.fromExternalId to edge.toExternalId) shouldBeEqualTo ("v1" to "missing")
            }
    }
}
