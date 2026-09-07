package io.bluetape4k.graph.io.options

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class NdJsonReadOptionsTest {

    @Test
    fun `기본값은 기존 입력을 사실상 제한하지 않는다`() {
        NdJsonReadOptions().maxLineChars shouldBeEqualTo Int.MAX_VALUE
    }

    @Test
    fun `copy는 줄 길이 상한을 보존하고 변경한다`() {
        val original = NdJsonReadOptions(maxLineChars = 128)

        original.copy().maxLineChars shouldBeEqualTo 128
        original.copy(maxLineChars = 256).maxLineChars shouldBeEqualTo 256
    }

    @Test
    fun `줄 길이 상한은 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> { NdJsonReadOptions(maxLineChars = 0) }
        assertFailsWith<IllegalArgumentException> { NdJsonReadOptions(maxLineChars = -1) }
    }

    @Test
    fun `직렬화 왕복은 줄 길이 상한을 보존한다`() {
        val bytes = ByteArrayOutputStream().also { output ->
            ObjectOutputStream(output).use { it.writeObject(NdJsonReadOptions(maxLineChars = 4_096)) }
        }.toByteArray()

        val restored = ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as NdJsonReadOptions
        }

        restored shouldBeEqualTo NdJsonReadOptions(maxLineChars = 4_096)
    }
}
