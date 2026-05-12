package io.bluetape4k.graph.examples.ktor

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test

class KtorGraphAppTest {

    @Test
    fun `health route 는 UP 을 반환한다`() = runSuspendIO {
        testApplication {
            application {
                module()
            }
            startApplication()

            val response = client.get("/health")
            response.status shouldBeEqualTo HttpStatusCode.OK
            response.bodyAsText() shouldBeEqualTo "UP"
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
