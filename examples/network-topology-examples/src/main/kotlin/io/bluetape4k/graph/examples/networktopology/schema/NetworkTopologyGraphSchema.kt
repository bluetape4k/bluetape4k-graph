package io.bluetape4k.graph.examples.networktopology.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Site vertex는 device를 region 또는 facility 기준으로 묶는다.
 */
object SiteLabel: VertexLabel("Site") {
    val siteId = string("siteId")
    val name = string("name")
    val region = string("region")
    val status = string("status")
}

/**
 * Device vertex는 router, switch, service attachment point를 표현한다.
 */
object DeviceLabel: VertexLabel("Device") {
    val deviceId = string("deviceId")
    val name = string("name")
    val role = string("role")
    val status = string("status")
}

/**
 * Segment vertex는 routed 또는 switched network zone을 표현한다.
 */
object SegmentLabel: VertexLabel("Segment") {
    val segmentId = string("segmentId")
    val name = string("name")
    val cidr = string("cidr")
    val status = string("status")
}

/**
 * Service vertex는 topology에서 도달 가능한 business service를 표현한다.
 */
object ServiceLabel: VertexLabel("Service") {
    val serviceId = string("serviceId")
    val name = string("name")
    val tier = string("tier")
    val status = string("status")
}

/**
 * Site에서 device로 이어지는 ownership edge이다.
 */
object ContainsDeviceLabel: EdgeLabel("CONTAINS_DEVICE", SiteLabel, DeviceLabel) {
    val kind = string("kind")
}

/**
 * Device 사이의 physical 또는 logical link edge이다.
 */
object ConnectedToLabel: EdgeLabel("CONNECTED_TO", DeviceLabel, DeviceLabel) {
    val linkId = string("linkId")
    val medium = string("medium")
    val status = string("status")
}

/**
 * Network segment에 속한 device membership edge이다.
 */
object MemberOfSegmentLabel: EdgeLabel("MEMBER_OF_SEGMENT", DeviceLabel, SegmentLabel) {
    val kind = string("kind")
}

/**
 * Device에서 service로 이어지는 hosting 또는 attachment edge이다.
 */
object HostsServiceLabel: EdgeLabel("HOSTS_SERVICE", DeviceLabel, ServiceLabel) {
    val kind = string("kind")
}
