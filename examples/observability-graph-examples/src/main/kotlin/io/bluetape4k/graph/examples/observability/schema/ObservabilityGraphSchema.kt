package io.bluetape4k.graph.examples.observability.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Observability example에서 사용하는 runtime service vertex이다.
 */
object ServiceLabel: VertexLabel("Service") {
    val serviceId = string("serviceId")
    val name = string("name")
    val tier = string("tier")
    val status = string("status")
}

/**
 * Customer-facing API vertex이다.
 */
object ApiLabel: VertexLabel("Api") {
    val apiId = string("apiId")
    val name = string("name")
    val tier = string("tier")
    val status = string("status")
}

/**
 * Owning team vertex이다.
 */
object TeamLabel: VertexLabel("Team") {
    val teamId = string("teamId")
    val name = string("name")
    val status = string("status")
}

/**
 * Monitoring system이 emit한 alert vertex이다.
 */
object AlertLabel: VertexLabel("Alert") {
    val alertId = string("alertId")
    val name = string("name")
    val severity = string("severity")
    val status = string("status")
}

/**
 * Incident response 과정에서 생성된 incident vertex이다.
 */
object IncidentLabel: VertexLabel("Incident") {
    val incidentId = string("incidentId")
    val name = string("name")
    val severity = string("severity")
    val status = string("status")
}

/**
 * Caller에서 callee로 이어지는 dependency edge이다.
 */
object DependsOnLabel: EdgeLabel("DEPENDS_ON", ServiceLabel, ServiceLabel) {
    val kind = string("kind")
}

/**
 * Service에서 team으로 이어지는 ownership edge이다.
 */
object OwnedByLabel: EdgeLabel("OWNED_BY", ServiceLabel, TeamLabel) {
    val kind = string("kind")
}

/**
 * Alert와 service를 연결하는 correlation edge이다.
 */
object AlertsOnLabel: EdgeLabel("ALERTS_ON", AlertLabel, ServiceLabel) {
    val kind = string("kind")
}

/**
 * Incident에서 service로 이어지는 root-cause edge이다.
 */
object RootCauseLabel: EdgeLabel("ROOT_CAUSE", IncidentLabel, ServiceLabel) {
    val kind = string("kind")
}
