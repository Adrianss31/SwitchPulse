package com.switchpulse.app.data.local

import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.AiTaskTarget
import com.switchpulse.app.data.model.BackgroundRerankPolicy
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GamerSoulProfile
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.PlatformFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    @Test
    fun `exportToJson omits all API keys and round-trips preferences and library cleanly`() {
        val original = FilterSettings(
            minDiscountPercent = 40,
            minRegularPriceEur = 14.99f,
            appPlatformFilter = PlatformFilter.SWITCH_2,
            widgetPlatformFilter = PlatformFilter.SWITCH_1,
            syncIntervalHours = 6,
            blacklistIds = setOf("b1"),
            blacklistTitles = mapOf("b1" to "Bad Game"),
            likedGames = listOf(
                LikedGame(
                    id = "hades",
                    title = "Hades",
                    rating = 5,
                    isOwned = false,
                    targetPriceEur = 12.50,
                    userNote = "Combat perfetto"
                )
            ),
            recommendationWeight = 85,
            forYouOnlyMode = true,
            geminiApiKey = "SECRET_GEMINI_XYZ",
            openRouterApiKey = "SECRET_OPENROUTER_ABC",
            deepSeekApiKey = "SECRET_DEEPSEEK_123",
            userTastePrompt = "Amo i roguelike",
            rerankTarget = AiTaskTarget(AiProvider.OPENROUTER, "google/gemini-2.5-flash-lite"),
            backgroundRerankPolicy = BackgroundRerankPolicy.OFF,
            gamerSoulProfile = GamerSoulProfile(archetypeTitle = "Tattico Instancabile"),
            dismissedDiscoveryTitles = listOf("shovelware simulator")
        )

        val json = BackupManager.exportToJson(original)
        assertFalse(json.contains("SECRET_GEMINI_XYZ"))
        assertFalse(json.contains("SECRET_OPENROUTER_ABC"))
        assertFalse(json.contains("SECRET_DEEPSEEK_123"))

        val decoded = BackupManager.decodeFromJson(json).getOrThrow()
        assertEquals(40, decoded.minDiscountPercent)
        assertEquals(PlatformFilter.SWITCH_2, decoded.appPlatformFilter)
        assertEquals(1, decoded.likedGames.size)
        assertEquals(12.50, decoded.likedGames.first().targetPriceEur!!, 0.001)
        assertEquals(BackgroundRerankPolicy.OFF, decoded.backgroundRerankPolicy)
        assertTrue("shovelware simulator" in decoded.dismissedDiscoveryTitles)
        assertEquals("Tattico Instancabile", decoded.gamerSoulProfile?.archetypeTitle)
    }
}
