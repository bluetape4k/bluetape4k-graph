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
 * 소스 코드 의존성 그래프 서비스 (코루틴 방식).
 */
class CodeGraphSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "code_graph",
) {
    companion object : KLoggingChannel()

    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info("Code graph '$graphName' created")
        }
    }

    suspend fun addModule(name: String, path: String = "", version: String = "", language: String = "kotlin"): GraphVertex =
        ops.createVertex("Module", mapOf("name" to name, "path" to path, "version" to version, "language" to language))

    suspend fun addClass(name: String, qualifiedName: String, module: String = "", isAbstract: Boolean = false, isInterface: Boolean = false): GraphVertex =
        ops.createVertex("Class", mapOf("name" to name, "qualifiedName" to qualifiedName, "module" to module, "isAbstract" to isAbstract, "isInterface" to isInterface))

    suspend fun addFunction(name: String, signature: String, className: String = "", module: String = "", lineCount: Int = 0): GraphVertex =
        ops.createVertex("Function", mapOf("name" to name, "signature" to signature, "className" to className, "module" to module, "lineCount" to lineCount))

    suspend fun addDependency(fromModuleId: GraphElementId, toModuleId: GraphElementId, dependencyType: String = "compile", version: String = "") {
        ops.createEdge(fromModuleId, toModuleId, "DEPENDS_ON", mapOf("dependencyType" to dependencyType, "version" to version))
    }

    suspend fun addExtends(childId: GraphElementId, parentId: GraphElementId) {
        ops.createEdge(childId, parentId, "EXTENDS", emptyMap())
    }

    suspend fun addImplements(classId: GraphElementId, interfaceId: GraphElementId) {
        ops.createEdge(classId, interfaceId, "IMPLEMENTS", emptyMap())
    }

    suspend fun addCall(callerFunctionId: GraphElementId, calleeFunctionId: GraphElementId, callCount: Int = 1, isRecursive: Boolean = false) {
        ops.createEdge(callerFunctionId, calleeFunctionId, "CALLS", mapOf("callCount" to callCount, "isRecursive" to isRecursive))
    }

    suspend fun addBelongsTo(elementId: GraphElementId, moduleId: GraphElementId) {
        ops.createEdge(elementId, moduleId, "BELONGS_TO", emptyMap())
    }

    fun getDependencies(moduleId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.OUTGOING, maxDepth = 1))

    fun getDependents(moduleId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.INCOMING, maxDepth = 1))

    fun getTransitiveDependencies(moduleId: GraphElementId, maxDepth: Int = 5): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.OUTGOING, maxDepth = maxDepth))

    suspend fun findDependencyPath(fromId: GraphElementId, toId: GraphElementId): GraphPath? =
        ops.shortestPath(fromId, toId, PathOptions(edgeLabel = "DEPENDS_ON", maxDepth = 10))

    fun detectCircularDependency(moduleId: GraphElementId): Flow<GraphPath> =
        ops.allPaths(moduleId, moduleId, PathOptions(edgeLabel = "DEPENDS_ON", maxDepth = 5))

    fun getInheritanceChain(classId: GraphElementId, depth: Int = 5): Flow<GraphVertex> =
        ops.neighbors(classId, NeighborOptions(edgeLabel = "EXTENDS", direction = Direction.OUTGOING, maxDepth = depth))

    fun getCallChain(functionId: GraphElementId, maxDepth: Int = 5): Flow<GraphVertex> =
        ops.neighbors(functionId, NeighborOptions(edgeLabel = "CALLS", direction = Direction.OUTGOING, maxDepth = maxDepth))

    fun getImpactedModules(moduleId: GraphElementId, depth: Int = 3): Flow<GraphVertex> =
        ops.neighbors(moduleId, NeighborOptions(edgeLabel = "DEPENDS_ON", direction = Direction.INCOMING, maxDepth = depth))

    fun findModuleByName(name: String): Flow<GraphVertex> =
        ops.findVerticesByLabel("Module", mapOf("name" to name))

    fun findClassByName(name: String): Flow<GraphVertex> =
        ops.findVerticesByLabel("Class", mapOf("name" to name))
}
