package io.bluetape4k.gradle

import groovy.util.Node
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPom
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.w3c.dom.Element

private data class ManagedDependencyKey(
    val groupId: String,
    val artifactId: String,
    val type: String,
    val classifier: String,
)

/**
 * Project property 또는 환경 변수에서 값을 조회합니다.
 */
fun Project.getEnvOrProjectProperty(propertyKey: String, envKey: String): String {
    return findProperty(propertyKey) as? String ?: System.getenv(envKey).orEmpty()
}

data class CentralPublishingConfig(
    val username: String,
    val password: String,
)

/**
 * Central Portal 자격증명을 project property / 환경 변수에서 로딩합니다.
 */
fun Project.resolveCentralPublishingConfig(): CentralPublishingConfig {
    return CentralPublishingConfig(
        username = getEnvOrProjectProperty("central.user", "CENTRAL_USERNAME"),
        password = getEnvOrProjectProperty("central.password", "CENTRAL_PASSWORD"),
    )
}

/**
 * Central Snapshots 저장소를 공통 규약으로 추가합니다.
 */
fun RepositoryHandler.centralSnapshotsRepository(
    project: Project,
    repositoryName: String = "CentralSnapshots",
    repositoryUrl: String = "https://central.sonatype.com/repository/maven-snapshots/",
) {
    val central = project.resolveCentralPublishingConfig()
    maven {
        name = repositoryName
        url = project.uri(repositoryUrl)
        mavenContent {
            snapshotsOnly()
        }
        credentials {
            username = central.username
            password = central.password
        }
    }
}

/**
 * Bluetape4k 공통 POM 메타데이터를 적용합니다.
 */
fun MavenPom.applyBluetape4kPomMetadata(
    artifactDisplayName: String,
    artifactDescription: String,
) {
    name.set(artifactDisplayName)
    description.set(artifactDescription)
    url.set("https://github.com/bluetape4k/bluetape4k-graph")
    licenses {
        license {
            name.set("The Apache License, Version 2.0")
            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
        }
    }
    developers {
        developer {
            id.set("debop")
            name.set("Sunghyouk Bae")
            email.set("sunghyouk.bae@gmail.com")
        }
    }
    scm {
        url.set("https://www.github.com/bluetape4k/bluetape4k-graph")
        connection.set("scm:git:https://www.github.com/bluetape4k/bluetape4k-graph")
        developerConnection.set("scm:git:https://www.github.com/bluetape4k/bluetape4k-graph")
    }
}

/**
 * Maven dependencyManagement에 중복으로 기록된 좌표를 정리합니다.
 *
 * Gradle의 여러 platform 선언이 같은 BOM을 POM에 반복해서 기록할 수 있습니다.
 * 완전히 같은 선언은 한 개만 남기고, 같은 좌표에 서로 다른 선언이 있으면
 * 어떤 버전을 게시할지 추측하지 않고 POM 생성을 실패시킵니다.
 */
internal fun normalizeMavenPomDependencyManagement(pomNode: Node) {
    val seen = linkedMapOf<ManagedDependencyKey, Pair<Node, String>>()

    pomNode.children()
        .filterIsInstance<Node>()
        .filter { it.name().toString() == "dependencyManagement" }
        .flatMap { dependencyManagement ->
            dependencyManagement.children()
                .filterIsInstance<Node>()
                .filter { it.name().toString() == "dependencies" }
        }
        .forEach { dependenciesNode ->
            dependenciesNode.children()
                .filterIsInstance<Node>()
                .filter { it.name().toString() == "dependency" }
                .toList()
                .forEach { dependencyNode ->
                    val key = ManagedDependencyKey(
                        groupId = childText(dependencyNode, "groupId"),
                        artifactId = childText(dependencyNode, "artifactId"),
                        type = childText(dependencyNode, "type").ifBlank { "jar" },
                        classifier = childText(dependencyNode, "classifier"),
                    )
                    val fingerprint = nodeFingerprint(dependencyNode)
                    val previous = seen[key]
                    when {
                        previous == null -> seen[key] = dependencyNode to fingerprint
                        previous.second == fingerprint -> dependenciesNode.remove(dependencyNode)
                        else -> throw GradleException(
                            "Maven dependencyManagement contains conflicting entries for " +
                                    "${key.groupId}:${key.artifactId}:${key.type}" +
                                    key.classifier.takeIf { it.isNotBlank() }?.let { ":$it" }.orEmpty(),
                        )
                    }
                }
        }
}

private fun childText(node: Node, childName: String): String =
    node.children()
        .filterIsInstance<Node>()
        .firstOrNull { it.name().toString() == childName }
        ?.text()
        ?.trim()
        .orEmpty()

private fun nodeFingerprint(node: Node): String {
    val children = node.children().filterIsInstance<Node>()
    if (children.isEmpty()) {
        return listOf(node.name().toString(), node.attributes(), node.text().trim()).joinToString("|")
    }

    return listOf(
        node.name().toString(),
        node.attributes(),
        children.sortedBy { nodeFingerprint(it) }.joinToString(";") { nodeFingerprint(it) },
    ).joinToString("|")
}

/**
 * GenerateMavenPom이 작성한 파일에도 마지막으로 동일한 dependencyManagement 정리를 적용합니다.
 *
 * Spring dependency-management 같은 플러그인은 POM XML을 다른 withXml action에서
 * 늦게 보강할 수 있으므로, 파일이 생성된 뒤에 다시 검사해야 게시 결과를 보장할 수 있습니다.
 */
internal fun normalizeMavenPomDependencyManagementFile(pomFile: File) {
    if (!pomFile.isFile) return

    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }
    val document = factory.newDocumentBuilder().parse(pomFile)
    var changed = false
    val seen = linkedMapOf<ManagedDependencyKey, Pair<Element, String>>()

    directElements(document.documentElement, "dependencyManagement")
        .flatMap { directElements(it, "dependencies") }
        .forEach { dependenciesElement ->
            directElements(dependenciesElement, "dependency")
                .toList()
                .forEach { dependencyElement ->
                    val key = ManagedDependencyKey(
                        groupId = childElementText(dependencyElement, "groupId"),
                        artifactId = childElementText(dependencyElement, "artifactId"),
                        type = childElementText(dependencyElement, "type").ifBlank { "jar" },
                        classifier = childElementText(dependencyElement, "classifier"),
                    )
                    val fingerprint = elementFingerprint(dependencyElement)
                    val previous = seen[key]
                    when {
                        previous == null -> seen[key] = dependencyElement to fingerprint
                        previous.second == fingerprint -> {
                            dependenciesElement.removeChild(dependencyElement)
                            changed = true
                        }

                        else -> throw GradleException(
                            "Maven dependencyManagement contains conflicting entries for " +
                                    "${key.groupId}:${key.artifactId}:${key.type}" +
                                    key.classifier.takeIf { it.isNotBlank() }?.let { ":$it" }.orEmpty(),
                        )
                    }
                }
        }

    if (!changed) return

    val transformer = TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.INDENT, "yes")
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
    }
    transformer.transform(DOMSource(document), StreamResult(pomFile))
}

private fun directElements(parent: Element, localName: String): List<Element> =
    (0 until parent.childNodes.length)
        .mapNotNull { index -> parent.childNodes.item(index) as? Element }
        .filter { elementLocalName(it) == localName }

private fun childElementText(parent: Element, localName: String): String =
    directElements(parent, localName).firstOrNull()?.textContent?.trim().orEmpty()

private fun elementLocalName(element: Element): String =
    element.localName ?: element.nodeName.substringAfter(':')

private fun elementFingerprint(element: Element): String {
    val children = (0 until element.childNodes.length)
        .mapNotNull { index -> element.childNodes.item(index) as? Element }
    if (children.isEmpty()) {
        return listOf(elementLocalName(element), element.attributes.asMap(), element.textContent.trim())
            .joinToString("|")
    }

    return listOf(
        elementLocalName(element),
        element.attributes.asMap(),
        children.sortedBy { elementFingerprint(it) }.joinToString(";") { elementFingerprint(it) },
    ).joinToString("|")
}

private fun org.w3c.dom.NamedNodeMap.asMap(): String =
    (0 until length)
        .map { item(it).nodeName to item(it).nodeValue }
        .sortedBy { it.first }
        .joinToString(",") { (name, value) -> "$name=$value" }

data class PublishingSigningConfig(
    val keyId: String,
    val key: String,
    val password: String,
    val useGpgCmd: Boolean,
    val gpgExecutable: String,
    val gpgKeyName: String,
)

/**
 * Publishing signing 설정을 project property / 환경 변수에서 로딩합니다.
 */
fun Project.resolvePublishingSigningConfig(): PublishingSigningConfig {
    val normalizedKeyId = normalizeSigningKeyId(getEnvOrProjectProperty("signingKeyId", "SIGNING_KEY_ID"))
    normalizedKeyId.warning?.let(logger::warn)
    val keyId = normalizedKeyId.value
    val key = resolveSigningKey(getEnvOrProjectProperty("signingKey", "SIGNING_KEY"))
    val password = getEnvOrProjectProperty("signingPassword", "SIGNING_PASSWORD")
    val useGpgCmd = getEnvOrProjectProperty("signingUseGpgCmd", "SIGNING_USE_GPG_CMD").toBoolean()
    val gpgExecutable = getEnvOrProjectProperty("signing.gnupg.executable", "GPG_EXECUTABLE")
        .ifBlank { "/opt/homebrew/bin/gpg" }
    val gpgKeyName = getEnvOrProjectProperty("signing.gnupg.keyName", "GPG_KEY_NAME")
        .ifBlank { keyId }

    return PublishingSigningConfig(
        keyId = keyId,
        key = key,
        password = password,
        useGpgCmd = useGpgCmd,
        gpgExecutable = gpgExecutable,
        gpgKeyName = gpgKeyName,
    )
}

/**
 * Maven publication 서명 설정을 공통으로 적용합니다.
 */
fun Project.configurePublishingSigning(
    publicationName: String,
    enabled: Boolean = true,
    missingKeyWarning: String = "서명 키가 없어 서명을 수행하지 않습니다. " +
            "SIGNING_KEY(+SIGNING_PASSWORD)를 우선 설정하고, 필요 시 SIGNING_USE_GPG_CMD=true를 사용하세요.",
) {
    if (!enabled) return

    val publishing = project.extensions.findByType(PublishingExtension::class.java)
    val publication = publishing?.publications?.findByName(publicationName) as? MavenPublication
    publication?.pom?.withXml {
        normalizeMavenPomDependencyManagement(asNode())
    }
    project.afterEvaluate {
        val evaluatedPublication = extensions.findByType(PublishingExtension::class.java)
            ?.publications
            ?.findByName(publicationName) as? MavenPublication
        evaluatedPublication?.pom?.withXml {
            normalizeMavenPomDependencyManagement(asNode())
        }
    }
    project.gradle.projectsEvaluated {
        val evaluatedPublication = extensions.findByType(PublishingExtension::class.java)
            ?.publications
            ?.findByName(publicationName) as? MavenPublication
        evaluatedPublication?.pom?.withXml {
            normalizeMavenPomDependencyManagement(asNode())
        }
    }
    tasks.withType<GenerateMavenPom>().configureEach {
        doLast {
            normalizeMavenPomDependencyManagementFile(destination)
        }
    }

    val config = resolvePublishingSigningConfig()
    extensions.configure<SigningExtension> {
        when {
            config.key.isNotBlank() && config.password.isNotBlank() -> {
                useInMemoryPgpKeys(config.keyId.ifBlank { null }, config.key, config.password)
            }

            config.useGpgCmd -> {
                if (file(config.gpgExecutable).exists()) {
                    project.extensions.extraProperties["signing.gnupg.executable"] = config.gpgExecutable
                }
                if (config.gpgKeyName.isNotBlank()) {
                    project.extensions.extraProperties["signing.gnupg.keyName"] = config.gpgKeyName
                }
                useGpgCmd()
            }

            config.password.isNotBlank() -> {
                project.logger.warn(missingKeyWarning)
                return@configure
            }

            else -> return@configure
        }

        if (publication != null) {
            sign(publication)
        }
    }
}
