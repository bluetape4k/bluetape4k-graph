package io.bluetape4k.graph.examples.ktor

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.ktor.core.HealthResponse
import io.bluetape4k.ktor.testing.decodeJsonBody
import io.bluetape4k.ktor.testing.shouldHaveStatus
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

class KtorGraphAppTest {

    @Test
    fun `shared health route 는 UP 을 반환한다`() = runSuspendIO {
        testApplication {
            application {
                module()
            }
            startApplication()

            val response = client.get("/health")
            response shouldHaveStatus HttpStatusCode.OK
            response.decodeJsonBody<HealthResponse>().status shouldBeEqualTo HealthResponse.UP
        }
    }

    @Test
    fun `shared readiness route 는 UP 을 반환한다`() = runSuspendIO {
        testApplication {
            application {
                module()
            }
            startApplication()

            val response = client.get("/readyz")
            response shouldHaveStatus HttpStatusCode.OK
            response.decodeJsonBody<HealthResponse>().status shouldBeEqualTo HealthResponse.UP
        }
    }

    @Test
    fun `demo reset 후 city count 와 path 를 조회할 수 있다`() = runSuspendIO {
        testApplication {
            application {
                module()
            }
            startApplication()

            val resetResponse = client.post("/demo/reset")
            resetResponse shouldHaveStatus HttpStatusCode.OK
            resetResponse.bodyAsText() shouldBeEqualTo "reset"

            val countResponse = client.get("/cities/count")
            countResponse shouldHaveStatus HttpStatusCode.OK
            countResponse.bodyAsText() shouldBeEqualTo "3"

            val pathResponse = client.get("/cities/path")
            pathResponse shouldHaveStatus HttpStatusCode.OK
            pathResponse.bodyAsText() shouldBeEqualTo "Seoul -> Daejeon -> Busan"
        }
    }
}
