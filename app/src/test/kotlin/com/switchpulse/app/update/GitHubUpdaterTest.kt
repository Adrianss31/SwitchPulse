package com.switchpulse.app.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class GitHubUpdaterTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `version comparison handles semver tags and single build numbers`() {
        assertTrue(GitHubUpdater.isRemoteNewer("v1.0.2", "1.0.0", 1))
        assertTrue(GitHubUpdater.isRemoteNewer("v1.1.0", "1.0.9", 9))
        assertFalse(GitHubUpdater.isRemoteNewer("v1.0.0", "1.0.0", 1))
        assertFalse(GitHubUpdater.isRemoteNewer("v0.9.9", "1.0.0", 1))
        assertTrue(GitHubUpdater.isRemoteNewer("build-5", "1.0.0", 2))
    }

    @Test
    fun `checkLatestRelease detects newer release with apk asset`() = runTest {
        val json = """
            {
              "tag_name": "v1.0.5",
              "name": "SwitchPulse 1.0.5",
              "body": "Migliorato widget Editorial Bento",
              "html_url": "https://github.com/user/SwitchPulse/releases/tag/v1.0.5",
              "assets": [
                {
                  "name": "SwitchPulse-v1.0.5.apk",
                  "size": 4500000,
                  "browser_download_url": "https://github.com/user/SwitchPulse/releases/download/v1.0.5/SwitchPulse-v1.0.5.apk"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val updater = GitHubUpdater(OkHttpClient())
        val result = updater.checkLatestRelease(
            repoSlug = "user/SwitchPulse",
            currentVersionName = "1.0.0",
            currentVersionCode = 1,
            overrideEndpointUrl = server.url("/releases/latest").toString()
        )

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val release = (result as UpdateCheckResult.UpdateAvailable).release
        assertEquals("v1.0.5", release.tagName)
        assertEquals("SwitchPulse-v1.0.5.apk", release.apkFileName)
    }

    @Test
    fun `checkLatestRelease parses sha256 digest and verifies apk integrity`() = runTest {
        val fakeApk = "PK-fake-apk-bytes".toByteArray()
        val digestHex = MessageDigest.getInstance("SHA-256")
            .digest(fakeApk)
            .joinToString("") { "%02x".format(it) }

        val json = """
            {
              "tag_name": "v1.0.5",
              "name": "SwitchPulse 1.0.5",
              "assets": [
                {
                  "name": "SwitchPulse-v1.0.5.apk",
                  "size": ${fakeApk.size},
                  "digest": "sha256:$digestHex",
                  "browser_download_url": "${server.url("/download/SwitchPulse-v1.0.5.apk")}"
                }
              ]
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))
        server.enqueue(MockResponse().setResponseCode(200).setBody(String(fakeApk)))
        server.enqueue(MockResponse().setResponseCode(200).setBody(String(fakeApk)))

        val updater = GitHubUpdater(OkHttpClient())
        val checkResult = updater.checkLatestRelease(
            repoSlug = "user/SwitchPulse",
            currentVersionName = "1.0.0",
            currentVersionCode = 1,
            overrideEndpointUrl = server.url("/releases/latest").toString()
        ) as UpdateCheckResult.UpdateAvailable

        assertEquals(digestHex, checkResult.release.apkSha256)

        val tempDir = File(System.getProperty("java.io.tmpdir"), "sp-updater-test")
        tempDir.deleteRecursively()
        try {
            val okTarget = File(tempDir, "ok.apk")
            val downloaded = updater.downloadToFile(
                downloadUrl = checkResult.release.apkDownloadUrl,
                targetFile = okTarget,
                expectedSha256 = checkResult.release.apkSha256
            )
            assertTrue(downloaded.exists())
            assertEquals(String(fakeApk), downloaded.readText())

            val badTarget = File(tempDir, "bad.apk")
            val badShaResult = runCatching {
                updater.downloadToFile(
                    downloadUrl = checkResult.release.apkDownloadUrl,
                    targetFile = badTarget,
                    expectedSha256 = "0".repeat(64)
                )
            }
            assertTrue(badShaResult.isFailure)
            assertFalse(badTarget.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `checkLatestRelease handles missing apk asset`() = runTest {
        val json = """
            {
              "tag_name": "v1.0.5",
              "name": "SwitchPulse 1.0.5",
              "assets": []
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val updater = GitHubUpdater(OkHttpClient())
        val result = updater.checkLatestRelease(
            repoSlug = "user/SwitchPulse",
            currentVersionName = "1.0.0",
            currentVersionCode = 1,
            overrideEndpointUrl = server.url("/releases/latest").toString()
        )
        assertTrue(result is UpdateCheckResult.NoApkAsset)
    }
}
