import io.bluetape4k.gradle.applyBluetape4kPomMetadata
import io.bluetape4k.gradle.centralSnapshotsRepository
import io.bluetape4k.gradle.configurePublishingSigning

plugins {
    `java-platform`
    `maven-publish`
    signing
}

fun Project.isNonPublishedModule(): Boolean {
    val relativePath = rootProject.rootDir.toPath()
        .relativize(projectDir.toPath())
        .toString()
        .replace(File.separatorChar, '/')

    return relativePath == "examples" ||
            relativePath.startsWith("examples/") ||
            relativePath == "benchmark" ||
            relativePath.startsWith("benchmark/") ||
            name.contains("-demo") ||
            name.endsWith("-benchmark")
}

dependencies {
    constraints {
        api(libs.commons.configuration2)
        api(bt4k.classgraph)
        api(bt4k.httpclient5)
        api(bt4k.httpcore5.h2)
        api(bt4k.httpcore5.lib)
        api(bt4k.tomcat.embed.core)

        rootProject.subprojects {
            if (name != "bluetape4k-graph-bom" && !isNonPublishedModule()) {
                api(project(mapOf("path" to path)))
            }
        }
    }
}

publishing {
    publications {
        register("BluetapeGraph", MavenPublication::class) {
            from(components["javaPlatform"])
            pom {
                applyBluetape4kPomMetadata(
                    artifactDisplayName = "bluetape4k-graph-bom",
                    artifactDescription = "BOM for bluetape4k-graph modules",
                )
            }
        }
    }
    repositories {
        centralSnapshotsRepository(project)
        mavenLocal()
    }
}

configurePublishingSigning(publicationName = "BluetapeGraph")
