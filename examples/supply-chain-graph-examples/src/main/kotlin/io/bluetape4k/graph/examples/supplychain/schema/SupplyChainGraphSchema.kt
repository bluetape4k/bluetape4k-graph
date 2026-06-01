package io.bluetape4k.graph.examples.supplychain.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Supplier vertices for upstream source analysis.
 */
object SupplierLabel: VertexLabel("Supplier") {
    val supplierId = string("supplierId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Part vertices required by orders.
 */
object PartLabel: VertexLabel("Part") {
    val partId = string("partId")
    val name = string("name")
    val criticality = string("criticality")
    val status = string("status")
}

/**
 * Warehouse vertices used for stock and fulfillment.
 */
object WarehouseLabel: VertexLabel("Warehouse") {
    val warehouseId = string("warehouseId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Delivery route vertices.
 */
object RouteLabel: VertexLabel("Route") {
    val routeId = string("routeId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Carrier vertices operating routes.
 */
object CarrierLabel: VertexLabel("Carrier") {
    val carrierId = string("carrierId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Customer order vertices.
 */
object CustomerOrderLabel: VertexLabel("CustomerOrder") {
    val orderId = string("orderId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Supplier-to-part source edge.
 */
object SuppliesLabel: EdgeLabel("SUPPLIES", SupplierLabel, PartLabel) {
    val kind = string("kind")
}

/**
 * Part-to-order requirement edge.
 */
object RequiredByLabel: EdgeLabel("REQUIRED_BY", PartLabel, CustomerOrderLabel) {
    val kind = string("kind")
}

/**
 * Part-to-warehouse stock edge.
 */
object StockedAtLabel: EdgeLabel("STOCKED_AT", PartLabel, WarehouseLabel) {
    val kind = string("kind")
}

/**
 * Warehouse-to-route fulfillment edge.
 */
object UsesRouteLabel: EdgeLabel("USES_ROUTE", WarehouseLabel, RouteLabel) {
    val kind = string("kind")
}

/**
 * Route-to-order delivery edge.
 */
object DeliversToLabel: EdgeLabel("DELIVERS_TO", RouteLabel, CustomerOrderLabel) {
    val kind = string("kind")
}

/**
 * Carrier-to-route operation edge.
 */
object OperatesRouteLabel: EdgeLabel("OPERATES_ROUTE", CarrierLabel, RouteLabel) {
    val kind = string("kind")
}

/**
 * Part-to-part substitution edge.
 */
object AlternatePartLabel: EdgeLabel("ALTERNATE_PART", PartLabel, PartLabel) {
    val kind = string("kind")
}
