package io.bluetape4k.graph.algo

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotContain
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.edgeOf
import io.bluetape4k.graph.model.vertexOf
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class GraphAlgorithmLogSafetyTest {

    @Test
    fun `Dijkstra debug traversal log does not expose vertex properties`() {
        val secret = "dijkstra-secret-sentinel"
        val events = captureLogs(DijkstraRunner::class.java) {
            DijkstraRunner(
                fetchEdges = { id ->
                    if (id == GraphElementId("A")) {
                        listOf(edgeOf("A-B", "KNOWS", "A", "B", mapOf("cost" to 1.0)))
                    } else {
                        emptyList()
                    }
                },
                fetchVertex = { id ->
                    when (id.value) {
                        "A" -> vertexOf("A", "Person", mapOf("token" to secret))
                        "B" -> vertexOf("B", "Person")
                        else -> null
                    }
                },
            ).run(
                fromId = GraphElementId("A"),
                toId = GraphElementId("B"),
                options = PathOptions(weightProperty = "cost"),
            )
        }

        val traversalEvents = events.filter { it.formattedMessage.startsWith("current vertex") }
        traversalEvents.map { it.level } shouldBeEqualTo listOf(Level.DEBUG)
        traversalEvents.single().formattedMessage shouldBeEqualTo "current vertex loaded"
        traversalEvents.single().formattedMessage shouldNotContain secret
    }

    @Test
    fun `A star debug traversal log does not expose vertex properties`() {
        val secret = "a-star-secret-sentinel"
        val events = captureLogs(AStarRunner::class.java) {
            AStarRunner(
                fetchEdges = { id ->
                    if (id == GraphElementId("A")) {
                        listOf(edgeOf("A-B", "KNOWS", "A", "B", mapOf("cost" to 1.0)))
                    } else {
                        emptyList()
                    }
                },
                fetchVertex = { id ->
                    when (id.value) {
                        "A" -> vertexOf("A", "Person", mapOf("token" to secret))
                        "B" -> vertexOf("B", "Person")
                        else -> null
                    }
                },
                heuristic = { 0.0 },
            ).run(
                fromId = GraphElementId("A"),
                toId = GraphElementId("B"),
                options = PathOptions(weightProperty = "cost"),
            )
        }

        val traversalEvents = events.filter { it.formattedMessage.startsWith("current vertex") }
        traversalEvents.map { it.level } shouldBeEqualTo listOf(Level.DEBUG)
        traversalEvents.single().formattedMessage shouldBeEqualTo "current vertex loaded"
        traversalEvents.single().formattedMessage shouldNotContain secret
    }

    private fun captureLogs(loggerClass: Class<*>, block: () -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(loggerClass) as Logger
        val previousLevel = logger.level
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.DEBUG
        logger.addAppender(appender)

        try {
            block()
            return appender.list.toList()
        } finally {
            logger.detachAppender(appender)
            logger.level = previousLevel
            appender.stop()
        }
    }
}
