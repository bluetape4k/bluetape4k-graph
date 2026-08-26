package io.bluetape4k.graph.vt

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeNull
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.concurrent.CompletableFuture
import javax.tools.DiagnosticCollector
import javax.tools.JavaFileObject
import javax.tools.ToolProvider
import org.junit.jupiter.api.Test

class VirtualFutureOwnerAbiMigrationTckTest {

    @Test
    fun `official owner is loaded from bluetape core artifact`() {
        val officialOwner = Class.forName(OFFICIAL_OWNER)
        officialOwner.name shouldBeEqualTo OFFICIAL_OWNER

        val location = requireNotNull(officialOwner.protectionDomain.codeSource) {
            "Official virtual future owner has no code source."
        }.location.toURI().let(Path::of).toAbsolutePath().normalize()
        location.toString().lowercase(Locale.ROOT) shouldContain "bluetape4k-core"
    }

    @Test
    fun `legacy precompiled fixture fails while migrated fixture executes`() {
        withTemporaryDirectory { root ->
            val legacyOwner = compileFixture(
                root.resolve("legacy-owner"),
                mapOf(
                    "io/bluetape4k/concurrent/virtualthread/CompletableFutureNullableSupportKt.java" to
                        resource("/abi/virtual-future/legacy-owner/CompletableFutureNullableSupportKt.java"),
                ),
            )
            val legacyConsumer = compileFixture(
                root.resolve("legacy-consumer"),
                mapOf(
                    "io/bluetape4k/graph/vt/abi/LegacyVirtualFutureConsumer.java" to
                        resource("/abi/virtual-future/legacy-consumer/LegacyVirtualFutureConsumer.java"),
                ),
                additionalClasspath = listOf(legacyOwner),
            )

            val linkage = assertFailsWith<NoClassDefFoundError> {
                try {
                    invokeStatic(legacyConsumer, LEGACY_CONSUMER)
                } catch (error: InvocationTargetException) {
                    throw (error.cause ?: error)
                }
            }
            linkage.message shouldContain LEGACY_OWNER.replace('.', '/')

            val migratedConsumer = compileFixture(
                root.resolve("migrated-consumer"),
                mapOf(
                    "io/bluetape4k/graph/vt/abi/MigratedVirtualFutureConsumer.java" to
                        resource("/abi/virtual-future/migrated-consumer/MigratedVirtualFutureConsumer.java"),
                ),
            )
            val result = invokeStatic(migratedConsumer, MIGRATED_CONSUMER) as CompletableFuture<*>
            result.join().shouldBeNull()
        }
    }

    @Test
    fun `precompiled fixtures encode the owner-only ABI migration`() {
        withTemporaryDirectory { root ->
            val legacyOwner = compileFixture(
                root.resolve("legacy-owner"),
                mapOf(
                    "io/bluetape4k/concurrent/virtualthread/CompletableFutureNullableSupportKt.java" to
                        resource("/abi/virtual-future/legacy-owner/CompletableFutureNullableSupportKt.java"),
                ),
            )
            val legacyConsumer = compileFixture(
                root.resolve("legacy-consumer"),
                mapOf(
                    "io/bluetape4k/graph/vt/abi/LegacyVirtualFutureConsumer.java" to
                        resource("/abi/virtual-future/legacy-consumer/LegacyVirtualFutureConsumer.java"),
                ),
                additionalClasspath = listOf(legacyOwner),
            )
            val migratedConsumer = compileFixture(
                root.resolve("migrated-consumer"),
                mapOf(
                    "io/bluetape4k/graph/vt/abi/MigratedVirtualFutureConsumer.java" to
                        resource("/abi/virtual-future/migrated-consumer/MigratedVirtualFutureConsumer.java"),
                ),
            )

            classBytes(legacyConsumer, LEGACY_CONSUMER) shouldContain LEGACY_OWNER.replace('.', '/')
            classBytes(migratedConsumer, MIGRATED_CONSUMER) shouldContain OFFICIAL_OWNER.replace('.', '/')
        }
    }

    private fun compileFixture(
        root: Path,
        sources: Map<String, String>,
        additionalClasspath: List<Path> = emptyList(),
    ): Path {
        val sourceRoot = root.resolve("sources")
        val classes = root.resolve("classes")
        Files.createDirectories(sourceRoot)
        Files.createDirectories(classes)
        val sourceFiles = sources.map { (relativePath, source) ->
            sourceRoot.resolve(relativePath).also { path ->
                Files.createDirectories(path.parent)
                Files.writeString(path, source, StandardCharsets.UTF_8)
            }
        }
        val compiler = requireNotNull(ToolProvider.getSystemJavaCompiler()) {
            "JDK compiler is required for the ABI migration fixture."
        }
        val diagnostics = DiagnosticCollector<JavaFileObject>()
        compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8).use { fileManager ->
            val options = listOf(
                "--release",
                "25",
                "-proc:none",
                "-classpath",
                compilerClasspath(additionalClasspath),
                "-d",
                classes.toString(),
            )
            val task = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                options,
                null,
                fileManager.getJavaFileObjectsFromFiles(sourceFiles.map(Path::toFile)),
            )
            check(task.call() == true) {
                buildString {
                    appendLine("ABI migration fixture compilation failed")
                    diagnostics.diagnostics.forEach { appendLine(it) }
                }
            }
        }
        return classes
    }

    private fun compilerClasspath(additionalClasspath: List<Path>): String {
        val entries = linkedSetOf<Path>()
        System.getProperty("java.class.path")
            .orEmpty()
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .mapTo(entries, Path::of)
        listOf(Class.forName(OFFICIAL_OWNER), Function0::class.java).forEach { type ->
            type.protectionDomain.codeSource?.location?.toURI()?.let(Path::of)?.let(entries::add)
        }
        entries.addAll(additionalClasspath)
        return entries.joinToString(File.pathSeparator)
    }

    private fun invokeStatic(classes: Path, owner: String): Any? =
        URLClassLoader(arrayOf(classes.toUri().toURL()), javaClass.classLoader).use { loader ->
            Class.forName(owner, true, loader).getMethod("invoke").invoke(null)
        }

    private fun classBytes(classes: Path, owner: String): String =
        String(
            Files.readAllBytes(classes.resolve("${owner.replace('.', '/')}.class")),
            StandardCharsets.ISO_8859_1,
        )

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)) { "ABI fixture resource not found: $path" }
            .bufferedReader(StandardCharsets.UTF_8)
            .use { it.readText() }

    private fun <T> withTemporaryDirectory(block: (Path) -> T): T {
        val root = Files.createTempDirectory("graph-virtual-future-abi-")
        return try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    companion object {
        private const val LEGACY_OWNER =
            "io.bluetape4k.concurrent.virtualthread.CompletableFutureNullableSupportKt"
        private const val OFFICIAL_OWNER =
            "io.bluetape4k.concurrent.virtualthread.CompletableFutureSupportKt"
        private const val LEGACY_CONSUMER = "io.bluetape4k.graph.vt.abi.LegacyVirtualFutureConsumer"
        private const val MIGRATED_CONSUMER = "io.bluetape4k.graph.vt.abi.MigratedVirtualFutureConsumer"
    }
}
