package com.switchpulse.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import com.switchpulse.app.R
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.DealMatchSentiment
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.FeedSortMode
import com.switchpulse.app.data.model.PlatformFilter
import com.switchpulse.app.ui.components.ActionBanner
import com.switchpulse.app.ui.components.Cover
import com.switchpulse.app.ui.components.CutoutCorner
import com.switchpulse.app.ui.components.EditorialIndeterminateBar
import com.switchpulse.app.ui.components.EmptyState
import com.switchpulse.app.ui.components.MatchLine
import com.switchpulse.app.ui.components.PriceLine
import com.switchpulse.app.ui.components.PriceSize
import com.switchpulse.app.ui.components.RankCutout
import com.switchpulse.app.ui.components.RankDelta
import com.switchpulse.app.ui.components.RankNumber
import com.switchpulse.app.ui.components.RatingPopover
import com.switchpulse.app.ui.components.SkeletonBox
import com.switchpulse.app.ui.components.SwipeActionBackground
import com.switchpulse.app.ui.components.UnderlineTabs
import com.switchpulse.app.ui.motion.LocalReduceMotion
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.motion.hapticConfirm
import com.switchpulse.app.ui.motion.hapticTick
import com.switchpulse.app.ui.motion.pressScale
import com.switchpulse.app.ui.theme.Bricolage
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private var sessionInitialEntranceDone = false

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    state: MainUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onSelectPlatform: (PlatformFilter) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenDealDetail: (Deal, Int) -> Unit,
    onSwipeHideDeal: (Deal) -> Unit,
    onSwipeOwnedDeal: (Deal) -> Unit,
    onQuickRateDeal: (Deal, Int) -> Unit,
    onRefresh: () -> Unit,
    onResetFilters: () -> Unit,
    onRerankMissingDeals: () -> Unit,
    onRerankAllNow: () -> Unit,
    onOpenUpdateSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val view = LocalView.current
    val density = LocalDensity.current
    val reduceMotion = LocalReduceMotion.current

    val ratedMap = remember(state.settings.likedGames) {
        state.settings.likedGames
            .filter { it.rating != null }
            .associateBy { DealTitleNormalizer.normalize(it.title) }
    }
    val hasTasteSignals = remember(state.settings.likedGames, state.settings.userTastePrompt) {
        state.settings.likedGames.any { it.isPositiveRating || it.isNegativeRating } ||
            state.settings.userTastePrompt.isNotBlank()
    }

    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var popoverTargetDeal by remember { mutableStateOf<Deal?>(null) }
    var recentlyRatedBadgeId by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            focusRequester.requestFocus()
        }
    }

    val displayedDeals = remember(state.deals, state.allEligibleDeals, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            state.deals
        } else {
            val pool = state.allEligibleDeals.ifEmpty { state.deals }
            pool.filter { deal ->
                deal.title.lowercase().contains(q) ||
                    deal.displayPublisher.lowercase().contains(q) ||
                    (deal.developer?.lowercase()?.contains(q) == true) ||
                    deal.steamTagNames.any { it.lowercase().contains(q) } ||
                    deal.gameCategories.any { it.lowercase().contains(q) }
            }
        }
    }

    // Header collapse tracking (100% scroll-driven, pixel-faithful)
    val maxCollapsePx = with(density) { 48.dp.toPx() }
    var headerCollapsePx by remember { mutableFloatStateOf(0f) }
    val nestedScrollConnection = remember(maxCollapsePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (searchExpanded) return Offset.Zero
                val delta = available.y
                val next = (headerCollapsePx - delta).coerceIn(0f, maxCollapsePx)
                val consumed = headerCollapsePx - next
                headerCollapsePx = next
                return Offset(0f, consumed)
            }
        }
    }
    val collapseFraction = if (searchExpanded || maxCollapsePx <= 0f) 0f else (headerCollapsePx / maxCollapsePx).coerceIn(0f, 1f)

    // Track pull-to-refresh threshold haptic
    val pullState = rememberPullToRefreshState()
    var triggeredPullHaptic by remember { mutableStateOf(false) }
    LaunchedEffect(pullState.distanceFraction) {
        if (pullState.distanceFraction >= 1f && !triggeredPullHaptic) {
            triggeredPullHaptic = true
            view.hapticTick()
        } else if (pullState.distanceFraction < 0.8f) {
            triggeredPullHaptic = false
        }
    }

    var playInitialEntrance by remember { mutableStateOf(!sessionInitialEntranceDone && !reduceMotion && displayedDeals.isNotEmpty()) }
    LaunchedEffect(displayedDeals.isNotEmpty()) {
        if (displayedDeals.isNotEmpty() && !sessionInitialEntranceDone) {
            delay(500L)
            sessionInitialEntranceDone = true
            playInitialEntrance = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.paper)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
        ) {
            // Top editorial brand + utility buttons OR full-width search field
            if (searchExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .background(Color.Transparent)
                            .padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = c.ink,
                            modifier = Modifier.size(20.dp)
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = SpType.body.copy(fontSize = 15.sp, color = c.ink),
                            cursorBrush = SolidColor(c.ink),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.ranking_search_placeholder),
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
                    }
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clickable {
                                searchQuery = ""
                                searchExpanded = false
                            }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
                            style = SpType.tab.copy(fontWeight = FontWeight.SemiBold),
                            color = c.ink
                        )
                    }
                }
            } else {
                val topRowAlpha = (1f - collapseFraction * 1.4f).coerceIn(0f, 1f)
                if (topRowAlpha > 0.02f) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(lerp(44.dp, 0.dp, collapseFraction))
                            .graphicsLayer { alpha = topRowAlpha }
                            .padding(start = 20.dp, end = 12.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(c.accent)
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = SpType.rowPrice.copy(
                                    fontFamily = Bricolage,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                ),
                                color = c.ink
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { searchExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.ranking_search_cd),
                                    tint = c.ink,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable(onClick = onOpenFilters),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = stringResource(R.string.ranking_filters_cd),
                                    tint = c.ink,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (state.settings.hasNonDefaultFilters()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 9.dp, end = 9.dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(c.accent)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Display title ("La classifica") collapsing toward 18 sp + "ore HH:mm" or search/filters icons when collapsed
            val titleSize = lerp(40.sp, 18.sp, collapseFraction)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = lerp(2.dp, 8.dp, collapseFraction)),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.ranking_title),
                    style = SpType.screenTitle.copy(
                        fontSize = titleSize,
                        lineHeight = titleSize
                    ),
                    color = c.ink,
                    maxLines = 1
                )

                if (collapseFraction > 0.65f && !searchExpanded) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { searchExpanded = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = stringResource(R.string.ranking_search_cd),
                                tint = c.ink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable(onClick = onOpenFilters),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = stringResource(R.string.ranking_filters_cd),
                                tint = c.ink,
                                modifier = Modifier.size(20.dp)
                            )
                            if (state.settings.hasNonDefaultFilters()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 6.dp, end = 6.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(c.accent)
                                )
                            }
                        }
                    }
                } else {
                    val syncTime = formatTimeShort(state.settings.lastSyncEpochMs)
                    if (syncTime != null) {
                        Text(
                            text = "ore $syncTime",
                            style = SpType.caption.copy(fontSize = 12.sp),
                            color = c.muted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Platform underline tabs + sort trigger ("Per te v")
            val platformItems = listOf(
                stringResource(R.string.ranking_platform_all),
                stringResource(R.string.ranking_platform_sw2),
                stringResource(R.string.ranking_platform_sw1)
            )
            val platformValues = listOf(PlatformFilter.ALL, PlatformFilter.SWITCH_2, PlatformFilter.SWITCH_1)
            UnderlineTabs(
                items = platformItems,
                selectedIndex = platformValues.indexOf(state.settings.appPlatformFilter).coerceAtLeast(0),
                onSelect = { idx -> onSelectPlatform(platformValues[idx]) },
                trailing = {
                    val sortText = when (state.settings.feedSortMode) {
                        FeedSortMode.PERSONAL -> stringResource(R.string.ranking_sort_personal)
                        FeedSortMode.DISCOUNT -> stringResource(R.string.ranking_sort_discount)
                        FeedSortMode.PRICE_ASC -> stringResource(R.string.ranking_sort_price)
                    }
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clickable(onClick = onOpenFilters),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = sortText,
                            style = SpType.captionBold.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = c.ink
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = c.ink,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            )

            // Sync indeterminate bar directly under platform tabs
            if (state.isRefreshing && state.deals.isNotEmpty()) {
                EditorialIndeterminateBar()
            }

            // Contextual state banners (never shown unless actionable)
            val availableUpdate = state.updateState as? UpdateUiState.Available
            if (availableUpdate != null) {
                ActionBanner(
                    text = stringResource(R.string.ranking_banner_update, availableUpdate.release.tagName),
                    actionLabel = stringResource(R.string.ranking_banner_install),
                    onAction = onOpenUpdateSettings
                )
            } else if (state.isOfflineFallback) {
                val hhmm = formatTimeShort(state.settings.lastSyncEpochMs) ?: "--:--"
                ActionBanner(
                    text = stringResource(R.string.ranking_banner_offline, hhmm),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRefresh,
                    urgentOfflineIcon = true
                )
            } else if (state.settings.hasKeyFor(state.settings.rerankTarget.provider) && hasTasteSignals) {
                val aiError = state.settings.geminiStatusSummary.startsWith("⚠️")
                val missingAiCount = state.deals.count { it.aiAffinityScore == null }
                if (aiError) {
                    ActionBanner(
                        text = stringResource(R.string.ranking_banner_ai_error),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRerankAllNow
                    )
                } else if (missingAiCount > 0 && state.deals.isNotEmpty() && !state.isRefreshing) {
                    ActionBanner(
                        text = stringResource(R.string.ranking_banner_incomplete_ai, missingAiCount),
                        actionLabel = stringResource(R.string.ranking_banner_complete),
                        onAction = onRerankMissingDeals
                    )
                }
            }

            // Content body with PullToRefreshBox
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                state = pullState,
                indicator = {
                    val fraction = pullState.distanceFraction.coerceIn(0f, 1.3f)
                    if (fraction > 0.05f && !state.isRefreshing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = (8 + fraction * 18).dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(c.paper)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (fraction >= 1f) stringResource(R.string.action_refresh) else "01",
                                style = SpType.captionBold,
                                color = if (fraction >= 1f) c.accent else c.ink
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (state.isRefreshing && displayedDeals.isNotEmpty()) 0.5f else 1f }
            ) {
                AnimatedContent(
                    targetState = state.settings.appPlatformFilter,
                    transitionSpec = {
                        fadeIn(Motion.standard(reduceMotion)) togetherWith fadeOut(Motion.quick(reduceMotion))
                    },
                    label = "platform_feed_transition"
                ) { _ ->
                    when {
                        state.isRefreshing && state.deals.isEmpty() -> {
                            FeedLoadingSkeleton(modifier = Modifier.fillMaxSize())
                        }
                        displayedDeals.isEmpty() && searchQuery.isNotBlank() -> {
                            EmptyState(
                                title = stringResource(R.string.ranking_empty_search_title, searchQuery.trim()),
                                body = "",
                                actionLabel = stringResource(R.string.ranking_empty_search_cta),
                                onAction = { searchQuery = "" },
                                outlinedCta = true
                            )
                        }
                        displayedDeals.isEmpty() -> {
                            EmptyState(
                                title = stringResource(R.string.ranking_empty_filter_title),
                                body = stringResource(R.string.ranking_empty_filter_body),
                                actionLabel = stringResource(R.string.ranking_empty_filter_cta),
                                onAction = onResetFilters,
                                outlinedCta = true
                            )
                        }
                        searchQuery.isNotBlank() -> {
                            // Flat numbered search results across the entire eligible catalog
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                itemsIndexed(
                                    items = displayedDeals,
                                    key = { _, d -> d.id }
                                ) { idx, deal ->
                                    val rated = ratedMap[DealTitleNormalizer.normalize(deal.title)]?.rating
                                    SwipeableFeedContainer(
                                        onClick = { onOpenDealDetail(deal, idx + 1) },
                                        onLongPress = { popoverTargetDeal = deal },
                                        onSwipeRightOwned = { onSwipeOwnedDeal(deal) },
                                        onSwipeLeftHide = { onSwipeHideDeal(deal) },
                                        modifier = Modifier.animateItem(
                                            placementSpec = tween(320, easing = Motion.Out)
                                        )
                                    ) {
                                        DealRowItem(
                                            deal = deal,
                                            rank = idx + 1,
                                            delta = null,
                                            userRating = rated,
                                            justRatedId = recentlyRatedBadgeId,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                    }
                                    HorizontalDivider(
                                        thickness = 1.dp,
                                        color = c.line,
                                        modifier = Modifier.padding(horizontal = 20.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            val hero = displayedDeals.first()
                            val podium = displayedDeals.drop(1).take(2)
                            val rows = displayedDeals.drop(3)

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 28.dp)
                            ) {
                                // Hero #1
                                item(key = "hero_${hero.id}") {
                                    val heroRated = ratedMap[DealTitleNormalizer.normalize(hero.title)]?.rating
                                    SwipeableFeedContainer(
                                        onClick = { onOpenDealDetail(hero, 1) },
                                        onLongPress = { popoverTargetDeal = hero },
                                        onSwipeRightOwned = { onSwipeOwnedDeal(hero) },
                                        onSwipeLeftHide = { onSwipeHideDeal(hero) },
                                        modifier = Modifier
                                            .animateItem(placementSpec = tween(320, easing = Motion.Out))
                                            .then(
                                                if (playInitialEntrance) Modifier.staggeredEntrance(0) else Modifier
                                            )
                                    ) {
                                        HeroDealItem(
                                            deal = hero,
                                            userRating = heroRated,
                                            justRatedId = recentlyRatedBadgeId,
                                            sharedTransitionScope = sharedTransitionScope,
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                    }
                                }

                                // Podium #2 & #3
                                if (podium.isNotEmpty()) {
                                    item(key = "podium_${podium.joinToString("_") { it.id }}") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp)
                                                .padding(top = 18.dp, bottom = 14.dp)
                                                .then(if (playInitialEntrance) Modifier.staggeredEntrance(1) else Modifier),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            podium.forEachIndexed { idx, podDeal ->
                                                val rank = idx + 2
                                                val podRated = ratedMap[DealTitleNormalizer.normalize(podDeal.title)]?.rating
                                                SwipeableFeedContainer(
                                                    onClick = { onOpenDealDetail(podDeal, rank) },
                                                    onLongPress = { popoverTargetDeal = podDeal },
                                                    onSwipeRightOwned = { onSwipeOwnedDeal(podDeal) },
                                                    onSwipeLeftHide = { onSwipeHideDeal(podDeal) },
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    PodiumDealItem(
                                                        deal = podDeal,
                                                        rank = rank,
                                                        delta = state.rankDeltas[podDeal.id],
                                                        userRating = podRated,
                                                        sharedTransitionScope = sharedTransitionScope,
                                                        animatedVisibilityScope = animatedVisibilityScope
                                                    )
                                                }
                                            }
                                            if (podium.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }

                                // Numbered rows 04..30
                                itemsIndexed(
                                    items = rows,
                                    key = { _, d -> d.id }
                                ) { idx, deal ->
                                    val rank = idx + 4
                                    val rated = ratedMap[DealTitleNormalizer.normalize(deal.title)]?.rating
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .animateItem(placementSpec = tween(320, easing = Motion.Out))
                                            .then(if (playInitialEntrance) Modifier.staggeredEntrance(idx + 2) else Modifier)
                                    ) {
                                        HorizontalDivider(
                                            thickness = 1.dp,
                                            color = c.line,
                                            modifier = Modifier.padding(horizontal = 20.dp)
                                        )
                                        SwipeableFeedContainer(
                                            onClick = { onOpenDealDetail(deal, rank) },
                                            onLongPress = { popoverTargetDeal = deal },
                                            onSwipeRightOwned = { onSwipeOwnedDeal(deal) },
                                            onSwipeLeftHide = { onSwipeHideDeal(deal) }
                                        ) {
                                            DealRowItem(
                                                deal = deal,
                                                rank = rank,
                                                delta = state.rankDeltas[deal.id],
                                                userRating = rated,
                                                justRatedId = recentlyRatedBadgeId,
                                                sharedTransitionScope = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val activePopoverDeal = popoverTargetDeal
        if (activePopoverDeal != null) {
            val currentStars = ratedMap[DealTitleNormalizer.normalize(activePopoverDeal.title)]?.rating
            RatingPopover(
                title = activePopoverDeal.title,
                currentRating = currentStars,
                onRate = { stars ->
                    recentlyRatedBadgeId = activePopoverDeal.id
                    onQuickRateDeal(activePopoverDeal, stars)
                    popoverTargetDeal = null
                },
                onDismiss = { popoverTargetDeal = null }
            )
        }
    }
}

@Composable
private fun Modifier.staggeredEntrance(index: Int): Modifier {
    val density = LocalDensity.current
    val initialDy = with(density) { 14.dp.toPx() }
    val offsetY = remember { Animatable(initialDy) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(8) * 40L))
        launch { offsetY.animateTo(0f, tween(360, easing = Motion.Out)) }
        launch { alphaAnim.animateTo(1f, tween(360, easing = Motion.Out)) }
    }
    return this.graphicsLayer {
        translationY = offsetY.value
        alpha = alphaAnim.value
    }
}

@Composable
private fun SwipeableFeedContainer(
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onSwipeRightOwned: () -> Unit,
    onSwipeLeftHide: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val c = SpTheme.colors
    val view = LocalView.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current

    val thresholdPx = with(density) { 96.dp.toPx() }
    val dampStartPx = with(density) { 150.dp.toPx() }
    val touchSlopPx = with(density) { 8.dp.toPx() }

    val offsetX = remember { Animatable(0f) }
    var armedThreshold by remember { mutableStateOf(false) }
    var longPressActive by remember { mutableStateOf(false) }
    val pressInteraction = remember { MutableInteractionSource() }

    val bgTint by animateColorAsState(
        targetValue = if (longPressActive) c.pressed else c.paper,
        animationSpec = Motion.quick(),
        label = "long_press_bg"
    )
    val pressScaleTarget = if (longPressActive && !reduceMotion) 0.985f else 1f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Revealed background during swipe
        if (abs(offsetX.value) > 2f) {
            SwipeActionBackground(
                isRightSwipe = offsetX.value > 0f,
                armed = armedThreshold,
                modifier = Modifier.matchParentSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = pressScaleTarget
                    scaleY = pressScaleTarget
                }
                .pressScale(pressInteraction)
                .background(bgTint)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = {
                            longPressActive = false
                            view.hapticTick()
                            onLongPress()
                        },
                        onPress = { pressOffset ->
                            longPressActive = true
                            val start = pressOffset
                            try {
                                kotlinx.coroutines.coroutineScope {
                                    // Cancel darkened hold highlight immediately if pointer moves > 8 dp
                                    awaitPointerEventScope {
                                        while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Main)
                                            val ch = ev.changes.firstOrNull() ?: break
                                            if (!ch.pressed || (ch.position - start).getDistance() > touchSlopPx) {
                                                longPressActive = false
                                                break
                                            }
                                        }
                                    }
                                }
                            } finally {
                                longPressActive = false
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val current = offsetX.value
                            if (abs(current) >= thresholdPx) {
                                view.hapticConfirm()
                                val targetExit = if (current > 0f) size.width.toFloat() else -size.width.toFloat()
                                scope.launch {
                                    offsetX.animateTo(targetExit, Motion.exit(reduceMotion))
                                    if (current > 0f) onSwipeRightOwned() else onSwipeLeftHide()
                                    offsetX.snapTo(0f)
                                }
                            } else {
                                scope.launch {
                                    offsetX.animateTo(0f, Motion.settle())
                                }
                            }
                            armedThreshold = false
                        },
                        onDragCancel = {
                            armedThreshold = false
                            scope.launch { offsetX.animateTo(0f, Motion.settle()) }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            longPressActive = false
                            val rawNext = offsetX.value + dragAmount
                            val damped = if (abs(rawNext) > dampStartPx) {
                                val sign = if (rawNext >= 0) 1f else -1f
                                sign * (dampStartPx + (abs(rawNext) - dampStartPx) * 0.35f)
                            } else {
                                rawNext
                            }
                            val nowArmed = abs(damped) >= thresholdPx
                            if (nowArmed != armedThreshold) {
                                armedThreshold = nowArmed
                                if (nowArmed) view.hapticTick()
                            }
                            scope.launch { offsetX.snapTo(damped) }
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HeroDealItem(
    deal: Deal,
    userRating: Int?,
    justRatedId: String?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val c = SpTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            with(sharedTransitionScope) {
                Cover(
                    url = deal.imageUrl,
                    title = deal.title,
                    radius = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "cover-${deal.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> Motion.emphasized() }
                        )
                )
            }
            RankCutout(
                rank = 1,
                fontSize = 54.sp,
                color = c.accent,
                corner = CutoutCorner.TopStart,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = deal.title,
                style = SpType.cardTitle,
                color = c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            PriceLine(
                discountedPriceEur = deal.discountedPriceEur,
                regularPriceEur = deal.regularPriceEur,
                discountPercent = deal.discountPercent,
                size = PriceSize.M,
                previousDiscountedPriceEur = deal.previousDiscountedPriceEur
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        val expiry = DealFilterEngine.formatExpiryShort(deal.endsAtIso)
        val isUrgent = DealFilterEngine.isUrgentExpiry(deal.endsAtIso)
        val reason = deal.aiReason?.takeIf { it.isNotBlank() } ?: deal.matchReason
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MatchLine(
                score = deal.aiAffinityScore?.toInt(),
                reason = reason,
                sentiment = deal.matchSentiment,
                expirySuffix = expiry,
                isUrgentExpiry = isUrgent,
                singleLine = false,
                modifier = Modifier.weight(1f)
            )
            if (userRating != null) {
                QuickStarBadge(stars = userRating, animateBounce = deal.id == justRatedId)
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PodiumDealItem(
    deal: Deal,
    rank: Int,
    delta: Int?,
    userRating: Int?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val c = SpTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            with(sharedTransitionScope) {
                Cover(
                    url = deal.imageUrl,
                    title = deal.title,
                    radius = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "cover-${deal.id}"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            boundsTransform = { _, _ -> Motion.emphasized() }
                        )
                )
            }
            RankCutout(
                rank = rank,
                fontSize = 30.sp,
                color = c.ink,
                corner = CutoutCorner.TopStart,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = deal.title,
            style = SpType.rowTitle,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(3.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PriceLine(
                discountedPriceEur = deal.discountedPriceEur,
                regularPriceEur = deal.regularPriceEur,
                discountPercent = deal.discountPercent,
                size = PriceSize.S,
                previousDiscountedPriceEur = deal.previousDiscountedPriceEur
            )
            Spacer(modifier = Modifier.weight(1f))
            if (delta != null && delta != 0) {
                RankDelta(delta = delta, modifier = Modifier.padding(end = 6.dp))
            }
            val scoreVal = deal.aiAffinityScore?.toInt()
            if (userRating != null) {
                Text(
                    text = "★ $userRating",
                    style = SpType.captionBold,
                    color = c.ink
                )
            } else if (scoreVal != null && deal.matchSentiment != DealMatchSentiment.DISCOURAGED) {
                Text(
                    text = scoreVal.toString(),
                    style = SpType.captionBold.copy(fontFeatureSettings = "tnum"),
                    color = c.ink
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DealRowItem(
    deal: Deal,
    rank: Int,
    delta: Int?,
    userRating: Int?,
    justRatedId: String?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val c = SpTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.width(30.dp),
            horizontalAlignment = Alignment.Start
        ) {
            RankNumber(
                rank = rank,
                style = SpType.rankRow,
                color = c.ink
            )
            RankDelta(delta = delta)
        }

        with(sharedTransitionScope) {
            Cover(
                url = deal.imageUrl,
                title = deal.title,
                radius = 3.dp,
                modifier = Modifier
                    .width(80.dp)
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "cover-${deal.id}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ -> Motion.emphasized() }
                    )
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = deal.title,
                style = SpType.rowTitle,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val score = deal.aiAffinityScore?.toInt()
            val matchText = when {
                deal.matchSentiment == DealMatchSentiment.DISCOURAGED -> stringResource(R.string.ranking_not_for_you)
                score != null -> "${stringResource(R.string.detail_for_you_label)} $score"
                !deal.matchReason.isNullOrBlank() -> deal.matchReason
                else -> deal.displayPublisher
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = matchText,
                    style = SpType.captionBold.copy(
                        fontWeight = if (deal.matchSentiment == DealMatchSentiment.DISCOURAGED) FontWeight.SemiBold else FontWeight.Bold
                    ),
                    color = if (deal.matchSentiment == DealMatchSentiment.DISCOURAGED) c.faint else c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (userRating != null) {
                    QuickStarBadge(stars = userRating, animateBounce = deal.id == justRatedId)
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = DealFilterEngine.formatEur(deal.discountedPriceEur),
                style = SpType.rowPrice,
                color = c.ink,
                maxLines = 1,
                textAlign = TextAlign.End
            )
            if (deal.discountPercent > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "−${deal.discountPercent}%",
                    style = SpType.captionBold.copy(fontFeatureSettings = "tnum"),
                    color = c.accentText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickStarBadge(
    stars: Int,
    animateBounce: Boolean
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val scale = remember(stars, animateBounce) { Animatable(if (animateBounce && !reduceMotion) 0.4f else 1f) }
    LaunchedEffect(stars, animateBounce) {
        if (animateBounce && !reduceMotion) {
            scale.animateTo(1.18f, tween(180, easing = Motion.Out))
            scale.animateTo(1f, tween(180, easing = Motion.Out))
        }
    }
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(4.dp))
            .background(c.pressed)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = c.ink,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = stars.toString(),
            style = SpType.captionBold.copy(fontSize = 11.sp),
            color = c.ink
        )
    }
}

@Composable
private fun FeedLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonBox(modifier = Modifier.fillMaxWidth().height(197.dp))
        SkeletonBox(modifier = Modifier.fillMaxWidth(0.65f).height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBox(modifier = Modifier.weight(1f).height(95.dp))
            SkeletonBox(modifier = Modifier.weight(1f).height(95.dp))
        }
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBox(modifier = Modifier.width(28.dp).height(24.dp))
                SkeletonBox(modifier = Modifier.width(80.dp).height(45.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
                    SkeletonBox(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
                }
            }
        }
    }
}

private fun formatTimeShort(epochMs: Long): String? {
    if (epochMs <= 0L) return null
    val fmt = SimpleDateFormat("HH:mm", Locale.ITALY)
    return fmt.format(Date(epochMs))
}
