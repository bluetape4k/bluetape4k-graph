package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.TreeTraversalOptions
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldBeFalse
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
class GraphAlgorithmRepositoryCompatibilityTest {

    @Test
    fun `blocking BFS keeps legacy and migrated JVM overloads`() {
        assertTraversalOverloads(GraphAlgorithmRepository::class.java, "bfs")
    }

    @Test
    fun `blocking DFS keeps legacy and migrated JVM overloads`() {
        assertTraversalOverloads(GraphAlgorithmRepository::class.java, "dfs")
    }

    @Test
    fun `suspend BFS keeps legacy and migrated JVM overloads`() {
        assertTraversalOverloads(GraphSuspendAlgorithmRepository::class.java, "bfs")
    }

    @Test
    fun `suspend DFS keeps legacy and migrated JVM overloads`() {
        assertTraversalOverloads(GraphSuspendAlgorithmRepository::class.java, "dfs")
    }

    @Test
    fun `virtual thread BFS keeps legacy and migrated JVM overloads`() {
        assertTraversalOverloads(GraphVirtualThreadAlgorithmRepository::class.java, "bfsAsync")
    }

    @Test
    fun `virtual thread DFS keeps legacy and migrated JVM overloads`() {
        assertTraversalOverloads(GraphVirtualThreadAlgorithmRepository::class.java, "dfsAsync")
    }

    private fun assertTraversalOverloads(type: Class<*>, methodName: String) {
        val methods = type.declaredMethods.filter {
            it.name == methodName || it.name.startsWith("$methodName-") && !it.name.endsWith("\$default")
        }
        methods.any {
            it.parameterTypes.any { parameter -> parameter == BfsDfsOptions::class.java }
        }.shouldBeTrue()
        methods.any {
            it.parameterTypes.any { parameter -> parameter == TreeTraversalOptions::class.java }
        }.shouldBeTrue()

        val defaultMethods = type.declaredMethods.filter {
            it.name.startsWith("$methodName-") && it.name.endsWith("\$default")
        }
        defaultMethods.any { it.parameterTypes.any { parameter -> parameter == BfsDfsOptions::class.java } }
            .shouldBeTrue()
        defaultMethods.any { it.parameterTypes.any { parameter -> parameter == TreeTraversalOptions::class.java } }
            .shouldBeFalse()
    }
}
