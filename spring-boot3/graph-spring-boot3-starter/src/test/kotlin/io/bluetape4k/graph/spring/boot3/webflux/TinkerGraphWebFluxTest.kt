package io.bluetape4k.graph.spring.boot3.webflux

import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.spring.boot3.autoconfigure.GraphAutoConfiguration
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.runBlocking
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// NOTE: @WebFluxTest slice is not used here because the controller under test is defined as an inner
// class of this test (TestApp.SuspendController). Extracting it to a standalone class would add
// production scope just for testing; @SpringBootTest with RANDOM_PORT keeps the setup self-contained.
@SpringBootTest(
    classes = [TinkerGraphWebFluxTest.TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.web-application-type=reactive"],
)
@ActiveProfiles("tinkergraph")
class TinkerGraphWebFluxTest {

    companion object : KLogging()

    @Autowired
    lateinit var webClient: WebTestClient

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = [
        org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration::class,
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration::class,
    ])
    @Import(GraphAutoConfiguration::class)
    class TestApp {

        @RestController
        @RequestMapping("/test/suspend")
        class SuspendController(@Autowired val ops: GraphSuspendOperations) {

            @PostMapping("/vertices/{label}")
            suspend fun create(@PathVariable label: String): Map<String, String> {
                val v = ops.createVertex(label, mapOf("async" to "true"))
                return mapOf("id" to v.id.value, "label" to v.label)
            }
        }
    }

    @Test
    fun `suspend 컨트롤러로 vertex 생성`() = runBlocking<Unit> {
        webClient.post().uri("/test/suspend/vertices/User")
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody(Map::class.java)
            .consumeWith { r ->
                val body = r.responseBody.shouldNotBeNull()
                body.containsKey("id").shouldBeTrue()
            }
    }

    @Test
    fun `존재하지 않는 경로 요청 시 404 반환`() = runBlocking<Unit> {
        // Error path: POST to an unmapped path returns 404
        webClient.post().uri("/test/suspend/nonexistent/path")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `GET 요청으로 POST 전용 엔드포인트 접근 시 405 반환`() = runBlocking<Unit> {
        // Error path: wrong HTTP method on the suspend vertices endpoint returns 405
        webClient.get().uri("/test/suspend/vertices/User")
            .exchange()
            .expectStatus().isEqualTo(405)
    }
}
