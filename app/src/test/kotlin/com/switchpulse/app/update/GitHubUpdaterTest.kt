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
