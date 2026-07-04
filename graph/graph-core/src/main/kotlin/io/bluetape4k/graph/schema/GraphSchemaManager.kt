package io.bluetape4k.graph.schema

import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Synchronous API for graph backend indexes and constraints.
 *
 * Implementations hide backend DDL differences, but unsupported constraints must fail explicitly
 * with [UnsupportedOperationException] rather than pretending to succeed.
 *
 * ## Contract
 * - Labels and properties must be safe backend query identifiers.
 * - Unsupported schema DDL must fail with [UnsupportedOperationException], not a silent no-op.
 * - [listIndexes] and [listConstraints] return backend metadata mapped to common model types.
 *
 * ```kotlin
 * import io.bluetape4k.graph.schema.schemaManager
 *
 * val schema = ops.schemaManager()
 * schema.createIndex("Person", "email")
 * schema.createUniqueConstraint("Person", "email")
 * val indexes = schema.listIndexes()
 * ```
 */
interface GraphSchemaManager {

    /**
     * Creates a lookup index for the given vertex label and property.
	*
     * @param label vertex label.
     * @param property property name to index.
     */
    fun createIndex(label: String, property: String)

    /**
     * Creates a unique constraint for the given vertex label and property.
	*
     * @param label vertex label.
     * @param property property name that must be unique.
     */
    fun createUniqueConstraint(label: String, property: String)

    /**
     * Drops the lookup index for the given vertex label and property.
	*
     * @param label vertex label.
     * @param property indexed property name.
     */
    fun dropIndex(label: String, property: String)

    /**
     * Returns common index metadata defined in the current graph.
     */
    fun listIndexes(): List<GraphIndex>

    /**
     * Returns common constraint metadata defined in the current graph.
     */
    fun listConstraints(): List<GraphConstraint>
}

/**
 * Coroutine API for graph backend indexes and constraints.
 *
 * ## Contract
 * - Provides the same schema metadata semantics as [GraphSchemaManager].
 * - Blocking backend adapters run through [BlockingGraphSuspendSchemaManager] on [Dispatchers.IO].
 *
 * ```kotlin
 * import io.bluetape4k.graph.schema.schemaManager
 *
 * val schema = suspendOps.schemaManager()
 * schema.createIndex("Person", "email")
 * val constraints = schema.listConstraints()
 * ```
 */
interface GraphSuspendSchemaManager {

    /** Creates a lookup index for the given vertex label and property. */
    suspend fun createIndex(label: String, property: String)

    /** Creates a unique constraint for the given vertex label and property. */
    suspend fun createUniqueConstraint(label: String, property: String)

    /** Drops the lookup index for the given vertex label and property. */
    suspend fun dropIndex(label: String, property: String)

    /** Returns common index metadata defined in the current graph. */
    suspend fun listIndexes(): List<GraphIndex>

    /** Returns common constraint metadata defined in the current graph. */
    suspend fun listConstraints(): List<GraphConstraint>
}

/**
 * Capability interface for synchronous graph implementations that provide schema management.
 *
 * ```kotlin
 * val schema = ops.schemaManager()
 * schema.dropIndex("Person", "email")
 * ```
 */
interface GraphSchemaManagementOperations {
    /** Returns the schema manager for this graph implementation. */
    fun schemaManager(): GraphSchemaManager
}

/**
 * Capability interface for coroutine graph implementations that provide schema management.
 *
 * ```kotlin
 * val schema = suspendOps.schemaManager()
 * schema.listIndexes()
 * ```
 */
interface GraphSuspendSchemaManagementOperations {
    /** Returns the coroutine schema manager for this graph implementation. */
    fun schemaManager(): GraphSuspendSchemaManager
}

/**
 * Coroutine adapter that runs a synchronous schema manager on [Dispatchers.IO].
 *
 * ```kotlin
 * val suspendSchema = ops.schemaManager().asSuspendSchemaManager()
 * suspendSchema.createIndex("Person", "email")
 * ```
 */
class BlockingGraphSuspendSchemaManager(
    private val delegate: GraphSchemaManager,
): GraphSuspendSchemaManager {

    override suspend fun createIndex(label: String, property: String) {
        withContext(Dispatchers.IO) {
            delegate.createIndex(label, property)
        }
    }

    override suspend fun createUniqueConstraint(label: String, property: String) {
        withContext(Dispatchers.IO) {
            delegate.createUniqueConstraint(label, property)
        }
    }

    override suspend fun dropIndex(label: String, property: String) {
        withContext(Dispatchers.IO) {
            delegate.dropIndex(label, property)
        }
    }

    override suspend fun listIndexes(): List<GraphIndex> =
        withContext(Dispatchers.IO) {
            delegate.listIndexes()
        }

    override suspend fun listConstraints(): List<GraphConstraint> =
        withContext(Dispatchers.IO) {
            delegate.listConstraints()
        }
}

/**
 * Exposes a synchronous schema manager as a coroutine API.
 *
 * ```kotlin
 * val schema = ops.schemaManager().asSuspendSchemaManager()
 * ```
 */
fun GraphSchemaManager.asSuspendSchemaManager(): GraphSuspendSchemaManager =
    BlockingGraphSuspendSchemaManager(this)

/**
 * Explicit-failure manager for backends that cannot safely support schema DDL yet.
 *
 * ## Contract
 * - [listIndexes] and [listConstraints] return empty lists.
 * - Mutation APIs validate identifiers and then throw [UnsupportedOperationException].
 *
 * ```kotlin
 * val schema = UnsupportedGraphSchemaManager("AGE", "portable AGE index DDL is not available")
 * schema.listIndexes() // emptyList()
 * ```
 */
class UnsupportedGraphSchemaManager(
    private val backendName: String,
    private val reason: String,
): GraphSchemaManager {

    override fun createIndex(label: String, property: String) {
        GraphSchemaNames.validateLabelAndProperty(label, property)
        throw unsupported("createIndex")
    }

    override fun createUniqueConstraint(label: String, property: String) {
        GraphSchemaNames.validateLabelAndProperty(label, property)
        throw unsupported("createUniqueConstraint")
    }

    override fun dropIndex(label: String, property: String) {
        GraphSchemaNames.validateLabelAndProperty(label, property)
        throw unsupported("dropIndex")
    }

    override fun listIndexes(): List<GraphIndex> = emptyList()

    override fun listConstraints(): List<GraphConstraint> = emptyList()

    private fun unsupported(operation: String): UnsupportedOperationException =
        UnsupportedOperationException("$backendName does not support $operation through GraphSchemaManager: $reason")
}

/**
 * Returns the schema manager for [GraphOperations].
 *
 * If the implementation does not implement [GraphSchemaManagementOperations], this throws
 * [UnsupportedOperationException] instead of using an automatic no-op fallback.
 *
 * ```kotlin
 * import io.bluetape4k.graph.schema.schemaManager
 *
 * val schema = ops.schemaManager()
 * schema.createIndex("Person", "email")
 * ```
 */
fun GraphOperations.schemaManager(): GraphSchemaManager {
    val management = this as? GraphSchemaManagementOperations
        ?: throw UnsupportedOperationException(
            "${this::class.qualifiedName ?: this::class.simpleName} does not support graph schema management."
        )
    return management.schemaManager()
}

/**
 * Returns the coroutine schema manager for [GraphSuspendOperations].
 *
 * If the implementation does not implement [GraphSuspendSchemaManagementOperations], this throws
 * [UnsupportedOperationException].
 *
 * ```kotlin
 * import io.bluetape4k.graph.schema.schemaManager
 *
 * val schema = suspendOps.schemaManager()
 * schema.listConstraints()
 * ```
 */
fun GraphSuspendOperations.schemaManager(): GraphSuspendSchemaManager {
    val management = this as? GraphSuspendSchemaManagementOperations
        ?: throw UnsupportedOperationException(
            "${this::class.qualifiedName ?: this::class.simpleName} does not support suspend graph schema management."
        )
    return management.schemaManager()
}

/** Creates a lookup index from [VertexLabel] and [PropertyDef]. */
fun GraphSchemaManager.createIndex(label: VertexLabel, property: PropertyDef<*>) =
    createIndex(label.label, property.name)

/** Creates a unique constraint from [VertexLabel] and [PropertyDef]. */
fun GraphSchemaManager.createUniqueConstraint(label: VertexLabel, property: PropertyDef<*>) =
    createUniqueConstraint(label.label, property.name)

/** Drops a lookup index from [VertexLabel] and [PropertyDef]. */
fun GraphSchemaManager.dropIndex(label: VertexLabel, property: PropertyDef<*>) =
    dropIndex(label.label, property.name)

/** Creates a lookup index from [VertexLabel] and [PropertyDef]. */
suspend fun GraphSuspendSchemaManager.createIndex(label: VertexLabel, property: PropertyDef<*>) =
    createIndex(label.label, property.name)

/** Creates a unique constraint from [VertexLabel] and [PropertyDef]. */
suspend fun GraphSuspendSchemaManager.createUniqueConstraint(label: VertexLabel, property: PropertyDef<*>) =
    createUniqueConstraint(label.label, property.name)

/** Drops a lookup index from [VertexLabel] and [PropertyDef]. */
suspend fun GraphSuspendSchemaManager.dropIndex(label: VertexLabel, property: PropertyDef<*>) =
    dropIndex(label.label, property.name)

/**
 * Builds common schema object names.
 *
 * ```kotlin
 * val index = GraphSchemaNames.indexName("Person", "email")
 * val constraint = GraphSchemaNames.uniqueConstraintName("Person", "email")
 * ```
 */
object GraphSchemaNames {

    /**
     * Builds a common index name.
     */
    fun indexName(label: String, property: String): String =
        buildName("bt4k_idx", label, property)

    /**
     * Builds a common unique constraint name.
     */
    fun uniqueConstraintName(label: String, property: String): String =
        buildName("bt4k_uc", label, property)

    /**
     * Validates label and property identifiers.
     */
    fun validateLabelAndProperty(label: String, property: String): Pair<String, String> {
        val safeLabel = label.requireNotBlank("label").requireSafeIdentifier("label")
        val safeProperty = property.requireNotBlank("property").requireSafeIdentifier("property")
        return safeLabel to safeProperty
    }

    private fun buildName(prefix: String, label: String, property: String): String {
        val (safeLabel, safeProperty) = validateLabelAndProperty(label, property)
        return "${prefix}_${safeLabel}_${safeProperty}"
    }
}
