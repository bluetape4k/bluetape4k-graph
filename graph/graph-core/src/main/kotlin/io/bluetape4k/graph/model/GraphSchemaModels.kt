package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 그래프 스키마 객체가 적용되는 엔티티 종류.
 *
 * ```kotlin
 * val target = GraphSchemaEntityType.VERTEX
 * ```
 */
enum class GraphSchemaEntityType {
    /** 정점/노드 레이블에 적용되는 스키마 객체. */
    VERTEX,

    /** 간선/관계 타입에 적용되는 스키마 객체. */
    EDGE,

    /** 백엔드 메타데이터에서 엔티티 종류를 판별할 수 없는 스키마 객체. */
    UNKNOWN,
}

/**
 * 그래프 제약조건 종류.
 *
 * ```kotlin
 * val type = GraphConstraintType.UNIQUE
 * ```
 */
enum class GraphConstraintType {
    /** 특정 레이블과 속성 조합의 값이 유일해야 함을 나타낸다. */
    UNIQUE,

    /** 특정 속성이 반드시 존재해야 함을 나타낸다. */
    EXISTS,

    /** 백엔드 고유 제약조건이거나 아직 공통 타입으로 매핑하지 않은 종류. */
    UNKNOWN,
}

/**
 * 그래프 백엔드에 정의된 인덱스 메타데이터.
 *
 * @property name 백엔드 인덱스 이름. 이름이 없는 백엔드는 안정적인 합성 이름을 사용할 수 있다.
 * @property label 인덱스가 적용되는 정점 레이블 또는 간선 타입.
 * @property property 인덱스 속성 이름. label-only 인덱스는 `null`일 수 있다.
 * @property entityType 인덱스 대상 엔티티 종류.
 * @property unique 인덱스가 유니크 제약조건을 뒷받침하는지 여부.
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
 * 그래프 백엔드에 정의된 제약조건 메타데이터.
 *
 * @property name 백엔드 제약조건 이름. 이름이 없는 백엔드는 안정적인 합성 이름을 사용할 수 있다.
 * @property label 제약조건이 적용되는 정점 레이블 또는 간선 타입.
 * @property property 제약조건 속성 이름.
 * @property type 공통 제약조건 종류.
 * @property entityType 제약조건 대상 엔티티 종류.
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
