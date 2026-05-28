package io.bluetape4k.graph.examples.observability

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.observability.schema.ApiLabel
import io.bluetape4k.graph.examples.observability.schema.ServiceLabel
import io.bluetape4k.graph.examples.observability.schema.TeamLabel
import io.bluetape4k.graph.examples.observability.service.ObservabilityIncidentService
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractObservabilityIncidentTest {

    companion object: KLogging()

    protected abstract val ops: GraphOperations
    protected open val graphName: String = "observability_incident_test"
    protected val service: ObservabilityIncidentService by lazy { ObservabilityIncidentService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
    }

    @Test
    fun `finds downstream dependency blast radius`() {
        seedIncidentGraph()

        val serviceIds = service.downstreamDependencies("checkout-service", maxDepth = 2)
            .map { it.properties[ServiceLabel.serviceId.name] }

        serviceIds shouldContain "payment-service"
        serviceIds shouldContain "postgres-primary"
    }

    @Test
    fun `finds upstream services and affected public APIs`() {
        seedIncidentGraph()

        val upstreamServices = service.upstreamImpactedServices("payment-service", maxDepth = 3)
            .map { it.properties[ServiceLabel.serviceId.name] }
        val affectedApis = service.affectedApis("payment-service", maxDepth = 5)
            .map { it.properties[ApiLabel.apiId.name] }

        upstreamServices shouldContain "checkout-service"
        upstreamServices shouldContain "edge-api"
        affectedApis shouldContain "checkout-api"
        affectedApis shouldContain "mobile-checkout-api"
    }

    @Test
    fun `correlates alerts to smallest service boundary`() {
        seedIncidentGraph()

        val boundary = service.alertBoundary(listOf("payment-latency", "checkout-errors"), maxDepth = 1)
            .map { it.properties[ServiceLabel.serviceId.name] }

        boundary shouldContain "payment-service"
        boundary shouldContain "checkout-service"
        boundary.size shouldBeEqualTo boundary.toSet().size
    }

    @Test
    fun `traverses from failing service to owning team`() {
        seedIncidentGraph()

        val teams = service.owningTeams("payment-service")
            .map { it.properties[TeamLabel.teamId.name] }

        teams shouldContain "payments-team"
    }

    private fun seedIncidentGraph() {
        val edge = service.addService("edge-api", "Edge API", "edge")
        val checkout = service.addService("checkout-service", "Checkout Service", "application", status = "degraded")
        val payment = service.addService("payment-service", "Payment Service", "application", status = "failing")
        val postgres = service.addService("postgres-primary", "PostgreSQL Primary", "database", status = "degraded")
        val checkoutApi = service.addApi("checkout-api", "Checkout API", status = "degraded")
        val mobileApi = service.addApi("mobile-checkout-api", "Mobile Checkout API", status = "degraded")
        val payments = service.addTeam("payments-team", "Payments Team")
        val paymentAlert = service.addAlert("payment-latency", "Payment latency high", "critical")
        val checkoutAlert = service.addAlert("checkout-errors", "Checkout errors", "warning")
        val incident = service.addIncident("incident-1001", "Checkout payment incident", "critical")

        service.connectDependency(checkout.id, payment.id, kind = "sync-call")
        service.connectDependency(payment.id, postgres.id, kind = "jdbc")
        service.connectDependency(edge.id, checkout.id, kind = "http")
        service.connectDependency(checkoutApi.id, edge.id, kind = "http")
        service.connectDependency(mobileApi.id, edge.id, kind = "http")
        service.assignOwner(payment.id, payments.id)
        service.assignOwner(checkout.id, payments.id)
        service.attachAlert(paymentAlert.id, payment.id)
        service.attachAlert(checkoutAlert.id, checkout.id)
        service.markRootCause(incident.id, payment.id)

        service.owningTeams("payment-service").isNotEmpty().shouldBeTrue()
    }
}
