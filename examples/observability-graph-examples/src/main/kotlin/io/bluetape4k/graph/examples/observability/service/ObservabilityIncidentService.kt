package io.bluetape4k.graph.examples.observability.service

import io.bluetape4k.graph.examples.observability.schema.AlertLabel
import io.bluetape4k.graph.examples.observability.schema.AlertsOnLabel
import io.bluetape4k.graph.examples.observability.schema.ApiLabel
import io.bluetape4k.graph.examples.observability.schema.DependsOnLabel
import io.bluetape4k.graph.examples.observability.schema.IncidentLabel
import io.bluetape4k.graph.examples.observability.schema.OwnedByLabel
import io.bluetape4k.graph.examples.observability.schema.RootCauseLabel
import io.bluetape4k.graph.examples.observability.schema.ServiceLabel
import io.bluetape4k.graph.examples.observability.schema.TeamLabel
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank

/**
 * [GraphOperations] 위에 구성한 observability graph service이다.
 *
 * 이 service는 runtime service, public API, alert, incident, team ownership을 모델링한다.
 * Dependency blast-radius traversal, alert boundary correlation, ownership lookup을 보여준다.
 */
class ObservabilityIncidentService(
    private val ops: GraphOperations,
    private val graphName: String = "observability_incident",
) {
    companion object: KLogging()

    /**
     * Backing graph가 아직 없으면 생성한다.
     */
    fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Observability graph '$graphName' created" }
        }
    }

    fun addService(serviceId: String, name: String, tier: String, status: String = "healthy"): GraphVertex {
        serviceId.requireNotBlank("serviceId")
        name.requireNotBlank("name")
        tier.requireNotBlank("tier")
        return ops.createVertex(
            ServiceLabel.label,
            mapOf(
                ServiceLabel.serviceId.name to serviceId,
                ServiceLabel.name.name to name,
                ServiceLabel.tier.name to tier,
                ServiceLabel.status.name to status,
            )
        )
    }

    fun addApi(apiId: String, name: String, status: String = "healthy"): GraphVertex {
        apiId.requireNotBlank("apiId")
        name.requireNotBlank("name")
        return ops.createVertex(
            ApiLabel.label,
            mapOf(
                ApiLabel.apiId.name to apiId,
                ApiLabel.name.name to name,
                ApiLabel.tier.name to "public",
                ApiLabel.status.name to status,
            )
        )
    }

    fun addTeam(teamId: String, name: String): GraphVertex {
        teamId.requireNotBlank("teamId")
        name.requireNotBlank("name")
        return ops.createVertex(
            TeamLabel.label,
            mapOf(
                TeamLabel.teamId.name to teamId,
                TeamLabel.name.name to name,
                TeamLabel.status.name to "active",
            )
        )
    }

    fun addAlert(alertId: String, name: String, severity: String): GraphVertex {
        alertId.requireNotBlank("alertId")
        name.requireNotBlank("name")
        severity.requireNotBlank("severity")
        return ops.createVertex(
            AlertLabel.label,
            mapOf(
                AlertLabel.alertId.name to alertId,
                AlertLabel.name.name to name,
                AlertLabel.severity.name to severity,
                AlertLabel.status.name to "open",
            )
        )
    }

    fun addIncident(incidentId: String, name: String, severity: String): GraphVertex {
        incidentId.requireNotBlank("incidentId")
        name.requireNotBlank("name")
        severity.requireNotBlank("severity")
        return ops.createVertex(
            IncidentLabel.label,
            mapOf(
                IncidentLabel.incidentId.name to incidentId,
                IncidentLabel.name.name to name,
                IncidentLabel.severity.name to severity,
                IncidentLabel.status.name to "open",
            )
        )
    }

    fun connectDependency(fromId: GraphElementId, toId: GraphElementId, kind: String = "runtime") {
        kind.requireNotBlank("kind")
        ops.createEdge(fromId, toId, DependsOnLabel.label, mapOf(DependsOnLabel.kind.name to kind))
    }

    fun assignOwner(serviceId: GraphElementId, teamId: GraphElementId, kind: String = "oncall") {
        kind.requireNotBlank("kind")
        ops.createEdge(serviceId, teamId, OwnedByLabel.label, mapOf(OwnedByLabel.kind.name to kind))
    }

    fun attachAlert(alertId: GraphElementId, serviceId: GraphElementId, kind: String = "metric") {
        kind.requireNotBlank("kind")
        ops.createEdge(alertId, serviceId, AlertsOnLabel.label, mapOf(AlertsOnLabel.kind.name to kind))
    }

    fun markRootCause(incidentId: GraphElementId, serviceId: GraphElementId, kind: String = "investigation") {
        kind.requireNotBlank("kind")
        ops.createEdge(incidentId, serviceId, RootCauseLabel.label, mapOf(RootCauseLabel.kind.name to kind))
    }

    fun downstreamDependencies(serviceId: String, maxDepth: Int = 3): List<GraphVertex> =
        dependencyNeighbors(serviceId, Direction.OUTGOING, maxDepth)

    fun upstreamImpactedServices(serviceId: String, maxDepth: Int = 3): List<GraphVertex> =
        dependencyNeighbors(serviceId, Direction.INCOMING, maxDepth)
            .filter { it.label == ServiceLabel.label }

    fun affectedApis(serviceId: String, maxDepth: Int = 5): List<GraphVertex> =
        dependencyNeighbors(serviceId, Direction.INCOMING, maxDepth)
            .filter { it.label == ApiLabel.label }

    fun owningTeams(serviceId: String): List<GraphVertex> {
        val service = serviceById(serviceId) ?: return emptyList()
        return ops.neighbors(
            service.id,
            NeighborOptions(edgeLabel = OwnedByLabel.label, direction = Direction.OUTGOING, maxDepth = 1)
        )
    }

    fun alertBoundary(alertIds: Collection<String>, maxDepth: Int = 1): List<GraphVertex> {
        val directServices = alertIds
            .mapNotNull { alertById(it) }
            .flatMap { alert ->
                ops.neighbors(
                    alert.id,
                    NeighborOptions(edgeLabel = AlertsOnLabel.label, direction = Direction.OUTGOING, maxDepth = 1)
                )
            }
            .filter { it.label == ServiceLabel.label }

        val boundary = directServices + directServices.flatMap { service ->
            ops.neighbors(
                service.id,
                NeighborOptions(edgeLabel = DependsOnLabel.label, direction = Direction.BOTH, maxDepth = maxDepth)
            ).filter { it.label == ServiceLabel.label }
        }

        return boundary.distinctBy { it.id }
    }

    private fun dependencyNeighbors(serviceId: String, direction: Direction, maxDepth: Int): List<GraphVertex> {
        require(maxDepth >= 0) { "maxDepth must be >= 0, was $maxDepth" }
        val service = serviceById(serviceId) ?: return emptyList()
        return ops.neighbors(
            service.id,
            NeighborOptions(edgeLabel = DependsOnLabel.label, direction = direction, maxDepth = maxDepth)
        )
    }

    private fun serviceById(serviceId: String): GraphVertex? {
        serviceId.requireNotBlank("serviceId")
        return ops.findVerticesByLabel(
            ServiceLabel.label,
            mapOf(ServiceLabel.serviceId.name to serviceId),
        ).firstOrNull()
    }

    private fun alertById(alertId: String): GraphVertex? {
        alertId.requireNotBlank("alertId")
        return ops.findVerticesByLabel(
            AlertLabel.label,
            mapOf(AlertLabel.alertId.name to alertId),
        ).firstOrNull()
    }
}
