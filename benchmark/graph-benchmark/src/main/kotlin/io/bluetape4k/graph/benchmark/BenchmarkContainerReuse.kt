package io.bluetape4k.graph.benchmark

internal object BenchmarkContainerReuse {
    const val ENV_NAME: String = "BLUETAPE4K_TESTCONTAINERS_REUSE"

    fun isEnabled(environment: Map<String, String> = System.getenv()): Boolean =
        environment[ENV_NAME].toBoolean() && !isCi(environment)

    private fun isCi(environment: Map<String, String>): Boolean =
        !environment["CI"].isNullOrBlank() || !environment["GITHUB_ACTIONS"].isNullOrBlank()
}
