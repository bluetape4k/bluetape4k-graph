package io.bluetape4k.graph.examples.networktopology.service

import io.bluetape4k.graph.examples.networktopology.schema.ConnectedToLabel
import io.bluetape4k.graph.examples.networktopology.schema.DeviceLabel
import io.bluetape4k.graph.examples.networktopology.schema.HostsServiceLabel
import io.bluetape4k.graph.examples.networktopology.schema.MemberOfSegmentLabel
import io.bluetape4k.graph.examples.networktopology.schema.SegmentLabel
import io.bluetape4k.graph.examples.networktopology.schema.ServiceLabel
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

/**
 * Coroutine version of [NetworkTopologyImpactService].
 */
class NetworkTopologyImpactSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "network_topology",
) {
    companion object: KLoggingChannel()

    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Network-topology graph '$graphName' created" }
        }
    }

    suspend fun shortestDevicePath(
        sourceDeviceId: String,
        targetDeviceId: String,
        failedDeviceIds: Set<String> = emptySet(),
        failedLinkIds: Set<String> = emptySet(),
    ): NetworkRoute? {
        sourceDeviceId.requireNotBlank("sourceDeviceId")
        targetDeviceId.requireNotBlank("targetDeviceId")
        val source = deviceById(sourceDeviceId) ?: return null
        val target = deviceById(targetDeviceId) ?: return null
        return shortestDevicePath(source, target, failedDeviceIds, failedLinkIds)
            ?.let { NetworkRoute(it.map(::deviceId), "shortest active path") }
    }

    suspend fun shortestPathToService(
        serviceId: String,
        sourceDeviceId: String = "router-core",
        failedDeviceIds: Set<String> = emptySet(),
        failedLinkIds: Set<String> = emptySet(),
    ): NetworkRoute? {
        serviceId.requireNotBlank("serviceId")
        sourceDeviceId.requireNotBlank("sourceDeviceId")
        val service = serviceById(serviceId) ?: return null
        val host = serviceHost(service) ?: return null
        val source = deviceById(sourceDeviceId) ?: return null

        return shortestDevicePath(source, host, failedDeviceIds, failedLinkIds)
            ?.let { path -> NetworkRoute(path.map(::deviceId) + serviceId(service), "service is reachable") }
    }

    suspend fun impactedServicesByFailedDevice(
        failedDeviceId: String,
        sourceDeviceId: String = "router-core",
    ): List<GraphVertex> {
        failedDeviceId.requireNotBlank("failedDeviceId")
        return servicesUnreachableFrom(sourceDeviceId, failedDeviceIds = setOf(failedDeviceId))
    }

    suspend fun impactedServicesByFailedLink(
        failedLinkId: String,
        sourceDeviceId: String = "router-core",
    ): List<GraphVertex> {
        failedLinkId.requireNotBlank("failedLinkId")
        return servicesUnreachableFrom(sourceDeviceId, failedLinkIds = setOf(failedLinkId))
    }

    suspend fun isolatedSegments(sourceDeviceId: String = "router-core"): List<GraphVertex> {
        sourceDeviceId.requireNotBlank("sourceDeviceId")
        val source = deviceById(sourceDeviceId) ?: return emptyList()
        return ops.findVerticesByLabel(SegmentLabel.label)
            .toList()
            .filter { segment ->
                devicesInSegment(segment)
                    .none { device -> shortestDevicePath(source, device) != null }
            }
            .distinctBy { it.id }
    }

    suspend fun redundantDevicePaths(
        sourceDeviceId: String,
        targetDeviceId: String,
        maxDepth: Int = 5,
    ): List<NetworkRoute> {
        sourceDeviceId.requireNotBlank("sourceDeviceId")
        targetDeviceId.requireNotBlank("targetDeviceId")
        require(maxDepth > 0) { "maxDepth must be > 0, was $maxDepth" }

        val source = deviceById(sourceDeviceId) ?: return emptyList()
        val target = deviceById(targetDeviceId) ?: return emptyList()
        val paths = mutableListOf<List<GraphVertex>>()
        val queue = ArrayDeque<List<GraphVertex>>()
        queue += listOf(source)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val tail = path.last()
            if (path.size > maxDepth + 1) {
                continue
            }
            if (tail.id == target.id) {
                paths += path
                continue
            }
            activeConnectedDevices(tail)
                .filterNot { next -> path.any { it.id == next.id } }
                .forEach { next -> queue += path + next }
        }

        return paths
            .distinctBy { path -> path.map { it.id } }
            .map { NetworkRoute(it.map(::deviceId), "redundant active path candidate") }
    }

    private suspend fun servicesUnreachableFrom(
        sourceDeviceId: String,
        failedDeviceIds: Set<String> = emptySet(),
        failedLinkIds: Set<String> = emptySet(),
    ): List<GraphVertex> {
        val source = deviceById(sourceDeviceId) ?: return emptyList()
        return ops.findVerticesByLabel(ServiceLabel.label)
            .toList()
            .filter { service ->
                val host = serviceHost(service) ?: return@filter false
                shortestDevicePath(source, host) != null &&
                    shortestDevicePath(source, host, failedDeviceIds, failedLinkIds) == null
            }
            .distinctBy { it.id }
    }

    private suspend fun shortestDevicePath(
        source: GraphVertex,
        target: GraphVertex,
        failedDeviceIds: Set<String> = emptySet(),
        failedLinkIds: Set<String> = emptySet(),
    ): List<GraphVertex>? {
        if (deviceId(source) in failedDeviceIds || deviceId(target) in failedDeviceIds) {
            return null
        }
        val visited = mutableSetOf(source.id)
        val queue = ArrayDeque<List<GraphVertex>>()
        queue += listOf(source)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val tail = path.last()
            if (tail.id == target.id) {
                return path
            }
            activeConnectedDevices(tail, failedLinkIds)
                .filterNot { next -> next.id in visited || deviceId(next) in failedDeviceIds }
                .forEach { next ->
                    visited += next.id
                    queue += path + next
                }
        }

        return null
    }

    private suspend fun activeConnectedDevices(
        device: GraphVertex,
        failedLinkIds: Set<String> = emptySet(),
    ): List<GraphVertex> =
        activeConnectedEdges(device, failedLinkIds)
            .mapNotNull { edge ->
                val nextId = if (edge.startId == device.id) edge.endId else edge.startId
                ops.findVertexById(nextId)
            }
            .filter { it.label == DeviceLabel.label && it.properties[DeviceLabel.status.name] == "active" }
            .distinctBy { it.id }

    private suspend fun activeConnectedEdges(
        device: GraphVertex,
        failedLinkIds: Set<String> = emptySet(),
    ): List<GraphEdge> =
        (ops.findEdgesByStartId(device.id, ConnectedToLabel.label).toList() +
            ops.findEdgesByEndId(device.id, ConnectedToLabel.label).toList())
            .filter { edge ->
                edge.properties[ConnectedToLabel.status.name] == "active" &&
                    edge.properties[ConnectedToLabel.linkId.name] !in failedLinkIds
            }
            .distinctBy { it.id }

    private suspend fun serviceHost(service: GraphVertex): GraphVertex? =
        ops.neighbors(
            service.id,
            NeighborOptions(edgeLabel = HostsServiceLabel.label, direction = Direction.INCOMING, maxDepth = 1)
        ).firstOrNull { it.label == DeviceLabel.label }

    private suspend fun devicesInSegment(segment: GraphVertex): List<GraphVertex> =
        ops.neighbors(
            segment.id,
            NeighborOptions(edgeLabel = MemberOfSegmentLabel.label, direction = Direction.INCOMING, maxDepth = 1)
        ).toList().filter { it.label == DeviceLabel.label }

    private suspend fun deviceById(deviceId: String): GraphVertex? =
        ops.findVerticesByLabel(DeviceLabel.label, mapOf(DeviceLabel.deviceId.name to deviceId))
            .toList()
            .firstOrNull()

    private suspend fun serviceById(serviceId: String): GraphVertex? =
        ops.findVerticesByLabel(ServiceLabel.label, mapOf(ServiceLabel.serviceId.name to serviceId))
            .toList()
            .firstOrNull()

    private fun deviceId(device: GraphVertex): String =
        device.properties[DeviceLabel.deviceId.name].toString()

    private fun serviceId(service: GraphVertex): String =
        service.properties[ServiceLabel.serviceId.name].toString()
}
