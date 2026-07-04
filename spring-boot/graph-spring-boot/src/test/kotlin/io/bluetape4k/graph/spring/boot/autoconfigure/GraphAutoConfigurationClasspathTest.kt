package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphAutoConfigurationClasspathTest {

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphAgeAutoConfiguration::class.java,
                GraphFalkorDBAutoConfiguration::class.java,
                GraphMemgraphAutoConfiguration::class.java,
                GraphNeo4jAutoConfiguration::class.java,
                GraphTinkerGraphAutoConfiguration::class.java,
            )
        )

    @Test
    fun `missing optional AGE backend classes back off without startup failure`() {
        runner
            .withClassLoader(FilteredClassLoader("io.bluetape4k.graph.age"))
            .withPropertyValues("bluetape4k.graph.backend=age")
            .run { ctx ->
                ctx.startupFailure.shouldBeNull()
                ctx.containsBean("graphOperations").shouldBeFalse()
            }
    }

    @Test
    fun `missing optional Memgraph backend classes back off without startup failure`() {
        runner
            .withClassLoader(FilteredClassLoader("io.bluetape4k.graph.memgraph"))
            .withPropertyValues("bluetape4k.graph.backend=memgraph")
            .run { ctx ->
                ctx.startupFailure.shouldBeNull()
                ctx.containsBean("graphOperations").shouldBeFalse()
            }
    }
}
