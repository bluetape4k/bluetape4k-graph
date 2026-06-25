import dev.detekt.gradle.DetektCreateBaselineTask
import io.bluetape4k.gradle.applyBluetape4kPomMetadata
import io.bluetape4k.gradle.centralSnapshotsRepository
import io.bluetape4k.gradle.configurePublishingSigning
import io.bluetape4k.gradle.resolveCentralPublishingConfig
import io.bluetape4k.gradle.resolvePublishingSigningConfig
import nmcp.NmcpAggregationExtension
import nmcp.NmcpExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    base
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.jvm)

    // see: https://kotlinlang.org/docs/reference/compiler-plugins.html
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.allopen) apply false
    alias(libs.plugins.kotlin.noarg) apply false
    alias(libs.plugins.kotlin.jpa) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlinx.atomicfu)

    alias(libs.plugins.detekt)

    alias(libs.plugins.dependency.management)

    alias(libs.plugins.dokka)
    alias(libs.plugins.test.logger)
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.gatling) apply false

    alias(libs.plugins.nmcp.aggregation)
    alias(libs.plugins.nmcp) apply false

    // 테스트 커버리지 (Kotlin inline/suspend 정확 지원)
    alias(libs.plugins.kover)
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
val detektSupportedKotlinVersion = "2.3.21"
val bt4kCatalog = extensions.getByType<org.gradle.api.artifacts.VersionCatalogsExtension>().named("bt4k")
fun bt4kVersion(alias: String): String {
    val version = bt4kCatalog.findVersion(alias).get()
    return version.requiredVersion
        .ifBlank { version.preferredVersion }
        .ifBlank { version.strictVersion }
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
                    because("detekt 2.0.0-alpha.3 is built and validated with Kotlin 2.3.21")
                }
            }
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            languageVersion.set(KotlinVersion.KOTLIN_2_3)
            apiVersion.set(KotlinVersion.KOTLIN_2_3)
            freeCompilerArgs = listOf(
                "-Xjsr305=strict",
                "-jvm-default=enable",
                // "-Xinline-classes",   // Kotlin 2.+ 에서는 불필요
                "-Xstring-concat=indy",
                "-Xcontext-parameters",
                "-Xannotation-default-target=param-property",
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
            mavenBom(rootLibs.bluetape4k.bom.get().toString())
            mavenBom(rootLibs.spring.boot4.dependencies.get().toString())

            mavenBom(rootLibs.feign.bom.get().toString())
            mavenBom(rootLibs.micrometer.bom.get().toString())
            mavenBom(rootLibs.micrometer.tracing.bom.get().toString())
            mavenBom(rootLibs.log4j.bom.get().toString())
            mavenBom(rootLibs.testcontainers.bom.get().toString())
            mavenBom(rootLibs.junit.bom.get().toString())
            mavenBom(rootLibs.okhttp3.bom.get().toString())
            mavenBom(rootLibs.netty.bom.get().toString())
            mavenBom(rootLibs.jackson.bom.get().toString())
            mavenBom(rootLibs.jackson3.bom.get().toString())
            mavenBom(rootLibs.neo4j.bolt.connection.bom.get().toString())

            mavenBom(rootLibs.kotlinx.coroutines.bom.get().toString())
            mavenBom(rootLibs.kotlin.bom.get().toString())
        }
        dependencies {
            dependency("org.postgresql:postgresql:${bt4kVersion("postgresql")}")
            dependency(rootLibs.jetbrains.annotations.get().toString())

            dependency(rootLibs.kotlinx.coroutines.bom.get().toString())
            dependency(rootLibs.kotlinx.coroutines.core.lib.get().toString())
            dependency(rootLibs.kotlinx.coroutines.core.jvm.get().toString())
            dependency(rootLibs.kotlinx.coroutines.reactive.get().toString())
            dependency(rootLibs.kotlinx.coroutines.reactor.get().toString())
            dependency(rootLibs.kotlinx.coroutines.slf4j.get().toString())
            dependency(rootLibs.kotlinx.coroutines.debug.get().toString())
            dependency(rootLibs.kotlinx.coroutines.test.lib.get().toString())
            dependency(rootLibs.kotlinx.coroutines.test.jvm.get().toString())

            // Apache Commons
            dependency(rootLibs.commons.beanutils.get().toString())
            dependency(rootLibs.commons.collections4.get().toString())
            dependency(rootLibs.commons.compress.get().toString())
            dependency("commons-codec:commons-codec:${bt4kVersion("commons-codec")}")
            dependency("org.apache.commons:commons-csv:${bt4kVersion("commons-csv")}")
            dependency(rootLibs.commons.lang3.get().toString())
            dependency("commons-logging:commons-logging:${bt4kVersion("commons-logging")}")
            dependency(rootLibs.commons.math3.get().toString())
            dependency("org.apache.commons:commons-pool2:${bt4kVersion("commons-pool2")}")
            dependency(rootLibs.commons.text.get().toString())
            dependency("org.apache.commons:commons-exec:${bt4kVersion("commons-exec")}")
            dependency("commons-io:commons-io:${bt4kVersion("commons-io")}")

            dependency("org.slf4j:slf4j-api:${bt4kVersion("slf4j")}")
            dependency(rootLibs.jcl.over.slf4j.get().toString())
            dependency(rootLibs.jul.to.slf4j.get().toString())
            dependency(rootLibs.log4j.over.slf4j.get().toString())
            dependency(rootLibs.logback.classic.get().toString())
            dependency(rootLibs.logback.core.get().toString())

            // jakarta
            dependency(rootLibs.jakarta.activation.api.get().toString())
            dependency(rootLibs.jakarta.annotation.api.get().toString())
            dependency(rootLibs.jakarta.el.api.get().toString())
            dependency(rootLibs.jakarta.inject.api.get().toString())
            dependency(rootLibs.jakarta.interceptor.api.get().toString())
            dependency(rootLibs.jakarta.jms.api.get().toString())
            dependency(rootLibs.jakarta.json.api.get().toString())
            dependency(rootLibs.jakarta.json.impl.get().toString())
            dependency(rootLibs.jakarta.persistence.api.get().toString())
            dependency(rootLibs.jakarta.servlet.api.get().toString())
            dependency(rootLibs.jakarta.transaction.api.get().toString())
            dependency(rootLibs.jakarta.validation.api.get().toString())
            dependency(rootLibs.jakarta.ws.rs.api.get().toString())
            dependency("jakarta.xml.bind:jakarta.xml.bind-api:${bt4kVersion("jakarta-xml-bind")}")

            // Jackson
            dependency("com.fasterxml.jackson.core:jackson-annotations:${bt4kVersion("jackson-annotations")}")
            dependency(rootLibs.jackson.core.get().toString())
            dependency(rootLibs.jackson3.core.get().toString())

            // Compressor
            dependency(rootLibs.snappy.java.get().toString())
            dependency(rootLibs.lz4.java.get().toString())
            dependency("com.github.luben:zstd-jni:${bt4kVersion("zstd-jni")}")

            dependency(rootLibs.findbugs.get().toString())
            dependency("com.google.guava:guava:${bt4kVersion("guava")}")

            dependency(rootLibs.kryo5.get().toString())
            dependency("org.apache.fory:fory-kotlin:${bt4kVersion("fory-kotlin")}")

            dependency(rootLibs.caffeine.core.get().toString())
            dependency(rootLibs.caffeine.jcache.get().toString())

            dependency(rootLibs.objenesis.get().toString())
            dependency("org.ow2.asm:asm:${bt4kVersion("ow2-asm")}")

            dependency(rootLibs.reflectasm.get().toString())

            dependency(rootLibs.junit.bom.get().toString())
            dependency(rootLibs.junit.jupiter.all.get().toString())
            dependency(rootLibs.junit.jupiter.api.get().toString())
            dependency(rootLibs.junit.jupiter.engine.get().toString())
            dependency(rootLibs.junit.jupiter.migrationsupport.get().toString())
            dependency(rootLibs.junit.jupiter.params.get().toString())
            dependency(rootLibs.junit.platform.commons.get().toString())
            dependency(rootLibs.junit.platform.engine.get().toString())
            dependency(rootLibs.junit.platform.launcher.get().toString())
            dependency(rootLibs.junit.platform.runner.get().toString())

            dependency(rootLibs.assertj.core.get().toString())

            dependency(rootLibs.mockk.get().toString())
            dependency(rootLibs.datafaker.get().toString())
            dependency(rootLibs.random.beans.get().toString())

            dependency(rootLibs.jsonpath.get().toString())
            dependency(rootLibs.jsonassert.get().toString())

        }
    }

    dependencies {
        add("compileOnly", platform(rootLibs.bluetape4k.bom))
        add("compileOnly", platform(rootLibs.jackson.bom))
        add("compileOnly", platform(rootLibs.kotlinx.coroutines.bom))

        add("implementation", rootLibs.kotlin.stdlib)
        add("implementation", rootLibs.kotlin.reflect)
        add("testImplementation", rootLibs.kotlin.test.api)
        add("testImplementation", rootLibs.kotlin.test.junit5)

        add("implementation", rootLibs.kotlinx.coroutines.core.lib)
        add("implementation", rootLibs.kotlinx.atomicfu)

        add("implementation", rootLibs.slf4j.api)
        add("implementation", rootLibs.bluetape4k.logging)
        add("implementation", rootLibs.logback.classic)
        add("testImplementation", rootLibs.jcl.over.slf4j)
        add("testImplementation", rootLibs.jul.to.slf4j)
        add("testImplementation", rootLibs.log4j.over.slf4j)

        // JUnit 5
        add("testImplementation", rootLibs.bluetape4k.junit5)
        add("testImplementation", rootLibs.junit.jupiter.all)
        add("testRuntimeOnly", rootLibs.junit.platform.engine)

        add("testImplementation", rootLibs.mockk)
        add("testImplementation", rootLibs.awaitility.kotlin)

        add("testImplementation", rootLibs.datafaker)
        add("testImplementation", rootLibs.random.beans)
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
