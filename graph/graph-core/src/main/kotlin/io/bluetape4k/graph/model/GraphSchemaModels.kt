package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Entity type a graph schema object applies to.
 *
 * ```kotlin
 * val target = GraphSchemaEntityType.VERTEX
 * ```
 */
enum class GraphSchemaEntityType {
    /** Schema object for a vertex/node label. */
    VERTEX,

    /** Schema object for an edge/relationship type. */
    EDGE,

    /** Schema object whose entity type cannot be determined from backend metadata. */
    UNKNOWN,
}

/**
 * Graph constraint type.
 *
 * ```kotlin
 * val type = GraphConstraintType.UNIQUE
 * ```
 */
enum class GraphConstraintType {
    /** Requires unique values for a specific label and property combination. */
    UNIQUE,

    /** Requires a specific property to exist. */
    EXISTS,

    /** Backend-specific constraint, or one not yet mapped to a common type. */
    UNKNOWN,
}

/**
 * Index metadata defined in a graph backend.
 *
 * @property name Backend index name. Backends without names may use a stable synthetic name.
 * @property label Vertex label or edge type the index applies to.
 * @property property Indexed property name. Label-only indexes may be `null`.
 * @property entityType Target entity type for the index.
 * @property unique Whether the index backs a unique constraint.
 *
 * ```kotlin
 * val index = GraphIndex(
 *     name = "bt4k_idx_Person_email",
 *     label = "Person",
 *     property = "email",
 * )
 * ```
 */
data class GraphIndex(
    val name: String,
    val label: String,
    val property: String?,
    val entityType: GraphSchemaEntityType = GraphSchemaEntityType.VERTEX,
    val unique: Boolean = false,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Constraint metadata defined in a graph backend.
 *
 * @property name Backend constraint name. Backends without names may use a stable synthetic name.
 * @property label Vertex label or edge type the constraint applies to.
 * @property property Constraint property name.
 * @property type Common constraint type.
 * @property entityType Target entity type for the constraint.
 *
 * ```kotlin
 * val constraint = GraphConstraint(
 *     name = "bt4k_uc_Person_email",
 *     label = "Person",
 *     property = "email",
 *     type = GraphConstraintType.UNIQUE,
 * )
 * ```
 */
data class GraphConstraint(
    val name: String,
    val label: String,
    val property: String,
    val type: GraphConstraintType,
    val entityType: GraphSchemaEntityType = GraphSchemaEntityType.VERTEX,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
