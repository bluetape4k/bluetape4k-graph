package io.bluetape4k.graph.io.options

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.io.serializer.BinarySerializers
import io.bluetape4k.io.serializer.JdkBinarySerializer
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class NdJsonReadOptionsTest {

    companion object: KLogging()

    @Test
    fun `기본값은 기존 입력을 사실상 제한하지 않는다`() {
        NdJsonReadOptions().maxLineChars shouldBeEqualTo Int.MAX_VALUE
    }

    @Test
    fun `copy는 줄 길이 상한을 보존하고 변경한다`() {
        val original = NdJsonReadOptions(maxLineChars = 128)

        log.debug { "original=$original" }
        original.copy().maxLineChars shouldBeEqualTo 128
        original.copy(maxLineChars = 256).maxLineChars shouldBeEqualTo 256
    }

    @Test
    fun `줄 길이 상한은 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> { NdJsonReadOptions(maxLineChars = 0) }
        assertFailsWith<IllegalArgumentException> { NdJsonReadOptions(maxLineChars = -1) }
    }

    @Test
    fun `Java 직렬화 왕복은 줄 길이 상한을 보존한다`() {
        val options = NdJsonReadOptions(maxLineChars = 4_096)
        val serializer = JdkBinarySerializer(objectInputFilter = null)
        val bytes = serializer.serialize(options)
        val restored = serializer.deserialize<NdJsonReadOptions>(bytes)

        restored shouldBeEqualTo options
    }

    @Test
    fun `Fory 직렬화 왕복은 줄 길이 상한을 보존한다`() {
        val options = NdJsonReadOptions(maxLineChars = 4_096)
        val serializer = BinarySerializers.FastFory
        val bytes = serializer.serialize(options)
        val restored = serializer.deserialize<NdJsonReadOptions>(bytes)

        restored shouldBeEqualTo options
    }
}
