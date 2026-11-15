package com.switchpulse.app.data

import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealMatchSentiment
import com.switchpulse.app.data.model.DealMatchSource
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.LikedGame
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DealAffinityBreakdown(
    val positiveMatchPercent: Int,
    val negativeMatchPercent: Int,
    val netAffinityPercent: Int,
    val sharedPositiveLabels: List<String>,
    val sharedNegativeLabels: List<String>,
    val closestLikedTitle: String?,
    val closestDislikedTitle: String?,
    val aiScore: Int?,
    val aiReason: String?,
    val aiDetailedReason: String? = null
)

data class TasteGenreBar(
    val label: String,
    val avgStars: Double,
    val percent: Int,
    val isPositive: Boolean,
    val ratedCount: Int
)

/**
 * Computes on-device taste affinity & repulsion scores using weighted Cosine Similarity over Steam
 * community micro-tags, developers, Nintendo categories, publishers, and extracted subgenres,
 * calibrated from 1-5★ ratings and optionally blended with Gemini 4 Hydrogen semantic reranking.
 */
object RecommendationEngine {

    private val GENERIC_META_TAGS = setOf(
        "singleplayer",
        "giocatore singolo",
        "controller",
        "full controller support",
        "supporto controller completo",
        "steam achievements",
        "obiettivi di steam",
        "steam cloud",
        "family sharing",
        "condivisione familiare",
        "remote play",
        "2d",
        "3d"
    )

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
        val positiveGames = settings.likedGames.filter { it.isPositiveRating }
        val negativeGames = settings.likedGames.filter { it.isNegativeRating }
        val ratedGames = settings.likedGames.filter { it.rating != null }

        val weightRatio = if (settings.forYouOnlyMode) {
            1.0
        } else {
            settings.recommendationWeight.coerceIn(0, 100) / 100.0
        }

        if ((positiveGames.isEmpty() && negativeGames.isEmpty()) || weightRatio <= 0.0) {
            return deals.map { deal ->
                deal.copy(
                    personalScore = deal.hotScore,
                    matchReason = null,
                    matchSource = DealMatchSource.NONE,
                    matchSentiment = DealMatchSentiment.NONE
                )
            }
        }

        val posVectors = positiveGames.map { game ->
            val weight = starWeight(game.rating)
            val bundle = buildFeatureVector(game)
            game to bundle.copy(weights = scaleVector(bundle.weights, weight))
        }
        val negVectors = negativeGames.map { game ->
            val weight = abs(starWeight(game.rating))
            val bundle = buildFeatureVector(game)
            game to bundle.copy(weights = scaleVector(bundle.weights, weight))
        }

        val posProfile = mergeVectors(posVectors.map { it.second.weights })
        val negProfile = mergeVectors(negVectors.map { it.second.weights })
        val ratedByNormTitle = ratedGames.associateBy { DealTitleNormalizer.normalize(it.title) }

        val maxBoost = if (settings.forYouOnlyMode) 56.0 else 28.0
        val maxPenalty = if (settings.forYouOnlyMode) 60.0 else 32.0

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

            val posSim = cosineSimilarity(posProfile, dealFeatures.weights).coerceIn(0.0, 1.0)
            val negSim = cosineSimilarity(negProfile, dealFeatures.weights).coerceIn(0.0, 1.0)
            val localAffinity = (posSim - negSim * 0.85).coerceIn(-1.0, 1.0)

            val semanticCentered = deal.aiAffinityScore?.let { ((it - 50.0) / 50.0).coerceIn(-1.0, 1.0) }
            val combinedAffinity = if (semanticCentered != null) {
                semanticCentered * 0.70 + localAffinity * 0.30
            } else {
                localAffinity
            }

            // Anti-shovelware guardrail: unverified low-acclaim titles cannot ride positive tag overlap.
            val isVerifiedQuality = deal.steamReviewCount >= 300 ||
                deal.chartRank != null ||
                deal.publisher.lowercase().contains("nintendo")
            val qualityScale = if (isVerifiedQuality) 1.0 else 0.25

            val adjustment = if (combinedAffinity >= 0.0) {
                combinedAffinity * maxBoost * weightRatio * qualityScale
            } else {
                combinedAffinity * maxPenalty * weightRatio
            }

            val exactRated = ratedByNormTitle[DealTitleNormalizer.normalize(deal.title)]
            val hasAiReason = !deal.aiReason.isNullOrBlank() && deal.aiAffinityScore != null

            var reason: String? = null
            var source = DealMatchSource.NONE
            var sentiment = DealMatchSentiment.NONE

            when {
                exactRated?.rating != null -> {
                    reason = "Il tuo voto: ${exactRated.rating}★"
                    source = DealMatchSource.LOCAL
                    sentiment = when {
                        exactRated.rating >= 4 -> DealMatchSentiment.RECOMMENDED
                        exactRated.rating <= 2 -> DealMatchSentiment.DISCOURAGED
                        else -> DealMatchSentiment.NONE
                    }
                }
                combinedAffinity >= 0.14 && isVerifiedQuality -> {
                    sentiment = DealMatchSentiment.RECOMMENDED
                    if (hasAiReason) {
                        reason = deal.aiReason
                        source = DealMatchSource.GEMINI
                    } else {
                        reason = buildAnchorReason(posVectors, dealFeatures, positive = true)
                        source = if (reason != null) DealMatchSource.LOCAL else DealMatchSource.NONE
                    }
                }
                combinedAffinity <= -0.16 -> {
                    sentiment = DealMatchSentiment.DISCOURAGED
                    val aiScore = deal.aiAffinityScore
                    if (hasAiReason && aiScore != null && aiScore < 42.0) {
                        reason = deal.aiReason
                        source = DealMatchSource.GEMINI
                    } else {
                        reason = buildAnchorReason(negVectors, dealFeatures, positive = false)
                        source = if (reason != null) DealMatchSource.LOCAL else DealMatchSource.NONE
                    }
                }
            }

            val baseScore = if (deal.hotScore > 0.0) {
                deal.hotScore
            } else {
                (50.0 - deal.popularityRank.coerceAtMost(49)).coerceAtLeast(1.0)
            }

            deal.copy(
                personalScore = (baseScore + adjustment).coerceAtLeast(0.5),
                matchReason = reason,
                matchSource = source,
                matchSentiment = sentiment
            )
        }
    }

    /**
     * Detailed explainability summary shown in the per-game Detail Sheet.
     */
    fun explainDeal(deal: Deal, settings: FilterSettings): DealAffinityBreakdown {
        val positiveGames = settings.likedGames.filter { it.isPositiveRating }
        val negativeGames = settings.likedGames.filter { it.isNegativeRating }

        val posVectors = positiveGames.map { it to buildFeatureVector(it) }
        val negVectors = negativeGames.map { it to buildFeatureVector(it) }
        val dealFeatures = buildFeatureVector(
            steamTagIds = deal.steamTagIds,
            steamTagNames = deal.steamTagNames,
            developer = deal.developer,
            publisher = deal.publisher,
            categories = deal.gameCategories,
            title = deal.title,
            excerpt = deal.excerpt
        )

        val posProfile = mergeVectors(posVectors.map { (g, b) -> scaleVector(b.weights, starWeight(g.rating)) })
        val negProfile = mergeVectors(negVectors.map { (g, b) -> scaleVector(b.weights, abs(starWeight(g.rating))) })

        val posSim = cosineSimilarity(posProfile, dealFeatures.weights).coerceIn(0.0, 1.0)
        val negSim = cosineSimilarity(negProfile, dealFeatures.weights).coerceIn(0.0, 1.0)
        val localNet = (posSim - negSim * 0.85).coerceIn(-1.0, 1.0)

        val posAnchor = closestAnchor(posVectors, dealFeatures)
        val negAnchor = closestAnchor(negVectors, dealFeatures)

        return DealAffinityBreakdown(
            positiveMatchPercent = (posSim * 100.0).roundToInt(),
            negativeMatchPercent = (negSim * 100.0).roundToInt(),
            netAffinityPercent = (localNet * 100.0).roundToInt(),
            sharedPositiveLabels = posAnchor?.second.orEmpty(),
            sharedNegativeLabels = negAnchor?.second.orEmpty(),
            closestLikedTitle = posAnchor?.first?.title,
            closestDislikedTitle = negAnchor?.first?.title,
            aiScore = deal.aiAffinityScore?.roundToInt(),
            aiReason = deal.aiReason,
            aiDetailedReason = deal.aiDetailedReason
        )
    }

    /**
     * Picks up to 3 top Nintendo eShop categories from positively rated games (4-5★) so the fetch
     * step pulls relevant niche discounts without amplifying 1-2★ dislikes.
     */
    fun topLikedCategories(likedGames: List<LikedGame>, limit: Int = 3): List<String> {
        val positive = likedGames.filter { it.isPositiveRating }
        if (positive.isEmpty()) return emptyList()
        val counts = HashMap<String, Double>()
        positive.forEach { game ->
            val w = starWeight(game.rating)
            game.gameCategories.forEach { cat ->
                val clean = cat.trim().lowercase()
                if (clean.isNotBlank()) {
                    counts[clean] = (counts[clean] ?: 0.0) + w
                }
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
    }

    /**
     * Computes top genre/tag taste bars from 1–5★ rated games in the Library so the UI shows
     * which genres drive recommendations most strongly.
     */
    fun computeTasteProfile(likedGames: List<LikedGame>, limit: Int = 3): List<TasteGenreBar> {
        val rated = likedGames.filter { it.rating != null }
        if (rated.isEmpty()) return emptyList()

        data class Accumulator(var sumStars: Int = 0, var count: Int = 0)
        val byLabel = LinkedHashMap<String, Accumulator>()

        rated.forEach { game ->
            val rating = game.rating ?: return@forEach
            val labelsForGame = linkedSetOf<String>()

            // Specific subgenre keywords first, then curated Steam tags, then Nintendo categories
            val combinedText = "${game.title} ${game.excerpt.orEmpty()}"
            SUBGENRE_PATTERNS.forEach { (label, regex) ->
                if (regex.containsMatchIn(combinedText)) {
                    labelsForGame.add(label)
                }
            }

            game.steamTagNames
                .map { it.trim() }
                .filter { it.isNotBlank() && it.lowercase() !in GENERIC_META_TAGS }
                .take(4)
                .forEach { tag ->
                    val existingLower = labelsForGame.map { it.lowercase() }
                    if (tag.lowercase() !in existingLower) {
                        labelsForGame.add(tag)
                    }
                }

            if (labelsForGame.isEmpty()) {
                game.gameCategories
                    .mapNotNull { raw ->
                        val clean = raw.trim().lowercase()
                        CATEGORY_LABELS[clean] ?: clean.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
                    }
                    .take(2)
                    .forEach { labelsForGame.add(it) }
            }

            labelsForGame.forEach { label ->
                val acc = byLabel.getOrPut(label) { Accumulator() }
                acc.sumStars += rating
                acc.count += 1
            }
        }

        return byLabel.entries
            .map { (label, acc) ->
                val avg = acc.sumStars.toDouble() / acc.count.toDouble()
                val pct = ((avg / 5.0) * 100.0).roundToInt().coerceIn(20, 100)
                TasteGenreBar(
                    label = label,
                    avgStars = avg,
                    percent = pct,
                    isPositive = avg >= 3.5,
                    ratedCount = acc.count
                )
            }
            .sortedWith(
                compareByDescending<TasteGenreBar> { it.isPositive }
                    .thenByDescending { it.ratedCount * it.avgStars }
                    .thenByDescending { it.avgStars }
            )
            .take(limit)
    }

    internal fun starWeight(rating: Int?): Double = when (rating) {
        5 -> 1.0
        4 -> 0.55
        3 -> 0.0
        2 -> -0.50
        1 -> -0.95
        else -> 0.0
    }

    private fun buildAnchorReason(
        vectors: List<Pair<LikedGame, FeatureBundle>>,
        dealFeatures: FeatureBundle,
        positive: Boolean
    ): String? {
        val match = closestAnchor(vectors, dealFeatures) ?: return null
        val anchor = match.first
        val sharedLabels = match.second
        val shortAnchorTitle = anchor.title
            .replace(Regex("\\s*[–\\-]\\s*Nintendo Switch.*$", RegexOption.IGNORE_CASE), "")
            .take(22)
            .trim()

        return if (positive) {
            if (sharedLabels.isNotEmpty()) {
                "Simile a $shortAnchorTitle · ${sharedLabels.joinToString(", ")}"
            } else {
                "Affine a $shortAnchorTitle"
            }
        } else {
            val stars = anchor.rating?.let { " (${it}★)" }.orEmpty()
            if (sharedLabels.isNotEmpty()) {
                "Poco affine: ricorda $shortAnchorTitle$stars · ${sharedLabels.joinToString(", ")}"
            } else {
                "Sconsigliato: vicino a $shortAnchorTitle$stars"
            }
        }
    }

    private fun closestAnchor(
        vectors: List<Pair<LikedGame, FeatureBundle>>,
        dealFeatures: FeatureBundle
    ): Pair<LikedGame, List<String>>? {
        var bestGame: LikedGame? = null
        var bestSim = 0.0
        var bestBundle: FeatureBundle? = null

        vectors.forEach { (game, bundle) ->
            val sim = cosineSimilarity(bundle.weights, dealFeatures.weights)
            if (sim > bestSim) {
                bestSim = sim
                bestGame = game
                bestBundle = bundle
            }
        }
        val anchor = bestGame ?: return null
        if (bestSim <= 0.0) return null

        val sharedLabels = bestBundle?.labels.orEmpty()
            .filterKeys { it in dealFeatures.weights }
            .entries
            .sortedByDescending { dealFeatures.weights[it.key] ?: 0.0 }
            .map { it.value }
            .distinct()
            .take(2)
        return anchor to sharedLabels
    }

    private fun buildFeatureVector(game: LikedGame): FeatureBundle = buildFeatureVector(
        steamTagIds = game.steamTagIds,
        steamTagNames = game.steamTagNames,
        developer = game.developer,
        publisher = game.publisher,
        categories = game.gameCategories,
        title = game.title,
        excerpt = game.excerpt
    )

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

    private fun scaleVector(vec: Map<String, Double>, scale: Double): Map<String, Double> {
        if (scale == 1.0) return vec
        return vec.mapValues { (_, v) -> v * scale }
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
