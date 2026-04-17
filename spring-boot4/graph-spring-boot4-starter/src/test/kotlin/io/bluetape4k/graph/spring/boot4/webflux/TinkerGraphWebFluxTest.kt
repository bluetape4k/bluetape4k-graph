package io.bluetape4k.graph.spring.boot4.webflux

import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.spring.boot4.autoconfigure.GraphAutoConfiguration
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest(
    classes = [TinkerGraphWebFluxTest.TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.web-application-type=reactive"],
)
@AutoConfigureWebTestClient
@ActiveProfiles("tinkergraph")
class TinkerGraphWebFluxTest {

    companion object : KLogging()

    @Autowired
    lateinit var webClient: WebTestClient

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = [
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
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
}
