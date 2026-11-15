package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.LikedGame
import kotlin.math.sqrt

/**
 * Computes on-device taste affinity scores using weighted Cosine Similarity over Steam community
 * micro-tags, developers, Nintendo categories, publishers, and extracted subgenre keywords,
 * optionally blended with Gemini semantic rerank scores.
 */
object RecommendationEngine {

    private val SUBGENRE_PATTERNS: List<Pair<String, Regex>> = listOf(
        "Roguelite" to Regex("\\brogueli(?:te|ke)|stile rogue\\b", RegexOption.IGNORE_CASE),
        "Metroidvania" to Regex("\\bmetroidvania\\b", RegexOption.IGNORE_CASE),
        "Bullet Hell" to Regex("\\bbullet\\s*hell|inferno di proiettili|survivor", RegexOption.IGNORE_CASE),
        "Deckbuilder" to Regex("\\bdeckbuild|carte\\b", RegexOption.IGNORE_CASE),
        "Souls-like" to Regex("\\bsouls[- ]?like\\b", RegexOption.IGNORE_CASE),
        "Survival Horror" to Regex("\\bsurvival horror|horror\\b", RegexOption.IGNORE_CASE),
        "Strategia a turni" to Regex("\\ba turni|turn[- ]based|tattic", RegexOption.IGNORE_CASE),
        "Platform" to Regex("\\bplatform\\b", RegexOption.IGNORE_CASE),
        "JRPG" to Regex("\\bjrpg|giochi? di ruolo|\\brpg\\b", RegexOption.IGNORE_CASE),
        "Cozy / Sim" to Regex("\\bcozy|fattoria|farming|life sim\\b", RegexOption.IGNORE_CASE),
        "Puzzle" to Regex("\\bpuzzle|rompicapo\\b", RegexOption.IGNORE_CASE)
    )

    private val CATEGORY_LABELS: Map<String, String> = mapOf(
        "action" to "Azione",
        "arcade" to "Arcade",
        "shooter" to "Sparatutto",
        "rpg" to "GDR",
        "adventure" to "Avventura",
        "strategy" to "Strategia",
        "platformer" to "Platform",
        "puzzle" to "Puzzle",
        "racing" to "Corse",
        "simulation" to "Simulazione",
        "fighting" to "Picchiaduro",
        "indie" to "Indie"
    )

    fun applyRecommendations(
        deals: List<Deal>,
        settings: FilterSettings
    ): List<Deal> {
        val liked = settings.likedGames
        val weightRatio = if (settings.forYouOnlyMode) {
            1.0
        } else {
            settings.recommendationWeight.coerceIn(0, 100) / 100.0
        }

        if (liked.isEmpty() || weightRatio <= 0.0) {
            return deals.map { deal ->
                deal.copy(
                    personalScore = deal.hotScore,
                    matchReason = null
                )
            }
        }

        val likedVectors = liked.map { game ->
            game to buildFeatureVector(
                steamTagIds = game.steamTagIds,
                steamTagNames = game.steamTagNames,
                developer = game.developer,
                publisher = game.publisher,
                categories = game.gameCategories,
                title = game.title,
                excerpt = game.excerpt
            )
        }
        val profileVector = mergeVectors(likedVectors.map { it.second.weights })
        val likedNormTitles = liked.map { DealTitleNormalizer.normalize(it.title) }.toSet()

        val maxBoost = if (settings.forYouOnlyMode) 56.0 else 28.0

        return deals.map { deal ->
            val dealFeatures = buildFeatureVector(
                steamTagIds = deal.steamTagIds,
                steamTagNames = deal.steamTagNames,
                developer = deal.developer,
                publisher = deal.publisher,
                categories = deal.gameCategories,
                title = deal.title,
                excerpt = deal.excerpt
            )

            val cosine = cosineSimilarity(profileVector, dealFeatures.weights)
                .coerceIn(0.0, 1.0)
            val semanticRatio = deal.aiAffinityScore?.let { (it / 100.0).coerceIn(0.0, 1.0) }
            val combinedAffinity = if (semanticRatio != null) {
                semanticRatio * 0.70 + cosine * 0.30
            } else {
                cosine
            }

            // Anti-shovelware guardrail: unverified low-acclaim titles cannot ride tag overlap to the top.
            val isVerifiedQuality = deal.steamReviewCount >= 300 ||
                deal.chartRank != null ||
                deal.publisher.lowercase().contains("nintendo")
            val qualityScale = if (isVerifiedQuality) 1.0 else 0.25

            val affinityBoost = combinedAffinity * maxBoost * weightRatio * qualityScale
            val isExactLiked = DealTitleNormalizer.normalize(deal.title) in likedNormTitles

            val reason = when {
                isExactLiked -> "Tra i tuoi preferiti ♥"
                !isVerifiedQuality || combinedAffinity < 0.14 -> null
                !deal.aiReason.isNullOrBlank() -> deal.aiReason
                else -> buildLocalReason(
                    likedVectors = likedVectors,
                    dealFeatures = dealFeatures
                )
            }

            val baseScore = if (deal.hotScore > 0.0) deal.hotScore else (50.0 - deal.popularityRank.coerceAtMost(49)).coerceAtLeast(1.0)
            deal.copy(
                personalScore = baseScore + affinityBoost,
                matchReason = reason
            )
        }
    }

    /**
     * Picks up to 3 top Nintendo eShop categories from the user's liked games so the fetch step
     * can also pull niche discounted matches that sit deeper in global popularity.
     */
    fun topLikedCategories(likedGames: List<LikedGame>, limit: Int = 3): List<String> {
        if (likedGames.isEmpty()) return emptyList()
        val counts = HashMap<String, Int>()
        likedGames.forEach { game ->
            game.gameCategories.forEach { cat ->
                val clean = cat.trim().lowercase()
                if (clean.isNotBlank()) {
                    counts[clean] = (counts[clean] ?: 0) + 1
                }
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    private fun buildLocalReason(
        likedVectors: List<Pair<LikedGame, FeatureBundle>>,
        dealFeatures: FeatureBundle
    ): String? {
        var bestLiked: LikedGame? = null
        var bestSim = 0.0
        var bestBundle: FeatureBundle? = null

        likedVectors.forEach { (likedGame, bundle) ->
            val sim = cosineSimilarity(bundle.weights, dealFeatures.weights)
            if (sim > bestSim) {
                bestSim = sim
                bestLiked = likedGame
                bestBundle = bundle
            }
        }

        val anchor = bestLiked ?: return null
        val sharedLabels = bestBundle?.labels.orEmpty()
            .filterKeys { it in dealFeatures.weights }
            .entries
            .sortedByDescending { (dealFeatures.weights[it.key] ?: 0.0) }
            .map { it.value }
            .distinct()
            .take(2)

        val shortAnchorTitle = anchor.title
            .replace(Regex("\\s*[–\\-]\\s*Nintendo Switch.*$", RegexOption.IGNORE_CASE), "")
            .take(22)
            .trim()

        return if (sharedLabels.isNotEmpty()) {
            "Simile a $shortAnchorTitle · ${sharedLabels.joinToString(", ")}"
        } else {
            "Affine a $shortAnchorTitle"
        }
    }

    private fun buildFeatureVector(
        steamTagIds: List<Int>,
        steamTagNames: List<String>,
        developer: String?,
        publisher: String,
        categories: List<String>,
        title: String,
        excerpt: String?
    ): FeatureBundle {
        val weights = HashMap<String, Double>()
        val labels = HashMap<String, String>()

        steamTagIds.forEachIndexed { index, tagId ->
            val key = "tag:$tagId"
            val w = (3.2 - index * 0.16).coerceAtLeast(1.4)
            weights[key] = w
            steamTagNames.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { name ->
                labels[key] = name
            }
        }

        val normDev = developer?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (normDev != null) {
            val key = "dev:$normDev"
            weights[key] = 2.8
            labels[key] = developer.trim()
        }

        val normPub = publisher.trim().lowercase().takeIf { it.isNotBlank() && it != "nintendo eshop" }
        if (normPub != null) {
            val key = "pub:$normPub"
            weights[key] = 1.1
        }

        categories.forEach { rawCat ->
            val clean = rawCat.trim().lowercase()
            if (clean.isNotBlank()) {
                val key = "cat:$clean"
                weights[key] = 1.25
                labels[key] = CATEGORY_LABELS[clean] ?: clean.replaceFirstChar { it.uppercase() }
            }
        }

        val combinedText = "$title ${excerpt.orEmpty()}"
        SUBGENRE_PATTERNS.forEach { (label, regex) ->
            if (regex.containsMatchIn(combinedText)) {
                val key = "kw:$label"
                weights[key] = 2.2
                labels[key] = label
            }
        }

        return FeatureBundle(weights = weights, labels = labels)
    }

    private fun mergeVectors(vectors: List<Map<String, Double>>): Map<String, Double> {
        if (vectors.isEmpty()) return emptyMap()
        val merged = HashMap<String, Double>()
        vectors.forEach { vec ->
            vec.forEach { (k, v) ->
                merged[k] = (merged[k] ?: 0.0) + v
            }
        }
        return merged
    }

    private fun cosineSimilarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0

        a.forEach { (k, v) ->
            normA += v * v
            b[k]?.let { bv -> dot += v * bv }
        }
        b.values.forEach { v -> normB += v * v }

        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0.0) dot / denom else 0.0
    }

    private data class FeatureBundle(
        val weights: Map<String, Double>,
        val labels: Map<String, String>
    )
}
