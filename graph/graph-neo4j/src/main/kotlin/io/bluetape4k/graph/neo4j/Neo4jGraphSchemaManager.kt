package io.bluetape4k.graph.neo4j

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
 * Neo4j Cypher DDL 기반 schema manager.
 *
 * 정점 label의 단일 property index와 unique constraint를 관리한다.
 */
class Neo4jGraphSchemaManager(
    private val driver: Driver,
    private val database: String = "neo4j",
): GraphSchemaManager {

    private fun session(): Session =
        driver.session(SessionConfig.builder().withDatabase(database).build())

    override fun createIndex(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        val name = GraphSchemaNames.indexName(safeLabel, safeProperty)

        session().use { session ->
            session.run("CREATE INDEX $name IF NOT EXISTS FOR (n:$safeLabel) ON (n.$safeProperty)").consume()
        }
    }

    override fun createUniqueConstraint(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        val name = GraphSchemaNames.uniqueConstraintName(safeLabel, safeProperty)

        session().use { session ->
            session.run(
                "CREATE CONSTRAINT $name IF NOT EXISTS FOR (n:$safeLabel) REQUIRE n.$safeProperty IS UNIQUE"
            ).consume()
        }
    }

    override fun dropIndex(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        val name = GraphSchemaNames.indexName(safeLabel, safeProperty)

        session().use { session ->
            session.run("DROP INDEX $name IF EXISTS").consume()
        }
    }

    override fun listIndexes(): List<GraphIndex> =
        session().use { session ->
            session.run(
                """
                SHOW INDEXES
                YIELD name, labelsOrTypes, properties, type, entityType
                RETURN name, labelsOrTypes, properties, type, entityType
                """.trimIndent()
            ).list(::recordToIndex)
        }

    override fun listConstraints(): List<GraphConstraint> =
        session().use { session ->
            session.run(
                """
                SHOW CONSTRAINTS
                YIELD name, labelsOrTypes, properties, type, entityType
                RETURN name, labelsOrTypes, properties, type, entityType
                """.trimIndent()
            ).list(::recordToConstraint)
        }

    private fun recordToIndex(record: Record): GraphIndex {
        val labels = record.strings("labelsOrTypes")
        val properties = record.strings("properties")
        return GraphIndex(
            name = record.stringOrNull("name").orEmpty(),
            label = labels.firstOrNull().orEmpty(),
            property = properties.firstOrNull(),
            entityType = record.entityType(),
            unique = false,
        )
    }

    private fun recordToConstraint(record: Record): GraphConstraint {
        val labels = record.strings("labelsOrTypes")
        val properties = record.strings("properties")
        val type = record.stringOrNull("type").orEmpty()
        return GraphConstraint(
            name = record.stringOrNull("name").orEmpty(),
            label = labels.firstOrNull().orEmpty(),
            property = properties.firstOrNull().orEmpty(),
            type = if (type.contains("UNIQUE", ignoreCase = true)) {
                GraphConstraintType.UNIQUE
            } else {
                GraphConstraintType.UNKNOWN
            },
            entityType = record.entityType(),
        )
    }
}

internal fun Record.stringOrNull(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        if (keys().contains(key)) runCatching { get(key).asString() }.getOrNull() else null
    }

internal fun Record.strings(vararg keys: String): List<String> =
    keys.firstNotNullOfOrNull { key ->
        if (keys().contains(key)) get(key).toStrings() else null
    }.orEmpty()

internal fun Record.entityType(): GraphSchemaEntityType =
    when (stringOrNull("entityType", "entity_type")?.uppercase()) {
        "NODE", "VERTEX"             -> GraphSchemaEntityType.VERTEX
        "RELATIONSHIP", "EDGE", "REL" -> GraphSchemaEntityType.EDGE
        else                         -> GraphSchemaEntityType.UNKNOWN
    }

private fun Value.toStrings(): List<String> {
    if (isNull) return emptyList()
    return runCatching { asList { it.asString() } }
        .getOrElse { runCatching { listOf(asString()) }.getOrDefault(emptyList()) }
}
