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
        rootProject.subprojects {
            if (name != "bluetape4k-graph-bom" && !isNonPublishedModule()) {
                api(this)
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
