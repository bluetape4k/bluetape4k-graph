package io.bluetape4k.graph.examples.observability.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Runtime service vertices used by the observability example.
 */
object ServiceLabel: VertexLabel("Service") {
    val serviceId = string("serviceId")
    val name = string("name")
    val tier = string("tier")
    val status = string("status")
}

/**
 * Customer-facing API vertices.
 */
object ApiLabel: VertexLabel("Api") {
    val apiId = string("apiId")
    val name = string("name")
    val tier = string("tier")
    val status = string("status")
}

/**
 * Owning team vertices.
 */
object TeamLabel: VertexLabel("Team") {
    val teamId = string("teamId")
    val name = string("name")
    val status = string("status")
}

/**
 * Alert vertices emitted by monitoring systems.
 */
object AlertLabel: VertexLabel("Alert") {
    val alertId = string("alertId")
    val name = string("name")
    val severity = string("severity")
    val status = string("status")
}

/**
 * Incident vertices created by incident response.
 */
object IncidentLabel: VertexLabel("Incident") {
    val incidentId = string("incidentId")
    val name = string("name")
    val severity = string("severity")
    val status = string("status")
}

/**
 * Dependency edges from callers to callees.
 */
object DependsOnLabel: EdgeLabel("DEPENDS_ON", ServiceLabel, ServiceLabel) {
    val kind = string("kind")
}

/**
 * Ownership edges from services to teams.
 */
object OwnedByLabel: EdgeLabel("OWNED_BY", ServiceLabel, TeamLabel) {
    val kind = string("kind")
}

/**
 * Alert-to-service correlation edges.
 */
object AlertsOnLabel: EdgeLabel("ALERTS_ON", AlertLabel, ServiceLabel) {
    val kind = string("kind")
}

/**
 * Incident-to-service root-cause edges.
 */
object RootCauseLabel: EdgeLabel("ROOT_CAUSE", IncidentLabel, ServiceLabel) {
    val kind = string("kind")
}
