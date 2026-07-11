package io.bluetape4k.graph.benchmark

internal object BenchmarkContainerReuse {
    const val ENV_NAME: String = "BLUETAPE4K_TESTCONTAINERS_REUSE"

    fun isEnabled(environment: Map<String, String> = System.getenv()): Boolean =
        environment[ENV_NAME].toBoolean() && !isCi(environment)

    private fun isCi(environment: Map<String, String>): Boolean =
        "CI" in environment || "GITHUB_ACTIONS" in environment
}

internal object BenchmarkFalkorLifecycle {
    fun close(
        reusableServer: Boolean,
        closeOperations: () -> Unit,
        closeDriver: () -> Unit,
        closeServer: () -> Unit,
    ) {
        runCatching(closeOperations)
        runCatching(closeDriver)
        if (!reusableServer) {
            runCatching(closeServer)
        }
    }
}
