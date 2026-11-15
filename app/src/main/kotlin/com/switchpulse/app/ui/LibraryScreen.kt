package com.switchpulse.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.switchpulse.app.data.RecommendationEngine
import com.switchpulse.app.data.TasteGenreBar
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.ui.theme.EshopCrimson
import com.switchpulse.app.ui.theme.EshopCrimsonSoft
import java.util.Locale

private val WarmGoldDark = Color(0xFFFBBF24)
private val WarmGoldLight = Color(0xFFB45309)
private val BarAmber = Color(0xFFF59E0B)

private enum class LibraryFilter(val label: String) {
    ALL("Tutti"),
    OWNED("Posseduti"),
    BACKLOG("Da giocare"),
    LIKED("Apprezzati 4–5★"),
    DISLIKED("1–2★ Sconsigliati")
}

@Composable
fun LibraryScreen(
    state: MainUiState,
    showAddGameSheet: Boolean,
    onOpenAddGameSheet: () -> Unit,
    onDismissAddGameSheet: () -> Unit,
    onSearchGames: (String) -> Unit,
    onClearSearchResults: () -> Unit,
    onSetGameRating: (LikedGame, Int?) -> Unit,
    onSetGameOwned: (LikedGame, Boolean) -> Unit,
    onSetGameNote: (LikedGame, String?) -> Unit = { _, _ -> },
    onRemoveGame: (String) -> Unit,
    onOpenEshop: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }
    var expandedGameId by remember { mutableStateOf<String?>(null) }

    val library = state.settings.likedGames
    val tasteBars = remember(library) {
        RecommendationEngine.computeTasteProfile(library, limit = 3)
    }

    val filteredItems = remember(library, activeFilter) {
        when (activeFilter) {
            LibraryFilter.ALL -> library
            LibraryFilter.OWNED -> library.filter { it.isOwned }
            LibraryFilter.BACKLOG -> library.filter { it.isOwned && it.rating == null }
            LibraryFilter.LIKED -> library.filter { it.isPositiveRating }
            LibraryFilter.DISLIKED -> library.filter { it.isNegativeRating }
        }
    }

    val ownedCount = library.count { it.isOwned }
    val ratedCount = library.count { it.rating != null }
    val backlogCount = library.count { it.isOwned && it.rating == null }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
    ) {
        // Editorial Taste Matrix Bento Card
        item(key = "taste_bento") {
            TasteProfileBentoCard(
                tasteBars = tasteBars,
                ownedCount = ownedCount,
                backlogCount = backlogCount,
                ratedCount = ratedCount,
                geminiEnabled = state.settings.geminiApiKey.isNotBlank(),
                activeFilter = activeFilter,
                onSelectStatFilter = { targetFilter ->
                    activeFilter = if (activeFilter == targetFilter) LibraryFilter.ALL else targetFilter
                },
                onOpenAddGameSheet = onOpenAddGameSheet,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Single-line sleek filter strip
        item(key = "filter_strip") {
            LibraryFilterStrip(
                activeFilter = activeFilter,
                onSelectFilter = { activeFilter = it },
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
            )
        }

        if (filteredItems.isEmpty()) {
            item(key = "empty_state") {
                LibraryEmptyState(
                    hasAnyGames = library.isNotEmpty(),
                    onOpenAddGameSheet = onOpenAddGameSheet,
                    onResetFilter = { activeFilter = LibraryFilter.ALL },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
            }
        } else {
            itemsIndexed(
                items = filteredItems,
                key = { _, item -> item.id }
            ) { index, item ->
                val isExpanded = expandedGameId == item.id
                LibraryMatrixRow(
                    game = item,
                    expanded = isExpanded,
                    onToggleExpand = {
                        expandedGameId = if (isExpanded) null else item.id
                    },
                    onSetRating = { nextRating -> onSetGameRating(item, nextRating) },
                    onToggleOwned = { onSetGameOwned(item, !item.isOwned) },
                    onSetNote = { nextNote -> onSetGameNote(item, nextNote) },
                    onRemove = {
                        if (expandedGameId == item.id) expandedGameId = null
                        onRemoveGame(item.id)
                    },
                    onOpenEshop = item.eshopUrl?.let { url -> { onOpenEshop(url) } }
                )
                if (index < filteredItems.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
                    )
                }
            }
        }
    }

    if (showAddGameSheet) {
        AddGameSearchSheet(
            state = state,
            onDismiss = onDismissAddGameSheet,
            onSearchGames = onSearchGames,
            onClearSearchResults = onClearSearchResults,
            onSetGameRating = onSetGameRating,
            onSetGameOwned = onSetGameOwned
        )
    }
}

@Composable
private fun TasteProfileBentoCard(
    tasteBars: List<TasteGenreBar>,
    ownedCount: Int,
    backlogCount: Int,
    ratedCount: Int,
    geminiEnabled: Boolean,
    activeFilter: LibraryFilter,
    onSelectStatFilter: (LibraryFilter) -> Unit,
    onOpenAddGameSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val goldAccent = if (isDark) WarmGoldDark else WarmGoldLight

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.55f else 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (geminiEnabled) {
                        "PROFILO RACCOMANDAZIONI · GEMINI"
                    } else {
                        "PROFILO RACCOMANDAZIONI · LOCALE"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (tasteBars.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Valuta 1–5★ i tuoi titoli per visualizzare qui i generi che guidano i consigli.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TextButton(
                        onClick = onOpenAddGameSheet,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ Cerca",
                            color = EshopCrimson,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tasteBars.forEachIndexed { index, bar ->
                        val barColor = when {
                            !bar.isPositive -> MaterialTheme.colorScheme.onSurfaceVariant
                            index == 0 -> EshopCrimson
                            index == 1 -> EshopCrimsonSoft
                            else -> BarAmber
                        }
                        TasteGenreBarRow(
                            bar = bar,
                            barColor = barColor,
                            goldAccent = goldAccent
                        )
                    }
                }
            }

            // Compact bottom stat pills inside the Bento card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BentoStatPill(
                    text = "$ownedCount Posseduti",
                    selected = activeFilter == LibraryFilter.OWNED,
                    onClick = { onSelectStatFilter(LibraryFilter.OWNED) },
                    modifier = Modifier.weight(1f)
                )
                BentoStatPill(
                    text = "$backlogCount Da giocare",
                    selected = activeFilter == LibraryFilter.BACKLOG,
                    onClick = { onSelectStatFilter(LibraryFilter.BACKLOG) },
                    modifier = Modifier.weight(1f)
                )
                BentoStatPill(
                    text = "$ratedCount Votati",
                    selected = activeFilter == LibraryFilter.LIKED,
                    onClick = { onSelectStatFilter(LibraryFilter.LIKED) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TasteGenreBarRow(
    bar: TasteGenreBar,
    barColor: Color,
    goldAccent: Color
) {
    val animatedFraction by animateFloatAsState(
        targetValue = (bar.percent / 100f).coerceIn(0.18f, 1f),
        animationSpec = tween(durationMillis = 360),
        label = "taste_bar_fill"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = bar.label,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(88.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(barColor.copy(alpha = 0.16f))
        ) {
            val showLabelInside = animatedFraction >= 0.34f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(RoundedCornerShape(999.dp))
                    .background(barColor),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (showLabelInside) {
                    val labelColor = if (barColor == BarAmber || barColor == EshopCrimsonSoft) {
                        Color(0xFF14161B)
                    } else {
                        Color.White
                    }
                    Text(
                        text = "${bar.percent}%",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = labelColor,
                        modifier = Modifier.padding(end = 8.dp),
                        maxLines = 1
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.width(46.dp)
        ) {
            Text(
                text = String.format(Locale.US, "%.1f", bar.avgStars),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = if (bar.isPositive) goldAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun BentoStatPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) {
        EshopCrimson.copy(alpha = 0.75f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
    }
    val bgColor = if (selected) {
        EshopCrimson.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = if (selected) EshopCrimson else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibraryFilterStrip(
    activeFilter: LibraryFilter,
    onSelectFilter: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryFilter.entries.forEach { filter ->
            val selected = activeFilter == filter
            val bg = if (selected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                Color.Transparent
            }
            val borderColor = if (selected) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)
            }

            Box(
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(999.dp))
                    .clickable { onSelectFilter(filter) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = filter.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryMatrixRow(
    game: LikedGame,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSetRating: (Int?) -> Unit,
    onToggleOwned: () -> Unit,
    onSetNote: (String?) -> Unit,
    onRemove: () -> Unit,
    onOpenEshop: (() -> Unit)?
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val goldStarColor = if (isDark) WarmGoldDark else WarmGoldLight
    val keyboard = LocalSoftwareKeyboardController.current
    var noteDraft by remember(game.id, game.userNote) { mutableStateOf(game.userNote.orEmpty()) }
    val savedNote = game.userNote.orEmpty()
    val isNoteDirty = noteDraft.trim() != savedNote.trim()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Crisp vertical cover artwork thumbnail
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(66.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        width = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!game.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = game.imageUrl,
                        contentDescription = game.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.CollectionsBookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title + metadata line
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val genreText = game.steamTagNames
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString(" · ")
                    .ifBlank { game.gameCategories.take(2).joinToString(" · ") }
                    .ifBlank { game.publisher }

                val statusSuffix = when {
                    game.isNegativeRating -> "Sconsigliato all'IA"
                    game.isOwned && game.rating == null -> "Da giocare"
                    else -> null
                }

                val metaLine = listOfNotNull(
                    genreText.takeIf { it.isNotBlank() },
                    statusSuffix
                ).joinToString(" · ")

                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (game.isNegativeRating) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val savedQuote = game.userNote?.trim()?.takeIf { it.isNotEmpty() }
                if (savedQuote != null && !expanded) {
                    Text(
                        text = "\"$savedQuote\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Rating pill (tap to expand inline 5-star editor)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val rating = game.rating
                if (rating != null) {
                    val pillBg = when {
                        rating >= 4 -> WarmGoldDark.copy(alpha = if (isDark) 0.16f else 0.14f)
                        rating == 3 -> MaterialTheme.colorScheme.surfaceContainerHighest
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val starColor = when {
                        rating >= 4 -> goldStarColor
                        rating == 3 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(pillBg)
                            .clickable(onClick = onToggleExpand)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = starColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = rating.toString(),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (rating >= 4) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onToggleExpand)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Vota",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // 1-tap Owned Bookmark Toggle (44dp target)
                IconButton(
                    onClick = onToggleOwned,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (game.isOwned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (game.isOwned) "Posseduto" else "Segna come posseduto",
                        tint = if (game.isOwned) EshopCrimson else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }

        // Progressive inline rating, personal comment & quick-actions strip
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 66.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RatingStarBar(
                        rating = game.rating,
                        onSelectRating = { clicked ->
                            onSetRating(if (clicked == game.rating) null else clicked)
                        },
                        starSize = 30.dp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (onOpenEshop != null) {
                            IconButton(
                                onClick = onOpenEshop,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Apri su eShop",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Rimuovi dalla libreria",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { if (it.length <= 240) noteDraft = it },
                    label = { Text("Perché questo voto? (commento per l'IA)") },
                    placeholder = { Text("Es. adoro il combat tattico, troppo grinding...") },
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboard?.hide()
                            if (isNoteDirty) {
                                onSetNote(noteDraft.trim().takeIf { it.isNotEmpty() })
                            }
                        }
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isNoteDirty) {
                                IconButton(
                                    onClick = {
                                        keyboard?.hide()
                                        onSetNote(noteDraft.trim().takeIf { it.isNotEmpty() })
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Salva commento",
                                        tint = EshopCrimson
                                    )
                                }
                            }
                            if (noteDraft.isNotBlank() || savedNote.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        noteDraft = ""
                                        if (savedNote.isNotBlank()) {
                                            onSetNote(null)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Cancella commento"
                                    )
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LibraryEmptyState(
    hasAnyGames: Boolean,
    onOpenAddGameSheet: () -> Unit,
    onResetFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CollectionsBookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(30.dp)
            )
            Text(
                text = if (!hasAnyGames) {
                    "La tua Libreria è vuota"
                } else {
                    "Nessun titolo in questo filtro"
                },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = if (!hasAnyGames) {
                    "Aggiungi i giochi che hai giocato o possiedi per escluderli dagli sconti e calibrare il profilo gusto."
                } else {
                    "Cambia filtro sopra o cerca un nuovo titolo Switch da aggiungere alla collezione."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (!hasAnyGames) {
                Button(
                    onClick = onOpenAddGameSheet,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EshopCrimson,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aggiungi gioco")
                }
            } else {
                OutlinedButton(
                    onClick = onResetFilter,
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text("Mostra tutti")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGameSearchSheet(
    state: MainUiState,
    onDismiss: () -> Unit,
    onSearchGames: (String) -> Unit,
    onClearSearchResults: () -> Unit,
    onSetGameRating: (LikedGame, Int?) -> Unit,
    onSetGameOwned: (LikedGame, Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchInput by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val existingByNormTitle = remember(state.settings.likedGames) {
        state.settings.likedGames.associateBy { DealTitleNormalizer.normalize(it.title) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Aggiungi alla Libreria",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Cerca titoli Switch / Switch 2 su eShop: dai un voto da 1 a 5★ oppure segnali come già posseduti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    placeholder = { Text("Es. Hades, Zelda, Balatro...") },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchInput.trim().length >= 2) {
                                keyboardController?.hide()
                                onSearchGames(searchInput)
                            }
                        }
                    ),
                    trailingIcon = {
                        if (searchInput.isNotEmpty() || state.profileSearchResults.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchInput = ""
                                    onClearSearchResults()
                                }
                            ) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Pulisci ricerca")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                FilledTonalButton(
                    onClick = {
                        keyboardController?.hide()
                        onSearchGames(searchInput)
                    },
                    enabled = !state.isSearchingProfile && searchInput.trim().length >= 2,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    if (state.isSearchingProfile) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Cerca",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (state.profileSearchResults.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                ) {
                    itemsIndexed(
                        items = state.profileSearchResults,
                        key = { _, candidate -> candidate.id }
                    ) { idx, candidate ->
                        val saved = existingByNormTitle[DealTitleNormalizer.normalize(candidate.title)]
                        val effectiveRating = saved?.rating
                        val effectiveOwned = saved?.isOwned == true

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(46.dp)
                                        .height(58.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!candidate.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = candidate.imageUrl,
                                            contentDescription = candidate.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.CollectionsBookmark,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val tags = candidate.steamTagNames.filter { it.isNotBlank() }.take(3)
                                        .ifEmpty { candidate.gameCategories.take(2) }
                                    val meta = listOfNotNull(
                                        candidate.publisher.takeIf { it.isNotBlank() },
                                        tags.joinToString(" · ").takeIf { it.isNotBlank() }
                                    ).joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            text = meta,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { onSetGameOwned(saved ?: candidate, !effectiveOwned) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (effectiveOwned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                                        contentDescription = if (effectiveOwned) "Posseduto" else "Segna come posseduto",
                                        tint = if (effectiveOwned) EshopCrimson else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RatingStarBar(
                                    rating = effectiveRating,
                                    onSelectRating = { star ->
                                        val next = if (star == effectiveRating) null else star
                                        onSetGameRating(saved ?: candidate, next)
                                    },
                                    starSize = 28.dp
                                )
                                if (effectiveOwned) {
                                    Text(
                                        text = "Posseduto",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = EshopCrimson
                                    )
                                }
                            }
                        }

                        if (idx < state.profileSearchResults.lastIndex) {
                            HorizontalDivider(
                                thickness = 0.8.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
                            )
                        }
                    }
                }
            }
        }
    }
}
