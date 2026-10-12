package io.bluetape4k.graph.schema

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphConstraintType
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class GraphSchemaDriftPlannerTest: AbstractGraphTest() {

    companion object: KLogging()

    @Test
    fun `missing index is planned and dry run does not mutate live schema`() {
        TinkerGraphOperations().use { ops ->
            val manager = ops.schemaManager()
            val desired = GraphSchemaDefinition(
                indexes = setOf(GraphIndex("ignored", "Person", "email")),
            )

            val plan = manager.plan(desired)
            log.debug { "plan=$plan" }

            plan.items.single().action shouldBeEqualTo GraphSchemaPlanAction.CREATE_INDEX
            manager.listIndexes().shouldBeEmpty()
        }
    }

    @Test
    fun `extra index is skipped unless destructive drops are explicitly enabled`() {
        TinkerGraphOperations().use { ops ->
            val manager = ops.schemaManager()
            manager.createIndex("Person", "email")

            val safe = manager.plan(GraphSchemaDefinition())
            log.debug { "safe=$safe" }
            safe.items.single().action shouldBeEqualTo GraphSchemaPlanAction.SKIP

            val destructive = manager.plan(
                GraphSchemaDefinition(),
                GraphSchemaPlanOptions(dryRun = false, allowDestructiveDrops = true),
            )
            log.debug { "destructive=$destructive" }
            destructive.items.single().action shouldBeEqualTo GraphSchemaPlanAction.DROP_INDEX
            destructive.apply(manager).isSuccessful.shouldBeTrue()
            manager.listIndexes().shouldBeEmpty()
        }
    }

    @Test
    fun `unsupported constraint creation is reported without silent success`() {
        TinkerGraphOperations().use { ops ->
            val manager = ops.schemaManager()
            val plan = manager.plan(
                GraphSchemaDefinition(
                    constraints = setOf(
                        GraphConstraint("ignored", "Person", "email", GraphConstraintType.UNIQUE),
                    ),
                ),
                GraphSchemaPlanOptions(dryRun = false),
            )

            val report = plan.apply(manager)
            log.debug { "report=$report" }

            report.isSuccessful.shouldBeFalse()
            report.unsupported.single().action shouldBeEqualTo GraphSchemaPlanAction.UNSUPPORTED
            report.unsupported.single().reason shouldContain "unique"
        }
    }

    @Test
    fun `destructive drops require dry run to be disabled`() {
        assertFailsWith<IllegalArgumentException> {
            GraphSchemaPlanOptions(dryRun = true, allowDestructiveDrops = true)
        }
    }
}
