package io.bluetape4k.graph.examples.code.service

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.Flow

/**
 * 소스 코드 의존성 그래프 서비스 (코루틴 suspend 방식).
 *
 * [CodeGraphService]의 suspend/Flow 버전. [GraphSuspendOperations]을 사용한다.
 *
 * ```kotlin
 * val ops = TinkerGraphSuspendOperations()
 * val service = CodeGraphSuspendService(ops)
 *
 * runBlocking {
 *     service.initialize()
 *     val core = service.addModule("graph-core")
 *     val age  = service.addModule("graph-age")
 *     service.addDependency(age.id, core.id)
 *
 *     val deps = service.getDependencies(age.id).toList()  // [graph-core]
 *     val path = service.findDependencyPath(age.id, core.id)
 * }
 * ```
 */
class CodeGraphSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "code_graph",
) {
    companion object : KLoggingChannel()

    /**
     * 그래프를 초기화한다. 존재하지 않으면 생성한다.
     *
     * ```kotlin
     * service.initialize()
     * ```
     */
    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info("Code graph '$graphName' created")
        }
    }

    /**
     * 모듈 정점을 추가한다.
     *
     * ```kotlin
     * val module = service.addModule("graph-core", path = "graph/graph-core")
     * ```
     */
    suspend fun addModule(name: String, path: String = "", version: String = "", language: String = "kotlin"): GraphVertex =
        ops.createVertex("Module", mapOf("name" to name, "path" to path, "version" to version, "language" to language))

    /**
     * 클래스 정점을 추가한다.
     *
     * ```kotlin
     * val cls = service.addClass("MyClass", "com.example.MyClass", module = "core")
     * ```
     */
    suspend fun addClass(name: String, qualifiedName: String, module: String = "", isAbstract: Boolean = false, isInterface: Boolean = false): GraphVertex =
        ops.createVertex("Class", mapOf("name" to name, "qualifiedName" to qualifiedName, "module" to module, "isAbstract" to isAbstract, "isInterface" to isInterface))

    /**
     * 함수 정점을 추가한다.
     *
     * ```kotlin
     * val fn = service.addFunction("doSomething", "fun doSomething(): Unit", className = "MyClass")
     * ```
     */
    suspend fun addFunction(name: String, signature: String, className: String = "", module: String = "", lineCount: Int = 0): GraphVertex =
        ops.createVertex("Function", mapOf("name" to name, "signature" to signature, "className" to className, "module" to module, "lineCount" to lineCount))

    /**
     * 모듈 간 의존성 간선을 추가한다.
     *
     * ```kotlin
     * service.addDependency(ageModule.id, coreModule.id, dependencyType = "compile")
     * ```
     */
    suspend fun addDependency(fromModuleId: GraphElementId, toModuleId: GraphElementId, dependencyType: String = "compile", version: String = "") {
        ops.createEdge(fromModuleId, toModuleId, "DEPENDS_ON", mapOf("dependencyType" to dependencyType, "version" to version))
    }

    /**
     * 클래스 상속 간선을 추가한다.
     *
     * ```kotlin
     * service.addExtends(childClass.id, parentClass.id)
     * ```
     */
    suspend fun addExtends(childId: GraphElementId, parentId: GraphElementId) {
        ops.createEdge(childId, parentId, "EXTENDS", emptyMap())
    }

    /**
     * 인터페이스 구현 간선을 추가한다.
     *
     * ```kotlin
     * service.addImplements(concreteClass.id, interfaceClass.id)
     * ```
     */
    suspend fun addImplements(classId: GraphElementId, interfaceId: GraphElementId) {
        ops.createEdge(classId, interfaceId, "IMPLEMENTS", emptyMap())
    }

    /**
     * 함수 호출 관계 간선을 추가한다.
     *
     * ```kotlin
     * service.addCall(callerFn.id, calleeFn.id, callCount = 3)
     * ```
     */
    suspend fun addCall(callerFunctionId: GraphElementId, calleeFunctionId: GraphElementId, callCount: Int = 1, isRecursive: Boolean = false) {
        ops.createEdge(callerFunctionId, calleeFunctionId, "CALLS", mapOf("callCount" to callCount, "isRecursive" to isRecursive))
    }

    /**
     * 클래스/함수가 모듈에 속함을 나타내는 간선을 추가한다.
     *
     * ```kotlin
     * service.addBelongsTo(cls.id, module.id)
     * ```
     */
    suspend fun addBelongsTo(elementId: GraphElementId, moduleId: GraphElementId) {
        ops.createEdge(elementId, moduleId, "BELONGS_TO", emptyMap())
    }

    /**
     * 특정 모듈이 의존하는 모듈 목록을 [Flow]로 반환한다.
     *
     * ```kotlin
     * val deps = service.getDependencies(ageModule.id).toList()
     * ```
     */
    fun getDependencies(moduleId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.OUTGOING, maxDepth = 1))

    /**
     * 특정 모듈에 의존하는 모듈 목록(역방향)을 [Flow]로 반환한다.
     *
     * ```kotlin
     * val dependents = service.getDependents(coreModule.id).toList()  // [ageModule, neo4jModule, ...]
     * ```
     */
    fun getDependents(moduleId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.INCOMING, maxDepth = 1))

    /**
     * 전이 의존성 (n단계)을 [Flow]로 반환한다.
     *
     * ```kotlin
     * val transitive = service.getTransitiveDependencies(ageModule.id, maxDepth = 5).toList()
     * ```
     */
    fun getTransitiveDependencies(moduleId: GraphElementId, maxDepth: Int = 5): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.OUTGOING, maxDepth = maxDepth))

    /**
     * 두 모듈 간 최단 의존성 경로를 탐색한다.
     *
     * ```kotlin
     * val path = service.findDependencyPath(ageModule.id, coreModule.id)
     * ```
     */
    suspend fun findDependencyPath(fromId: GraphElementId, toId: GraphElementId): GraphPath? =
        ops.shortestPath(fromId, toId, PathOptions(edgeLabel = "DEPENDS_ON", maxDepth = 10))

    /**
     * 순환 의존성을 탐지한다. 비어 있으면 순환 없음.
     *
     * ```kotlin
     * val cycles = service.detectCircularDependency(moduleId).toList()
     * ```
     */
    fun detectCircularDependency(moduleId: GraphElementId): Flow<GraphPath> =
        ops.allPaths(moduleId, moduleId, PathOptions(edgeLabel = "DEPENDS_ON", maxDepth = 5))

    /**
     * 클래스 상속 계층을 [Flow]로 반환한다.
     *
     * ```kotlin
     * val chain = service.getInheritanceChain(childClass.id, depth = 3).toList()
     * ```
     */
    fun getInheritanceChain(classId: GraphElementId, depth: Int = 5): Flow<GraphVertex> =
        ops.neighbors(classId, NeighborOptions(edgeLabel = "EXTENDS", direction = Direction.OUTGOING, maxDepth = depth))

    /**
     * 함수 호출 체인을 [Flow]로 반환한다.
     *
     * ```kotlin
     * val chain = service.getCallChain(entryFn.id, maxDepth = 5).toList()
     * ```
     */
    fun getCallChain(functionId: GraphElementId, maxDepth: Int = 5): Flow<GraphVertex> =
        ops.neighbors(functionId, NeighborOptions(edgeLabel = "CALLS", direction = Direction.OUTGOING, maxDepth = maxDepth))

    /**
     * 이 모듈이 변경될 때 영향받는 모듈 목록을 [Flow]로 반환한다.
     *
     * ```kotlin
     * val impacted = service.getImpactedModules(coreModule.id, depth = 3).toList()
     * ```
     */
    fun getImpactedModules(moduleId: GraphElementId, depth: Int = 3): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.INCOMING, maxDepth = depth))

    /**
     * 모듈 이름으로 검색한다.
     *
     * ```kotlin
     * val modules = service.findModuleByName("graph-core").toList()
     * ```
     */
    fun findModuleByName(name: String): Flow<GraphVertex> =
        ops.findVerticesByLabel("Module", mapOf("name" to name))

    /**
     * 클래스 이름으로 검색한다.
     *
     * ```kotlin
     * val classes = service.findClassByName("GraphOperations").toList()
     * ```
     */
    fun findClassByName(name: String): Flow<GraphVertex> =
        ops.findVerticesByLabel("Class", mapOf("name" to name))
}
