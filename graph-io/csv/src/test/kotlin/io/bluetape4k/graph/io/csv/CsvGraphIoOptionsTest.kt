package io.bluetape4k.graph.io.csv

import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
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
        val action = { CsvPropertyMode.PrefixedColumns(" ") }
        action shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `raw json column name must not be blank`() {
        val action = { CsvPropertyMode.RawJsonColumn(" ") }
        action shouldThrow IllegalArgumentException::class
    }
}
