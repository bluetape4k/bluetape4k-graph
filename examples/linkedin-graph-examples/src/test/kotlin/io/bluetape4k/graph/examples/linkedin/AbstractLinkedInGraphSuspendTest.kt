package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.examples.linkedin.service.LinkedInGraphSuspendService
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractLinkedInGraphSuspendTest {

    companion object: KLoggingChannel()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "linkedin_test"
    protected val service: LinkedInGraphSuspendService by lazy { LinkedInGraphSuspendService(ops, graphName) }

    @BeforeEach
    fun setup() = runSuspendIO {
        if (ops.graphExists(graphName)) ops.dropGraph(graphName)
        service.initialize()
    }

    @Test
    fun `사람 추가 및 인맥 연결`() = runSuspendIO {
        val alice = service.addPerson("Alice", "Software Engineer", "TechCorp", "Seoul")
        val bob = service.addPerson("Bob", "Product Manager", "StartupXYZ", "Busan")

        alice.id.shouldNotBeNull()
        bob.id.shouldNotBeNull()

        service.connect(alice.id, bob.id, since = "2023-01-01", strength = 8)

        val connections = service.getDirectConnections(alice.id).toList()
        connections.shouldNotBeEmpty()
        connections.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `최단 인맥 경로 탐색 - 6단계 분리`() = runSuspendIO {
        val alice = service.addPerson("Alice", "Engineer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Manager", "B", "Seoul")
        val carol = service.addPerson("Carol", "Designer", "C", "Seoul")
        val dave = service.addPerson("Dave", "CTO", "D", "Seoul")

        service.connect(alice.id, bob.id)
        service.connect(bob.id, carol.id)
        service.connect(carol.id, dave.id)

        val path = service.findConnectionPath(alice.id, dave.id)
        path.shouldNotBeNull()
        path.vertices.size shouldBeGreaterThan 1
        path.vertices.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `2촌 인맥 탐색`() = runSuspendIO {
        val alice = service.addPerson("Alice", "Engineer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Manager", "B", "Seoul")
        val carol = service.addPerson("Carol", "Designer", "C", "Seoul")

        service.connect(alice.id, bob.id)
        service.connect(bob.id, carol.id)

        val secondDegree = service.getConnectionsWithinDegree(alice.id, degree = 2).toList()
        secondDegree.shouldNotBeEmpty()
        secondDegree.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `회사 추가 및 재직자 조회`() = runSuspendIO {
        val alice = service.addPerson("Alice", "Engineer", "TechCorp", "Seoul")
        val bob = service.addPerson("Bob", "Designer", "TechCorp", "Seoul")
        val techCorp = service.addCompany("TechCorp", "Technology", "Seoul")

        service.addWorkExperience(alice.id, techCorp.id, "Software Engineer", isCurrent = true)
        service.addWorkExperience(bob.id, techCorp.id, "Designer", isCurrent = true)

        val employees = service.findEmployees(techCorp.id).toList()
        employees.shouldNotBeEmpty()
        employees.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `팔로우 관계 생성`() = runSuspendIO {
        val alice = service.addPerson("Alice", "Influencer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Fan", "B", "Seoul")

        service.follow(bob.id, alice.id)

        val followers = ops.neighbors(
            alice.id,
            NeighborOptions(edgeLabel = "FOLLOWS", direction = Direction.INCOMING, maxDepth = 1)
        ).toList()
        followers.shouldNotBeEmpty()
        followers.forEach { log.debug { "vertex=$it" } }
    }
}
