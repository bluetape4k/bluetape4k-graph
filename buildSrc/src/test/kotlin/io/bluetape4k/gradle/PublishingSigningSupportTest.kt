package io.bluetape4k.gradle

import java.util.Base64
import org.gradle.testfixtures.ProjectBuilder
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

    private fun privateKeyArmor(): String =
        "-----BEGIN PGP PRIVATE KEY BLOCK-----\n" +
            "Version: test\n\n" +
            "ZmFrZS1rZXk=\n" +
            "=abcd\n" +
            "-----END PGP PRIVATE KEY BLOCK-----\n"
}
