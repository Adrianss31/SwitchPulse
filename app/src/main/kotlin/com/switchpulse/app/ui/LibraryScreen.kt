package com.switchpulse.app.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switchpulse.app.R
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.RecommendationEngine
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.ui.components.Cover
import com.switchpulse.app.ui.components.EmptyState
import com.switchpulse.app.ui.components.SectionLabel
import com.switchpulse.app.ui.components.StatTrio
import com.switchpulse.app.ui.components.SwipeActionBackground
import com.switchpulse.app.ui.components.TasteBars
import com.switchpulse.app.ui.components.UnderlineTabs
import com.switchpulse.app.ui.motion.LocalReduceMotion
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.motion.hapticConfirm
import com.switchpulse.app.ui.motion.hapticTick
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class LibraryFilterTab {
    ALL,
    OWNED,
    BACKLOG,
    LIKED,
    DISLIKED
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    state: MainUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenSettings: () -> Unit,
    onOpenAddGameSheet: () -> Unit,
    onOpenLibraryDetail: (LikedGame) -> Unit,
    onSwipeRemoveGame: (LikedGame) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState()
) {
    val c = SpTheme.colors
    var activeFilter by remember { mutableStateOf(LibraryFilterTab.ALL) }

    val library = state.settings.likedGames
    val tasteBars = remember(library) {
        RecommendationEngine.computeTasteProfile(library, limit = 3)
    }

    val filteredItems = remember(library, activeFilter) {
        when (activeFilter) {
            LibraryFilterTab.ALL -> library
            LibraryFilterTab.OWNED -> library.filter { it.isOwned }
            LibraryFilterTab.BACKLOG -> library.filter { it.isOwned && it.rating == null }
            LibraryFilterTab.LIKED -> library.filter { it.isPositiveRating }
            LibraryFilterTab.DISLIKED -> library.filter { it.isNegativeRating }
        }
    }

    val ownedCount = library.count { it.isOwned }
    val backlogCount = library.count { it.isOwned && it.rating == null }
    val ratedCount = library.count { it.rating != null }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(c.paper)
            .windowInsetsPadding(WindowInsets.statusBars),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // Header: "Libreria" + gear icon + black "+ Aggiungi" pill
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.library_title),
                    style = SpType.screenTitle,
                    color = c.ink,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clickable(onClick = onOpenSettings),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.library_settings_cd),
                            tint = c.ink,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(c.ink)
                            .clickable(onClick = onOpenAddGameSheet)
                            .padding(start = 10.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = c.paper,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.library_add),
                            style = SpType.tab.copy(fontWeight = FontWeight.Bold),
                            color = c.paper
                        )
                    }
                }
            }
        }

        // Stat Trio
        item(key = "stats") {
            Spacer(modifier = Modifier.height(18.dp))
            StatTrio(
                owned = ownedCount,
                backlog = backlogCount,
                rated = ratedCount,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // Taste profile bars
        if (tasteBars.isNotEmpty()) {
            item(key = "taste_bars") {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionLabel(text = stringResource(R.string.library_taste_section))
                    Spacer(modifier = Modifier.height(10.dp))
                    TasteBars(bars = tasteBars)
                }
            }
        }

        // Filter underline tabs
        item(key = "filter_tabs") {
            Spacer(modifier = Modifier.height(18.dp))
            val tabLabels = listOf(
                stringResource(R.string.library_filter_all),
                stringResource(R.string.library_filter_owned),
                stringResource(R.string.library_filter_backlog),
                stringResource(R.string.library_filter_liked),
                stringResource(R.string.library_filter_disliked)
            )
            UnderlineTabs(
                items = tabLabels,
                selectedIndex = activeFilter.ordinal,
                onSelect = { idx -> activeFilter = LibraryFilterTab.entries[idx] },
                scrollable = true,
                horizontalPadding = 20.dp
            )
        }

        if (filteredItems.isEmpty()) {
            item(key = "empty") {
                Spacer(modifier = Modifier.height(12.dp))
                if (library.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.library_empty_title),
                        body = stringResource(R.string.library_empty_body),
                        actionLabel = stringResource(R.string.library_empty_cta),
                        onAction = onOpenAddGameSheet,
                        centered = false,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .background(c.bannerBg)
                    )
                } else {
                    EmptyState(
                        title = "Nessun gioco in questa scheda",
                        body = "Cambia filtro per vedere il resto della libreria.",
                        actionLabel = stringResource(R.string.library_filter_all),
                        onAction = { activeFilter = LibraryFilterTab.ALL },
                        outlinedCta = true
                    )
                }
            }
        } else {
            items(
                items = filteredItems,
                key = { it.id }
            ) { item ->
                SwipeRemoveLibraryRow(
                    game = item,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = { onOpenLibraryDetail(item) },
                    onSwipeRemove = { onSwipeRemoveGame(item) },
                    modifier = Modifier.animateItem(placementSpec = tween(240, easing = Motion.Out))
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SwipeRemoveLibraryRow(
    game: LikedGame,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onSwipeRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    val thresholdPx = with(density) { 96.dp.toPx() }
    val dampStartPx = with(density) { 150.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (offsetX.value < -2f) {
                SwipeActionBackground(
                    isRightSwipe = false,
                    armed = armed,
                    rightActionLabel = stringResource(R.string.action_remove),
                    modifier = Modifier.matchParentSize()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .background(c.paper)
                    .clickable(onClick = onClick)
                    .pointerInput(game.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (offsetX.value <= -thresholdPx) {
                                    view.hapticConfirm()
                                    scope.launch {
                                        offsetX.animateTo(-size.width.toFloat(), Motion.exit(reduceMotion))
                                        onSwipeRemove()
                                        offsetX.snapTo(0f)
                                    }
                                } else {
                                    scope.launch { offsetX.animateTo(0f, Motion.settle()) }
                                }
                                armed = false
                            },
                            onDragCancel = {
                                armed = false
                                scope.launch { offsetX.animateTo(0f, Motion.settle()) }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val rawNext = (offsetX.value + dragAmount).coerceAtMost(0f)
                                val damped = if (abs(rawNext) > dampStartPx) {
                                    -(dampStartPx + (abs(rawNext) - dampStartPx) * 0.35f)
                                } else {
                                    rawNext
                                }
                                val nowArmed = damped <= -thresholdPx
                                if (nowArmed != armed) {
                                    armed = nowArmed
                                    if (nowArmed) view.hapticTick()
                                }
                                scope.launch { offsetX.snapTo(damped) }
                            }
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                with(sharedTransitionScope) {
                    Cover(
                        url = game.imageUrl,
                        title = game.title,
                        radius = 3.dp,
                        modifier = Modifier
                            .width(80.dp)
                            .sharedBounds(
                                sharedContentState = rememberSharedContentState(key = "library-${game.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = { _, _ -> Motion.emphasized(reduceMotion) },
                                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                                    ContentScale.Crop,
                                    Alignment.Center
                                )
                            )
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = game.title,
                        style = SpType.rowTitle,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val rating = game.rating
                    val statusTag = when {
                        game.isOwned && rating == null -> stringResource(R.string.library_tag_backlog)
                        game.isOwned -> stringResource(R.string.library_tag_owned)
                        game.targetPriceEur != null -> "< ${DealFilterEngine.formatEur(game.targetPriceEur)}"
                        else -> null
                    }
                    val metaAnnotated = buildAnnotatedString {
                        if (rating != null) {
                            withStyle(SpanStyle(color = c.ink, letterSpacing = 1.sp)) {
                                append("★".repeat(rating))
                            }
                            withStyle(SpanStyle(color = c.starDim, letterSpacing = 1.sp)) {
                                append("★".repeat((5 - rating).coerceAtLeast(0)))
                            }
                        }
                        if (statusTag != null) {
                            withStyle(SpanStyle(color = c.muted)) {
                                if (rating != null) append(" · ")
                                append(statusTag)
                            }
                        }
                    }
                    if (metaAnnotated.isNotEmpty()) {
                        Text(
                            text = metaAnnotated,
                            style = SpType.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!game.userNote.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubbleOutline,
                        contentDescription = stringResource(R.string.library_has_comment_cd),
                        tint = c.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = c.line,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}
