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
 * graph backend index와 constraint를 위한 synchronous API.
 *
 * implementation은 backend DDL 차이를 숨기지만, unsupported constraint는 성공한 척하지 않고
 * [UnsupportedOperationException]으로 명시적으로 실패해야 한다.
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
     * 주어진 vertex label과 property에 lookup index를 생성한다.
	*
     * @param label vertex label.
     * @param property index 대상 property name.
     */
    fun createIndex(label: String, property: String)

    /**
     * 주어진 vertex label과 property에 unique constraint를 생성한다.
	*
     * @param label vertex label.
     * @param property unique해야 하는 property name.
     */
    fun createUniqueConstraint(label: String, property: String)

    /**
     * 주어진 vertex label과 property의 lookup index를 삭제한다.
	*
     * @param label vertex label.
     * @param property index가 걸린 property name.
     */
    fun dropIndex(label: String, property: String)

    /**
     * 현재 graph에 정의된 common index metadata를 반환한다.
     */
    fun listIndexes(): List<GraphIndex>

    /**
     * 현재 graph에 정의된 common constraint metadata를 반환한다.
     */
    fun listConstraints(): List<GraphConstraint>
}

/**
 * graph backend index와 constraint를 위한 coroutine API.
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

    /** 주어진 vertex label과 property에 lookup index를 생성한다. */
    suspend fun createIndex(label: String, property: String)

    /** 주어진 vertex label과 property에 unique constraint를 생성한다. */
    suspend fun createUniqueConstraint(label: String, property: String)

    /** 주어진 vertex label과 property의 lookup index를 삭제한다. */
    suspend fun dropIndex(label: String, property: String)

    /** 현재 graph에 정의된 common index metadata를 반환한다. */
    suspend fun listIndexes(): List<GraphIndex>

    /** 현재 graph에 정의된 common constraint metadata를 반환한다. */
    suspend fun listConstraints(): List<GraphConstraint>
}

/**
 * schema management를 제공하는 synchronous graph implementation용 capability interface.
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
 * schema management를 제공하는 coroutine graph implementation용 capability interface.
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
 * synchronous schema manager를 [Dispatchers.IO]에서 실행하는 coroutine adapter.
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
 * synchronous schema manager를 coroutine API로 노출한다.
 *
 * ```kotlin
 * val schema = ops.schemaManager().asSuspendSchemaManager()
 * ```
 */
fun GraphSchemaManager.asSuspendSchemaManager(): GraphSuspendSchemaManager =
    BlockingGraphSuspendSchemaManager(this)

/**
 * schema DDL을 아직 안전하게 지원할 수 없는 backend를 위한 explicit-failure manager.
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
 * [GraphOperations]용 schema manager를 반환한다.
 *
 * implementation이 [GraphSchemaManagementOperations]를 구현하지 않으면 이 함수는
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
 * [GraphSuspendOperations]용 coroutine schema manager를 반환한다.
 *
 * implementation이 [GraphSuspendSchemaManagementOperations]를 구현하지 않으면 이 함수는
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
 * common schema object name을 만든다.
 *
 * ```kotlin
 * val index = GraphSchemaNames.indexName("Person", "email")
 * val constraint = GraphSchemaNames.uniqueConstraintName("Person", "email")
 * ```
 */
object GraphSchemaNames {

    /**
     * common index name을 만든다.
     */
    fun indexName(label: String, property: String): String =
        buildName("bt4k_idx", label, property)

    /**
     * common unique constraint name을 만든다.
     */
    fun uniqueConstraintName(label: String, property: String): String =
        buildName("bt4k_uc", label, property)

    /**
     * label과 property identifier를 검증한다.
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
