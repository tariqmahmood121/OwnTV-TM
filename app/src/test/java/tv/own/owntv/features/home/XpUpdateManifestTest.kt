package tv.own.owntv.features.home

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XpUpdateManifestTest {
    private val pkg = "com.oakstudios.movioxp"
    private val hex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private fun sample(): JSONObject = JSONObject()
        .put("app", "MovioXP")
        .put("packageName", pkg)
        .put("versionName", "3.1.0-qa1")
        .put("versionCode", 100090)
        .put("channel", "qa")
        .put("apkUrl", "https://moviotv.opticalzoo.com/downloads/qa/movioxp.apk")
        .put("apkSizeBytes", 31000000L)
        .put("sha256", hex)
        .put("releaseNotes", "Signed QA")
        .put("publishedAt", "2026-11-25T09:00:00Z")
        .put("minSupportedVersionCode", 1)

    @Test fun acceptsStrictlyMatchingQaManifest() {
        val manifest = XpUpdateManifest.parse(sample().toString(), pkg, "qa")
        assertEquals(100090L, manifest.versionCode)
        assertEquals("qa", manifest.channel)
        assertEquals(hex, manifest.sha256)
    }
    @Test fun rejectsWrongAppOrPackageOrChannel() {
        assertTrue(runCatching { XpUpdateManifest.parse(sample().put("app", "MovioTV").toString(), pkg, "qa") }.isFailure)
        assertTrue(runCatching { XpUpdateManifest.parse(sample().put("packageName", "wrong").toString(), pkg, "qa") }.isFailure)
        assertTrue(runCatching { XpUpdateManifest.parse(sample().put("channel", "stable").toString(), pkg, "qa") }.isFailure)
    }
    @Test fun rejectsHttpAndInvalidChecksumsAndSizes() {
        assertTrue(runCatching { XpUpdateManifest.parse(sample().put("apkUrl", "http://example.org/app.apk").toString(), pkg, "qa") }.isFailure)
        assertTrue(runCatching { XpUpdateManifest.parse(sample().put("sha256", "bad").toString(), pkg, "qa") }.isFailure)
        assertTrue(runCatching { XpUpdateManifest.parse(sample().put("apkSizeBytes", 0).toString(), pkg, "qa") }.isFailure)
    }
    @Test fun malformedJsonCannotMeanUpToDate() {
        assertTrue(runCatching { XpUpdateManifest.parse("{}", pkg, "qa") }.isFailure)
    }
}
