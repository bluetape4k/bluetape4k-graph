package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.testcontainers.graphdb.FalkorDBServer
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * 중앙 Testcontainers launcher의 기본 이미지가 그래프 저장소 manifest와 일치하는지 검증합니다.
 */
class TestcontainersImageLauncherContractTest {

    @Test
    fun `공유 launcher 기본 이미지가 graph manifest와 일치한다`() {
        val expected = listOf(
            "${Neo4jServer.IMAGE}:${Neo4jServer.TAG}",
            "${MemgraphServer.IMAGE}:${MemgraphServer.TAG}",
            "${PostgreSQLAgeServer.IMAGE}:${PostgreSQLAgeServer.TAG}",
            "${FalkorDBServer.IMAGE}:${FalkorDBServer.TAG}",
        )
        manifestImages() shouldBeEqualTo expected
    }

    private fun manifestImages(): List<String> {
        val manifest = repositoryRoot()
            .resolve(".github/testcontainers-images.txt")
        return Files.readAllLines(manifest)
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
    }

    private fun repositoryRoot(): Path {
        var candidate = Path.of("").toAbsolutePath()
        while (candidate.parent != null) {
            if (Files.isRegularFile(candidate.resolve(".github/testcontainers-images.txt"))) {
                return candidate
            }
            candidate = candidate.parent
        }
        error("graph repository root cannot be found from ${Path.of("").toAbsolutePath()}")
    }
}
