package com.switchpulse.app.data.model

import com.switchpulse.app.data.DealFilterEngine

enum class GameDetailOrigin {
    FEED_DEAL,
    LIBRARY_GAME,
    DISCOVERY_PICK
}

/**
 * Unified presentation model for the full-screen Game Detail view, built seamlessly
 * from an eShop Deal, a Library entry, or an AI Discovery Pick. Sections with null/empty data
 * hide gracefully in the UI.
 */
data class GameDetailModel(
    val id: String,
    val title: String,
    val normalizedTitle: String,
    val publisher: String,
    val developer: String?,
    val platformLabel: String?,
    val coverUrl: String?,
    val sharedCoverKey: String,
    val eshopUrl: String?,
    val rank: Int?,
    val regularPriceEur: Double?,
    val discountedPriceEur: Double?,
    val discountPercent: Int?,
    val endsAtIso: String?,
    val lowestSeenPriceEur: Double?,
    val isHistoricalLow: Boolean,
    val previousDiscountedPriceEur: Double?,
    val aiScore: Int?,
    val aiHeadline: String?,
    val aiReasonText: String?,
    val localSummaryReason: String?,
    val matchSentiment: DealMatchSentiment,
    val steamPositivePercent: Int?,
    val metacriticScore: Int?,
    val excerpt: String?,
    val origin: GameDetailOrigin,
    val sourceDeal: Deal?,
    val sourceLikedGame: LikedGame?,
    val sourcePick: AiDiscoveryPick?
) {
    fun toLikedGame(existing: LikedGame?): LikedGame {
        if (existing != null) return existing
        return sourceDeal?.toLikedGame()
            ?: sourceLikedGame
            ?: sourcePick?.toLikedGame()
            ?: LikedGame(id = id, title = title, publisher = publisher)
    }

    companion object {
        fun fromDeal(
            deal: Deal,
            rank: Int?,
            sharedCoverKey: String = "cover-${deal.id}"
        ): GameDetailModel {
            val aiScore = deal.aiAffinityScore?.toInt()?.coerceIn(0, 100)
            return GameDetailModel(
                id = deal.id,
                title = deal.title,
                normalizedTitle = DealTitleNormalizer.normalize(deal.title),
                publisher = deal.displayPublisher,
                developer = deal.developer,
                platformLabel = deal.platform.displayName,
                coverUrl = deal.imageUrl?.takeIf { it.isNotBlank() },
                sharedCoverKey = sharedCoverKey,
                eshopUrl = deal.eshopUrl.takeIf { it.isNotBlank() },
                rank = rank,
                regularPriceEur = deal.regularPriceEur.takeIf { it > 0.0 },
                discountedPriceEur = deal.discountedPriceEur.takeIf { it > 0.0 },
                discountPercent = deal.discountPercent.takeIf { it > 0 },
                endsAtIso = deal.endsAtIso,
                lowestSeenPriceEur = deal.lowestSeenPriceEur,
                isHistoricalLow = deal.isHistoricalLow,
                previousDiscountedPriceEur = deal.previousDiscountedPriceEur,
                aiScore = aiScore,
                aiHeadline = verdictForScore(aiScore),
                aiReasonText = deal.aiDetailedReason?.takeIf { it.isNotBlank() }
                    ?: deal.aiReason?.takeIf { it.isNotBlank() },
                localSummaryReason = deal.matchReason?.takeIf { it.isNotBlank() },
                matchSentiment = deal.matchSentiment,
                steamPositivePercent = deal.steamPositivePercent?.takeIf { deal.steamReviewCount > 0 },
                metacriticScore = deal.metacriticScore,
                excerpt = deal.excerpt,
                origin = GameDetailOrigin.FEED_DEAL,
                sourceDeal = deal,
                sourceLikedGame = null,
                sourcePick = null
            )
        }

        fun fromLibraryGame(
            game: LikedGame,
            matchingActiveDeal: Deal? = null
        ): GameDetailModel {
            if (matchingActiveDeal != null) {
                return fromDeal(
                    deal = matchingActiveDeal,
                    rank = null,
                    sharedCoverKey = "library-${game.id}"
                ).copy(
                    origin = GameDetailOrigin.LIBRARY_GAME,
                    sourceLikedGame = game
                )
            }
            return GameDetailModel(
                id = game.id,
                title = game.title,
                normalizedTitle = DealTitleNormalizer.normalize(game.title),
                publisher = game.publisher.ifBlank { "Nintendo eShop" },
                developer = game.developer,
                platformLabel = "Switch",
                coverUrl = game.imageUrl?.takeIf { it.isNotBlank() },
                sharedCoverKey = "library-${game.id}",
                eshopUrl = game.eshopUrl?.takeIf { it.isNotBlank() }
                    ?: "https://www.nintendo.com/it-it/Search/Search-299117.html?q=${java.net.URLEncoder.encode(game.title, "UTF-8")}",
                rank = null,
                regularPriceEur = null,
                discountedPriceEur = null,
                discountPercent = null,
                endsAtIso = null,
                lowestSeenPriceEur = null,
                isHistoricalLow = false,
                previousDiscountedPriceEur = null,
                aiScore = null,
                aiHeadline = null,
                aiReasonText = null,
                localSummaryReason = null,
                matchSentiment = DealMatchSentiment.NONE,
                steamPositivePercent = null,
                metacriticScore = null,
                excerpt = game.excerpt,
                origin = GameDetailOrigin.LIBRARY_GAME,
                sourceDeal = null,
                sourceLikedGame = game,
                sourcePick = null
            )
        }

        fun fromDiscoveryPick(
            pick: AiDiscoveryPick,
            matchingActiveDeal: Deal? = null
        ): GameDetailModel {
            val sharedKey = "pick-${DealTitleNormalizer.normalize(pick.title)}"
            val reg = matchingActiveDeal?.regularPriceEur ?: pick.regularPriceEur
            val disc = matchingActiveDeal?.discountedPriceEur ?: pick.currentPriceEur
            val pct = matchingActiveDeal?.discountPercent ?: pick.discountPercent
            return GameDetailModel(
                id = matchingActiveDeal?.id ?: sharedKey,
                title = pick.title,
                normalizedTitle = DealTitleNormalizer.normalize(pick.title),
                publisher = pick.publisher.ifBlank { "Nintendo eShop" },
                developer = matchingActiveDeal?.developer,
                platformLabel = matchingActiveDeal?.platform?.displayName ?: "Switch",
                coverUrl = (matchingActiveDeal?.imageUrl ?: pick.imageUrl)?.takeIf { it.isNotBlank() },
                sharedCoverKey = sharedKey,
                eshopUrl = matchingActiveDeal?.eshopUrl
                    ?: pick.eshopUrl?.takeIf { it.startsWith("http") }
                    ?: "https://www.nintendo.com/it-it/Search/Search-299117.html?q=${java.net.URLEncoder.encode(pick.title, "UTF-8")}",
                rank = null,
                regularPriceEur = reg?.takeIf { it > 0.0 },
                discountedPriceEur = disc?.takeIf { it > 0.0 },
                discountPercent = pct.takeIf { it > 0 },
                endsAtIso = matchingActiveDeal?.endsAtIso,
                lowestSeenPriceEur = matchingActiveDeal?.lowestSeenPriceEur,
                isHistoricalLow = matchingActiveDeal?.isHistoricalLow ?: false,
                previousDiscountedPriceEur = matchingActiveDeal?.previousDiscountedPriceEur,
                aiScore = null,
                aiHeadline = pick.pitchHook.takeIf { it.isNotBlank() },
                aiReasonText = pick.reason.takeIf { it.isNotBlank() },
                localSummaryReason = null,
                matchSentiment = DealMatchSentiment.RECOMMENDED,
                steamPositivePercent = matchingActiveDeal?.steamPositivePercent?.takeIf { matchingActiveDeal.steamReviewCount > 0 },
                metacriticScore = matchingActiveDeal?.metacriticScore,
                excerpt = matchingActiveDeal?.excerpt,
                origin = GameDetailOrigin.DISCOVERY_PICK,
                sourceDeal = matchingActiveDeal,
                sourceLikedGame = null,
                sourcePick = pick
            )
        }

        fun resolveDescriptionTexts(
            detail: GameDetailModel,
            insights: GameExternalInsights?
        ): Pair<String?, String?> {
            val headline = detail.aiHeadline
                ?: insights?.aiVerdict?.takeIf { it.isNotBlank() }
            val reason = detail.aiReasonText
                ?: detail.localSummaryReason
                ?: insights?.summaryNote?.takeIf { it.isNotBlank() }
                ?: detail.excerpt
            return headline to reason
        }

        fun verdictForScore(score: Int?): String? = when {
            score == null -> null
            score >= 85 -> "Imperdibile per te"
            score >= 70 -> "Consigliatissimo"
            score >= 55 -> "Buon affare"
            score >= 40 -> "Neutro / Misto"
            else -> "Lascia perdere"
        }

        fun syntheticDealForInsights(model: GameDetailModel): Deal? {
            model.sourceDeal?.let { return it }
            val title = model.title.takeIf { it.isNotBlank() } ?: return null
            return Deal(
                id = model.id,
                nsuid = null,
                title = title,
                publisher = model.publisher,
                platform = Platform.SWITCH_1,
                regularPriceEur = model.regularPriceEur ?: 0.0,
                discountedPriceEur = model.discountedPriceEur ?: 0.0,
                discountPercent = model.discountPercent ?: 0,
                endsAtIso = model.endsAtIso,
                imageUrl = model.coverUrl,
                eshopUrl = model.eshopUrl.orEmpty(),
                popularityRank = model.rank ?: 1
            )
        }
    }
}
