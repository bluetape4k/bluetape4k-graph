package io.bluetape4k.graph.examples.ktor

import com.falkordb.FalkorDB
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.falkordb.FalkorDBServer
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.KLogging
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Integration tests for the FalkorDB-backed Ktor application module.
 *
 * ## Behavior / Contract
 * - Uses the singleton [FalkorDBServer.Launcher.falkordb] Testcontainers instance.
 * - The shared [driver] is caller-owned and reused across tests; it is not closed
 *   between test methods and is closed after the test class completes.
 * - Each test calls `POST /demo/reset` before asserting state to ensure isolation.
 *
 * ```kotlin
 * testApplication {
 *     application { falkorDbModule(driver) }
 * }
 * ```
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FalkorDBKtorGraphAppTest {

    companion object : KLogging() {
        private val server = FalkorDBServer.Launcher.falkordb
        private val driverLazy = lazy { FalkorDB.driver(server.host, server.port) }
        private val driver by driverLazy
    }

    @AfterAll
    fun tearDown() {
        if (driverLazy.isInitialized()) {
            driver.close()
        }
    }

    @Test
    fun `health route returns UP`() = runSuspendIO {
        testApplication {
            application {
                falkorDbModule(driver)
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
                falkorDbModule(driver)
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
