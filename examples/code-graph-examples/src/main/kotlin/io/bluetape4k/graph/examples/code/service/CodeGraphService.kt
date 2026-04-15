package io.bluetape4k.graph.examples.code.service

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging

/**
 * 소스 코드 의존성 그래프 서비스.
 * 모듈/클래스/함수 간 관계를 그래프로 관리한다.
 *
 * ```kotlin
 * val ops = TinkerGraphOperations()
 * val service = CodeGraphService(ops)
 * service.initialize()
 *
 * val coreModule = service.addModule("graph-core", path = "graph/graph-core")
 * val ageModule  = service.addModule("graph-age",  path = "graph/graph-age")
 * service.addDependency(ageModule.id, coreModule.id, dependencyType = "compile")
 *
 * val deps = service.getDependencies(ageModule.id)  // [graph-core]
 * val path = service.findDependencyPath(ageModule.id, coreModule.id)
 * ```
 */
class CodeGraphService(
    private val ops: GraphOperations,
    private val graphName: String = "code_graph",
) {
    companion object : KLogging()

    /**
     * 그래프 초기화. 존재하지 않으면 생성한다.
     *
     * ```kotlin
     * service.initialize()  // "code_graph" 그래프가 없으면 생성
     * ```
     */
    fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info("Code graph '$graphName' created")
        }
    }

    /**
     * 모듈 정점을 추가한다.
     *
     * ```kotlin
     * val core = service.addModule("graph-core", path = "graph/graph-core")
     * ```
     *
     * @param name 모듈 이름.
     * @param path 파일시스템 경로.
     * @param version 버전 문자열.
     * @param language 언어 (예: "kotlin", "java").
     * @return 생성된 [GraphVertex].
     */
    fun addModule(
        name: String,
        path: String = "",
        version: String = "",
        language: String = "kotlin",
    ): GraphVertex = ops.createVertex(
        "Module",
        mapOf("name" to name, "path" to path, "version" to version, "language" to language)
    )

    /**
     * 클래스 정점을 추가한다.
     *
     * ```kotlin
     * val cls = service.addClass("MyClass", "com.example.MyClass", module = "core")
     * ```
     */
    fun addClass(
        name: String,
        qualifiedName: String,
        module: String = "",
        isAbstract: Boolean = false,
        isInterface: Boolean = false,
    ): GraphVertex = ops.createVertex(
        "Class",
        mapOf(
            "name" to name,
            "qualifiedName" to qualifiedName,
            "module" to module,
            "isAbstract" to isAbstract,
            "isInterface" to isInterface,
        )
    )

    /**
     * 함수 정점을 추가한다.
     *
     * ```kotlin
     * val fn = service.addFunction("doSomething", "fun doSomething(): Unit", className = "MyClass")
     * ```
     */
    fun addFunction(
        name: String,
        signature: String,
        className: String = "",
        module: String = "",
        lineCount: Int = 0,
    ): GraphVertex = ops.createVertex(
        "Function",
        mapOf(
            "name" to name,
            "signature" to signature,
            "className" to className,
            "module" to module,
            "lineCount" to lineCount,
        )
    )

    /**
     * 모듈 간 의존성 간선을 추가한다.
     *
     * ```kotlin
     * service.addDependency(ageModule.id, coreModule.id, dependencyType = "compile")
     * ```
     *
     * @param fromModuleId 의존하는 모듈 ID.
     * @param toModuleId 의존되는 모듈 ID.
     * @param dependencyType 의존성 종류 ("compile", "runtime", "test").
     * @param version 의존 버전 문자열.
     */
    fun addDependency(
        fromModuleId: GraphElementId,
        toModuleId: GraphElementId,
        dependencyType: String = "compile",
        version: String = "",
    ) {
        ops.createEdge(
            fromModuleId, toModuleId, "DEPENDS_ON",
            mapOf("dependencyType" to dependencyType, "version" to version)
        )
    }

    /**
     * 클래스 상속 간선을 추가한다.
     *
     * ```kotlin
     * service.addExtends(childClass.id, parentClass.id)
     * ```
     */
    fun addExtends(childId: GraphElementId, parentId: GraphElementId) {
        ops.createEdge(childId, parentId, "EXTENDS", emptyMap())
    }

    /**
     * 인터페이스 구현 간선을 추가한다.
     *
     * ```kotlin
     * service.addImplements(concreteClass.id, interfaceClass.id)
     * ```
     */
    fun addImplements(classId: GraphElementId, interfaceId: GraphElementId) {
        ops.createEdge(classId, interfaceId, "IMPLEMENTS", emptyMap())
    }

    /**
     * 함수 호출 관계 간선을 추가한다.
     *
     * ```kotlin
     * service.addCall(callerFn.id, calleeFn.id, callCount = 3)
     * ```
     */
    fun addCall(
        callerFunctionId: GraphElementId,
        calleeFunctionId: GraphElementId,
        callCount: Int = 1,
        isRecursive: Boolean = false,
    ) {
        ops.createEdge(
            callerFunctionId, calleeFunctionId, "CALLS",
            mapOf("callCount" to callCount, "isRecursive" to isRecursive)
        )
    }

    /**
     * 클래스/함수가 모듈에 속함을 나타내는 간선을 추가한다.
     *
     * ```kotlin
     * service.addBelongsTo(cls.id, module.id)
     * ```
     */
    fun addBelongsTo(elementId: GraphElementId, moduleId: GraphElementId) {
        ops.createEdge(elementId, moduleId, "BELONGS_TO", emptyMap())
    }

    /**
     * 특정 모듈이 의존하는 모듈 목록을 반환한다.
     *
     * ```kotlin
     * val deps = service.getDependencies(ageModule.id)  // [coreModule]
     * ```
     */
    fun getDependencies(moduleId: GraphElementId): List<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.OUTGOING, maxDepth = 1))

    /**
     * 특정 모듈에 의존하는 모듈 목록(역방향)을 반환한다.
     *
     * ```kotlin
     * val dependents = service.getDependents(coreModule.id)  // [ageModule, neo4jModule, ...]
     * ```
     */
    fun getDependents(moduleId: GraphElementId): List<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.INCOMING, maxDepth = 1))

    /**
     * 전이 의존성 (n단계까지) 모듈 목록을 반환한다.
     *
     * ```kotlin
     * val transitive = service.getTransitiveDependencies(ageModule.id, maxDepth = 5)
     * ```
     *
     * @param moduleId 기준 모듈 ID.
     * @param maxDepth 최대 탐색 깊이 (기본: 5).
     * @return 전이 의존 [GraphVertex] 목록.
     */
    fun getTransitiveDependencies(moduleId: GraphElementId, maxDepth: Int = 5): List<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.OUTGOING, maxDepth = maxDepth))

    /**
     * 두 모듈 간 최단 의존성 경로를 탐색한다.
     *
     * ```kotlin
     * val path = service.findDependencyPath(ageModule.id, coreModule.id)
     * ```
     *
     * @param fromId 출발 모듈 ID.
     * @param toId 도착 모듈 ID.
     * @return 최단 [GraphPath], 경로가 없으면 `null`.
     */
    fun findDependencyPath(fromId: GraphElementId, toId: GraphElementId): GraphPath? =
        ops.shortestPath(fromId, toId, PathOptions(edgeLabel = "DEPENDS_ON", maxDepth = 10))

    /**
     * 순환 의존성을 탐지한다. `A→B→C→A` 패턴 (모듈이 자기 자신으로 돌아오는 경로).
     *
     * ```kotlin
     * val cycles = service.detectCircularDependency(moduleId)
     * // cycles.isEmpty() → 순환 없음
     * ```
     *
     * @param moduleId 검사할 모듈 ID.
     * @return 순환 경로 [GraphPath] 목록. 비어 있으면 순환 없음.
     */
    fun detectCircularDependency(moduleId: GraphElementId): List<GraphPath> =
        ops.allPaths(moduleId, moduleId, PathOptions(edgeLabel = "DEPENDS_ON", maxDepth = 5))

    /**
     * 클래스 상속 계층을 반환한다.
     *
     * ```kotlin
     * val chain = service.getInheritanceChain(childClass.id, depth = 3)
     * ```
     */
    fun getInheritanceChain(classId: GraphElementId, depth: Int = 5): List<GraphVertex> =
        ops.neighbors(classId, NeighborOptions(edgeLabel = "EXTENDS", direction = Direction.OUTGOING, maxDepth = depth))

    /**
     * 함수 호출 체인을 반환한다.
     *
     * ```kotlin
     * val chain = service.getCallChain(entryFn.id, maxDepth = 5)
     * ```
     */
    fun getCallChain(functionId: GraphElementId, maxDepth: Int = 5): List<GraphVertex> =
        ops.neighbors(functionId, NeighborOptions(edgeLabel = "CALLS", direction = Direction.OUTGOING, maxDepth = maxDepth))

    /**
     * 이 모듈이 변경될 때 영향받는 모듈 목록을 반환한다.
     *
     * ```kotlin
     * val impacted = service.getImpactedModules(coreModule.id, depth = 3)
     * ```
     */
    fun getImpactedModules(moduleId: GraphElementId, depth: Int = 3): List<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.INCOMING, maxDepth = depth))

    /**
     * 모듈 이름으로 검색한다.
     *
     * ```kotlin
     * val modules = service.findModuleByName("graph-core")
     * ```
     */
    fun findModuleByName(name: String): List<GraphVertex> =
        ops.findVerticesByLabel("Module", mapOf("name" to name))

    /**
     * 클래스 이름으로 검색한다.
     *
     * ```kotlin
     * val classes = service.findClassByName("GraphOperations")
     * ```
     */
    fun findClassByName(name: String): List<GraphVertex> =
        ops.findVerticesByLabel("Class", mapOf("name" to name))
}
