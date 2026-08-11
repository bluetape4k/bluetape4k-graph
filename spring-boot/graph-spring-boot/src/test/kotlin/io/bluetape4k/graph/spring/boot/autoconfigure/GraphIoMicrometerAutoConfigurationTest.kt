package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.io.micrometer.GraphIoMicrometerProgressListener
import io.bluetape4k.graph.io.report.GraphIoProgressListener
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration

class GraphIoMicrometerAutoConfigurationTest {

    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(GraphIoMicrometerAutoConfiguration::class.java))

    @Test
    fun `metrics enabled with registry creates named bridge bean`() {
        runner
            .withBean(MeterRegistry::class.java, ::SimpleMeterRegistry)
            .withPropertyValues("bluetape4k.graph.io.metrics.enabled=true")
            .run { context ->
                context.containsBean(GraphIoMicrometerAutoConfiguration.BEAN_NAME).shouldBeTrue()
                context.getBean(
                    GraphIoMicrometerAutoConfiguration.BEAN_NAME,
                    GraphIoMicrometerProgressListener::class.java,
                ).shouldNotBeNull()
                context.getBeanProvider(GraphIoMicrometerProgressListener::class.java)
                    .getIfAvailable()
                    .shouldNotBeNull()
            }
    }

    @Test
    fun `metrics disabled by default`() {
        runner
            .withBean(MeterRegistry::class.java, ::SimpleMeterRegistry)
            .run { context ->
                context.containsBean(GraphIoMicrometerAutoConfiguration.BEAN_NAME).shouldBeFalse()
            }
    }

    @Test
    fun `bridge is not an unqualified progress listener autowire candidate`() {
        runner
            .withBean(MeterRegistry::class.java, ::SimpleMeterRegistry)
            .withUserConfiguration(GenericListenerConsumer::class.java)
            .withPropertyValues("bluetape4k.graph.io.metrics.enabled=true")
            .run { context ->
                context.getBean(GenericListenerConsumer::class.java).listener.shouldBeNull()
            }
    }

    @Test
    fun `metrics enabled without registry backs off`() {
        runner
            .withPropertyValues("bluetape4k.graph.io.metrics.enabled=true")
            .run { context ->
                context.containsBean(GraphIoMicrometerAutoConfiguration.BEAN_NAME).shouldBeFalse()
            }
    }

    @Test
    fun `missing optional bridge class backs off`() {
        runner
            .withClassLoader(FilteredClassLoader("io.bluetape4k.graph.io.micrometer"))
            .withPropertyValues("bluetape4k.graph.io.metrics.enabled=true")
            .run { context ->
                context.startupFailure.shouldBeNull()
                context.containsBean(GraphIoMicrometerAutoConfiguration.BEAN_NAME).shouldBeFalse()
            }
    }

    @Configuration(proxyBeanMethods = false)
    private class GenericListenerConsumer {
        @Autowired(required = false)
        var listener: GraphIoProgressListener? = null
    }
}
