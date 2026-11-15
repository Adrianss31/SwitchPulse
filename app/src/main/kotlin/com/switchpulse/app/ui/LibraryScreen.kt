package com.switchpulse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.ui.theme.EshopCrimson

private enum class LibraryFilter(val label: String) {
    ALL("Tutti"),
    OWNED("Posseduti"),
    BACKLOG("Da giocare"),
    LIKED("Apprezzati 4–5★"),
    DISLIKED("Sconsigliati 1–2★")
}

@Composable
fun LibraryScreen(
    state: MainUiState,
    onSearchGames: (String) -> Unit,
    onClearSearchResults: () -> Unit,
    onSetGameRating: (LikedGame, Int?) -> Unit,
    onSetGameOwned: (LikedGame, Boolean) -> Unit,
    onRemoveGame: (String) -> Unit,
    onOpenEshop: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchInput by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(LibraryFilter.ALL) }

    val library = state.settings.likedGames
    val existingByNormTitle = remember(library) {
        library.associateBy { DealTitleNormalizer.normalize(it.title) }
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Collection summary header
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    LibraryStat("In libreria", library.size)
                    LibraryStat("Posseduti", ownedCount)
                    LibraryStat("Da giocare", backlogCount)
                    LibraryStat("Votati", ratedCount)
                }
            }
        }

        // Add / rate game search card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Aggiungi o valuta un gioco",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Cerca qualsiasi titolo Switch / Switch 2: puoi segnarlo come già posseduto (verrà escluso dagli sconti) o dargli un voto da 1 a 5★ per guidare i consigli.",
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
                            label = { Text("Titolo (es. Hades, Zelda, Hollow Knight)") },
                            singleLine = true,
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
                            onClick = { onSearchGames(searchInput) },
                            enabled = !state.isSearchingProfile && searchInput.trim().length >= 2
                        ) {
                            if (state.isSearchingProfile) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Search, contentDescription = "Cerca", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    if (state.profileSearchResults.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        state.profileSearchResults.forEachIndexed { idx, candidate ->
                            val saved = existingByNormTitle[DealTitleNormalizer.normalize(candidate.title)]
                            val effectiveRating = saved?.rating
                            val effectiveOwned = saved?.isOwned == true

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (!candidate.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = candidate.imageUrl,
                                            contentDescription = candidate.title,
                                            modifier = Modifier
                                                .width(64.dp)
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
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
                                        starSize = 24.dp
                                    )

                                    FilterChip(
                                        selected = effectiveOwned,
                                        onClick = { onSetGameOwned(saved ?: candidate, !effectiveOwned) },
                                        label = { Text(if (effectiveOwned) "Posseduto ✓" else "+ Posseduto") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = EshopCrimson,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                            if (idx < state.profileSearchResults.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        // Filter row for saved collection
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryFilter.entries.forEach { filter ->
                    val selected = activeFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { activeFilter = filter },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EshopCrimson,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }

        if (filteredItems.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CollectionsBookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = if (library.isEmpty()) {
                                "La tua Libreria è vuota"
                            } else {
                                "Nessun gioco corrisponde al filtro selezionato"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Cerca un titolo qui sopra oppure tocca una qualsiasi offerta nella classifica per dargli da 1 a 5 stelle o segnarlo come posseduto.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredItems, key = { it.id }) { item ->
                LibraryGameCard(
                    game = item,
                    onSetRating = { nextRating -> onSetGameRating(item, nextRating) },
                    onToggleOwned = { onSetGameOwned(item, !item.isOwned) },
                    onRemove = { onRemoveGame(item.id) },
                    onOpenEshop = item.eshopUrl?.let { url -> { onOpenEshop(url) } }
                )
            }
        }
    }
}

@Composable
private fun LibraryStat(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = if (count > 0) EshopCrimson else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LibraryGameCard(
    game: LikedGame,
    onSetRating: (Int?) -> Unit,
    onToggleOwned: () -> Unit,
    onRemove: () -> Unit,
    onOpenEshop: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(68.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                            Icons.Rounded.CollectionsBookmark,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = game.steamTagNames.filter { it.isNotBlank() }.take(3).joinToString(" · ")
                        .ifBlank { game.gameCategories.take(2).joinToString(" · ") }
                        .ifBlank { game.publisher }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (onOpenEshop != null) {
                    IconButton(onClick = onOpenEshop, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Apri su eShop",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Rimuovi dalla libreria",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

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
                    starSize = 24.dp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = game.isOwned,
                        onClick = onToggleOwned,
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Inventory2,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        label = { Text(if (game.isOwned) "Posseduto" else "Non posseduto") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EshopCrimson,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }
        }
    }
}
