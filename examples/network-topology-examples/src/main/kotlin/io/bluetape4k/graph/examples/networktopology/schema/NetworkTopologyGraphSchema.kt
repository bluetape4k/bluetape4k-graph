package io.bluetape4k.graph.examples.networktopology.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Site vertices group devices by region or facility.
 */
object SiteLabel: VertexLabel("Site") {
    val siteId = string("siteId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Device vertices represent routers, switches, and service attachment points.
 */
object DeviceLabel: VertexLabel("Device") {
    val deviceId = string("deviceId")
    val name = string("name")
    val role = string("role")
    val status = string("status")
}

/**
 * Segment vertices represent routed or switched network zones.
 */
object SegmentLabel: VertexLabel("Segment") {
    val segmentId = string("segmentId")
    val name = string("name")
    val cidr = string("cidr")
    val status = string("status")
}

/**
 * Service vertices represent business services reachable over the topology.
 */
object ServiceLabel: VertexLabel("Service") {
    val serviceId = string("serviceId")
    val name = string("name")
    val tier = string("tier")
    val status = string("status")
}

/**
 * Site-to-device ownership edge.
 */
object ContainsDeviceLabel: EdgeLabel("CONTAINS_DEVICE", SiteLabel, DeviceLabel) {
    val kind = string("kind")
}

/**
 * Device-to-device physical or logical link edge.
 */
object ConnectedToLabel: EdgeLabel("CONNECTED_TO", DeviceLabel, DeviceLabel) {
    val linkId = string("linkId")
    val medium = string("medium")
    val status = string("status")
}

/**
 * Device membership in a network segment.
 */
object MemberOfSegmentLabel: EdgeLabel("MEMBER_OF_SEGMENT", DeviceLabel, SegmentLabel) {
    val kind = string("kind")
}

/**
 * Device-to-service hosting or attachment edge.
 */
object HostsServiceLabel: EdgeLabel("HOSTS_SERVICE", DeviceLabel, ServiceLabel) {
    val kind = string("kind")
}
