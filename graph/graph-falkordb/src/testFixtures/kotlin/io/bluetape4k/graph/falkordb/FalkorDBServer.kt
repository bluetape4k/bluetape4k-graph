package io.bluetape4k.graph.falkordb

import io.bluetape4k.testcontainers.graphdb.FalkorDBServer as SharedFalkorDBServer

/**
 * Compatibility access point for the shared FalkorDB Testcontainers launcher.
 *
 * New tests should prefer importing [io.bluetape4k.testcontainers.graphdb.FalkorDBServer]
 * directly. This object keeps existing graph module tests on the centralized
 * singleton launcher while preserving the current test-fixtures `Launcher` path.
 */
object FalkorDBServer {
    object Launcher {
        val falkordb: SharedFalkorDBServer
            get() = SharedFalkorDBServer.Launcher.falkordb
    }
}
