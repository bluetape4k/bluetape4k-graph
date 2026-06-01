package io.bluetape4k.graph.examples.supplychain.service

import io.bluetape4k.graph.examples.supplychain.schema.AlternatePartLabel
import io.bluetape4k.graph.examples.supplychain.schema.CustomerOrderLabel
import io.bluetape4k.graph.examples.supplychain.schema.DeliversToLabel
import io.bluetape4k.graph.examples.supplychain.schema.PartLabel
import io.bluetape4k.graph.examples.supplychain.schema.RequiredByLabel
import io.bluetape4k.graph.examples.supplychain.schema.RouteLabel
import io.bluetape4k.graph.examples.supplychain.schema.SupplierLabel
import io.bluetape4k.graph.examples.supplychain.schema.SuppliesLabel
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.toList

/**
 * Coroutine version of [SupplyChainImpactService].
 */
class SupplyChainImpactSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "supply_chain",
) {
    companion object: KLoggingChannel()

    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Supply-chain graph '$graphName' created" }
        }
    }

    suspend fun impactedOrdersBySupplier(supplierId: String): List<GraphVertex> {
        supplierId.requireNotBlank("supplierId")
        val supplier = supplierById(supplierId) ?: return emptyList()
        return outgoing(supplier, SuppliesLabel.label, PartLabel.label)
            .flatMap { part -> ordersForPart(part) }
            .distinctBy { it.id }
    }

    suspend fun impactedOrdersByPart(partId: String): List<GraphVertex> {
        partId.requireNotBlank("partId")
        val part = partById(partId) ?: return emptyList()
        return ordersForPart(part)
    }

    suspend fun impactedOrdersByRoute(routeId: String): List<GraphVertex> {
        routeId.requireNotBlank("routeId")
        val route = routeById(routeId) ?: return emptyList()
        return outgoing(route, DeliversToLabel.label, CustomerOrderLabel.label)
            .distinctBy { it.id }
    }

    suspend fun alternateRoutesForOrder(orderId: String, failedRouteId: String): List<GraphVertex> {
        orderId.requireNotBlank("orderId")
        failedRouteId.requireNotBlank("failedRouteId")
        val order = orderById(orderId) ?: return emptyList()
        val orderRegion = order.properties[CustomerOrderLabel.region.name]

        val routes = ops.findVerticesByLabel(RouteLabel.label).toList()
        return routes
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

    suspend fun bottleneckParts(): List<GraphVertex> =
        ops.findVerticesByLabel(PartLabel.label).toList()
            .filter { part ->
                incoming(part, SuppliesLabel.label, SupplierLabel.label).size == 1 &&
                    outgoing(part, RequiredByLabel.label, CustomerOrderLabel.label).size >= 2
            }
            .distinctBy { it.id }

    suspend fun partSubstitutionCycles(): List<List<String>> {
        val cycles = mutableSetOf<List<String>>()
        ops.findVerticesByLabel(PartLabel.label).toList().forEach { start ->
            outgoing(start, AlternatePartLabel.label, PartLabel.label).forEach { next ->
                val returns = outgoing(next, AlternatePartLabel.label, PartLabel.label).any { it.id == start.id }
                if (returns) {
                    cycles += listOf(partId(start), partId(next)).sorted()
                }
            }
        }
        return cycles.toList()
    }

    private suspend fun ordersForPart(part: GraphVertex): List<GraphVertex> =
        outgoing(part, RequiredByLabel.label, CustomerOrderLabel.label)
            .distinctBy { it.id }

    private suspend fun outgoing(vertex: GraphVertex, edgeLabel: String, vertexLabel: String): List<GraphVertex> =
        ops.neighbors(
            vertex.id,
            NeighborOptions(edgeLabel = edgeLabel, direction = Direction.OUTGOING, maxDepth = 1)
        ).toList().filter { it.label == vertexLabel }

    private suspend fun incoming(vertex: GraphVertex, edgeLabel: String, vertexLabel: String): List<GraphVertex> =
        ops.neighbors(
            vertex.id,
            NeighborOptions(edgeLabel = edgeLabel, direction = Direction.INCOMING, maxDepth = 1)
        ).toList().filter { it.label == vertexLabel }

    private suspend fun supplierById(supplierId: String): GraphVertex? =
        ops.findVerticesByLabel(SupplierLabel.label, mapOf(SupplierLabel.supplierId.name to supplierId))
            .toList()
            .firstOrNull()

    private suspend fun partById(partId: String): GraphVertex? =
        ops.findVerticesByLabel(PartLabel.label, mapOf(PartLabel.partId.name to partId))
            .toList()
            .firstOrNull()

    private suspend fun routeById(routeId: String): GraphVertex? =
        ops.findVerticesByLabel(RouteLabel.label, mapOf(RouteLabel.routeId.name to routeId))
            .toList()
            .firstOrNull()

    private suspend fun orderById(orderId: String): GraphVertex? =
        ops.findVerticesByLabel(CustomerOrderLabel.label, mapOf(CustomerOrderLabel.orderId.name to orderId))
            .toList()
            .firstOrNull()

    private fun partId(part: GraphVertex): String =
        part.properties[PartLabel.partId.name].toString()
}
