package io.bluetape4k.graph.schema

import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphConstraintType
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test

class GraphSchemaDriftPlannerTest {

    @Test
    fun `missing index is planned and dry run does not mutate live schema`() {
        val ops = TinkerGraphOperations()
        val manager = ops.schemaManager()
        val desired = GraphSchemaDefinition(
            indexes = setOf(GraphIndex("ignored", "Person", "email")),
        )

        val plan = manager.plan(desired)

        plan.items.single().action shouldBeEqualTo GraphSchemaPlanAction.CREATE_INDEX
        manager.listIndexes() shouldBeEqualTo emptyList()
        ops.close()
    }

    @Test
    fun `extra index is skipped unless destructive drops are explicitly enabled`() {
        val ops = TinkerGraphOperations()
        val manager = ops.schemaManager()
        manager.createIndex("Person", "email")

        val safe = manager.plan(GraphSchemaDefinition())
        safe.items.single().action shouldBeEqualTo GraphSchemaPlanAction.SKIP

        val destructive = manager.plan(
            GraphSchemaDefinition(),
            GraphSchemaPlanOptions(dryRun = false, allowDestructiveDrops = true),
        )
        destructive.items.single().action shouldBeEqualTo GraphSchemaPlanAction.DROP_INDEX
        destructive.apply(manager).isSuccessful shouldBeEqualTo true
        manager.listIndexes() shouldBeEqualTo emptyList()
        ops.close()
    }

    @Test
    fun `unsupported constraint creation is reported without silent success`() {
        val ops = TinkerGraphOperations()
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

        report.isSuccessful shouldBeEqualTo false
        report.unsupported.single().action shouldBeEqualTo GraphSchemaPlanAction.UNSUPPORTED
        report.unsupported.single().reason shouldContain "unique"
        ops.close()
    }

    @Test
    fun `destructive drops require dry run to be disabled`() {
        assertFailsWith<IllegalArgumentException> {
            GraphSchemaPlanOptions(dryRun = true, allowDestructiveDrops = true)
        }
    }
}
