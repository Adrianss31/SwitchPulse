package com.switchpulse.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switchpulse.app.R
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.ui.components.Cover
import com.switchpulse.app.ui.components.PrimaryButton
import com.switchpulse.app.ui.components.StarRating
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameSheet(
    state: MainUiState,
    onDismiss: () -> Unit,
    onSearchGames: (String) -> Unit,
    onClearSearchResults: () -> Unit,
    onSaveGameToLibrary: (game: LikedGame, rating: Int?, owned: Boolean, note: String?) -> Unit
) {
    val c = SpTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    var expandedResultId by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            delay(350L)
            onSearchGames(trimmed)
        } else {
            onClearSearchResults()
        }
    }

    val existingByNorm = remember(state.settings.likedGames) {
        state.settings.likedGames.associateBy { DealTitleNormalizer.normalize(it.title) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.paper,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.add_game_title),
                    style = SpType.sheetTitle,
                    color = c.ink
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = c.ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 1.5 dp ink border search box, autofocused
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.5.dp, c.ink, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = c.ink,
                    modifier = Modifier.size(18.dp)
                )
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = SpType.body.copy(fontSize = 15.sp, color = c.ink),
                    cursorBrush = SolidColor(c.ink),
                    decorationBox = { inner ->
                        if (query.isEmpty()) {
                            Text(
                                text = stringResource(R.string.add_game_search_hint),
                                style = SpType.body.copy(fontSize = 15.sp),
                                color = c.muted
                            )
                        }
                        inner()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )
                if (state.isSearchingProfile) {
                    CircularProgressIndicator(
                        color = c.ink,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_clear),
                        tint = c.muted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                query = ""
                                expandedResultId = null
                            }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.add_game_search_help),
                style = SpType.caption,
                color = c.muted
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                items(
                    items = state.profileSearchResults,
                    key = { it.id }
                ) { candidate ->
                    val norm = DealTitleNormalizer.normalize(candidate.title)
                    val existing = existingByNorm[norm]
                    val isExpanded = expandedResultId == candidate.id

                    AddGameResultRow(
                        candidate = candidate,
                        existing = existing,
                        expanded = isExpanded,
                        onExpand = { expandedResultId = if (isExpanded) null else candidate.id },
                        onSave = { rating, owned, note ->
                            onSaveGameToLibrary(candidate, rating, owned, note)
                            expandedResultId = null
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = c.line)
                }
            }
        }
    }
}

@Composable
private fun AddGameResultRow(
    candidate: LikedGame,
    existing: LikedGame?,
    expanded: Boolean,
    onExpand: () -> Unit,
    onSave: (rating: Int?, owned: Boolean, note: String?) -> Unit
) {
    val c = SpTheme.colors
    var draftRating by remember(candidate.id, existing) { mutableStateOf(existing?.rating) }
    var draftOwned by remember(candidate.id, existing) { mutableStateOf(existing?.isOwned ?: true) }
    var draftNote by remember(candidate.id, existing) { mutableStateOf(existing?.userNote.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Cover(
                url = candidate.imageUrl,
                title = candidate.title,
                radius = 3.dp,
                modifier = Modifier.width(80.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.title,
                    style = SpType.rowTitle,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = candidate.publisher.takeIf { it.isNotBlank() } ?: "Switch"
                Text(
                    text = meta,
                    style = SpType.caption,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (existing != null && !expanded) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(c.pressed)
                        .clickable(onClick = onExpand)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.add_game_added),
                        style = SpType.captionBold,
                        color = c.ink
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(1.dp, c.border), CircleShape)
                        .clickable(onClick = onExpand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.library_add),
                        tint = c.ink,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(Motion.standard()) + fadeIn(),
            exit = shrinkVertically(Motion.quick())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StarRating(
                        rating = draftRating,
                        onRate = { draftRating = it },
                        starSize = 26.dp,
                        touchSize = 44.dp
                    )
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (draftOwned) c.ink else Color.Transparent)
                            .border(
                                BorderStroke(1.dp, if (draftOwned) c.ink else c.border),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { draftOwned = !draftOwned }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (draftOwned) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = c.paper,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.library_filter_owned),
                            style = SpType.captionBold.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = if (draftOwned) c.paper else c.ink
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.add_game_note_prompt),
                        style = SpType.captionBold.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = c.ink
                    )
                    Text(
                        text = stringResource(R.string.add_game_note_help),
                        style = SpType.caption.copy(fontSize = 13.sp),
                        color = c.muted
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = draftNote,
                    onValueChange = { if (it.length <= 240) draftNote = it },
                    minLines = 2,
                    maxLines = 2,
                    shape = RoundedCornerShape(4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.ink,
                        unfocusedBorderColor = c.border,
                        focusedTextColor = c.ink,
                        unfocusedTextColor = c.ink
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                PrimaryButton(
                    text = stringResource(R.string.add_game_cta),
                    onClick = {
                        onSave(
                            draftRating,
                            draftOwned,
                            draftNote.trim().takeIf { it.isNotEmpty() }
                        )
                    },
                    enabled = draftRating != null || draftOwned || draftNote.isNotBlank(),
                    height = 48.dp
                )
            }
        }
    }
}
