import io.bluetape4k.gradle.applyBluetape4kPomMetadata
import io.bluetape4k.gradle.centralSnapshotsRepository
import io.bluetape4k.gradle.configurePublishingSigning

plugins {
    `java-platform`
    `maven-publish`
    signing
}

dependencies {
    constraints {
        rootProject.subprojects {
            if (name != "bluetape4k-graph-bom" &&
                !projectDir.absolutePath.contains("/examples/")
            ) {
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
