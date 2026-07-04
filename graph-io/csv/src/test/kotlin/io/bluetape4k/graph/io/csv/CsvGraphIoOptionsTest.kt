package io.bluetape4k.graph.io.csv

import io.bluetape4k.logging.KLogging
import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import org.junit.jupiter.api.Test

class CsvGraphIoOptionsTest {

    companion object: KLogging()

    @Test
    fun `default mode is prefixed columns prop dot`() {
        val mode = CsvGraphIoOptions().propertyMode
        (mode is CsvPropertyMode.PrefixedColumns) shouldBeEqualTo true
        (mode as CsvPropertyMode.PrefixedColumns).prefix shouldBeEqualTo "prop."
    }

    @Test
    fun `prefixed prefix must not be blank`() {
        assertFailsWith<IllegalArgumentException> { CsvPropertyMode.PrefixedColumns(" ") }
    }

    @Test
    fun `raw json column name must not be blank`() {
        assertFailsWith<IllegalArgumentException> { CsvPropertyMode.RawJsonColumn(" ") }
    }
}
