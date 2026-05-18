package io.bluetape4k.graph.falkordb

import com.falkordb.Driver
import com.falkordb.Record
import com.falkordb.ResultSet
import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.model.GraphSchemaEntityType
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.graph.schema.GraphSchemaNames
import kotlinx.coroutines.CancellationException

/**
 * FalkorDB Cypher DDL 기반 스키마 관리자.
 *
 * 단일 property range index를 생성/삭제/조회한다. Unique constraint는 FalkorDB의
 * `GRAPH.CONSTRAINT CREATE` 명령이 필요하고 jfalkordb 0.7.0의 graph query surface와 분리되어
 * 있으므로 현재 API에서는 명시적으로 미지원 처리한다.
 */
class FalkorDBGraphSchemaManager(
    private val driver: Driver,
    private val graphName: String = FalkorDBGraphOperations.DEFAULT_GRAPH_NAME,
): GraphSchemaManager {

    private fun withGraph(block: (com.falkordb.Graph) -> ResultSet): ResultSet =
        driver.graph(graphName).use(block)

    override fun createIndex(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        ignoreAlreadyExists {
            withGraph { graph ->
                graph.query("CREATE INDEX FOR (n:$safeLabel) ON (n.$safeProperty)")
            }
        }
    }

    override fun createUniqueConstraint(label: String, property: String) {
        GraphSchemaNames.validateLabelAndProperty(label, property)
        throw UnsupportedOperationException(
            "FalkorDB unique constraints require GRAPH.CONSTRAINT CREATE and are not exposed by this manager yet."
        )
    }

    override fun dropIndex(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        ignoreMissing {
            withGraph { graph ->
                graph.query("DROP INDEX ON :$safeLabel($safeProperty)")
            }
        }
    }

    override fun listIndexes(): List<GraphIndex> =
        withGraph { graph -> graph.readOnlyQuery("CALL db.indexes()") }
            .map(::recordToIndex)
            .filter { it.label.isNotBlank() }

    override fun listConstraints(): List<GraphConstraint> =
        emptyList()

    private fun recordToIndex(record: Record): GraphIndex {
        val label = record.stringOrNull("label").orEmpty()
        val property = record.stringOrNull("properties", "property")
        val entityType = when (record.stringOrNull("entitytype", "entityType")?.uppercase()) {
            "NODE", "VERTEX"              -> GraphSchemaEntityType.VERTEX
            "RELATIONSHIP", "EDGE", "REL" -> GraphSchemaEntityType.EDGE
            else                          -> GraphSchemaEntityType.UNKNOWN
        }

        return GraphIndex(
            name = if (property != null) GraphSchemaNames.indexName(label, property) else "bt4k_idx_$label",
            label = label,
            property = property,
            entityType = entityType,
            unique = false,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun ignoreAlreadyExists(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            if (!message.contains("already", ignoreCase = true) && !message.contains("exists", ignoreCase = true)) {
                throw e
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun ignoreMissing(block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            if (!message.contains("not found", ignoreCase = true) && !message.contains("does not exist", ignoreCase = true)) {
                throw e
            }
        }
    }
}

private fun Record.stringOrNull(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        if (!containsKey(key)) return@firstNotNullOfOrNull null
        val value = runCatching { getValue<Any?>(key) }.getOrNull() ?: return@firstNotNullOfOrNull null
        when (value) {
            is List<*> -> value.firstOrNull()?.toString()
            else       -> value.toString()
        }
    }
