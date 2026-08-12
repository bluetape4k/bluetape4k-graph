import dev.detekt.gradle.DetektCreateBaselineTask
import groovy.json.JsonOutput
import io.bluetape4k.gradle.applyBluetape4kPomMetadata
import io.bluetape4k.gradle.centralSnapshotsRepository
import io.bluetape4k.gradle.configurePublishingSigning
import io.bluetape4k.gradle.resolveCentralPublishingConfig
import io.bluetape4k.gradle.resolvePublishingSigningConfig
import nmcp.NmcpAggregationExtension
import nmcp.NmcpExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    base
    `maven-publish`
    signing
    alias(bt4k.plugins.kotlin.jvm)

    // see: https://kotlinlang.org/docs/reference/compiler-plugins.html
    alias(bt4k.plugins.kotlin.spring) apply false
    alias(bt4k.plugins.kotlin.allopen) apply false
    alias(bt4k.plugins.kotlin.noarg) apply false
    alias(bt4k.plugins.kotlin.jpa) apply false
    alias(bt4k.plugins.kotlin.serialization) apply false
    alias(bt4k.plugins.kotlinx.atomicfu)

    alias(bt4k.plugins.detekt.dev)

    alias(bt4k.plugins.dependency.management)

    alias(bt4k.plugins.dokka)
    alias(bt4k.plugins.test.logger)
    alias(bt4k.plugins.shadow) apply false
    alias(bt4k.plugins.gatling) apply false

    alias(bt4k.plugins.nmcp.aggregation)
    alias(bt4k.plugins.nmcp) apply false

    // 테스트 커버리지 (Kotlin inline/suspend 정확 지원)
    alias(bt4k.plugins.kover)
}

val centralPublishing = resolveCentralPublishingConfig()
val centralUser: String = centralPublishing.username
val centralPassword: String = centralPublishing.password
val centralSnapshotsParallelism: Int = providers
    .gradleProperty("centralSnapshotsParallelism")
    .map(String::toInt)
    .orElse(8)
    .get()

allprojects {
    group = providers.gradleProperty("projectGroup").get()
    val snapshotSuffix = providers.gradleProperty("snapshotVersion").orElse("").get()
    version = providers.gradleProperty("baseVersion").get() + snapshotSuffix

    repositories {
        mavenCentral()
        google()

        // bluetape4k snapshot 버전 사용 시만 사용하세요.
        maven {
            name = "central-snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
    // bluetape4k snapshot 버전 사용 시만 사용하세요.
    configurations.all {
        resolutionStrategy.cacheChangingModulesFor(1, TimeUnit.DAYS)
    }
}

// Capture root-project catalog reference once; used inside subprojects {} closures
// where `libs` is not in scope (different receiver type in the lambda).
val rootLibs = libs
val rootBt4k = bt4k
val bt4kCatalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("bt4k")
fun bt4kLibrary(alias: String) = bt4kCatalog.findLibrary(alias).get()
fun bt4kVersion(alias: String): String {
    val version = bt4kCatalog.findVersion(alias).get()
    return version.requiredVersion
        .ifBlank { version.preferredVersion }
        .ifBlank { version.strictVersion }
}
val detektSupportedKotlinVersion = bt4kVersion("kotlin")


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

val detektBaselineFile = layout.projectDirectory.file("config/detekt/baseline.xml")

val detektProjectBaseline = tasks.register<DetektCreateBaselineTask>("detektProjectBaseline") {
    description = "Regenerates the shared Detekt baseline for existing findings."
    ignoreFailures.set(true)
    parallel.set(true)
    buildUponDefaultConfig.set(true)
    setSource(files(rootDir))
    baseline.set(detektBaselineFile)
    include("**/*.kt")
    include("**/*.kts")
    exclude("**/build/**")
    exclude("**/.gradle/**")
    exclude("**/resources/**")
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(25)
    }
    if (!isNonPublishedModule()) {
        apply(plugin = "com.gradleup.nmcp")
    }

    configurations.matching { it.name.startsWith("nmcp") }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("kotlinx-serialization")) {
                useVersion("1.9.0")
                because("nmcp runtime compatibility (avoid serialization ABI mismatch)")
            }
        }
    }

    plugins.withId("com.gradleup.nmcp") {
        extensions.configure<NmcpExtension>("nmcp") {
            publishAllPublicationsToCentralPortal {
                username.set(centralUser)
                password.set(centralPassword)
                publishingType.set("AUTOMATIC")
                uploadSnapshotsParallelism.set(centralSnapshotsParallelism)
            }
        }
    }

    // BOM 모듈은 java-platform 플러그인을 사용하므로 Java/Kotlin 설정을 건너뜁니다.
    if (name == "bluetape4k-graph-bom") return@subprojects

    apply {
        plugin<JavaLibraryPlugin>()

        plugin("org.jetbrains.kotlin.jvm")

        // Atomicfu
        plugin("org.jetbrains.kotlinx.atomicfu")

        if (!isNonPublishedModule()) {
            plugin("maven-publish")
            plugin("signing")
        }

        plugin("io.spring.dependency-management")

        plugin("org.jetbrains.dokka")
        plugin("com.adarshr.test-logger")

        // Detekt — CI quality gate for publishable Kotlin modules.
        if (!isNonPublishedModule()) {
            plugin("dev.detekt")
        }

        // Kover — Kotlin 코드 커버리지 (bom/benchmark/examples 는 커버리지 대상에서 제외)
        if (!isNonPublishedModule() && name != "bluetape4k-graph-bom") {
            plugin("org.jetbrains.kotlinx.kover")
        }
    }

    plugins.withId("dev.detekt") {
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension>("detekt") {
            baseline.set(detektBaselineFile)
            buildUponDefaultConfig.set(true)
        }
        configurations.named("detekt") {
            resolutionStrategy.eachDependency {
                if (requested.group == "org.jetbrains.kotlin") {
                    useVersion(detektSupportedKotlinVersion)
                    because("detekt and Kotlin compiler artifacts must use the centrally governed Kotlin version")
                }
            }
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    kotlin {
        jvmToolchain(25)
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_2_4)
            apiVersion.set(KotlinVersion.KOTLIN_2_4)
            jvmTarget.set(JvmTarget.JVM_25)
            freeCompilerArgs = listOf(
                "-Xjsr305=strict",
                "-jvm-default=enable",
                // "-Xinline-classes",   // Kotlin 2.+ 에서는 불필요
                "-Xstring-concat=indy",
            )
            val experimentalAnnotations = listOf(
                "kotlin.RequiresOptIn",
                "kotlin.ExperimentalStdlibApi",
                "kotlin.contracts.ExperimentalContracts",
                "kotlin.experimental.ExperimentalTypeInference",
                "kotlinx.coroutines.ExperimentalCoroutinesApi",
                "kotlinx.coroutines.InternalCoroutinesApi",
                "kotlinx.coroutines.FlowPreview",
                "kotlinx.coroutines.DelicateCoroutinesApi",
            )
            freeCompilerArgs.addAll(experimentalAnnotations.map { "-opt-in=$it" })
        }
    }

    atomicfu {
        transformJvm = true
        jvmVariant = "VH"
    }

    tasks {
        compileJava {
            options.isIncremental = true
        }

        compileKotlin {
            compilerOptions {
                incremental = true
            }
        }

        abstract class TestMutexService: BuildService<BuildServiceParameters.None>
        abstract class SigningMutexService: BuildService<BuildServiceParameters.None>
        abstract class NmcpPublishMutexService: BuildService<BuildServiceParameters.None>

        val testMutex = gradle.sharedServices.registerIfAbsent(
            "test-mutex",
            TestMutexService::class
        ) {
            maxParallelUsages.set(1)
        }
        val signingMutex = gradle.sharedServices.registerIfAbsent(
            "signing-mutex",
            SigningMutexService::class
        ) {
            maxParallelUsages.set(1)
        }
        val nmcpPublishMutex = gradle.sharedServices.registerIfAbsent(
            "nmcp-publish-mutex",
            NmcpPublishMutexService::class
        ) {
            maxParallelUsages.set(1)
        }

        test {
            usesService(testMutex)

            useJUnitPlatform()

            jvmArgs(
                "-Xshare:off",
                "-Xms2G",
                "-Xmx4G",
                "-XX:+UseG1GC",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+EnableDynamicAgentLoading",
                "--enable-preview",
                "-Didea.io.use.nio2=true"
            )

            testLogging {
                showExceptions = true
                showCauses = true
                showStackTraces = true

                events("failed")
            }
        }

        val signingUsesGpgCmd = resolvePublishingSigningConfig().useGpgCmd
        withType<Sign>().configureEach {
            if (signingUsesGpgCmd) {
                usesService(signingMutex)
            }
        }
        configureEach {
            if (name.startsWith("nmcpPublishAllPublicationsToCentral")) {
                usesService(nmcpPublishMutex)
            }
        }

        testlogger {
            theme = com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA_PARALLEL
            showFullStackTraces = true
        }

        dokka {
            configureEach {
                dokkaSourceSets {
                    configureEach {
                        val dokkaModuleDoc = project.file("dokka.md")
                        if (dokkaModuleDoc.isFile) {
                            includes.from(dokkaModuleDoc)
                        }
                    }
                }
                dokkaPublications.html {
                    outputDirectory.set(project.file("docs/api"))
                }
            }
        }

        clean {
            doLast {
                delete("./.project")
                delete("./out")
                delete("./bin")
            }
        }
    }

    dependencyManagement {
        setApplyMavenExclusions(false)

        imports {
            mavenBom(bt4kLibrary("bluetape4k-bom").get().toString())
            mavenBom("org.springframework.boot:spring-boot-dependencies:${bt4kVersion("spring-boot4")}")

            mavenBom(rootBt4k.feign.bom.get().toString())
            mavenBom(rootBt4k.micrometer.bom.get().toString())
            mavenBom(rootBt4k.micrometer.tracing.bom.get().toString())
            mavenBom("org.apache.logging.log4j:log4j-bom:${bt4kVersion("log4j")}")
            mavenBom("org.testcontainers:testcontainers-bom:${bt4kVersion("testcontainers")}")
            mavenBom(rootBt4k.junit.bom.get().toString())
            mavenBom(rootBt4k.okhttp3.bom.get().toString())
            mavenBom("io.netty:netty-bom:${bt4kVersion("netty")}")
            mavenBom("com.fasterxml.jackson:jackson-bom:${bt4kVersion("jackson")}")
            mavenBom("tools.jackson:jackson-bom:${bt4kVersion("jackson3")}")
            mavenBom(rootBt4k.neo4j.bolt.connection.bom.get().toString())

            mavenBom("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${bt4kVersion("kotlinx-coroutines")}")
            mavenBom("org.jetbrains.kotlin:kotlin-bom:${bt4kVersion("kotlin")}")
        }
        dependencies {
            // <central-catalog-local-aliases>
            dependency("com.fasterxml.jackson.core:jackson-core:${bt4kVersion("jackson")}")
            dependency("com.fasterxml.jackson.module:jackson-module-blackbird:${bt4kVersion("jackson")}")
            dependency("com.fasterxml.jackson.module:jackson-module-kotlin:${bt4kVersion("jackson")}")
            dependency("com.fasterxml.jackson:jackson-bom:${bt4kVersion("jackson")}")
            dependency("org.jetbrains.exposed:exposed-dao:${bt4kVersion("exposed")}")
            dependency("org.jetbrains.kotlin:kotlin-bom:${bt4kVersion("kotlin")}")
            dependency("org.jetbrains.kotlin:kotlin-reflect:${bt4kVersion("kotlin")}")
            dependency("org.jetbrains.kotlin:kotlin-stdlib:${bt4kVersion("kotlin")}")
            dependency("org.jetbrains.kotlin:kotlin-test:${bt4kVersion("kotlin")}")
            dependency("org.jetbrains.kotlin:kotlin-test-junit5:${bt4kVersion("kotlin")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-test:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.slf4j:jcl-over-slf4j:${bt4kVersion("slf4j")}")
            dependency("org.slf4j:jul-to-slf4j:${bt4kVersion("slf4j")}")
            dependency("org.slf4j:log4j-over-slf4j:${bt4kVersion("slf4j")}")
            dependency("org.springframework.boot:spring-boot-dependencies:${bt4kVersion("spring-boot4")}")
            dependency("org.testcontainers:testcontainers:${bt4kVersion("testcontainers")}")
            dependency("org.testcontainers:testcontainers-bom:${bt4kVersion("testcontainers")}")
            dependency("org.testcontainers:testcontainers-neo4j:${bt4kVersion("testcontainers")}")
            dependency("org.testcontainers:testcontainers-postgresql:${bt4kVersion("testcontainers")}")
            dependency("tools.jackson.core:jackson-core:${bt4kVersion("jackson3")}")
            dependency("tools.jackson.module:jackson-module-blackbird:${bt4kVersion("jackson3")}")
            dependency("tools.jackson.module:jackson-module-kotlin:${bt4kVersion("jackson3")}")
            dependency("tools.jackson:jackson-bom:${bt4kVersion("jackson3")}")
            // </central-catalog-local-aliases>
            dependency("org.postgresql:postgresql:${bt4kVersion("postgresql")}")
            dependency(rootBt4k.jetbrains.annotations.get().toString())

            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-bom:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-debug:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-test:${bt4kVersion("kotlinx-coroutines")}")
            dependency("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:${bt4kVersion("kotlinx-coroutines")}")

            // Apache Commons
            dependency(rootBt4k.commons.beanutils.get().toString())
            dependency(rootBt4k.commons.collections4.get().toString())
            dependency(bt4kLibrary("commons-compress").get().toString())
            dependency("commons-codec:commons-codec:${bt4kVersion("commons-codec")}")
            dependency("org.apache.commons:commons-csv:${bt4kVersion("commons-csv")}")
            dependency(bt4kLibrary("commons-lang3").get().toString())
            dependency("commons-logging:commons-logging:${bt4kVersion("commons-logging")}")
            dependency(rootBt4k.commons.math3.get().toString())
            dependency("org.apache.commons:commons-pool2:${bt4kVersion("commons-pool2")}")
            dependency(rootBt4k.commons.text.get().toString())
            dependency("org.apache.commons:commons-exec:${bt4kVersion("commons-exec")}")
            dependency("commons-io:commons-io:${bt4kVersion("commons-io")}")

            dependency("org.slf4j:slf4j-api:${bt4kVersion("slf4j")}")
            dependency("org.slf4j:jcl-over-slf4j:${bt4kVersion("slf4j")}")
            dependency("org.slf4j:jul-to-slf4j:${bt4kVersion("slf4j")}")
            dependency("org.slf4j:log4j-over-slf4j:${bt4kVersion("slf4j")}")
            dependency(rootBt4k.logback.asProvider().get().toString())
            dependency(rootBt4k.logback.core.get().toString())

            // jakarta
            dependency(bt4kLibrary("jakarta-activation-api").get().toString())
            dependency(rootBt4k.jakarta.annotation.api.get().toString())
            dependency(rootBt4k.jakarta.el.api.get().toString())
            dependency(rootBt4k.jakarta.inject.api.get().toString())
            dependency(rootBt4k.jakarta.interceptor.api.get().toString())
            dependency(rootBt4k.jakarta.jms.api.get().toString())
            dependency(rootBt4k.jakarta.json.api.get().toString())
            dependency(rootBt4k.jakarta.json.impl.get().toString())
            dependency(rootBt4k.jakarta.persistence.v32.get().toString())
            dependency(rootBt4k.jakarta.servlet.api.get().toString())
            dependency(rootBt4k.jakarta.transaction.api.get().toString())
            dependency(rootBt4k.jakarta.validation.api.get().toString())
            dependency(rootBt4k.jakarta.ws.rs.api.get().toString())
            dependency("jakarta.xml.bind:jakarta.xml.bind-api:${bt4kVersion("jakarta-xml-bind")}")

            // Jackson
            dependency("com.fasterxml.jackson.core:jackson-annotations:${bt4kVersion("jackson-annotations")}")
            dependency("com.fasterxml.jackson.core:jackson-core:${bt4kVersion("jackson")}")
            dependency("tools.jackson.core:jackson-core:${bt4kVersion("jackson3")}")

            // Compressor
            dependency(rootBt4k.snappy.java.get().toString())
            dependency(rootBt4k.at.yawk.lz4.java.get().toString())
            dependency("com.github.luben:zstd-jni:${bt4kVersion("zstd-jni")}")

            dependency(rootBt4k.findbugs.get().toString())
            dependency("com.google.guava:guava:${bt4kVersion("guava")}")

            dependency(rootBt4k.kryo5.get().toString())
            dependency("org.apache.fory:fory-kotlin:${bt4kVersion("fory-kotlin")}")

            dependency(rootBt4k.caffeine.core.get().toString())
            dependency(rootBt4k.caffeine.jcache.get().toString())

            dependency(rootBt4k.objenesis.get().toString())
            dependency("org.ow2.asm:asm:${bt4kVersion("ow2-asm")}")

            dependency("com.esotericsoftware:reflectasm:${bt4kVersion("reflectasm")}")

            dependency(rootBt4k.junit.bom.get().toString())
            dependency(rootBt4k.junit.jupiter.all.get().toString())
            dependency(rootBt4k.junit.jupiter.api.get().toString())
            dependency(rootBt4k.junit.jupiter.engine.get().toString())
            dependency(rootBt4k.junit.jupiter.migrationsupport.get().toString())
            dependency(rootBt4k.junit.jupiter.params.get().toString())
            dependency(rootBt4k.junit.platform.commons.get().toString())
            dependency(rootBt4k.junit.platform.engine.get().toString())
            dependency(rootBt4k.junit.platform.launcher.get().toString())
            dependency(rootBt4k.junit.platform6.runner.get().toString())

            dependency("org.assertj:assertj-core:${bt4kVersion("assertj-core")}")

            dependency(rootBt4k.mockk.get().toString())
            dependency(rootBt4k.datafaker.get().toString())
            dependency("io.github.benas:random-beans:${bt4kVersion("random-beans")}")

            dependency(rootBt4k.jsonpath.v3.get().toString())
            dependency(rootBt4k.jsonassert.v2.get().toString())

        }
    }

    dependencies {
        add("compileOnly", platform(bt4kLibrary("bluetape4k-bom")))
        add("compileOnly", platform(rootLibs.jackson.bom))
        add("compileOnly", platform(rootLibs.kotlinx.coroutines.bom))

        add("implementation", rootLibs.kotlin.stdlib)
        add("implementation", rootLibs.kotlin.reflect)
        add("testImplementation", rootLibs.kotlin.test.api)
        add("testImplementation", rootLibs.kotlin.test.junit5)

        add("implementation", rootLibs.kotlinx.coroutines.core.lib)
        add("implementation", rootBt4k.kotlinx.atomicfu)

        add("implementation", bt4kLibrary("slf4j-api"))
        add("implementation", bt4kLibrary("bluetape4k-logging"))
        add("implementation", rootBt4k.logback.asProvider())
        add("testImplementation", rootLibs.jcl.over.slf4j)
        add("testImplementation", rootLibs.jul.to.slf4j)
        add("testImplementation", rootLibs.log4j.over.slf4j)

        // JUnit 5
        add("testImplementation", bt4kLibrary("bluetape4k-junit5"))
        add("testImplementation", rootBt4k.junit.jupiter.all)
        add("testRuntimeOnly", rootBt4k.junit.platform.engine)

        add("testImplementation", rootBt4k.mockk)
        add("testImplementation", "org.awaitility:awaitility-kotlin:${bt4kVersion("awaitility")}")

        add("testImplementation", rootBt4k.datafaker)
        add("testImplementation", "io.github.benas:random-beans:${bt4kVersion("random-beans")}")
    }

    /*
        1. mavenLocal 에 publish 시에는 ./gradlew publishBluetapeGraphPublicationToMavenLocalRepository 를 수행
        2. Maven Central 배포:
        ```bash
        $ ./gradlew clean build
        $ ./gradlew publishAggregationToCentralPortal
        ```
        */
    if (!isNonPublishedModule()) {
        publishing {
            publications {
                create<MavenPublication>("BluetapeGraph") {
                    val binaryJar = components["java"]

                    val sourcesJar = tasks.register<Jar>("sourcesJar") {
                        archiveClassifier.set("sources")
                        from(sourceSets["main"].allSource)
                    }

                    val javadocJar = tasks.register<Jar>("javadocJar") {
                        archiveClassifier.set("javadoc")
                        val javadocDir = layout.buildDirectory.asFile.get().resolve("javadoc")
                        from(javadocDir.path)
                    }

                    from(binaryJar)
                    artifact(sourcesJar)
                    artifact(javadocJar)

                    pom {
                        applyBluetape4kPomMetadata(
                            artifactDisplayName = project.name,
                            artifactDescription = "Bluetape4k Graph Library for Kotlin",
                        )
                    }
                }
            }
            repositories {
                centralSnapshotsRepository(project)
                mavenLocal()
            }
        }

        configurePublishingSigning(
            publicationName = "BluetapeGraph",
            enabled = true,
        )
    }

    tasks.withType<GenerateMavenPom>().configureEach {
        notCompatibleWithConfigurationCache("publishing tasks are not cache-safe")
    }
    tasks.withType<PublishToMavenRepository>().configureEach {
        notCompatibleWithConfigurationCache("publishing tasks are not cache-safe")
        if (repository.name == "nmcp") {
            repository.url = uri(layout.buildDirectory.dir("nmcp/m2"))
        }
    }
    tasks.withType<PublishToMavenLocal>().configureEach {
        notCompatibleWithConfigurationCache("publishing tasks are not cache-safe")
    }
    tasks.matching { it.name.endsWith("ToNmcpRepository") }.configureEach {
        outputs.upToDateWhen { false }
    }
}

// Maven Central Portal 집계 배포 설정
val publishableProjects = subprojects.filterNot { project ->
    project.isNonPublishedModule()
}

extensions.configure<NmcpAggregationExtension>("nmcpAggregation") {
    centralPortal {
        username.set(centralUser)
        password.set(centralPassword)
        publishingType.set("AUTOMATIC")
        uploadSnapshotsParallelism.set(centralSnapshotsParallelism)
    }
}

dependencies {
    publishableProjects.forEach { publishableProject ->
        add("nmcpAggregation", project(publishableProject.path))
    }
}

val manualModuleInventory = subprojects
    .map { subproject ->
        val sourceDir = rootProject.projectDir.toPath()
            .relativize(subproject.projectDir.toPath())
            .toString()
            .replace(File.separatorChar, '/')
        val kind = when {
            sourceDir.startsWith("benchmark/") -> "benchmark"
            sourceDir.startsWith("examples/") -> "example"
            else -> "library"
        }
        linkedMapOf(
            "gradlePath" to subproject.path,
            "projectName" to subproject.name,
            "sourceDir" to sourceDir,
            "kind" to kind,
        )
    }
    .sortedBy { it.getValue("gradlePath") }

tasks.register("exportManualModuleInventory") {
    group = "documentation"
    description = "Exports the deterministic manual project inventory."
    val outputFile = layout.buildDirectory.file("manual/module-inventory.json")
    outputs.file(outputFile)
    inputs.property("manualModuleInventoryJson", JsonOutput.prettyPrint(JsonOutput.toJson(manualModuleInventory)) + "\n")

    doLast {
        val target = outputs.files.singleFile
        target.parentFile.mkdirs()
        target.writeText(inputs.properties.getValue("manualModuleInventoryJson").toString())
    }
}

// ─── Kover 집계 설정 ────────────────────────────────────────────────────
// 루트에서 커버리지 측정 대상 서브모듈을 `kover` 의존성으로 등록하면
// `./gradlew koverXmlReport` / `koverHtmlReport` 실행 시 집계 리포트를 생성한다.
dependencies {
    subprojects
        .filter { sub ->
            sub.name != "bluetape4k-graph-bom" &&
                !sub.path.contains("examples") &&
                !sub.path.contains("benchmark")
        }
        .forEach { sub -> kover(project(sub.path)) }
}
