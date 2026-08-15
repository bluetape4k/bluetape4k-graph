package io.bluetape4k.graph.schema

import io.bluetape4k.graph.model.GraphConstraint
import io.bluetape4k.graph.model.GraphIndex
import java.io.Serializable

/**
 * backend-neutral로 선언한 desired schema이다.
 *
 * `name`은 backend가 synthetic name을 반환할 수 있으므로 drift 비교에서 사용하지 않는다.
 */
data class GraphSchemaDefinition(
    val indexes: Set<GraphIndex> = emptySet(),
    val constraints: Set<GraphConstraint> = emptySet(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** schema drift 계획에서 수행할 작업이다. */
enum class GraphSchemaPlanAction {
    CREATE_INDEX,
    CREATE_CONSTRAINT,
    DROP_INDEX,
    DROP_CONSTRAINT,
    SKIP,
    UNSUPPORTED,
}

/** schema drift 계획의 한 항목이다. */
data class GraphSchemaPlanItem(
    val action: GraphSchemaPlanAction,
    val index: GraphIndex? = null,
    val constraint: GraphConstraint? = null,
    val reason: String,
) : Serializable {
    init {
        require((index == null) xor (constraint == null)) {
            "Exactly one of index or constraint must be provided."
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * drift 계산 및 적용 옵션.
 *
 * 기본값은 dry-run이며 destructive drop을 허용하지 않는다. 삭제를 수행하려면 두 옵션을
 * 모두 명시적으로 켜야 한다.
 */
data class GraphSchemaPlanOptions(
    val dryRun: Boolean = true,
    val allowDestructiveDrops: Boolean = false,
) : Serializable {
    init {
        require(!allowDestructiveDrops || !dryRun) {
            "allowDestructiveDrops requires dryRun=false."
        }
    }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** schema drift 계획 결과이다. */
data class GraphSchemaPlan(
    val items: List<GraphSchemaPlanItem>,
    val options: GraphSchemaPlanOptions,
) : Serializable {
    val isNoop: Boolean get() = items.all { it.action == GraphSchemaPlanAction.SKIP }

    /** 계획에 기록된 create/drop 작업을 명시적으로 적용한다. */
    fun apply(manager: GraphSchemaManager): GraphSchemaApplyReport {
        val applied = mutableListOf<GraphSchemaPlanItem>()
        val skipped = mutableListOf<GraphSchemaPlanItem>()
        val unsupported = mutableListOf<GraphSchemaPlanItem>()

        if (options.dryRun) {
            return GraphSchemaApplyReport(applied, items, emptyList())
        }

        items.forEach { item ->
            when (val outcome = applyItem(manager, item)) {
                ApplyOutcome.APPLIED -> applied += item
                ApplyOutcome.SKIPPED -> skipped += item
                is ApplyOutcome.Unsupported -> unsupported += item.copy(
                    action = GraphSchemaPlanAction.UNSUPPORTED,
                    reason = outcome.reason,
                )
            }
        }
        return GraphSchemaApplyReport(applied, skipped, unsupported)
    }

    private fun applyItem(manager: GraphSchemaManager, item: GraphSchemaPlanItem): ApplyOutcome =
        when (item.action) {
            GraphSchemaPlanAction.CREATE_INDEX -> createIndex(manager, item)
            GraphSchemaPlanAction.CREATE_CONSTRAINT -> createConstraint(manager, item)
            GraphSchemaPlanAction.DROP_INDEX -> dropIndex(manager, item)
            GraphSchemaPlanAction.DROP_CONSTRAINT,
            GraphSchemaPlanAction.UNSUPPORTED,
            -> ApplyOutcome.Unsupported(item.reason)
            GraphSchemaPlanAction.SKIP -> ApplyOutcome.SKIPPED
        }

    private fun createIndex(manager: GraphSchemaManager, item: GraphSchemaPlanItem): ApplyOutcome {
        val index = requireNotNull(item.index)
        return unsupportedAsOutcome {
            manager.createIndex(index.label, requireProperty(index.property))
        }
    }

    private fun createConstraint(manager: GraphSchemaManager, item: GraphSchemaPlanItem): ApplyOutcome {
        val constraint = requireNotNull(item.constraint)
        return unsupportedAsOutcome {
            manager.createUniqueConstraint(constraint.label, constraint.property)
        }
    }

    private fun dropIndex(manager: GraphSchemaManager, item: GraphSchemaPlanItem): ApplyOutcome {
        val index = requireNotNull(item.index)
        manager.dropIndex(index.label, requireProperty(index.property))
        return ApplyOutcome.APPLIED
    }

    private fun unsupportedAsOutcome(action: () -> Unit): ApplyOutcome =
        try {
            action()
            ApplyOutcome.APPLIED
        } catch (error: UnsupportedOperationException) {
            ApplyOutcome.Unsupported(error.message ?: "GraphSchemaManager does not support this operation.")
        }

    private sealed interface ApplyOutcome {
        data object APPLIED: ApplyOutcome
        data object SKIPPED: ApplyOutcome
        data class Unsupported(val reason: String): ApplyOutcome
    }

    private fun requireProperty(property: String?): String =
        requireNotNull(property) { "Schema mutation requires a property name." }

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/** schema drift 적용 결과이다. */
data class GraphSchemaApplyReport(
    val applied: List<GraphSchemaPlanItem>,
    val skipped: List<GraphSchemaPlanItem>,
    val unsupported: List<GraphSchemaPlanItem>,
) : Serializable {
    val isSuccessful: Boolean get() = unsupported.isEmpty()

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 현재 live schema와 desired schema를 비교해 dry-run 계획을 만든다.
 *
 * extra object는 기본적으로 `SKIP`이며, `allowDestructiveDrops=true`이고 `dryRun=false`일 때만
 * drop 계획으로 바뀐다. constraint drop은 현재 공통 API가 없으므로 항상 `UNSUPPORTED`이다.
 */
fun GraphSchemaManager.plan(
    desired: GraphSchemaDefinition,
    options: GraphSchemaPlanOptions = GraphSchemaPlanOptions(),
): GraphSchemaPlan {
    val liveIndexes = listIndexes()
    val liveConstraints = listConstraints()
    val desiredIndexes = desired.indexes
    val desiredConstraints = desired.constraints
    val items = buildList {
        addMissingIndexes(liveIndexes, desiredIndexes)
        addMissingConstraints(liveConstraints, desiredConstraints)
        addExtraIndexes(liveIndexes, desiredIndexes, options)
        addExtraConstraints(liveConstraints, desiredConstraints, options)
    }
    return GraphSchemaPlan(items, options)
}

private fun MutableList<GraphSchemaPlanItem>.addMissingIndexes(
    live: Collection<GraphIndex>,
    desired: Set<GraphIndex>,
) {
    desired.filterNot { wanted -> live.any { it.sameSchema(wanted) } }.forEach {
        add(GraphSchemaPlanItem(GraphSchemaPlanAction.CREATE_INDEX, index = it, reason = "desired index is missing"))
    }
}

private fun MutableList<GraphSchemaPlanItem>.addMissingConstraints(
    live: Collection<GraphConstraint>,
    desired: Set<GraphConstraint>,
) {
    desired.filterNot { wanted -> live.any { it.sameSchema(wanted) } }.forEach {
        add(
            GraphSchemaPlanItem(
                GraphSchemaPlanAction.CREATE_CONSTRAINT,
                constraint = it,
                reason = "desired unique constraint is missing",
            ),
        )
    }
}

private fun MutableList<GraphSchemaPlanItem>.addExtraIndexes(
    live: List<GraphIndex>,
    desired: Set<GraphIndex>,
    options: GraphSchemaPlanOptions,
) {
    live.filterNot { existing -> desired.any { it.sameSchema(existing) } }.forEach {
        add(
            GraphSchemaPlanItem(
                action = options.dropAction(GraphSchemaPlanAction.DROP_INDEX),
                index = it,
                reason = options.dropReason("live index is extra"),
            ),
        )
    }
}

private fun MutableList<GraphSchemaPlanItem>.addExtraConstraints(
    live: List<GraphConstraint>,
    desired: Set<GraphConstraint>,
    options: GraphSchemaPlanOptions,
) {
    live.filterNot { existing -> desired.any { it.sameSchema(existing) } }.forEach {
        add(
            GraphSchemaPlanItem(
                action = options.dropAction(GraphSchemaPlanAction.UNSUPPORTED),
                constraint = it,
                reason = options.dropReason("GraphSchemaManager does not expose constraint drop"),
            ),
        )
    }
}

private fun GraphSchemaPlanOptions.dropAction(destructiveAction: GraphSchemaPlanAction): GraphSchemaPlanAction =
    if (allowDestructiveDrops) destructiveAction else GraphSchemaPlanAction.SKIP

private fun GraphSchemaPlanOptions.dropReason(destructiveReason: String): String =
    if (allowDestructiveDrops) destructiveReason else "destructive drop is disabled"

private fun GraphIndex.sameSchema(other: GraphIndex): Boolean =
    label == other.label && property == other.property && entityType == other.entityType && unique == other.unique

private fun GraphConstraint.sameSchema(other: GraphConstraint): Boolean =
    label == other.label && property == other.property && entityType == other.entityType && type == other.type
