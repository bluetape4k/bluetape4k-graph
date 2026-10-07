package io.bluetape4k.gradle

import java.util.Base64
import groovy.util.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.kotlin.dsl.getByType
import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublishingSigningSupportTest {

    @Test
    fun `normalizeSigningKeyId keeps short key ID without warning`() {
        val normalized = normalizeSigningKeyId("5C6DF399")

        assertEquals("5C6DF399", normalized.value)
        assertNull(normalized.warning)
    }

    @Test
    fun `normalizeSigningKeyId returns warning for long key ID`() {
        val normalized = normalizeSigningKeyId("7CF28E155C6DF399")

        assertEquals("5C6DF399", normalized.value)
        assertEquals(
            "Signing key ID used a 16-digit hexadecimal form; normalized to the trailing 8 digits.",
            normalized.warning,
        )
    }

    @Test
    fun `resolveSigningKey decodes escaped private key armor`() {
        val privateKey = privateKeyArmor()

        assertEquals(privateKey, resolveSigningKey(privateKey.replace("\n", "\\n")))
    }

    @Test
    fun `resolveSigningKey decodes base64 private key armor`() {
        val privateKey = privateKeyArmor()
        val encoded = Base64.getEncoder().encodeToString(privateKey.toByteArray())

        assertEquals(privateKey, resolveSigningKey(encoded))
    }

    @Test
    fun `resolveSigningKey preserves raw private key armor`() {
        val privateKey = privateKeyArmor()

        assertEquals(privateKey, resolveSigningKey(privateKey))
    }

    @Test
    fun `resolvePublishingSigningConfig preserves gpg fallback and API`() {
        val project = ProjectBuilder.builder().build()
        project.extensions.extraProperties.set("signingKeyId", "7CF28E155C6DF399")
        project.extensions.extraProperties.set("signingUseGpgCmd", "true")
        project.extensions.extraProperties.set("signing.gnupg.executable", "/custom/bin/gpg")
        project.extensions.extraProperties.set("signing.gnupg.keyName", "release-key")

        val config = project.resolvePublishingSigningConfig()
        val legacyShape = PublishingSigningConfig("id", "key", "password", false, "gpg", "name")

        assertEquals("5C6DF399", config.keyId)
        assertTrue(config.useGpgCmd)
        assertEquals("/custom/bin/gpg", config.gpgExecutable)
        assertEquals("release-key", config.gpgKeyName)
        assertEquals("id", legacyShape.keyId)
    }

    @Test
    fun `PublishingSigningConfig preserves its six-field adapter shape`() {
        assertEquals(
            6,
            PublishingSigningConfig::class.java.declaredConstructors.maxOf { it.parameterCount },
        )
    }

    @Test
    fun `configurePublishingSigning applies gpg executable and normalized key fallback`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("signing")
        val executable = project.projectDir.resolve("gpg-fixture").apply {
            writeText("fixture")
        }
        project.extensions.extraProperties.set("signingKeyId", "7CF28E155C6DF399")
        project.extensions.extraProperties.set("signingUseGpgCmd", "true")
        project.extensions.extraProperties.set("signing.gnupg.executable", executable.absolutePath)

        project.configurePublishingSigning("missing-publication")

        assertEquals(
            executable.absolutePath,
            project.extensions.extraProperties.get("signing.gnupg.executable"),
        )
        assertEquals(
            "5C6DF399",
            project.extensions.extraProperties.get("signing.gnupg.keyName"),
        )
    }

    @Test
    fun `normalizes identical dependencyManagement entries`() {
        val pom = pomWithManagedDependencies(
            groupId = "org.jetbrains.kotlin",
            artifactId = "kotlin-stdlib",
            versions = listOf("2.2.20", "2.2.20"),
        )

        normalizeMavenPomDependencyManagement(pom)

        val dependencies = pom.children()
            .filterIsInstance<groovy.util.Node>()
            .single { it.name() == "dependencyManagement" }
            .children()
            .filterIsInstance<groovy.util.Node>()
            .single { it.name() == "dependencies" }
            .children()
            .filterIsInstance<groovy.util.Node>()
            .filter { it.name() == "dependency" }

        assertEquals(1, dependencies.size)
    }

    @Test
    fun `fails when dependencyManagement entries share a coordinate but differ`() {
        val pom = pomWithManagedDependencies(
            groupId = "io.netty",
            artifactId = "netty-bom",
            versions = listOf("4.1.139.Final", "4.2.19.Final"),
            type = "pom",
            scope = "import",
        )

        val error = assertFailsWith<org.gradle.api.GradleException> {
            normalizeMavenPomDependencyManagement(pom)
        }

        assertTrue(error.message.orEmpty().contains("io.netty:netty-bom:pom"))
    }

    @Test
    fun `GenerateMavenPom actions remove identical managed entries from the generated file`() {
        val (task, pomFile) = generatedPomTask(
            groupId = "org.jetbrains.kotlin",
            artifactId = "kotlin-stdlib",
            versions = listOf("2.2.20", "2.2.20"),
        )

        task.actions.forEach { action -> action.execute(task) }

        assertEquals(1, managedDependencyCount(pomFile, "org.jetbrains.kotlin", "kotlin-stdlib"))
    }

    @Test
    fun `GenerateMavenPom actions reject conflicting managed entries`() {
        val (task, _) = generatedPomTask(
            groupId = "io.netty",
            artifactId = "netty-bom",
            versions = listOf("4.1.139.Final", "4.2.19.Final"),
            type = "pom",
            scope = "import",
        )

        val error = assertFailsWith<org.gradle.api.GradleException> {
            task.actions.forEach { action -> action.execute(task) }
        }

        assertTrue(error.message.orEmpty().contains("io.netty:netty-bom:pom"))
    }

    private fun generatedPomTask(
        groupId: String,
        artifactId: String,
        versions: List<String>,
        type: String? = null,
        scope: String? = null,
    ): Pair<GenerateMavenPom, File> {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("maven-publish")
        project.pluginManager.apply("signing")
        val publication = project.extensions.getByType<PublishingExtension>()
            .publications
            .create("test", MavenPublication::class.java)

        project.configurePublishingSigning("test")
        publication.pom.withXml {
            val dependencyManagement = asNode().appendNode("dependencyManagement") as Node
            val dependencies = dependencyManagement.appendNode("dependencies") as Node
            versions.forEach { version ->
                val dependency = dependencies.appendNode("dependency") as Node
                dependency.appendNode("groupId", groupId)
                dependency.appendNode("artifactId", artifactId)
                dependency.appendNode("version", version)
                type?.let { dependency.appendNode("type", it) }
                scope?.let { dependency.appendNode("scope", it) }
            }
        }

        val task = project.tasks.create("generateTestPom", GenerateMavenPom::class.java)
        task.pom = publication.pom
        val pomFile = File.createTempFile("graph-pom-", ".xml").apply { deleteOnExit() }
        task.destination = pomFile
        return task to pomFile
    }

    private fun managedDependencyCount(file: File, groupId: String, artifactId: String): Int {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        return (0 until document.getElementsByTagName("dependency").length)
            .map { document.getElementsByTagName("dependency").item(it) }
            .count { dependency ->
                val group = childElementText(dependency, "groupId")
                val artifact = childElementText(dependency, "artifactId")
                group == groupId && artifact == artifactId
            }
    }

    private fun childElementText(parent: org.w3c.dom.Node, name: String): String {
        for (index in 0 until parent.childNodes.length) {
            val child = parent.childNodes.item(index)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE && child.nodeName == name) {
                return child.textContent.trim()
            }
        }
        return ""
    }

    private fun pomWithManagedDependencies(
        groupId: String,
        artifactId: String,
        versions: List<String>,
        type: String? = null,
        scope: String? = null,
    ): Node {
        val pom = Node(null, "project")
        val dependencyManagement = Node(pom, "dependencyManagement")
        val dependencies = Node(dependencyManagement, "dependencies")
        versions.forEach { version ->
            val dependency = Node(dependencies, "dependency")
            Node(dependency, "groupId", groupId)
            Node(dependency, "artifactId", artifactId)
            Node(dependency, "version", version)
            type?.let { Node(dependency, "type", it) }
            scope?.let { Node(dependency, "scope", it) }
        }
        return pom
    }

    private fun privateKeyArmor(): String =
        "-----BEGIN PGP PRIVATE KEY BLOCK-----\n" +
            "Version: test\n\n" +
            "ZmFrZS1rZXk=\n" +
            "=abcd\n" +
            "-----END PGP PRIVATE KEY BLOCK-----\n"
}
