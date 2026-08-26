package io.bluetape4k.graph.io.checkpoint

import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.source.GraphImportSource
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** checkpoint 대상 입력 source에 대해 안정적인 version-aware identity를 만든다. */
object GraphImportCheckpointIdentity {

    /** 호출자가 지정한 identity를 우선하고, 없으면 source에서 계산한다. */
    fun resolve(options: GraphImportOptions, vararg sources: GraphImportSource): String {
        options.checkpointSourceIdentity?.let { return it }
        require(sources.isNotEmpty()) { "at least one source is required" }
        if (options.checkpointStore != null && sources.any { it is GraphImportSource.InputStreamSource }) {
            throw IllegalArgumentException(
                "checkpointSourceIdentity is required for InputStreamSource resume",
            )
        }
        return sources.joinToString(separator = "|") { source -> identityOf(source) }
    }

    private fun identityOf(source: GraphImportSource): String = when (source) {
        is GraphImportSource.PathSource -> {
            val path = source.path.toAbsolutePath().normalize()
            val size = Files.size(path)
            val modified = Files.getLastModifiedTime(path).toMillis()
            digestPath(path, size, modified)
        }
        is GraphImportSource.InputStreamSource ->
            "stream:${System.identityHashCode(source.input)}"
    }

    /** 공통 및 포맷별 import 의미를 나타내는 안정적인 지문을 반환한다. */
    fun optionsIdentity(options: GraphImportOptions, vararg formatOptions: String): String = digest(
        listOf(
            options.batchSize,
            options.maxEdgeBufferSize,
            options.onDuplicateVertexId,
            options.onMissingEdgeEndpoint,
            options.defaultVertexLabel,
            options.defaultEdgeLabel,
            options.preserveExternalIdProperty,
            *formatOptions,
        ).joinToString(separator = "\u0000") { it.toString() },
    )

    private fun digestPath(path: Path, size: Long, modified: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("path=$path:size=$size:modified=$modified\u0000".toByteArray(StandardCharsets.UTF_8))
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun digest(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
