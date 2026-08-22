package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.testcontainers.PropertyExportingServer
import io.bluetape4k.testcontainers.spring.registerDynamicProperties
import org.springframework.test.context.DynamicPropertyRegistry

/**
 * Testcontainers property namespace에서 그래프 테스트 설정으로 연결할 alias를 정의합니다.
 *
 * [GraphAutoConfiguration]의 운영 property 이름은 호환성을 위해 유지하고,
 * [PropertyExportingServer]가 제공하는 generic `testcontainers.*` key를 테스트 전용
 * alias로 연결합니다. 이 파일은 `src/test`에만 있어 production API나 SDK-neutral
 * Testcontainers core 의존성에 Spring을 추가하지 않습니다.
 */
internal data class GraphDynamicPropertyMapping(
    val propertyNamespace: String,
    val sourceKeyToTargetProperty: Map<String, String>,
)

/** 그래프 backend별 Testcontainers namespace 및 기존 Spring property alias입니다. */
internal val graphBackendDynamicPropertyMappings: Map<String, GraphDynamicPropertyMapping> = mapOf(
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

/**
 * 서버의 generic property와 그래프 모듈이 이미 사용하는 property alias를 함께 등록합니다.
 *
 * 두 경로 모두 supplier를 통해 값을 늦게 해석하므로 컨테이너 시작 이후의 endpoint를
 * 읽고, 등록 시점에는 [PropertyExportingServer.properties]를 호출하지 않습니다.
 */
internal fun PropertyExportingServer.registerGraphDynamicProperties(
    registry: DynamicPropertyRegistry,
    mapping: GraphDynamicPropertyMapping,
) {
    require(propertyNamespace == mapping.propertyNamespace) {
        "PropertyExportingServer namespace '$propertyNamespace' does not match '${mapping.propertyNamespace}'"
    }

    registerDynamicProperties(registry)
    mapping.sourceKeyToTargetProperty.forEach { (sourceKey, targetProperty) ->
        registry.add(targetProperty) {
            properties()[sourceKey]
                ?: error(
                    "PropertyExportingServer '$propertyNamespace' did not provide property '$sourceKey'",
                )
        }
    }
}
