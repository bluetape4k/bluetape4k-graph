package io.bluetape4k.graph.examples.observability

import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.observability.schema.ApiLabel
import io.bluetape4k.graph.examples.observability.schema.ServiceLabel
import io.bluetape4k.graph.examples.observability.service.ObservabilityIncidentSuspendService
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractObservabilityIncidentSuspendTest {

    companion object: KLoggingChannel()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "observability_incident_suspend_test"
    protected val service: ObservabilityIncidentSuspendService by lazy { ObservabilityIncidentSuspendService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() = runSuspendIO {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
    }

    @Test
    fun `finds suspend downstream dependency blast radius`() = runSuspendIO {
        seedIncidentGraph()

        val serviceIds = service.downstreamDependencies("checkout-service", maxDepth = 2)
            .map { it.properties[ServiceLabel.serviceId.name] }

        serviceIds shouldContain "payment-service"
        serviceIds shouldContain "postgres-primary"
    }

    @Test
    fun `finds suspend affected public APIs`() = runSuspendIO {
        seedIncidentGraph()

        val affectedApis = service.affectedApis("payment-service", maxDepth = 5)
            .map { it.properties[ApiLabel.apiId.name] }

        affectedApis shouldContain "checkout-api"
        affectedApis shouldContain "mobile-checkout-api"
    }

    private suspend fun seedIncidentGraph() {
        val edge = service.addService("edge-api", "Edge API", "edge")
        val checkout = service.addService("checkout-service", "Checkout Service", "application", status = "degraded")
        val payment = service.addService("payment-service", "Payment Service", "application", status = "failing")
        val postgres = service.addService("postgres-primary", "PostgreSQL Primary", "database", status = "degraded")
        val checkoutApi = service.addApi("checkout-api", "Checkout API", status = "degraded")
        val mobileApi = service.addApi("mobile-checkout-api", "Mobile Checkout API", status = "degraded")
        val payments = service.addTeam("payments-team", "Payments Team")
        val paymentAlert = service.addAlert("payment-latency", "Payment latency high", "critical")
        val incident = service.addIncident("incident-1001", "Checkout payment incident", "critical")

        service.connectDependency(checkout.id, payment.id, kind = "sync-call")
        service.connectDependency(payment.id, postgres.id, kind = "jdbc")
        service.connectDependency(edge.id, checkout.id, kind = "http")
        service.connectDependency(checkoutApi.id, edge.id, kind = "http")
        service.connectDependency(mobileApi.id, edge.id, kind = "http")
        service.assignOwner(payment.id, payments.id)
        service.attachAlert(paymentAlert.id, payment.id)
        service.markRootCause(incident.id, payment.id)
    }
}
