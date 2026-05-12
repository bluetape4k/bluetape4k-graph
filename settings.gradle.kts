pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        // https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention
        id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
    }
}

val baseProjectName = "bluetape4k"

rootProject.name = "$baseProjectName-graph"

include("bluetape4k-graph-bom")
project(":bluetape4k-graph-bom").projectDir = file("bom")

includeModules("graph", false, false)
includeModules("graph-io", false, true, excludeModuleNames = setOf("okio"))
include("graph-okio")
project(":graph-okio").projectDir = file("graph-io/okio")
includeModules("benchmark", false, false)
includeModules("examples", false, false)
includeModules("ktor", false, false)
includeModules("spring-boot", false, false)

fun includeModules(
    baseDir: String,
    withProjectName: Boolean = true,
    withBaseDir: Boolean = true,
    excludeModuleNames: Set<String> = emptySet(),
) {
    files("$rootDir/$baseDir").files
        .filter { it.isDirectory }
        .forEach { moduleDir ->
            moduleDir.listFiles()
                ?.filter {
                    it.isDirectory &&
                        !it.name.startsWith(".") &&
                        it.name !in excludeModuleNames &&
                        File(it, "build.gradle.kts").exists()
                }
                ?.forEach { dir ->
                    val basePath = baseDir.replace("/", "-")
                    val projectName = when {
                        !withProjectName && !withBaseDir -> dir.name
                        withProjectName && !withBaseDir -> baseProjectName + "-" + dir.name
                        withProjectName                 -> baseProjectName + "-" + basePath + "-" + dir.name
                        else                             -> basePath + "-" + dir.name
                    }

                    include(projectName)
                    project(":$projectName").projectDir = dir
                }
        }
}
