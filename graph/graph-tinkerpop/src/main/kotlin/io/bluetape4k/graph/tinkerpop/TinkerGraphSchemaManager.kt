package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.GraphUnsupportedOperationException
import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.graph.schema.GraphSchemaNames
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import java.util.concurrent.ConcurrentHashMap

/**
 * TinkerGraph용 in-memory 스키마 관리자.
 *
 * TinkerGraph는 durable schema DDL을 제공하지 않으므로 인덱스 요청은 현재 manager 인스턴스에만
 * 기록한다. 유니크 제약조건은 실제로 강제할 수 없으므로 명시적으로 실패시킨다.
 */
class TinkerGraphSchemaManager: GraphSchemaManager {

    companion object: KLogging()

    internal var mutationLock: ((() -> Unit) -> Unit)? = null

    private val indexes = ConcurrentHashMap.newKeySet<GraphIndex>()

    override fun createIndex(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        log.debug { "Creating index: label=$safeLabel, property=$safeProperty" }

        mutate {
            indexes += GraphIndex(
                name = GraphSchemaNames.indexName(safeLabel, safeProperty),
                label = safeLabel,
                property = safeProperty,
            )
        }
    }

    override fun createUniqueConstraint(label: String, property: String) {
        log.debug { "Creating unique constraint: label=$label, property=$property" }
        GraphSchemaNames.validateLabelAndProperty(label, property)
        mutate {
            throw GraphUnsupportedOperationException("TinkerGraph does not enforce unique constraints.")
        }
    }

    override fun dropIndex(label: String, property: String) {
        val (safeLabel, safeProperty) = GraphSchemaNames.validateLabelAndProperty(label, property)
        log.debug { "drop index... label=$safeLabel, property=$safeProperty" }
        mutate {
            indexes.removeIf { it.label == safeLabel && it.property == safeProperty }
        }
    }

    override fun listIndexes(): List<GraphIndex> =
        indexes.sortedWith(compareBy(GraphIndex::label, GraphIndex::property, GraphIndex::name))

    override fun listConstraints(): List<GraphConstraint> = emptyList()

    private fun mutate(action: () -> Unit) {
        mutationLock?.invoke(action) ?: action()
    }
}
