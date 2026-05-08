package io.bluetape4k.graph.io.okio

import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import org.junit.jupiter.api.Test

class CompressorTest {

    @Test
    fun `GZIP isAvailable returns true`() {
        Compressor.GZIP.isAvailable().shouldBeTrue()
    }

    @Test
    fun `LZ4 isAvailable returns true when on classpath`() {
        Compressor.LZ4.isAvailable().shouldBeTrue()
    }

    @Test
    fun `SNAPPY isAvailable returns true when on classpath`() {
        Compressor.SNAPPY.isAvailable().shouldBeTrue()
    }

    @Test
    fun `ZSTD isAvailable returns true when on classpath`() {
        Compressor.ZSTD.isAvailable().shouldBeTrue()
    }

    @Test
    fun `BZIP2 isAvailable returns true when on classpath`() {
        Compressor.BZIP2.isAvailable().shouldBeTrue()
    }

    @Test
    fun `LZ4 streamingCompressor returns non-null`() {
        Compressor.LZ4.streamingCompressor().shouldNotBeNull()
    }

    @Test
    fun `SNAPPY streamingCompressor returns non-null`() {
        Compressor.SNAPPY.streamingCompressor().shouldNotBeNull()
    }

    @Test
    fun `ZSTD streamingCompressor returns non-null`() {
        Compressor.ZSTD.streamingCompressor().shouldNotBeNull()
    }

    @Test
    fun `BZIP2 streamingCompressor returns non-null`() {
        Compressor.BZIP2.streamingCompressor().shouldNotBeNull()
    }
}
