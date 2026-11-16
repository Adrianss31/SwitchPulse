package com.switchpulse.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.GamerSoulProfile
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.ui.theme.EshopCrimson
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDiscoverySheet(
    soulProfile: GamerSoulProfile?,
    picks: List<AiDiscoveryPick>,
    library: List<LikedGame>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefreshPicks: () -> Unit,
    onOpenEshop: (String) -> Unit,
    onToggleOwned: (LikedGame, Boolean) -> Unit,
    onQuickRate: (LikedGame, Int) -> Unit,
    onDismissPick: (String) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val libraryByNorm = remember(library) {
        library.associateBy { DealTitleNormalizer.normalize(it.title) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = EshopCrimson,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = soulProfile?.archetypeTitle?.ifBlank { "Scoperte per la tua Anima" }
                                ?: "Scoperte per la tua Anima",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "Titoli Switch fuori dalla tua libreria (anche non in sconto)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshPicks,
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EshopCrimson)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Trova altri titoli")
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Chiudi")
                    }
                }
            }

            if (isLoading && picks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = EshopCrimson)
                        Text(
                            text = "Analizzo la tua anima e cerco su eShop Italia...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (picks.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Nessun consiglio ancora generato.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onRefreshPicks,
                            colors = ButtonDefaults.buttonColors(containerColor = EshopCrimson)
                        ) {
                            Text("Genera 7 consigli adesso")
                        }
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(picks, key = { it.title }) { pick ->
                        val existing = libraryByNorm[DealTitleNormalizer.normalize(pick.title)]
                        DiscoveryPickCard(
                            pick = pick,
                            existingLibraryEntry = existing,
                            onOpenEshop = {
                                val target = pick.eshopUrl?.takeIf { it.startsWith("http") }
                                    ?: "https://www.nintendo.com/it-it/Search/Search-299117.html?q=${android.net.Uri.encode(pick.title)}"
                                onOpenEshop(target)
                            },
                            onToggleOwned = {
                                val entry = existing ?: pick.toLikedGame()
                                onToggleOwned(entry, !(existing?.isOwned ?: false))
                            },
                            onQuickRate = {
                                val entry = existing ?: pick.toLikedGame()
                                onQuickRate(entry, 5)
                            },
                            onDismissPick = { onDismissPick(pick.title) }
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = onRefreshPicks,
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isLoading) "Cerco altri giochi..." else "Proponi altri titoli diversi")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryPickCard(
    pick: AiDiscoveryPick,
    existingLibraryEntry: LikedGame?,
    onOpenEshop: () -> Unit,
    onToggleOwned: () -> Unit,
    onQuickRate: () -> Unit,
    onDismissPick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(54.dp)
                        .height(68.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!pick.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = pick.imageUrl,
                            contentDescription = pick.title,
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

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = pick.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pick.publisher.isNotBlank()) {
                        Text(
                            text = pick.publisher,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (pick.pitchHook.isNotBlank()) {
                        Text(
                            text = pick.pitchHook,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = EshopCrimson,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (pick.discountPercent > 0) {
                        Surface(shape = RoundedCornerShape(6.dp), color = EshopCrimson) {
                            Text(
                                text = "-${pick.discountPercent}%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                    val cur = pick.currentPriceEur
                    val reg = pick.regularPriceEur
                    if (cur != null) {
                        Text(
                            text = String.format(Locale.ITALY, "%.2f €", cur),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold)
                        )
                        if (pick.discountPercent > 0 && reg != null) {
                            Text(
                                text = String.format(Locale.ITALY, "%.2f €", reg),
                                style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.LineThrough),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (pick.estimatedPriceHint.isNotBlank()) {
                        Text(
                            text = pick.estimatedPriceHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (pick.reason.isNotBlank()) {
                Text(
                    text = pick.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenEshop,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("eShop", fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                }

                val isOwned = existingLibraryEntry?.isOwned == true
                FilledTonalButton(
                    onClick = onToggleOwned,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isOwned) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                        tint = if (isOwned) EshopCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isOwned) "In libreria" else "Possiedo", fontSize = 12.sp)
                }

                val isRated = existingLibraryEntry?.rating != null
                FilledTonalButton(
                    onClick = onQuickRate,
                    enabled = !isRated,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = existingLibraryEntry?.rating?.let { "${it}★" } ?: "+ 5★",
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = onDismissPick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Scarta suggerimento",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
