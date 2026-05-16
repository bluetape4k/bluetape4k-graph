package io.bluetape4k.graph.examples.ktor

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.falkordb.FalkorDBServer
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Integration tests for the FalkorDB-backed Ktor application module.
 *
 * ## Behavior / Contract
 * - Uses the singleton [FalkorDBServer.Launcher.falkordb] Testcontainers instance.
 * - Each test creates a fresh [testApplication] scope; state is isolated by
 *   calling `POST /demo/reset` before any assertion.
 *
 * ```kotlin
 * val server = FalkorDBServer.Launcher.falkordb
 * testApplication {
 *     application { falkorDbModule(server.host, server.port) }
 * }
 * ```
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FalkorDBKtorGraphAppTest {

    companion object : KLogging() {
        private val server = FalkorDBServer.Launcher.falkordb
    }

    @Test
    fun `health route returns UP`() = runSuspendIO {
        testApplication {
            application {
                falkorDbModule(server.host, server.port)
            }
            startApplication()

            val response = client.get("/health")
            response.status shouldBeEqualTo HttpStatusCode.OK
            response.bodyAsText() shouldBeEqualTo "UP"
        }
    }

    @Test
    fun `demo reset and city count and path work with FalkorDB`() = runSuspendIO {
        testApplication {
            application {
                falkorDbModule(server.host, server.port)
            }
            startApplication()

            val resetResponse = client.post("/demo/reset")
            resetResponse.status shouldBeEqualTo HttpStatusCode.OK
            resetResponse.bodyAsText() shouldBeEqualTo "reset"

            val countResponse = client.get("/cities/count")
            countResponse.status shouldBeEqualTo HttpStatusCode.OK
            countResponse.bodyAsText() shouldBeEqualTo "3"

            val pathResponse = client.get("/cities/path")
            pathResponse.status shouldBeEqualTo HttpStatusCode.OK
            pathResponse.bodyAsText() shouldBeEqualTo "Seoul -> Daejeon -> Busan"
        }
    }
}
