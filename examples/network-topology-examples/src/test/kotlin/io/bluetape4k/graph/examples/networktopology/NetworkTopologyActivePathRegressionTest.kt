package io.bluetape4k.graph.examples.networktopology

import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.graph.examples.networktopology.schema.ConnectedToLabel
import io.bluetape4k.graph.examples.networktopology.schema.DeviceLabel
import io.bluetape4k.graph.examples.networktopology.schema.HostsServiceLabel
import io.bluetape4k.graph.examples.networktopology.schema.ServiceLabel
import io.bluetape4k.graph.examples.networktopology.service.NetworkTopologyImpactService
import io.bluetape4k.graph.examples.networktopology.service.NetworkTopologyImpactSuspendService
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import org.junit.jupiter.api.Test

class NetworkTopologyActivePathRegressionTest {

    @Test
    fun `sync는 비활성 시작 장치에서 활성 target으로 경로를 반환하지 않는다`() {
        val ops = TinkerGraphOperations()
        try {
            createSyncFixture(ops)
            val service = NetworkTopologyImpactService(ops)

            service.shortestDevicePath(INACTIVE_SOURCE, ACTIVE_TARGET).shouldBeNull()
            service.shortestPathToService(TARGET_SERVICE, INACTIVE_SOURCE).shouldBeNull()
            service.redundantDevicePaths(INACTIVE_SOURCE, ACTIVE_TARGET).shouldBeEmpty()
        } finally {
            ops.close()
        }
    }

    @Test
    fun `sync는 비활성 source와 destination 및 failed device를 경로에서 제외한다`() {
        val ops = TinkerGraphOperations()
        try {
            createSyncFixture(ops)
            val service = NetworkTopologyImpactService(ops)

            service.shortestDevicePath(INACTIVE_SOURCE, INACTIVE_SOURCE).shouldBeNull()
            service.shortestDevicePath(ACTIVE_SOURCE, INACTIVE_TARGET).shouldBeNull()
            service.shortestDevicePath(
                ACTIVE_SOURCE,
                ACTIVE_TARGET,
                failedDeviceIds = setOf(FAILED_MIDDLE),
            ).shouldBeNull()
            service.shortestDevicePath(
                ACTIVE_SOURCE,
                ACTIVE_TARGET,
                failedDeviceIds = setOf(ACTIVE_SOURCE, ACTIVE_TARGET),
            ).shouldBeNull()
        } finally {
            ops.close()
        }
    }

    @Test
    fun `sync는 비활성 destination을 service와 redundant 경로에서 제외한다`() {
        val ops = TinkerGraphOperations()
        try {
            createSyncFixture(ops)
            val service = NetworkTopologyImpactService(ops)

            service.shortestPathToService(INACTIVE_TARGET_SERVICE, ACTIVE_SOURCE).shouldBeNull()
            service.redundantDevicePaths(ACTIVE_SOURCE, INACTIVE_TARGET).shouldBeEmpty()
            service.redundantDevicePaths(INACTIVE_SOURCE, INACTIVE_SOURCE).shouldBeEmpty()
        } finally {
            ops.close()
        }
    }

    @Test
    fun `suspend는 비활성 시작 장치에서 활성 target으로 경로를 반환하지 않는다`() = runSuspendIO {
        val ops = TinkerGraphSuspendOperations()
        try {
            createSuspendFixture(ops)
            val service = NetworkTopologyImpactSuspendService(ops)

            service.shortestDevicePath(INACTIVE_SOURCE, ACTIVE_TARGET).shouldBeNull()
            service.shortestPathToService(TARGET_SERVICE, INACTIVE_SOURCE).shouldBeNull()
            service.redundantDevicePaths(INACTIVE_SOURCE, ACTIVE_TARGET).shouldBeEmpty()
        } finally {
            ops.close()
        }
    }

    @Test
    fun `suspend는 비활성 source와 destination 및 failed device를 경로에서 제외한다`() = runSuspendIO {
        val ops = TinkerGraphSuspendOperations()
        try {
            createSuspendFixture(ops)
            val service = NetworkTopologyImpactSuspendService(ops)

            service.shortestDevicePath(INACTIVE_SOURCE, INACTIVE_SOURCE).shouldBeNull()
            service.shortestDevicePath(ACTIVE_SOURCE, INACTIVE_TARGET).shouldBeNull()
            service.shortestDevicePath(
                ACTIVE_SOURCE,
                ACTIVE_TARGET,
                failedDeviceIds = setOf(FAILED_MIDDLE),
            ).shouldBeNull()
            service.shortestDevicePath(
                ACTIVE_SOURCE,
                ACTIVE_TARGET,
                failedDeviceIds = setOf(ACTIVE_SOURCE, ACTIVE_TARGET),
            ).shouldBeNull()
        } finally {
            ops.close()
        }
    }

    @Test
    fun `suspend는 비활성 destination을 service와 redundant 경로에서 제외한다`() = runSuspendIO {
        val ops = TinkerGraphSuspendOperations()
        try {
            createSuspendFixture(ops)
            val service = NetworkTopologyImpactSuspendService(ops)

            service.shortestPathToService(INACTIVE_TARGET_SERVICE, ACTIVE_SOURCE).shouldBeNull()
            service.redundantDevicePaths(ACTIVE_SOURCE, INACTIVE_TARGET).shouldBeEmpty()
            service.redundantDevicePaths(INACTIVE_SOURCE, INACTIVE_SOURCE).shouldBeEmpty()
        } finally {
            ops.close()
        }
    }

    private fun createSyncFixture(ops: GraphOperations) {
        val devices = listOf(
            INACTIVE_SOURCE to "inactive",
            ACTIVE_SOURCE to "active",
            ACTIVE_MIDDLE to "active",
            FAILED_MIDDLE to "active",
            ACTIVE_TARGET to "active",
            INACTIVE_TARGET to "inactive",
        ).associate { (deviceId, status) ->
            deviceId to ops.createVertex(
                DeviceLabel.label,
                mapOf(
                    DeviceLabel.deviceId.name to deviceId,
                    DeviceLabel.status.name to status,
                ),
            )
        }

        fun connect(from: String, to: String, linkId: String) {
            ops.createEdge(
                devices.getValue(from).id,
                devices.getValue(to).id,
                ConnectedToLabel.label,
                mapOf(
                    ConnectedToLabel.linkId.name to linkId,
                    ConnectedToLabel.status.name to "active",
                ),
            )
        }

        connect(INACTIVE_SOURCE, ACTIVE_MIDDLE, "link-inactive-source")
        connect(ACTIVE_MIDDLE, ACTIVE_TARGET, "link-middle-target")
        connect(ACTIVE_SOURCE, FAILED_MIDDLE, "link-failed-middle-a")
        connect(FAILED_MIDDLE, ACTIVE_TARGET, "link-failed-middle-b")
        connect(ACTIVE_SOURCE, INACTIVE_TARGET, "link-inactive-target")

        val targetService = ops.createVertex(
            ServiceLabel.label,
            mapOf(ServiceLabel.serviceId.name to TARGET_SERVICE),
        )
        ops.createEdge(
            devices.getValue(ACTIVE_TARGET).id,
            targetService.id,
            HostsServiceLabel.label,
        )

        val inactiveTargetService = ops.createVertex(
            ServiceLabel.label,
            mapOf(ServiceLabel.serviceId.name to INACTIVE_TARGET_SERVICE),
        )
        ops.createEdge(
            devices.getValue(INACTIVE_TARGET).id,
            inactiveTargetService.id,
            HostsServiceLabel.label,
        )
    }

    private suspend fun createSuspendFixture(ops: GraphSuspendOperations) {
        val devices = listOf(
            INACTIVE_SOURCE to "inactive",
            ACTIVE_SOURCE to "active",
            ACTIVE_MIDDLE to "active",
            FAILED_MIDDLE to "active",
            ACTIVE_TARGET to "active",
            INACTIVE_TARGET to "inactive",
        ).associate { (deviceId, status) ->
            deviceId to ops.createVertex(
                DeviceLabel.label,
                mapOf(
                    DeviceLabel.deviceId.name to deviceId,
                    DeviceLabel.status.name to status,
                ),
            )
        }

        suspend fun connect(from: String, to: String, linkId: String) {
            ops.createEdge(
                devices.getValue(from).id,
                devices.getValue(to).id,
                ConnectedToLabel.label,
                mapOf(
                    ConnectedToLabel.linkId.name to linkId,
                    ConnectedToLabel.status.name to "active",
                ),
            )
        }

        connect(INACTIVE_SOURCE, ACTIVE_MIDDLE, "link-inactive-source")
        connect(ACTIVE_MIDDLE, ACTIVE_TARGET, "link-middle-target")
        connect(ACTIVE_SOURCE, FAILED_MIDDLE, "link-failed-middle-a")
        connect(FAILED_MIDDLE, ACTIVE_TARGET, "link-failed-middle-b")
        connect(ACTIVE_SOURCE, INACTIVE_TARGET, "link-inactive-target")

        val targetService = ops.createVertex(
            ServiceLabel.label,
            mapOf(ServiceLabel.serviceId.name to TARGET_SERVICE),
        )
        ops.createEdge(
            devices.getValue(ACTIVE_TARGET).id,
            targetService.id,
            HostsServiceLabel.label,
        )

        val inactiveTargetService = ops.createVertex(
            ServiceLabel.label,
            mapOf(ServiceLabel.serviceId.name to INACTIVE_TARGET_SERVICE),
        )
        ops.createEdge(
            devices.getValue(INACTIVE_TARGET).id,
            inactiveTargetService.id,
            HostsServiceLabel.label,
        )
    }

    private companion object {
        const val INACTIVE_SOURCE = "inactive-source"
        const val ACTIVE_SOURCE = "active-source"
        const val ACTIVE_MIDDLE = "active-middle"
        const val FAILED_MIDDLE = "failed-middle"
        const val ACTIVE_TARGET = "active-target"
        const val INACTIVE_TARGET = "inactive-target"
        const val TARGET_SERVICE = "target-service"
        const val INACTIVE_TARGET_SERVICE = "inactive-target-service"
    }
}
