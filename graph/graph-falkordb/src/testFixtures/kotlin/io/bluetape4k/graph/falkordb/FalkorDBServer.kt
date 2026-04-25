package io.bluetape4k.graph.falkordb

import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.utils.ShutdownQueue
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitAllStrategy
import org.testcontainers.utility.DockerImageName
import java.time.Duration

/**
 * [FalkorDB](https://falkordb.com/) 그래프 데이터베이스를 Testcontainers로 실행합니다.
 *
 * FalkorDB는 Redis 모듈 기반의 그래프 데이터베이스입니다.
 * jfalkordb 드라이버를 통해 `FalkorDB.driver(host, port)`로 접속합니다.
 *
 * **사전 설정**: `~/.testcontainers.properties`에 `testcontainers.reuse.enable=true` 추가 시
 * 컨테이너를 재사용하여 테스트 속도를 높일 수 있습니다.
 *
 * ```kotlin
 * val server = FalkorDBServer().apply { start() }
 * val driver = FalkorDB.driver(server.host, server.port)
 * driver.graph("myGraph").use { graph ->
 *     graph.query("RETURN 1")
 * }
 * ```
 *
 * @param imageName      Docker 이미지 이름 ([DockerImageName])
 * @param useDefaultPort 기본 포트를 호스트 포트로 고정할지 여부
 * @param reuse          컨테이너 재사용 여부
 */
class FalkorDBServer private constructor(
    imageName: DockerImageName,
    useDefaultPort: Boolean = false,
    reuse: Boolean = true,
) : GenericContainer<FalkorDBServer>(imageName) {

    companion object : KLogging() {
        /** FalkorDB 공식 Docker 이미지 이름 */
        const val IMAGE = "falkordb/falkordb"

        /** 고정 안정 버전 태그 — latest 사용 금지 */
        const val TAG = "v4.18.1"

        /** 시스템 프로퍼티 접두사에 사용되는 서버 이름 */
        const val NAME = "falkordb"

        /** FalkorDB(Redis) 기본 포트 */
        const val REDIS_PORT = 6379

        /**
         * [DockerImageName]을 직접 지정하여 [FalkorDBServer] 인스턴스를 생성합니다.
         *
         * @param imageName      Docker 이미지 이름
         * @param useDefaultPort 기본 포트를 호스트에 고정할지 여부
         * @param reuse          컨테이너 재사용 여부
         */
        @JvmStatic
        operator fun invoke(
            imageName: DockerImageName,
            useDefaultPort: Boolean = false,
            reuse: Boolean = true,
        ): FalkorDBServer = FalkorDBServer(imageName, useDefaultPort, reuse)

        /**
         * 이미지명과 태그를 문자열로 지정하여 [FalkorDBServer] 인스턴스를 생성합니다.
         *
         * @param image          Docker 이미지 이름 (기본값: [IMAGE])
         * @param tag            Docker 이미지 태그 (기본값: [TAG])
         * @param useDefaultPort 기본 포트를 호스트에 고정할지 여부
         * @param reuse          컨테이너 재사용 여부
         */
        @JvmStatic
        operator fun invoke(
            image: String = IMAGE,
            tag: String = TAG,
            useDefaultPort: Boolean = false,
            reuse: Boolean = true,
        ): FalkorDBServer {
            image.requireNotBlank("image")
            tag.requireNotBlank("tag")
            val imageName = DockerImageName.parse(image).withTag(tag)
            return FalkorDBServer(imageName, useDefaultPort, reuse)
        }
    }

    /** 호스트에 매핑된 Redis 포트 번호 */
    val port: Int get() = getMappedPort(REDIS_PORT)

    /** Redis 연결 URL (`redis://host:port` 형식) */
    val url: String get() = "redis://$host:$port"

    init {
        addExposedPorts(REDIS_PORT)
        withReuse(reuse)
        waitingFor(
            WaitAllStrategy()
                .withStrategy(Wait.forLogMessage(".*Ready to accept connections.*", 1))
                .withStrategy(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofSeconds(60))
        )

        if (useDefaultPort) {
            addFixedExposedPort(REDIS_PORT, REDIS_PORT)
        }
    }

    /**
     * 테스트에서 재사용할 FalkorDB 서버 싱글턴을 제공합니다.
     *
     * ```kotlin
     * val driver = FalkorDB.driver(
     *     FalkorDBServer.Launcher.falkordb.host,
     *     FalkorDBServer.Launcher.falkordb.port
     * )
     * ```
     */
    object Launcher {
        /**
         * 기본 설정으로 시작된 [FalkorDBServer] 싱글턴 인스턴스입니다.
         * JVM 종료 시 자동으로 정지됩니다.
         */
        val falkordb: FalkorDBServer by lazy {
            FalkorDBServer().apply {
                start()
                ShutdownQueue.register(this)
            }
        }
    }
}
