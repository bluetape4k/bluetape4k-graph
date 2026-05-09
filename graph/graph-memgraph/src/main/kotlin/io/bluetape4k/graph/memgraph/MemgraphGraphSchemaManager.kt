package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphConstraintType
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.model.GraphSchemaEntityType
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.graph.schema.GraphSchemaNames
import org.neo4j.driver.Driver
import org.neo4j.driver.Record
import org.neo4j.driver.Session
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.Value

/**
 * Memgraph Cypher DDL 기반 스키마 관리자.
 *
 * Memgraph의 label-property index 문법과 uniqueness constraint 문법을 공통 API로 감싼다.
 */
class MemgraphGraphSchemaManager(
    private val driver: Driver,
    private val database: String = "memgraph",
): GraphSchemaManager {

    private fun session(): Session =
        driver.session(SessionConfig.builder().withDatabase(database).build())

    override fun createIndex(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        ignoreAlreadyExists {
            session().use { session ->
                session.run("CREATE INDEX ON :$safeLabel($safeProperty)").consume()
            }
        }
    }

    override fun createUniqueConstraint(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        ignoreAlreadyExists {
            session().use { session ->
                session.run("CREATE CONSTRAINT ON (n:$safeLabel) ASSERT n.$safeProperty IS UNIQUE").consume()
            }
        }
    }

    override fun dropIndex(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        ignoreMissing {
            session().use { session ->
                session.run("DROP INDEX ON :$safeLabel($safeProperty)").consume()
            }
        }
    }

    override fun listIndexes(): List<GraphIndex> =
        session().use { session ->
            session.run("SHOW INDEX INFO").list(::recordToIndex).filter { it.label.isNotBlank() }
        }

    override fun listConstraints(): List<GraphConstraint> =
        session().use { session ->
            session.run("SHOW CONSTRAINT INFO").list(::recordToConstraint).filter { it.label.isNotBlank() }
        }

    private fun recordToIndex(record: Record): GraphIndex {
        val label = record.stringOrNull("label", "Label", "labels", "labelsOrTypes").orEmpty()
        val property = record.stringOrNull("property", "Property", "properties")
        val name = property?.let { GraphSchemaNames.indexName(label, it) } ?: "bt4k_idx_$label"
        return GraphIndex(
            name = name,
            label = label,
            property = property,
            entityType = GraphSchemaEntityType.VERTEX,
            unique = false,
        )
    }

    private fun recordToConstraint(record: Record): GraphConstraint {
        val label = record.stringOrNull("label", "Label", "labels", "labelsOrTypes").orEmpty()
        val property = record.stringOrNull("property", "Property", "properties").orEmpty()
        val type = record.stringOrNull("constraint type", "type", "Type").orEmpty()
        return GraphConstraint(
            name = if (property.isNotBlank()) GraphSchemaNames.uniqueConstraintName(label, property) else "bt4k_constraint_$label",
            label = label,
            property = property,
            type = if (type.contains("UNIQUE", ignoreCase = true)) {
                GraphConstraintType.UNIQUE
            } else {
                GraphConstraintType.UNKNOWN
            },
            entityType = GraphSchemaEntityType.VERTEX,
        )
    }

    private fun ignoreAlreadyExists(block: () -> Unit) {
        runCatching(block).getOrElse { e ->
            val message = e.message.orEmpty()
            if (!message.contains("already", ignoreCase = true) && !message.contains("exists", ignoreCase = true)) {
                throw e
            }
        }
    }

    private fun ignoreMissing(block: () -> Unit) {
        runCatching(block).getOrElse { e ->
            val message = e.message.orEmpty()
            if (!message.contains("not found", ignoreCase = true) && !message.contains("does not exist", ignoreCase = true)) {
                throw e
            }
        }
    }
}

private fun Record.stringOrNull(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        if (!keys().contains(key)) return@firstNotNullOfOrNull null
        val value = get(key)
        value.toStringOrNull()
    }

private fun Value.toStringOrNull(): String? {
    if (isNull) return null
    return runCatching { asString() }.getOrElse {
        runCatching { asList { it.asString() }.firstOrNull() }.getOrNull()
    }
}
