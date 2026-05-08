package io.bluetape4k.graph.io.report

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import org.junit.jupiter.api.Test

class GraphProgressTest {

    // ── GraphExportProgress ──────────────────────────────────────────────────

    @Test
    fun `GraphExportProgress holds all fields`() {
        val p = GraphExportProgress(exported = 10L, total = 100L, currentLabel = "Person", throughputPerSec = 50.0)
        p.exported shouldBeEqualTo 10L
        p.total shouldBeEqualTo 100L
        p.currentLabel shouldBeEqualTo "Person"
        p.throughputPerSec shouldBeEqualTo 50.0
    }

    @Test
    fun `GraphExportProgress defaults to null optional fields`() {
        val p = GraphExportProgress(exported = 5L)
        p.total.shouldBeNull()
        p.currentLabel.shouldBeNull()
        p.throughputPerSec.shouldBeNull()
    }

    @Test
    fun `GraphExportProgress copy creates modified instance`() {
        val p = GraphExportProgress(exported = 1L, total = 10L)
        val copy = p.copy(exported = 2L)
        copy.exported shouldBeEqualTo 2L
        copy.total shouldBeEqualTo 10L
    }

    @Test
    fun `GraphExportProgress equals and hashCode`() {
        val a = GraphExportProgress(3L, 30L, "Edge", 100.0)
        val b = GraphExportProgress(3L, 30L, "Edge", 100.0)
        (a == b) shouldBeEqualTo true
        a.hashCode() shouldBeEqualTo b.hashCode()
    }

    // ── GraphImportProgress ──────────────────────────────────────────────────

    @Test
    fun `GraphImportProgress holds all fields`() {
        val p = GraphImportProgress(processed = 20L, total = 200L, currentLabel = "KNOWS", throughputPerSec = 75.0)
        p.processed shouldBeEqualTo 20L
        p.total shouldBeEqualTo 200L
        p.currentLabel shouldBeEqualTo "KNOWS"
        p.throughputPerSec shouldBeEqualTo 75.0
    }

    @Test
    fun `GraphImportProgress defaults to null optional fields`() {
        val p = GraphImportProgress(processed = 0L)
        p.total.shouldBeNull()
        p.currentLabel.shouldBeNull()
        p.throughputPerSec.shouldBeNull()
    }

    @Test
    fun `GraphImportProgress copy creates modified instance`() {
        val p = GraphImportProgress(processed = 5L, total = 50L)
        val copy = p.copy(processed = 6L)
        copy.processed shouldBeEqualTo 6L
        copy.total shouldBeEqualTo 50L
    }

    @Test
    fun `GraphImportProgress equals and hashCode`() {
        val a = GraphImportProgress(7L, 70L, "Person", 200.0)
        val b = GraphImportProgress(7L, 70L, "Person", 200.0)
        (a == b) shouldBeEqualTo true
        a.hashCode() shouldBeEqualTo b.hashCode()
    }
}
