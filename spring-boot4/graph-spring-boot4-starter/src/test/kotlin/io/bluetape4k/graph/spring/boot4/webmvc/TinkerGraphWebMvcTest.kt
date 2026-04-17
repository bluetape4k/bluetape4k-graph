package io.bluetape4k.graph.spring.boot4.webmvc

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.spring.boot4.autoconfigure.GraphAutoConfiguration
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest(
    classes = [TinkerGraphWebMvcTest.TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureTestRestTemplate
@ActiveProfiles("tinkergraph")
class TinkerGraphWebMvcTest {

    companion object : KLogging()

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = [
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    ])
    @Import(GraphAutoConfiguration::class)
    class TestApp {

        @RestController
        @RequestMapping("/test/graph")
        class GraphController(@Autowired val ops: GraphOperations) {

            @PostMapping("/vertices/{label}")
            fun create(@PathVariable label: String): Map<String, String> {
                val v = ops.createVertex(label, mapOf("test" to "true"))
                return mapOf(
                    "id" to v.id.value,
                    "virtual" to Thread.currentThread().isVirtual.toString(),
                )
            }
        }
    }

    @Test
    fun `vertex 생성 + Virtual Thread 실행 확인`() {
        val resp = restTemplate.postForEntity(
            "/test/graph/vertices/Person", null, Map::class.java,
        )
        resp.statusCode.is2xxSuccessful.shouldBeTrue()
        @Suppress("UNCHECKED_CAST")
        val body = (resp.body.shouldNotBeNull()) as Map<String, String>
        body["virtual"].shouldBeEqualTo("true")
        body["id"].shouldNotBeNull()
    }
}
