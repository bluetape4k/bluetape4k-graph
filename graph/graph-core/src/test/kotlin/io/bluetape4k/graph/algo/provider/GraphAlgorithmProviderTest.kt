package io.bluetape4k.graph.algo.provider

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test

class GraphAlgorithmProviderTest {

    @Test
    fun `AUTO policy records explicit JVM fallback when no provider is configured`() {
        val execution = GraphAlgorithmProviderSelector.select(GraphAlgorithmId.PAGE_RANK)

        execution.algorithm shouldBeEqualTo GraphAlgorithmId.PAGE_RANK
        execution.providerId shouldBeEqualTo GraphAlgorithmProviderSelector.JVM_PROVIDER_ID
        execution.path shouldBeEqualTo GraphAlgorithmExecutionPath.JVM_FALLBACK
        execution.fallbackReason shouldBeEqualTo GraphAlgorithmFallbackReason.NO_PROVIDER
    }

    @Test
    fun `provider descriptor selects native path without coupling graph-core to native SDK`() {
        val provider = DescriptorOnlyProvider(
            GraphAlgorithmProviderDescriptor(
                id = "neo4j-gds",
                version = "2.13",
                algorithms = setOf(GraphAlgorithmId.PAGE_RANK, GraphAlgorithmId.BFS),
            ),
        )

        val execution = GraphAlgorithmProviderSelector.select(
            algorithm = GraphAlgorithmId.PAGE_RANK,
            providers = listOf(provider),
        )

        execution.path shouldBeEqualTo GraphAlgorithmExecutionPath.NATIVE
        execution.providerId shouldBeEqualTo "neo4j-gds"
        execution.fallbackReason shouldBeEqualTo null
    }

    @Test
    fun `unsupported provider does not silently satisfy NATIVE_ONLY policy`() {
        val provider = DescriptorOnlyProvider(
            GraphAlgorithmProviderDescriptor(
                id = "memgraph-mage",
                algorithms = setOf(GraphAlgorithmId.CONNECTED_COMPONENTS),
            ),
        )

        val exception = assertFailsWith<GraphAlgorithmProviderUnavailableException> {
            GraphAlgorithmProviderSelector.select(
                algorithm = GraphAlgorithmId.PAGE_RANK,
                providers = listOf(provider),
                policy = GraphAlgorithmProviderPolicy.NATIVE_ONLY,
            )
        }

        exception.message.shouldContain("PAGE_RANK")
        exception.message.shouldContain("no provider supports")
    }

    @Test
    fun `JVM_ONLY policy is observable as a deliberate fallback`() {
        val provider = DescriptorOnlyProvider(
            GraphAlgorithmProviderDescriptor(
                id = "neo4j-gds",
                algorithms = setOf(GraphAlgorithmId.PAGE_RANK),
            ),
        )

        val execution = GraphAlgorithmProviderSelector.select(
            algorithm = GraphAlgorithmId.PAGE_RANK,
            providers = listOf(provider),
            policy = GraphAlgorithmProviderPolicy.JVM_ONLY,
        )

        execution.path shouldBeEqualTo GraphAlgorithmExecutionPath.JVM_FALLBACK
        execution.fallbackReason shouldBeEqualTo GraphAlgorithmFallbackReason.JVM_ONLY_POLICY
        (execution.providerId == "neo4j-gds").shouldBeFalse()
        (execution.providerId == GraphAlgorithmProviderSelector.JVM_PROVIDER_ID).shouldBeTrue()
    }

    @Test
    fun `execution observer receives the selected provider`() {
        val observed = mutableListOf<GraphAlgorithmExecution>()
        val observer = GraphAlgorithmExecutionObserver { observed += it }
        val execution = GraphAlgorithmProviderSelector.select(GraphAlgorithmId.DFS)

        observer.onExecution(execution)

        observed shouldBeEqualTo listOf(execution)
    }

    private class DescriptorOnlyProvider(
        override val descriptor: GraphAlgorithmProviderDescriptor,
    ): GraphAlgorithmProvider
}
