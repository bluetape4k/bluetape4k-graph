package io.bluetape4k.graph.examples.supplychain.service

import io.bluetape4k.graph.examples.supplychain.schema.AlternatePartLabel
import io.bluetape4k.graph.examples.supplychain.schema.CustomerOrderLabel
import io.bluetape4k.graph.examples.supplychain.schema.DeliversToLabel
import io.bluetape4k.graph.examples.supplychain.schema.PartLabel
import io.bluetape4k.graph.examples.supplychain.schema.RequiredByLabel
import io.bluetape4k.graph.examples.supplychain.schema.RouteLabel
import io.bluetape4k.graph.examples.supplychain.schema.StockedAtLabel
import io.bluetape4k.graph.examples.supplychain.schema.SupplierLabel
import io.bluetape4k.graph.examples.supplychain.schema.SuppliesLabel
import io.bluetape4k.graph.examples.supplychain.schema.UsesRouteLabel
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank

/**
 * [GraphOperations] 위에 구성한 supply-chain impact analysis service이다.
 *
 * 이 service는 deterministic impact query와 candidate discovery를 보여준다. Optimization 또는 routing solver는 의도적으로 피하고,
 * 모든 example은 sample dataset 위의 bounded graph traversal이다.
 */
class SupplyChainImpactService(
    private val ops: GraphOperations,
    private val graphName: String = "supply_chain",
) {
    companion object: KLogging()

    /**
     * Backing graph가 아직 없으면 생성한다.
     */
    fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Supply-chain graph '$graphName' created" }
        }
    }

    fun impactedOrdersBySupplier(supplierId: String): List<GraphVertex> {
        supplierId.requireNotBlank("supplierId")
        val supplier = supplierById(supplierId) ?: return emptyList()
        return outgoing(supplier, SuppliesLabel.label, PartLabel.label)
            .flatMap(::ordersForPart)
            .distinctBy { it.id }
    }

    fun impactedOrdersByPart(partId: String): List<GraphVertex> {
        partId.requireNotBlank("partId")
        val part = partById(partId) ?: return emptyList()
        return ordersForPart(part)
    }

    fun impactedOrdersByRoute(routeId: String): List<GraphVertex> {
        routeId.requireNotBlank("routeId")
        val route = routeById(routeId) ?: return emptyList()
        return outgoing(route, DeliversToLabel.label, CustomerOrderLabel.label)
            .distinctBy { it.id }
    }

    fun alternateRoutesForOrder(orderId: String, failedRouteId: String): List<GraphVertex> {
        orderId.requireNotBlank("orderId")
        failedRouteId.requireNotBlank("failedRouteId")
        val order = orderById(orderId) ?: return emptyList()
        val orderRegion = order.properties[CustomerOrderLabel.region.name]

        return ops.findVerticesByLabel(RouteLabel.label)
            .filter { route ->
                route.properties[RouteLabel.routeId.name] != failedRouteId &&
                    route.properties[RouteLabel.region.name] == orderRegion &&
                    route.properties[RouteLabel.status.name] == "active"
            }
            .filter { route ->
                outgoing(route, DeliversToLabel.label, CustomerOrderLabel.label).any { it.id == order.id }
            }
            .distinctBy { it.id }
    }

    fun bottleneckParts(): List<GraphVertex> =
        ops.findVerticesByLabel(PartLabel.label)
            .filter { part ->
                incoming(part, SuppliesLabel.label, SupplierLabel.label).size == 1 &&
                    outgoing(part, RequiredByLabel.label, CustomerOrderLabel.label).size >= 2
            }
            .distinctBy { it.id }

    fun partSubstitutionCycles(): List<List<String>> {
        val cycles = mutableSetOf<List<String>>()
        ops.findVerticesByLabel(PartLabel.label).forEach { start ->
            outgoing(start, AlternatePartLabel.label, PartLabel.label).forEach { next ->
                val returns = outgoing(next, AlternatePartLabel.label, PartLabel.label).any { it.id == start.id }
                if (returns) {
                    cycles += listOf(partId(start), partId(next)).sorted()
                }
            }
        }
        return cycles.toList()
    }

    private fun ordersForPart(part: GraphVertex): List<GraphVertex> =
        outgoing(part, RequiredByLabel.label, CustomerOrderLabel.label)
            .distinctBy { it.id }

    private fun outgoing(vertex: GraphVertex, edgeLabel: String, vertexLabel: String): List<GraphVertex> =
        ops.neighbors(
            vertex.id,
            NeighborOptions(edgeLabel = edgeLabel, direction = Direction.OUTGOING, maxDepth = 1)
        ).filter { it.label == vertexLabel }

    private fun incoming(vertex: GraphVertex, edgeLabel: String, vertexLabel: String): List<GraphVertex> =
        ops.neighbors(
            vertex.id,
            NeighborOptions(edgeLabel = edgeLabel, direction = Direction.INCOMING, maxDepth = 1)
        ).filter { it.label == vertexLabel }

    private fun supplierById(supplierId: String): GraphVertex? =
        ops.findVerticesByLabel(SupplierLabel.label, mapOf(SupplierLabel.supplierId.name to supplierId)).firstOrNull()

    private fun partById(partId: String): GraphVertex? =
        ops.findVerticesByLabel(PartLabel.label, mapOf(PartLabel.partId.name to partId)).firstOrNull()

    private fun routeById(routeId: String): GraphVertex? =
        ops.findVerticesByLabel(RouteLabel.label, mapOf(RouteLabel.routeId.name to routeId)).firstOrNull()

    private fun orderById(orderId: String): GraphVertex? =
        ops.findVerticesByLabel(CustomerOrderLabel.label, mapOf(CustomerOrderLabel.orderId.name to orderId)).firstOrNull()

    private fun partId(part: GraphVertex): String =
        part.properties[PartLabel.partId.name].toString()
}
