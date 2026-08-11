package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.graph.io.micrometer.GraphIoMicrometerProgressListener
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

private const val METER_REGISTRY_CLASS = "io.micrometer.core.instrument.MeterRegistry"
private const val BRIDGE_CLASS = "io.bluetape4k.graph.io.micrometer.GraphIoMicrometerProgressListener"

/**
 * Micrometer bridge를 명시적으로 활성화할 때 graph-io progress listener를 등록한다.
 *
 * Micrometer와 bridge는 선택 의존성이므로 기본 graph-io classpath에는 추가하지 않는다.
 * 등록 bean은 일반적인 [io.bluetape4k.graph.io.report.GraphIoProgressListener] 자동
 * 주입 후보가 아니며, [BEAN_NAME]으로 명시적으로 조회해야 한다.
 */
@AutoConfiguration
@ConditionalOnClass(name = [METER_REGISTRY_CLASS, BRIDGE_CLASS])
@ConditionalOnProperty(
    prefix = "bluetape4k.graph.io.metrics",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class GraphIoMicrometerAutoConfiguration {

    /** Micrometer bridge bean을 위한 선택 조건 그룹. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = [METER_REGISTRY_CLASS, BRIDGE_CLASS])
    @ConditionalOnBean(MeterRegistry::class)
    @ConditionalOnMissingBean(name = [BEAN_NAME])
    class MicrometerConfiguration {

        @Bean(name = [BEAN_NAME], autowireCandidate = false)
        fun graphIoMicrometerProgressListener(registry: MeterRegistry): GraphIoMicrometerProgressListener =
            GraphIoMicrometerProgressListener(registry)
    }

    companion object {
        const val BEAN_NAME: String = "graphIoMicrometerProgressListener"

    }
}
