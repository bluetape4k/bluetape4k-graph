package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import java.util.concurrent.CompletableFuture

/**
 * Virtual-thread graph traversal repository.
 *
 * Runs the synchronous [GraphTraversalRepository] on Java 25 Project Loom virtual threads
 * and returns results as `CompletableFuture<T>`. This supports interop with Java code
 * and CompletableFuture-based pipelines.
 *
 * Kotlin code should prefer [GraphSuspendTraversalRepository].
 *
 * ### Usage
 * ```kotlin
 * val ops: GraphOperations = TinkerGraphOperations()
 * val vtOps = ops.asVirtualThreadTraversal()
 * val future = vtOps.neighborsAsync(alice.id, NeighborOptions(edgeLabel = "KNOWS"))
 * val friends = future.join()
 * ```
 */
interface GraphVirtualThreadTraversalRepository {

    /**
     * Finds adjacent neighbor vertices from the start vertex on a virtual thread.
     *
     * @param startId vertex ID to start traversal from.
     * @param options traversal options for label filtering, direction, and maximum depth.
     * @return [CompletableFuture] containing adjacent [GraphVertex] values.
     */
    fun neighborsAsync(
        startId: GraphElementId,
        options: NeighborOptions = NeighborOptions.Default,
    ): CompletableFuture<List<GraphVertex>>

    /**
     * Finds the shortest path between two vertices on a virtual thread.
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options for label filtering and maximum depth.
     * @return [CompletableFuture] containing the shortest [GraphPath], or `null` when no path exists.
     */
    fun shortestPathAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): CompletableFuture<GraphPath?>

    /**
     * Finds all paths between two vertices on a virtual thread.
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options for label filtering and maximum depth.
     * @return [CompletableFuture] containing [GraphPath] values.
     */
    fun allPathsAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions = PathOptions.Default,
    ): CompletableFuture<List<GraphPath>>

    /**
     * Finds a weighted shortest path with the A* algorithm on a virtual thread.
     *
     * @param fromId source vertex ID.
     * @param toId target vertex ID.
     * @param options traversal options; [PathOptions.weightProperty] is required.
     * @param heuristic synchronous estimated cost function to the target.
     * @return [CompletableFuture] containing the weighted shortest [GraphPath], or `null` when no path exists.
     */
    fun aStarPathAsync(
        fromId: GraphElementId,
        toId: GraphElementId,
        options: PathOptions,
        heuristic: (GraphVertex) -> Double,
    ): CompletableFuture<GraphPath?>
}
