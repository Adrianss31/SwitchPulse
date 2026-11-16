package com.switchpulse.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealMatchSentiment
import com.switchpulse.app.data.model.DealMatchSource
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.Platform
import com.switchpulse.app.data.model.PlatformFilter
import com.switchpulse.app.ui.theme.EshopCrimson
import com.switchpulse.app.ui.theme.EshopCrimsonSoft

private enum class FeedSortMode(val label: String) {
    PERSONAL("Per Te"),
    DISCOUNT("Sconto %"),
    PRICE_ASC("Prezzo €")
}

@Composable
fun FeedScreen(
    state: MainUiState,
    onSelectPlatform: (PlatformFilter) -> Unit,
    onOpenDealDetail: (Deal) -> Unit,
    onHideDeal: (Deal) -> Unit,
    onToggleForYou: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onResetFilters: () -> Unit,
    onOpenUpdateTab: () -> Unit,
    onRerankMissingDeals: () -> Unit = {},
    onRerankAllNow: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val ratedMap = remember(state.settings.likedGames) {
        state.settings.likedGames
            .filter { it.rating != null }
            .associateBy { DealTitleNormalizer.normalize(it.title) }
    }
    val hasTasteSignals = remember(state.settings.likedGames) {
        state.settings.likedGames.any { it.isPositiveRating || it.isNegativeRating }
    }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(FeedSortMode.PERSONAL) }

    val displayedDeals = remember(state.deals, searchQuery, sortMode) {
        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            state.deals
        } else {
            state.deals.filter { deal ->
                deal.title.lowercase().contains(q) ||
                    deal.publisher.lowercase().contains(q) ||
                    (deal.developer?.lowercase()?.contains(q) == true) ||
                    deal.steamTagNames.any { it.lowercase().contains(q) } ||
                    deal.gameCategories.any { it.lowercase().contains(q) }
            }
        }
        when (sortMode) {
            FeedSortMode.PERSONAL -> filtered
            FeedSortMode.DISCOUNT -> filtered.sortedWith(
                compareByDescending<Deal> { it.discountPercent }
                    .thenByDescending { it.personalScore }
            )
            FeedSortMode.PRICE_ASC -> filtered.sortedWith(
                compareBy<Deal> { it.discountedPriceEur }
                    .thenByDescending { it.personalScore }
            )
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Segmented filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PlatformFilter.entries.forEach { option ->
                    val selected = state.settings.appPlatformFilter == option
                    val label = when (option) {
                        PlatformFilter.ALL -> "Tutte"
                        PlatformFilter.SWITCH_2 -> "Switch 2"
                        PlatformFilter.SWITCH_1 -> "Switch"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { onSelectPlatform(option) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasTasteSignals) {
                    val forYouActive = state.settings.forYouOnlyMode
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (forYouActive) EshopCrimson
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                            .clickable { onToggleForYou(!forYouActive) }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Solo Per Te",
                            tint = if (forYouActive) Color.White else EshopCrimson,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Per Te",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (forYouActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (searchExpanded || searchQuery.isNotBlank()) EshopCrimson
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                        .clickable {
                            if (searchExpanded && searchQuery.isBlank()) {
                                searchExpanded = false
                            } else {
                                searchExpanded = !searchExpanded
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Cerca nelle offerte",
                        tint = if (searchExpanded || searchQuery.isNotBlank()) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = "${displayedDeals.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (searchExpanded || searchQuery.isNotBlank()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cerca titolo, studio o genere in offerta...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    IconButton(onClick = {
                        searchQuery = ""
                        searchExpanded = false
                    }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Chiudi ricerca", modifier = Modifier.size(18.dp))
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Quick sort strip + interactive AI status pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FeedSortMode.entries.forEach { mode ->
                val active = sortMode == mode
                Surface(
                    onClick = { sortMode = mode },
                    shape = RoundedCornerShape(999.dp),
                    color = if (active) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (state.deals.isNotEmpty()) {
            FeedAiStatusPill(
                state = state,
                hasTasteSignals = hasTasteSignals,
                onOpenSettingsTab = onOpenUpdateTab,
                onRerankMissingDeals = onRerankMissingDeals,
                onRerankAllNow = onRerankAllNow,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Update available banner
        val availableUpdate = state.updateState as? UpdateUiState.Available
        if (availableUpdate != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenUpdateTab() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aggiornamento ${availableUpdate.release.tagName}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Tocca per installare da GitHub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Body states
        when {
            state.isRefreshing && state.deals.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = EshopCrimson)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Scarico le offerte da Nintendo eShop Italia...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            state.errorMessage != null && state.deals.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = EshopCrimson)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Riprova")
                        }
                    }
                }
            }

            state.deals.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nessuna offerta corrisponde ai filtri attuali.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(onClick = onResetFilters) {
                            Text("Ripristina filtri")
                        }
                    }
                }
            }

            displayedDeals.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nessun titolo corrisponde a \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FilledTonalButton(onClick = {
                            searchQuery = ""
                            searchExpanded = false
                        }) {
                            Text("Svuota ricerca")
                        }
                    }
                }
            }

            else -> {
                val hero = displayedDeals.first()
                val rest = displayedDeals.drop(1)

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.errorMessage != null) {
                        item(key = "error_banner") {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Offline · Mostro ultimi dati salvati",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onRefresh) {
                                        Text("Riprova")
                                    }
                                }
                            }
                        }
                    }

                    item(key = "hero_${hero.id}") {
                        val userRating = ratedMap[DealTitleNormalizer.normalize(hero.title)]?.rating
                        HeroDealCard(
                            deal = hero,
                            userRating = userRating,
                            forYouMode = state.settings.forYouOnlyMode,
                            onClick = { onOpenDealDetail(hero) },
                            onHide = { onHideDeal(hero) }
                        )
                    }

                    itemsIndexed(rest, key = { _, d -> d.id }) { idx, deal ->
                        val userRating = ratedMap[DealTitleNormalizer.normalize(deal.title)]?.rating
                        DealRowCard(
                            rank = idx + 2,
                            deal = deal,
                            userRating = userRating,
                            onClick = { onOpenDealDetail(deal) },
                            onHide = { onHideDeal(deal) }
                        )
                    }
                }
            }
        }
    }
}

private fun formatMatchDisplay(deal: Deal): String? {
    val reason = deal.matchReason ?: return null
    return if (deal.matchSource == DealMatchSource.GEMINI) "✨ $reason" else reason
}

@Composable
private fun HeroDealCard(
    deal: Deal,
    userRating: Int?,
    forYouMode: Boolean,
    onClick: () -> Unit,
    onHide: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1F222A))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = deal.imageUrl,
            contentDescription = deal.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x33000000),
                            Color(0x55000000),
                            Color(0xE80A0B0E)
                        )
                    )
                )
        )

        // Top badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0x99000000)
            ) {
                Text(
                    text = if (forYouMode && deal.matchReason != null) {
                        "#1 Per te · ${deal.platform.displayName}"
                    } else {
                        "#1 Popolare · ${deal.platform.displayName}"
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (userRating != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xAA000000)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "$userRating",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = EshopCrimson
                ) {
                    Text(
                        text = "-${deal.discountPercent}%",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x88000000))
                        .clickable(onClick = onHide),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Nascondi titolo",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Bottom details
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                val matchLabel = formatMatchDisplay(deal)
                if (matchLabel != null) {
                    val pillColor = when {
                        deal.matchSentiment == DealMatchSentiment.DISCOURAGED -> Color(0xD9B45309)
                        deal.matchSentiment == DealMatchSentiment.NONE && deal.matchSource == DealMatchSource.GEMINI ->
                            Color(0xC71E293B)
                        else -> EshopCrimson.copy(alpha = 0.88f)
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = pillColor,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = matchLabel,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = deal.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val expiry = DealFilterEngine.formatExpiryShort(deal.endsAtIso)
                val sub = if (expiry != null) "${deal.publisher} · $expiry" else deal.publisher
                Text(
                    text = sub,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = DealFilterEngine.formatEur(deal.regularPriceEur),
                        color = Color(0xFF6B7280),
                        fontSize = 11.sp,
                        textDecoration = TextDecoration.LineThrough
                    )
                    Text(
                        text = DealFilterEngine.formatEur(deal.discountedPriceEur),
                        color = Color(0xFF111214),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun DealRowCard(
    rank: Int,
    deal: Deal,
    userRating: Int?,
    onClick: () -> Unit,
    onHide: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                AsyncImage(
                    model = deal.imageUrl,
                    contentDescription = deal.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xAA000000)
                ) {
                    Text(
                        text = "#$rank",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deal.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val expiry = DealFilterEngine.formatExpiryShort(deal.endsAtIso)
                val platformColor = if (deal.platform == Platform.SWITCH_2) {
                    if (MaterialTheme.colorScheme.background.red < 0.3f) EshopCrimsonSoft else EshopCrimson
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = deal.platform.displayName,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = platformColor
                    )
                    if (expiry != null) {
                        Text(
                            text = " · $expiry",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = DealFilterEngine.formatEur(deal.regularPriceEur),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = TextDecoration.LineThrough
                )
                val matchLabel = formatMatchDisplay(deal)
                if (matchLabel != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val reasonColor = when {
                        deal.matchSentiment == DealMatchSentiment.DISCOURAGED -> Color(0xFFD97706)
                        deal.matchSentiment == DealMatchSentiment.NONE && deal.matchSource == DealMatchSource.GEMINI ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                        MaterialTheme.colorScheme.background.red < 0.3f -> EshopCrimsonSoft
                        else -> EshopCrimson
                    }
                    Text(
                        text = matchLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = reasonColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (userRating != null) {
                        Text(
                            text = "${userRating}★",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFD97706)
                        )
                    }
                    Text(
                        text = DealFilterEngine.formatEur(deal.discountedPriceEur),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(7.dp),
                    color = EshopCrimson
                ) {
                    Text(
                        text = "-${deal.discountPercent}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = onHide,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Nascondi titolo",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun FeedAiStatusPill(
    state: MainUiState,
    hasTasteSignals: Boolean,
    onOpenSettingsTab: () -> Unit,
    onRerankMissingDeals: () -> Unit,
    onRerankAllNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val task = state.settings.rerankTarget
    val hasKey = state.settings.hasKeyFor(task.provider)
    val analyzedCount = state.deals.count { it.aiAffinityScore != null }
    val totalCount = state.deals.size
    val missingCount = (totalCount - analyzedCount).coerceAtLeast(0)
    val statusText = state.settings.geminiStatusSummary
    val isError = statusText.startsWith("⚠️")

    val (label, actionLabel, onCardClick, onActionClick) = when {
        !hasKey -> Quadruple(
            "Motore locale · Aggiungi API key ${task.provider.shortLabel} in Filtri",
            "Configura",
            onOpenSettingsTab,
            onOpenSettingsTab
        )
        isError -> Quadruple(
            statusText.take(64),
            "Riprova",
            onOpenSettingsTab,
            onRerankAllNow
        )
        analyzedCount > 0 && missingCount > 0 -> Quadruple(
            "✨ ${task.provider.shortLabel} · $analyzedCount/$totalCount valutate",
            "Completa ($missingCount)",
            onOpenSettingsTab,
            onRerankMissingDeals
        )
        analyzedCount > 0 -> Quadruple(
            "✨ ${task.provider.shortLabel} · ${task.modelIdShort()} · $analyzedCount/$totalCount valutate",
            null,
            onOpenSettingsTab,
            onRerankAllNow
        )
        !hasTasteSignals -> Quadruple(
            "✨ ${task.provider.shortLabel} pronto · Vota un gioco in Libreria",
            null,
            onOpenSettingsTab,
            onOpenSettingsTab
        )
        else -> Quadruple(
            "✨ ${task.provider.shortLabel} pronto · Analisi AI non ancora eseguita",
            "Analizza",
            onOpenSettingsTab,
            onRerankAllNow
        )
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = BorderStroke(
            1.dp,
            if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCardClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            if (state.isRefreshing && hasKey) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = EshopCrimson
                )
            } else if (actionLabel != null) {
                Surface(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(999.dp),
                    color = EshopCrimson.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = EshopCrimson,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

private fun com.switchpulse.app.data.model.AiTaskTarget.modelIdShort(): String =
    modelId.substringAfter('/').removeSuffix(":free").take(22)

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
