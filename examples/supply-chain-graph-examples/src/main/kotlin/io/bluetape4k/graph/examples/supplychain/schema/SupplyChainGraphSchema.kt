package io.bluetape4k.graph.examples.supplychain.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Upstream source analysis를 위한 supplier vertex이다.
 */
object SupplierLabel: VertexLabel("Supplier") {
    private fun readResolve(): Any = SupplierLabel
    val supplierId = string("supplierId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Order에 필요한 part vertex이다.
 */
object PartLabel: VertexLabel("Part") {
    private fun readResolve(): Any = PartLabel
    val partId = string("partId")
    val name = string("name")
    val criticality = string("criticality")
    val status = string("status")
}

/**
 * Stock과 fulfillment에 사용하는 warehouse vertex이다.
 */
object WarehouseLabel: VertexLabel("Warehouse") {
    private fun readResolve(): Any = WarehouseLabel
    val warehouseId = string("warehouseId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Delivery route vertex이다.
 */
object RouteLabel: VertexLabel("Route") {
    private fun readResolve(): Any = RouteLabel
    val routeId = string("routeId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Route를 operate하는 carrier vertex이다.
 */
object CarrierLabel: VertexLabel("Carrier") {
    private fun readResolve(): Any = CarrierLabel
    val carrierId = string("carrierId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Customer order vertex이다.
 */
object CustomerOrderLabel: VertexLabel("CustomerOrder") {
    private fun readResolve(): Any = CustomerOrderLabel
    val orderId = string("orderId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Supplier에서 part로 이어지는 source edge이다.
 */
object SuppliesLabel: EdgeLabel("SUPPLIES", SupplierLabel, PartLabel) {
    private fun readResolve(): Any = SuppliesLabel
    val kind = string("kind")
}

/**
 * Part에서 order로 이어지는 requirement edge이다.
 */
object RequiredByLabel: EdgeLabel("REQUIRED_BY", PartLabel, CustomerOrderLabel) {
    private fun readResolve(): Any = RequiredByLabel
    val kind = string("kind")
}

/**
 * Part에서 warehouse로 이어지는 stock edge이다.
 */
object StockedAtLabel: EdgeLabel("STOCKED_AT", PartLabel, WarehouseLabel) {
    private fun readResolve(): Any = StockedAtLabel
    val kind = string("kind")
}

/**
 * Warehouse에서 route로 이어지는 fulfillment edge이다.
 */
object UsesRouteLabel: EdgeLabel("USES_ROUTE", WarehouseLabel, RouteLabel) {
    private fun readResolve(): Any = UsesRouteLabel
    val kind = string("kind")
}

/**
 * Route에서 order로 이어지는 delivery edge이다.
 */
object DeliversToLabel: EdgeLabel("DELIVERS_TO", RouteLabel, CustomerOrderLabel) {
    private fun readResolve(): Any = DeliversToLabel
    val kind = string("kind")
}

/**
 * Carrier에서 route로 이어지는 operation edge이다.
 */
object OperatesRouteLabel: EdgeLabel("OPERATES_ROUTE", CarrierLabel, RouteLabel) {
    private fun readResolve(): Any = OperatesRouteLabel
    val kind = string("kind")
}

/**
 * Part 사이의 substitution edge이다.
 */
object AlternatePartLabel: EdgeLabel("ALTERNATE_PART", PartLabel, PartLabel) {
    private fun readResolve(): Any = AlternatePartLabel
    val kind = string("kind")
}
