package io.bluetape4k.graph.model

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.io.Serializable

class GraphSchemaModelsTest: AbstractGraphTest() {

    companion object: KLogging()

    @Test
    fun `GraphSchemaEntityType은 vertex edge unknown을 가진다`() {
        GraphSchemaEntityType.entries shouldBeEqualTo listOf(
            GraphSchemaEntityType.VERTEX,
            GraphSchemaEntityType.EDGE,
            GraphSchemaEntityType.COMPOSITE,
            GraphSchemaEntityType.UNKNOWN,
        )
    }

    @Test
    fun `GraphConstraintType은 unique exists unknown을 가진다`() {
        GraphConstraintType.entries shouldBeEqualTo listOf(
            GraphConstraintType.UNIQUE,
            GraphConstraintType.EXISTS,
            GraphConstraintType.UNKNOWN,
        )
    }

    @Test
    fun `GraphIndex 기본 entityType은 vertex이며 unique는 false이다`() {
        val index = GraphIndex(name = "bt4k_idx_Person_email", label = "Person", property = "email")

        log.debug { "index=$index" }
        index.name shouldBeEqualTo "bt4k_idx_Person_email"
        index.label shouldBeEqualTo "Person"
        index.property shouldBeEqualTo "email"
        index.entityType shouldBeEqualTo GraphSchemaEntityType.VERTEX
        index.unique shouldBeEqualTo false
    }

    @Test
    fun `GraphIndex는 edge schema와 property 없는 index를 표현한다`() {
        val index = GraphIndex(
            name = "bt4k_idx_KNOWS",
            label = "KNOWS",
            property = null,
            entityType = GraphSchemaEntityType.EDGE,
            unique = true,
        )

        log.debug { "index=$index" }
        index.property.shouldBeNull()
        index.entityType shouldBeEqualTo GraphSchemaEntityType.EDGE
        index.unique shouldBeEqualTo true
    }

    @Test
    fun `GraphIndex copy로 일부 필드만 변경한다`() {
        val base = GraphIndex(name = "bt4k_idx_Person_email", label = "Person", property = "email")
        val updated = base.copy(name = "bt4k_idx_Account_email", label = "Account")

        log.debug { "updated=$updated" }
        updated.name shouldBeEqualTo "bt4k_idx_Account_email"
        updated.label shouldBeEqualTo "Account"
        updated.property shouldBeEqualTo "email"
        updated.entityType shouldBeEqualTo GraphSchemaEntityType.VERTEX
        updated.unique shouldBeEqualTo false
    }

    @Test
    fun `GraphConstraint 기본 entityType은 vertex이다`() {
        val constraint = GraphConstraint(
            name = "bt4k_uc_Person_email",
            label = "Person",
            property = "email",
            type = GraphConstraintType.UNIQUE,
        )

        log.debug { "constraint=$constraint" }
        constraint.name shouldBeEqualTo "bt4k_uc_Person_email"
        constraint.label shouldBeEqualTo "Person"
        constraint.property shouldBeEqualTo "email"
        constraint.type shouldBeEqualTo GraphConstraintType.UNIQUE
        constraint.entityType shouldBeEqualTo GraphSchemaEntityType.VERTEX
    }

    @Test
    fun `GraphConstraint는 edge constraint도 표현한다`() {
        val constraint = GraphConstraint(
            name = "bt4k_exists_KNOWS_since",
            label = "KNOWS",
            property = "since",
            type = GraphConstraintType.EXISTS,
            entityType = GraphSchemaEntityType.EDGE,
        )

        log.debug { "constraint=$constraint" }
        constraint.type shouldBeEqualTo GraphConstraintType.EXISTS
        constraint.entityType shouldBeEqualTo GraphSchemaEntityType.EDGE
    }

    @Test
    fun `GraphConstraint copy로 type만 변경한다`() {
        val base = GraphConstraint(
            name = "bt4k_constraint_unknown",
            label = "Account",
            property = "risk",
            type = GraphConstraintType.UNKNOWN,
        )
        val updated = base.copy(type = GraphConstraintType.EXISTS)

        log.debug { "updated=$updated" }
        updated.name shouldBeEqualTo "bt4k_constraint_unknown"
        updated.label shouldBeEqualTo "Account"
        updated.property shouldBeEqualTo "risk"
        updated.type shouldBeEqualTo GraphConstraintType.EXISTS
    }

    @Test
    fun `schema metadata model들은 Serializable이다`() {
        val index: Serializable = GraphIndex(
            name = "bt4k_idx_Person_email",
            label = "Person",
            property = "email",
        )
        val constraint: Serializable = GraphConstraint(
            name = "bt4k_uc_Person_email",
            label = "Person",
            property = "email",
            type = GraphConstraintType.UNIQUE,
        )

        index shouldBeInstanceOf Serializable::class
        constraint shouldBeInstanceOf Serializable::class
    }
}
