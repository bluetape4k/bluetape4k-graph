package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphMergeOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.schema.GraphSchemaManagementOperations
import io.bluetape4k.graph.schema.GraphSchemaManager
import io.bluetape4k.logging.KLogging
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * [Neo4jGraphOperations] wrapper backed by [ConcurrentHashMap] caches.
 *
 * Read methods (`findVertexById`, `findVerticesByLabel`, `neighbors`, `shortestPath`,
 * `allPaths`, and `findEdgesByLabel`) cache their results so repeated lookups become
 * in-memory hits instead of database round trips.
 *
 * Write methods (`createVertex`, `updateVertex`, `deleteVertex`, `createEdge`,
 * and `deleteEdge`) invalidate caches to keep subsequent reads consistent.
 *
 * **Write-result memoization**:
 * repeated `createVertex` and `createEdge` calls with identical arguments return the
 * previously created [GraphVertex] or [GraphEdge]. This is intended for benchmarks
 * and repeat-heavy tests; production code that needs transactional insert semantics
 * should use [Neo4jGraphOperations] directly.
 *
 * Read caches use [ConcurrentHashMap] rather than Caffeine to avoid TinyLFU bookkeeping.
 * TTL and max-size eviction are intentionally absent; explicit `clear()` calls on writes
 * maintain consistency.
 *
 * ### Usage
 * ```kotlin
 * val driver = GraphDatabase.driver("bolt://localhost:7687", AuthTokens.none())
 * val baseOps = Neo4jGraphOperations(driver)
 *
 * // Wrap the base operations for benchmark or repeat-read workloads.
 * val ops = CachingNeo4jGraphOperations(baseOps)
 *
 * // First lookup: database call.
 * val alice = ops.findVertexById("Person", aliceId)
 *
 * // Second lookup: cache hit, no database call.
 * val aliceCached = ops.findVertexById("Person", aliceId)
 *
 * // Deleting a vertex invalidates all caches.
 * ops.deleteVertex("Person", aliceId)
 *
 * // The next lookup misses the cache and reads from the database again.
 * val afterDelete = ops.findVertexById("Person", aliceId)  // null
 * ```
 */
/**
 * @param delegate [Neo4jGraphOperations] instance that performs the actual database calls.
 * @param maxSize compatibility parameter; currently unused after the ConcurrentHashMap migration.
 * @param expireAfterWrite compatibility parameter; currently unused because writes explicitly clear caches.
 */
class CachingNeo4jGraphOperations(
    private val delegate: Neo4jGraphOperations,
    @Suppress("UNUSED_PARAMETER") maxSize: Long = 10_000,
    @Suppress("UNUSED_PARAMETER") expireAfterWrite: Duration = Duration.ofMinutes(5),
): GraphOperations by delegate, GraphSchemaManagementOperations, GraphMergeOperations {

    companion object : KLogging()

    override fun schemaManager(): GraphSchemaManager =
        delegate.schemaManager()

    private data class VertexKey(val label: String, val id: GraphElementId)
    private data class LabelKey(val label: String, val filter: Map<String, Any?>)
    private data class NeighborKey(val startId: GraphElementId, val options: NeighborOptions)
    private data class PathKey(val fromId: GraphElementId, val toId: GraphElementId, val options: PathOptions)
    private data class EdgeLabelKey(val label: String, val filter: Map<String, Any?>)
    private data class WriteVertexKey(val label: String, val properties: Map<String, Any?>)
    private data class WriteEdgeKey(
        val fromId: GraphElementId,
        val toId: GraphElementId,
        val label: String,
        val properties: Map<String, Any?>,
    )

    // ConcurrentHashMap avoids TinyLFU bookkeeping and keeps lookup overhead low.
    // It does not allow null values, so nullable results are wrapped in Optional.
    private val vertexByIdCache: ConcurrentHashMap<VertexKey, Optional<GraphVertex>> = ConcurrentHashMap(128)

    private val verticesByLabelCache: ConcurrentHashMap<LabelKey, List<GraphVertex>> = ConcurrentHashMap(128)

    private val neighborsCache: ConcurrentHashMap<NeighborKey, List<GraphVertex>> = ConcurrentHashMap(128)

    // shortestPath can return null, so the cache stores Optional values.
    private val shortestPathCache: ConcurrentHashMap<PathKey, Optional<GraphPath>> = ConcurrentHashMap(128)

    private val allPathsCache: ConcurrentHashMap<PathKey, List<GraphPath>> = ConcurrentHashMap(128)

    private val edgesByLabelCache: ConcurrentHashMap<EdgeLabelKey, List<GraphEdge>> = ConcurrentHashMap(128)

    // Write-result memoization avoids database round trips for repeated identical create calls.
    // invalidateAll() clears these maps on destructive writes.
    private val createVertexMap: ConcurrentHashMap<WriteVertexKey, GraphVertex> = ConcurrentHashMap(128)

    private val createEdgeMap: ConcurrentHashMap<WriteEdgeKey, GraphEdge> = ConcurrentHashMap(128)

    private fun invalidateAll() {
        vertexByIdCache.clear()
        verticesByLabelCache.clear()
        neighborsCache.clear()
        shortestPathCache.clear()
        allPathsCache.clear()
        edgesByLabelCache.clear()
        createVertexMap.clear()
        createEdgeMap.clear()
    }

    // Invalidate only read caches so createVertex/createEdge do not clear their own write caches.
    private fun invalidateReads() {
        vertexByIdCache.clear()
        verticesByLabelCache.clear()
        neighborsCache.clear()
        shortestPathCache.clear()
        allPathsCache.clear()
        edgesByLabelCache.clear()
    }

    /**
     * Finds one vertex by ID and caches both hits and misses.
	*
	 * ```kotlin
     * val first  = ops.findVertexById("Person", id)         // database lookup
     * val second = ops.findVertexById("Person", id)         // cache hit
     * val absent = ops.findVertexById("Person", unknownId)  // database lookup, null cached
     * val again  = ops.findVertexById("Person", unknownId)  // cache hit, still null
	 * ```
	 */
    override fun findVertexById(label: String, id: GraphElementId): GraphVertex? {
        val key = VertexKey(label, id)
        val cached = vertexByIdCache[key]
        if (cached != null) return cached.orElse(null)
        val value = Optional.ofNullable(delegate.findVertexById(label, id))
        vertexByIdCache[key] = value
        return value.orElse(null)
    }

    /**
     * Finds vertices by label and property filter.
     *
     * The `(label, filter)` pair is the cache key, so empty and non-empty filters are cached independently.
	 *
	 * ```kotlin
     * val all   = ops.findVerticesByLabel("Person")                             // database lookup
     * val all2  = ops.findVerticesByLabel("Person")                             // cache hit
     * val alice = ops.findVerticesByLabel("Person", mapOf("name" to "Alice"))   // separate cache entry
	 * ```
	 */
    override fun findVerticesByLabel(label: String, filter: Map<String, Any?>): List<GraphVertex> {
        val key = LabelKey(label, filter)
        val cached = verticesByLabelCache[key]
        if (cached != null) return cached
        val value = delegate.findVerticesByLabel(label, filter)
        verticesByLabelCache[key] = value
        return value
    }

    /**
     * Finds neighbor vertices and caches by `(startId, options)`.
	 *
	 * ```kotlin
     * val first  = ops.neighbors(aliceId, NeighborOptions.Default)  // database lookup
     * val second = ops.neighbors(aliceId, NeighborOptions.Default)  // cache hit
	 * ```
	 */
    override fun neighbors(startId: GraphElementId, options: NeighborOptions): List<GraphVertex> {
        val key = NeighborKey(startId, options)
        val cached = neighborsCache[key]
        if (cached != null) return cached
        val value = delegate.neighbors(startId, options)
        neighborsCache[key] = value
        return value
    }

    /**
     * Finds the shortest path between two vertices and caches both hits and misses.
	 *
	 * ```kotlin
     * val path  = ops.shortestPath(aId, bId, PathOptions.Default)  // database lookup
     * val path2 = ops.shortestPath(aId, bId, PathOptions.Default)  // cache hit, including null
	 * ```
	 */
    override fun shortestPath(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): GraphPath? {
        val key = PathKey(fromId, toId, options)
        val cached = shortestPathCache[key]
        if (cached != null) return cached.orElse(null)
        val value = Optional.ofNullable(delegate.shortestPath(fromId, toId, options))
        shortestPathCache[key] = value
        return value.orElse(null)
    }

    /**
     * Finds all paths between two vertices and caches by `(fromId, toId, options)`.
	 *
	 * ```kotlin
     * val paths  = ops.allPaths(aId, bId, PathOptions.Default)  // database lookup
     * val paths2 = ops.allPaths(aId, bId, PathOptions.Default)  // cache hit
	 * ```
	 */
    override fun allPaths(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
    ): List<GraphPath> {
        val key = PathKey(fromId, toId, options)
        val cached = allPathsCache[key]
        if (cached != null) return cached
        val value = delegate.allPaths(fromId, toId, options)
        allPathsCache[key] = value
        return value
    }

    /**
     * Finds edges by label and property filter.
     *
     * The `(label, filter)` pair is the cache key.
	 *
	 * ```kotlin
     * val all      = ops.findEdgesByLabel("KNOWS")                         // database lookup
     * val filtered = ops.findEdgesByLabel("KNOWS", mapOf("since" to 2020)) // separate cache entry
     * val cached   = ops.findEdgesByLabel("KNOWS")                         // cache hit
	 * ```
	 */
    override fun findEdgesByLabel(label: String, filter: Map<String, Any?>): List<GraphEdge> {
        val key = EdgeLabelKey(label, filter)
        val cached = edgesByLabelCache[key]
        if (cached != null) return cached
        val value = delegate.findEdgesByLabel(label, filter)
        edgesByLabelCache[key] = value
        return value
    }

    /**
     * Creates a vertex and stores the result in the write-result memoization cache.
     *
     * Repeating the same `(label, properties)` call returns the cached [GraphVertex] without
     * another database call. Read caches are invalidated, but write-result caches are retained.
	 *
	 * ```kotlin
     * val a = ops.createVertex("Person", props)  // database write, read caches invalidated
     * val b = ops.createVertex("Person", props)  // memoized hit, same object as a
	 * ```
	 *
     * > **Note**: use [Neo4jGraphOperations] directly when transactional insert semantics matter.
	 */
    override fun createVertex(label: String, properties: Map<String, Any?>): GraphVertex {
        val key = WriteVertexKey(label, properties)
        val cached = createVertexMap[key]
        if (cached != null) return cached
        val created = delegate.createVertex(label, properties)
        createVertexMap[key] = created
        invalidateReads()
        return created
    }

    override fun createVertices(label: String, propertiesList: List<Map<String, Any?>>): List<GraphVertex> =
        delegate.createVertices(label, propertiesList).also { invalidateAll() }

    /**
     * Updates vertex properties and invalidates all read and write caches.
	 *
	 * ```kotlin
	 * ops.updateVertex("Person", id, mapOf("age" to 31))
     * // Later findVertexById/findVerticesByLabel calls miss the cache and read fresh data.
	 * ```
	 */
    override fun updateVertex(label: String, id: GraphElementId, properties: Map<String, Any?>): GraphVertex? =
        delegate.updateVertex(label, id, properties).also { invalidateAll() }

    /**
     * Deletes a vertex and invalidates all read and write caches.
     *
     * The `createVertex` memoization cache is also cleared, so recreating with the same
     * arguments after deletion creates a new database record.
	 *
	 * ```kotlin
	 * ops.deleteVertex("Person", id)
     * // createVertex("Person", sameProps) misses the write cache and creates a new record.
	 * ```
	 */
    override fun deleteVertex(label: String, id: GraphElementId): Boolean =
        delegate.deleteVertex(label, id).also { invalidateAll() }

    /**
     * Creates an edge and stores the result in the write-result memoization cache.
     *
     * Repeating the same `(fromId, toId, label, properties)` call returns the cached [GraphEdge]
     * without another database call. Read caches are invalidated, but write-result caches are retained.
	 *
	 * ```kotlin
     * val e1 = ops.createEdge(aId, bId, "KNOWS")  // database write, read caches invalidated
     * val e2 = ops.createEdge(aId, bId, "KNOWS")  // memoized hit, same object as e1
	 * ```
	 *
     * > **Note**: use [Neo4jGraphOperations] directly when transactional insert semantics matter.
	 */
    override fun createEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        properties: Map<String, Any?>,
    ): GraphEdge {
        val key = WriteEdgeKey(fromId, toId, label, properties)
        val cached = createEdgeMap[key]
        if (cached != null) return cached
        val created = delegate.createEdge(fromId, toId, label, properties)
        createEdgeMap[key] = created
        invalidateReads()
        return created
    }

    override fun createEdges(label: String, edges: List<BatchEdge>): List<GraphEdge> =
        delegate.createEdges(label, edges).also { invalidateAll() }

    /**
     * Deletes an edge and invalidates all read and write caches.
     *
     * The `createEdge` memoization cache is also cleared, so recreating with the same
     * arguments after deletion creates a new database record.
	 *
	 * ```kotlin
	 * ops.deleteEdge("KNOWS", edgeId)
     * // createEdge(aId, bId, "KNOWS") misses the write cache and creates a new record.
	 * ```
	 */
    override fun deleteEdge(label: String, id: GraphElementId): Boolean =
        delegate.deleteEdge(label, id).also { invalidateAll() }

    override fun mergeVertex(
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphVertex =
        delegate.mergeVertex(label, matchProperties, setProperties).also { invalidateAll() }

    override fun mergeEdge(
        fromId: GraphElementId,
        toId: GraphElementId,
        label: String,
        matchProperties: Map<String, Any?>,
        setProperties: Map<String, Any?>,
    ): GraphEdge =
        delegate.mergeEdge(fromId, toId, label, matchProperties, setProperties).also { invalidateAll() }
}
