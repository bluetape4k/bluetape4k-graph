package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/** 임포트 소스/익스포트 싱크에서 BufferedReader/Writer를 열고, 부모 디렉터리를 자동 생성하는 헬퍼. */
internal object GraphIoPaths {

    fun openReader(source: GraphImportSource): BufferedReader = when (source) {
        is GraphImportSource.PathSource ->
            Files.newBufferedReader(source.path, source.charset)
        is GraphImportSource.InputStreamSource -> {
            val reader = BufferedReader(InputStreamReader(source.input, source.charset))
            if (source.closeInput) reader
            else object : BufferedReader(reader) {
                override fun close() { /* caller owns the stream */ }
            }
        }
    }

    fun openWriter(sink: GraphExportSink): BufferedWriter = when (sink) {
        is GraphExportSink.PathSink -> {
            sink.path.parent?.let { Files.createDirectories(it) }
            val opts = if (sink.append)
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            else
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            Files.newBufferedWriter(sink.path, sink.charset, *opts)
        }
        is GraphExportSink.OutputStreamSink -> {
            val writer = BufferedWriter(OutputStreamWriter(sink.output, sink.charset))
            if (sink.closeOutput) writer
            else object : BufferedWriter(writer) {
                override fun close() { flush() /* caller owns the stream */ }
            }
        }
    }

    fun describeSource(source: GraphImportSource): String? = when (source) {
        is GraphImportSource.PathSource -> source.path.toString()
        is GraphImportSource.InputStreamSource -> null
    }
}
