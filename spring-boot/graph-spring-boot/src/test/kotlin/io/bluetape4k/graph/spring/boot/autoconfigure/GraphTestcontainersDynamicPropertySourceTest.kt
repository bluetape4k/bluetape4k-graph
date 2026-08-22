package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.testcontainers.PropertyExportingServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import java.util.function.Supplier

/**
 * 그래프 Spring Boot 통합 테스트용 Testcontainers property bridge 계약을 검증합니다.
 *
 * 실제 Docker 컨테이너 없이 generic bridge와 기존 그래프 설정 alias의 lazy 계약을
 * 함께 검증해 테스트 설정이 컨테이너 lifecycle과 분리되지 않도록 합니다.
 */
class GraphTestcontainersDynamicPropertySourceTest {

    @AfterEach
    fun clearSystemProperties() {
        graphBackendDynamicPropertyMappings.values
            .flatMap { it.sourceKeyToTargetProperty.values }
            .forEach(System::clearProperty)
        System.clearProperty("testcontainers.falkordb.host")
    }

    @Test
    fun `모든 그래프 backend가 기존 설정 key alias를 선언한다`() {
        graphBackendDynamicPropertyMappings shouldBeEqualTo mapOf(
            "neo4j" to GraphDynamicPropertyMapping(
                propertyNamespace = "neo4j",
                sourceKeyToTargetProperty = mapOf("bolt-url" to "bluetape4k.graph.neo4j.uri"),
            ),
            "memgraph" to GraphDynamicPropertyMapping(
                propertyNamespace = "memgraph",
                sourceKeyToTargetProperty = mapOf("bolt-url" to "bluetape4k.graph.memgraph.uri"),
            ),
            "age" to GraphDynamicPropertyMapping(
                propertyNamespace = "postgresql-age",
                sourceKeyToTargetProperty = mapOf(
                    "jdbc-url" to "spring.datasource.url",
                    "username" to "spring.datasource.username",
                    "password" to "spring.datasource.password",
                ),
            ),
            "falkordb" to GraphDynamicPropertyMapping(
                propertyNamespace = "falkordb",
                sourceKeyToTargetProperty = mapOf(
                    "host" to "bluetape4k.graph.falkordb.host",
                    "port" to "bluetape4k.graph.falkordb.port",
                ),
            ),
        )
    }

    @Test
    fun `generic key와 기존 graph alias를 lazy supplier로 등록한다`() {
        val server = FakeServer(
            propertyNamespace = "falkordb",
            keys = linkedSetOf("host", "port"),
            values = mapOf("host" to "before", "port" to "6379"),
        )
        val registry = RecordingRegistry()

        server.registerGraphDynamicProperties(
            registry,
            graphBackendDynamicPropertyMappings.getValue("falkordb"),
        )

        server.propertiesCalls shouldBeEqualTo 0
        registry.names shouldBeEqualTo listOf(
            "testcontainers.falkordb.host",
            "testcontainers.falkordb.port",
            "bluetape4k.graph.falkordb.host",
            "bluetape4k.graph.falkordb.port",
        )

        server.values = mapOf("host" to "after", "port" to "6380")
        registry.value("testcontainers.falkordb.host") shouldBeEqualTo "after"
        registry.value("bluetape4k.graph.falkordb.host") shouldBeEqualTo "after"
        server.propertiesCalls shouldBeEqualTo 2

        server.values = mapOf("host" to "latest", "port" to "6381")
        registry.value("bluetape4k.graph.falkordb.host") shouldBeEqualTo "latest"
        server.propertiesCalls shouldBeEqualTo 3
    }

    @Test
    fun `alias source key가 없으면 supplier 평가 시 명시적으로 실패한다`() {
        val server = FakeServer(
            propertyNamespace = "falkordb",
            keys = linkedSetOf("host", "port"),
            values = mapOf("port" to "6379"),
        )
        val registry = RecordingRegistry()

        server.registerGraphDynamicProperties(
            registry,
            graphBackendDynamicPropertyMappings.getValue("falkordb"),
        )

        val error = assertFailsWith<IllegalStateException> {
            registry.value("bluetape4k.graph.falkordb.host")
        }

        error.message shouldBeEqualTo
            "PropertyExportingServer 'falkordb' did not provide property 'host'"
    }

    @Test
    fun `container lifecycle 예외는 supplier 평가 시 원래 타입으로 전달된다`() {
        val server = FakeServer(
            propertyNamespace = "falkordb",
            keys = linkedSetOf("host", "port"),
            values = mapOf("host" to "localhost", "port" to "6379"),
        )
        val registry = RecordingRegistry()

        server.registerGraphDynamicProperties(
            registry,
            graphBackendDynamicPropertyMappings.getValue("falkordb"),
        )
        server.propertiesFailure = IllegalStateException("server is stopped")

        val error = assertFailsWith<IllegalStateException> {
            registry.value("bluetape4k.graph.falkordb.host")
        }

        error.message shouldBeEqualTo "server is stopped"
    }

    @Test
    fun `bridge 등록은 system property를 변경하지 않는다`() {
        val key = "testcontainers.falkordb.host"
        System.setProperty(key, "existing")

        FakeServer(
            propertyNamespace = "falkordb",
            keys = setOf("host", "port"),
            values = mapOf("host" to "localhost", "port" to "6379"),
        ).registerGraphDynamicProperties(
            RecordingRegistry(),
            graphBackendDynamicPropertyMappings.getValue("falkordb"),
        )

        System.getProperty(key) shouldBeEqualTo "existing"
    }

    @Test
    fun `중복 등록은 registry 정책에 위임한다`() {
        val server = FakeServer(
            propertyNamespace = "falkordb",
            keys = setOf("host", "port"),
            values = mapOf("host" to "localhost", "port" to "6379"),
        )
        val registry = RecordingRegistry()
        val mapping = graphBackendDynamicPropertyMappings.getValue("falkordb")

        server.registerGraphDynamicProperties(registry, mapping)
        server.registerGraphDynamicProperties(registry, mapping)

        registry.names.count { it == "bluetape4k.graph.falkordb.host" } shouldBeEqualTo 2
        registry.valueAt(registry.names.indexOf("bluetape4k.graph.falkordb.host")) shouldBeEqualTo "localhost"
    }

    private class FakeServer(
        override val propertyNamespace: String,
        private val keys: Set<String>,
        var values: Map<String, String>,
        var propertiesFailure: RuntimeException? = null,
    ): PropertyExportingServer {
        var propertiesCalls: Int = 0
            private set

        override fun propertyKeys(): Set<String> = keys

        override fun properties(): Map<String, String> {
            propertiesCalls++
            propertiesFailure?.let { throw it }
            return values
        }
    }

    private class RecordingRegistry: DynamicPropertyRegistry {
        private val entries = mutableListOf<Pair<String, Supplier<Any>>>()

        val names: List<String>
            get() = entries.map { it.first }

        override fun add(name: String, valueSupplier: Supplier<Any>) {
            entries += name to valueSupplier
        }

        fun value(name: String): Any = entries.single { it.first == name }.second.get()

        fun valueAt(index: Int): Any = entries[index].second.get()
    }
}
