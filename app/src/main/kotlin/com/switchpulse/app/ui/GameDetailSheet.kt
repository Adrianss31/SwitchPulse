package com.switchpulse.app.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.RecommendationEngine
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.ui.theme.EshopCrimson
import com.switchpulse.app.ui.theme.EshopCrimsonSoft

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GameDetailSheet(
    deal: Deal,
    settings: FilterSettings,
    insights: GameExternalInsights?,
    isLoadingInsights: Boolean,
    libraryEntry: LikedGame?,
    onDismiss: () -> Unit,
    onOpenEshop: (String) -> Unit,
    onSetRating: (Int?) -> Unit,
    onSetOwned: (Boolean) -> Unit,
    onHideDeal: () -> Unit,
    onRefreshInsights: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val breakdown = remember(deal, settings.likedGames) {
        RecommendationEngine.explainDeal(deal, settings)
    }
    val currentRating = libraryEntry?.rating
    val isOwned = libraryEntry?.isOwned == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cover banner + header
            if (!deal.imageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = deal.imageUrl,
                        contentDescription = deal.title,
                        modifier = Modifier.fillMaxWidth().height(168.dp),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        shape = RoundedCornerShape(999.dp),
                        color = EshopCrimson
                    ) {
                        Text(
                            text = "-${deal.discountPercent}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                val expiry = DealFilterEngine.formatExpiryShort(deal.endsAtIso)
                val devPart = deal.developer?.takeIf { it.isNotBlank() && it != deal.publisher }
                val metaParts = listOfNotNull(
                    deal.platform.displayName,
                    deal.publisher,
                    devPart?.let { "Dev: $it" },
                    expiry
                )
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = DealFilterEngine.formatEur(deal.discountedPriceEur),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = EshopCrimson
                    )
                    Text(
                        text = DealFilterEngine.formatEur(deal.regularPriceEur),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            }

            // Interactive 1-5 star rating & Owned toggle
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "La tua valutazione",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = when (currentRating) {
                                    5, 4 -> "Usa questi tag per consigliarti giochi simili"
                                    3 -> "Neutro (nessuna spinta né penalità)"
                                    2, 1 -> "Penalizza titoli simili nei consigli"
                                    else -> "Dai un voto 1–5★ per calibrare i consigli"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currentRating != null) {
                            TextButton(
                                onClick = { onSetRating(null) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Azzera", fontSize = 12.sp)
                            }
                        }
                    }

                    RatingStarBar(
                        rating = currentRating,
                        onSelectRating = { clicked ->
                            onSetRating(if (clicked == currentRating) null else clicked)
                        },
                        starSize = 30.dp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isOwned,
                            onClick = { onSetOwned(!isOwned) },
                            label = {
                                Text(
                                    if (isOwned) "Già posseduto (nascosto dal feed)"
                                    else "Segna come già posseduto"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Inventory2,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = EshopCrimson,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }
            }

            // AI & Affinity Analysis Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Analisi Affinità & AI",
                        style = MaterialTheme.typography.labelLarge
                    )

                    val aiDisplayText = breakdown.aiDetailedReason?.takeIf { it.isNotBlank() }
                        ?: breakdown.aiReason?.takeIf { it.isNotBlank() }
                    if (breakdown.aiScore != null || aiDisplayText != null) {
                        val scoreVal = breakdown.aiScore
                        val badgeBg = when {
                            scoreVal != null && scoreVal >= 65 -> EshopCrimson
                            scoreVal != null && scoreVal < 40 -> Color(0xFF92400E)
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                        val badgeFg = if (scoreVal != null && (scoreVal >= 65 || scoreVal < 40)) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeBg
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = badgeFg,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "${settings.geminiModel} · ${scoreVal ?: "--"}/100",
                                        color = badgeFg,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                        if (aiDisplayText != null) {
                            Text(
                                text = aiDisplayText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Text(
                            text = if (settings.geminiApiKey.isBlank()) {
                                "Valutazione locale attiva (inserisci una API key in Impostazioni per il rerank Gemini)."
                            } else {
                                "Nessun giudizio Gemini specifico su questo titolo (punteggio calcolato dal motore locale a micro-tag)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Affinità 4–5★: ${breakdown.positiveMatchPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (breakdown.positiveMatchPercent > 0) EshopCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Similitudine 1–2★: ${breakdown.negativeMatchPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (breakdown.closestLikedTitle != null && breakdown.sharedPositiveLabels.isNotEmpty()) {
                        Text(
                            text = "Tratti vicini a ${breakdown.closestLikedTitle}: ${breakdown.sharedPositiveLabels.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (breakdown.closestDislikedTitle != null && breakdown.sharedNegativeLabels.isNotEmpty()) {
                        Text(
                            text = "Tratti vicini a ${breakdown.closestDislikedTitle} (voto basso): ${breakdown.sharedNegativeLabels.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFD97706)
                        )
                    }

                    val tagChips = (deal.steamTagNames.filter { it.isNotBlank() }.take(6) +
                        deal.gameCategories.take(3)).distinct()
                    if (tagChips.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tagChips.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (deal.steamReviewCount > 0) {
                        val sentimentText = deal.steamPositivePercent?.let { "$it% positive su " } ?: ""
                        Text(
                            text = "Steam: $sentimentText${deal.steamReviewCount} recensioni",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Metacritic & HowLongToBeat Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Metacritic & HowLongToBeat",
                            style = MaterialTheme.typography.labelLarge
                        )
                        IconButton(
                            onClick = onRefreshInsights,
                            enabled = !isLoadingInsights,
                            modifier = Modifier.size(28.dp)
                        ) {
                            if (isLoadingInsights) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Aggiorna dati esterni",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    val score = insights?.metacriticScore ?: deal.metacriticScore
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val metaBg = when {
                            score == null -> MaterialTheme.colorScheme.surfaceContainerLowest
                            score >= 75 -> Color(0xFF16A34A)
                            score >= 50 -> Color(0xFFD97706)
                            else -> Color(0xFFDC2626)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = metaBg,
                            modifier = Modifier.weight(0.32f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = score?.toString() ?: "--",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = if (score != null) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Metacritic",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (score != null) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.weight(0.68f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                HltbMetric("Storia", insights?.hltbMainHours)
                                HltbMetric("Extra", insights?.hltbMainExtraHours)
                                HltbMetric("100%", insights?.hltbCompletionistHours)
                            }
                        }
                    }

                    val note = insights?.summaryNote?.takeIf { it.isNotBlank() }
                    if (note != null) {
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    val source = insights?.sourceLabel?.takeIf { it.isNotBlank() }
                    if (source != null) {
                        Text(
                            text = "Fonte: $source",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val encodedTitle = Uri.encode(DealTitleNormalizer.normalize(deal.title))
                    val metacriticUrl = insights?.metacriticUrl
                        ?: "https://www.metacritic.com/search/$encodedTitle/"
                    val hltbUrl = "https://howlongtobeat.com/?q=$encodedTitle"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenEshop(metacriticUrl) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("Metacritic", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        OutlinedButton(
                            onClick = { onOpenEshop(hltbUrl) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("HowLongToBeat", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            if (!deal.excerpt.isNullOrBlank()) {
                Text(
                    text = deal.excerpt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Bottom CTAs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        onHideDeal()
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nascondi")
                }

                Button(
                    onClick = { onOpenEshop(deal.eshopUrl) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = EshopCrimson)
                ) {
                    Text("Apri su Nintendo eShop")
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HltbMetric(label: String, value: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "--",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RatingStarBar(
    rating: Int?,
    onSelectRating: (Int) -> Unit,
    modifier: Modifier = Modifier,
    starSize: Dp = 24.dp
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (star in 1..5) {
            val active = (rating ?: 0) >= star
            val tint = when {
                !active -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                (rating ?: 0) <= 2 -> Color(0xFFD97706)
                rating == 3 -> MaterialTheme.colorScheme.primary
                else -> EshopCrimsonSoft
            }
            Icon(
                imageVector = if (active) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = "$star stelle",
                tint = tint,
                modifier = Modifier
                    .size(starSize)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onSelectRating(star) }
                    .padding(2.dp)
            )
        }
    }
}
