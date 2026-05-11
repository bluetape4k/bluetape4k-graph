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
 * 그래프 백엔드의 인덱스와 제약조건을 관리하는 동기 API.
 *
 * 구현체는 백엔드 DDL 차이를 숨기되, 지원하지 않는 제약조건을 성공한 것처럼 처리하지 않고
 * 명시적으로 [UnsupportedOperationException]을 던져야 한다.
 *
 * ## 동작/계약
 * - label과 property는 backend query에 안전한 identifier여야 한다.
 * - 지원하지 않는 schema DDL은 silent no-op 대신 [UnsupportedOperationException]으로 실패해야 한다.
 * - [listIndexes]와 [listConstraints]는 backend metadata를 공통 모델로 변환해 반환한다.
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
     * 지정한 정점 레이블과 속성에 조회 인덱스를 생성한다.
     *
     * @param label 정점 레이블.
     * @param property 인덱싱할 속성 이름.
     */
    fun createIndex(label: String, property: String)

    /**
     * 지정한 정점 레이블과 속성에 유니크 제약조건을 생성한다.
     *
     * @param label 정점 레이블.
     * @param property 유일해야 하는 속성 이름.
     */
    fun createUniqueConstraint(label: String, property: String)

    /**
     * 지정한 정점 레이블과 속성에 연결된 조회 인덱스를 제거한다.
     *
     * @param label 정점 레이블.
     * @param property 인덱싱된 속성 이름.
     */
    fun dropIndex(label: String, property: String)

    /**
     * 현재 그래프에 정의된 공통 인덱스 메타데이터를 반환한다.
     */
    fun listIndexes(): List<GraphIndex>

    /**
     * 현재 그래프에 정의된 공통 제약조건 메타데이터를 반환한다.
     */
    fun listConstraints(): List<GraphConstraint>
}

/**
 * 그래프 백엔드의 인덱스와 제약조건을 관리하는 코루틴 API.
 *
 * ## 동작/계약
 * - [GraphSchemaManager]와 같은 schema metadata semantics를 suspend API로 제공한다.
 * - blocking backend adapter는 [BlockingGraphSuspendSchemaManager]를 통해 [Dispatchers.IO]에서 실행한다.
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

    /** 지정한 정점 레이블과 속성에 조회 인덱스를 생성한다. */
    suspend fun createIndex(label: String, property: String)

    /** 지정한 정점 레이블과 속성에 유니크 제약조건을 생성한다. */
    suspend fun createUniqueConstraint(label: String, property: String)

    /** 지정한 정점 레이블과 속성에 연결된 조회 인덱스를 제거한다. */
    suspend fun dropIndex(label: String, property: String)

    /** 현재 그래프에 정의된 공통 인덱스 메타데이터를 반환한다. */
    suspend fun listIndexes(): List<GraphIndex>

    /** 현재 그래프에 정의된 공통 제약조건 메타데이터를 반환한다. */
    suspend fun listConstraints(): List<GraphConstraint>
}

/**
 * 동기 그래프 구현체가 스키마 관리 기능을 제공할 때 구현하는 capability interface.
 *
 * ```kotlin
 * val schema = ops.schemaManager()
 * schema.dropIndex("Person", "email")
 * ```
 */
interface GraphSchemaManagementOperations {
    /** 이 그래프 구현체의 스키마 관리자를 반환한다. */
    fun schemaManager(): GraphSchemaManager
}

/**
 * 코루틴 그래프 구현체가 스키마 관리 기능을 제공할 때 구현하는 capability interface.
 *
 * ```kotlin
 * val schema = suspendOps.schemaManager()
 * schema.listIndexes()
 * ```
 */
interface GraphSuspendSchemaManagementOperations {
    /** 이 그래프 구현체의 코루틴 스키마 관리자를 반환한다. */
    fun schemaManager(): GraphSuspendSchemaManager
}

/**
 * 동기 스키마 관리자를 [Dispatchers.IO]에서 실행하는 코루틴 어댑터.
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
 * 동기 스키마 관리자를 코루틴 API로 노출한다.
 *
 * ```kotlin
 * val schema = ops.schemaManager().asSuspendSchemaManager()
 * ```
 */
fun GraphSchemaManager.asSuspendSchemaManager(): GraphSuspendSchemaManager =
    BlockingGraphSuspendSchemaManager(this)

/**
 * 백엔드가 현재 schema DDL을 안전하게 지원하지 않을 때 사용하는 명시적 실패 관리자.
 *
 * ## 동작/계약
 * - [listIndexes]와 [listConstraints]는 빈 목록을 반환한다.
 * - mutation API는 identifier validation 후 [UnsupportedOperationException]을 던진다.
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
 * [GraphOperations]에서 스키마 관리자를 얻는다.
 *
 * 구현체가 [GraphSchemaManagementOperations]를 구현하지 않으면 auto no-op fallback을 사용하지 않고
 * 명시적으로 [UnsupportedOperationException]을 던진다.
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
 * [GraphSuspendOperations]에서 코루틴 스키마 관리자를 얻는다.
 *
 * 구현체가 [GraphSuspendSchemaManagementOperations]를 구현하지 않으면 명시적으로
 * [UnsupportedOperationException]을 던진다.
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

/** [VertexLabel]과 [PropertyDef]로 조회 인덱스를 생성한다. */
fun GraphSchemaManager.createIndex(label: VertexLabel, property: PropertyDef<*>) =
    createIndex(label.label, property.name)

/** [VertexLabel]과 [PropertyDef]로 유니크 제약조건을 생성한다. */
fun GraphSchemaManager.createUniqueConstraint(label: VertexLabel, property: PropertyDef<*>) =
    createUniqueConstraint(label.label, property.name)

/** [VertexLabel]과 [PropertyDef]로 조회 인덱스를 제거한다. */
fun GraphSchemaManager.dropIndex(label: VertexLabel, property: PropertyDef<*>) =
    dropIndex(label.label, property.name)

/** [VertexLabel]과 [PropertyDef]로 조회 인덱스를 생성한다. */
suspend fun GraphSuspendSchemaManager.createIndex(label: VertexLabel, property: PropertyDef<*>) =
    createIndex(label.label, property.name)

/** [VertexLabel]과 [PropertyDef]로 유니크 제약조건을 생성한다. */
suspend fun GraphSuspendSchemaManager.createUniqueConstraint(label: VertexLabel, property: PropertyDef<*>) =
    createUniqueConstraint(label.label, property.name)

/** [VertexLabel]과 [PropertyDef]로 조회 인덱스를 제거한다. */
suspend fun GraphSuspendSchemaManager.dropIndex(label: VertexLabel, property: PropertyDef<*>) =
    dropIndex(label.label, property.name)

/**
 * 공통 스키마 객체 이름을 생성한다.
 *
 * ```kotlin
 * val index = GraphSchemaNames.indexName("Person", "email")
 * val constraint = GraphSchemaNames.uniqueConstraintName("Person", "email")
 * ```
 */
object GraphSchemaNames {

    /**
     * 공통 인덱스 이름을 생성한다.
     */
    fun indexName(label: String, property: String): String =
        buildName("bt4k_idx", label, property)

    /**
     * 공통 유니크 제약조건 이름을 생성한다.
     */
    fun uniqueConstraintName(label: String, property: String): String =
        buildName("bt4k_uc", label, property)

    /**
     * 레이블과 속성 식별자를 검증한다.
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
