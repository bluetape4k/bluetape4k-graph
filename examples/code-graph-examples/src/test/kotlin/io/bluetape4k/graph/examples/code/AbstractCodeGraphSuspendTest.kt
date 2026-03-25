package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.examples.code.service.CodeGraphSuspendService
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractCodeGraphSuspendTest {

    companion object : KLogging()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "code_graph"
    protected val service: CodeGraphSuspendService by lazy { CodeGraphSuspendService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() = runBlocking {
        if (ops.graphExists(graphName)) ops.dropGraph(graphName)
        service.initialize()
    }

    @Test
    fun `모듈 추가 및 의존성 관계 구성`() = runTest {
        val core = service.addModule("core", "graph/graph-core", "1.0.0")
        val middle = service.addModule("middle", "graph/middle", "1.0.0")
        val app = service.addModule("app", "examples/app", "1.0.0")

        service.addDependency(middle.id, core.id, "compile")
        service.addDependency(app.id, middle.id, "compile")

        val middleDeps = service.getDependencies(middle.id).toList()
        middleDeps.shouldNotBeEmpty()

        val appDeps = service.getTransitiveDependencies(app.id, maxDepth = 3).toList()
        appDeps.shouldNotBeEmpty()
        appDeps.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `의존성 경로 탐색`() = runTest {
        val core = service.addModule("core", path = "", version = "1.0.0")
        val mid = service.addModule("middle", path = "", version = "1.0.0")
        val top = service.addModule("top", path = "", version = "1.0.0")

        service.addDependency(mid.id, core.id)
        service.addDependency(top.id, mid.id)

        val path = service.findDependencyPath(top.id, core.id)
        path.shouldNotBeNull()
        path.length shouldBeGreaterThan 0
        path.vertices.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `영향 범위 분석 - 역방향 탐색`() = runTest {
        val core = service.addModule("core", path = "", version = "1.0.0")
        val moduleA = service.addModule("moduleA", path = "", version = "1.0.0")
        val moduleB = service.addModule("moduleB", path = "", version = "1.0.0")

        service.addDependency(moduleA.id, core.id)
        service.addDependency(moduleB.id, core.id)

        val impacted = service.getImpactedModules(core.id, depth = 1).toList()
        impacted.shouldNotBeEmpty()
        impacted.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `클래스 상속 계층 탐색`() = runTest {
        val baseClass = service.addClass("Animal", "io.example.Animal")
        val midClass = service.addClass("Mammal", "io.example.Mammal")
        val leafClass = service.addClass("Dog", "io.example.Dog")

        service.addExtends(midClass.id, baseClass.id)
        service.addExtends(leafClass.id, midClass.id)

        val chain = service.getInheritanceChain(leafClass.id, depth = 3).toList()
        chain.shouldNotBeEmpty()
        chain.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `함수 호출 체인 분석`() = runTest {
        val funcA = service.addFunction("processOrder", "fun processOrder(orderId: Long)")
        val funcB = service.addFunction("validateOrder", "fun validateOrder(orderId: Long)")
        val funcC = service.addFunction("saveOrder", "fun saveOrder(order: Order)")

        service.addCall(funcA.id, funcB.id, callCount = 1)
        service.addCall(funcA.id, funcC.id, callCount = 1)
        service.addCall(funcB.id, funcC.id, callCount = 2)

        val callChain = service.getCallChain(funcA.id, maxDepth = 3).toList()
        callChain.shouldNotBeEmpty()
        callChain.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `의존성 없는 경우 경로 null`() = runTest {
        val isolated = service.addModule("isolated", path = "", version = "1.0.0")
        val other = service.addModule("other", path = "", version = "1.0.0")

        val path = service.findDependencyPath(isolated.id, other.id)
        path.shouldBeNull()
    }
}
