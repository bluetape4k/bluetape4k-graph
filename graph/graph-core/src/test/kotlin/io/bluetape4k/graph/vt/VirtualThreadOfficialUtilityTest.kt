package io.bluetape4k.graph.vt

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.concurrent.virtualthread.virtualFutureOfNullable
import org.junit.jupiter.api.Test

class VirtualThreadOfficialUtilityTest {

    @Test
    fun `virtualFutureOfNullable is owned by bluetape core`() {
        assertFailsWith<ClassNotFoundException> {
            Class.forName("io.bluetape4k.concurrent.virtualthread.CompletableFutureNullableSupportKt")
        }

        val officialOwner = Class.forName("io.bluetape4k.concurrent.virtualthread.CompletableFutureSupportKt")
        officialOwner.shouldNotBeNull().name shouldBeEqualTo
            "io.bluetape4k.concurrent.virtualthread.CompletableFutureSupportKt"

        virtualFutureOfNullable<String> { null }.join().shouldBeNull()
    }
}
