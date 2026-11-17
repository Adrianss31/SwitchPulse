package com.switchpulse.app.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switchpulse.app.R
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.RecommendationEngine
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GameDetailModel
import com.switchpulse.app.data.model.GameDetailOrigin
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.MetacriticOrigin
import com.switchpulse.app.ui.components.Cover
import com.switchpulse.app.ui.components.CutoutCorner
import com.switchpulse.app.ui.components.OwnedToggleButton
import com.switchpulse.app.ui.components.PriceLine
import com.switchpulse.app.ui.components.PriceSize
import com.switchpulse.app.ui.components.PrimaryButton
import com.switchpulse.app.ui.components.RankCutout
import com.switchpulse.app.ui.components.RollingDigitsText
import com.switchpulse.app.ui.components.SecondaryButton
import com.switchpulse.app.ui.components.SectionLabel
import com.switchpulse.app.ui.components.SkeletonBox
import com.switchpulse.app.ui.components.StarRating
import com.switchpulse.app.ui.components.ratingLabelResId
import com.switchpulse.app.ui.motion.LocalReduceMotion
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun GameDetailScreen(
    detail: GameDetailModel,
    settings: FilterSettings,
    insights: GameExternalInsights?,
    isLoadingInsights: Boolean,
    libraryEntry: LikedGame?,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDismiss: () -> Unit,
    onOpenEshop: (String) -> Unit,
    onSetRating: (Int?) -> Unit,
    onSetOwned: (Boolean) -> Unit,
    onSetNote: (String?) -> Unit,
    onSetTargetPrice: (Double?) -> Unit,
    onHideDeal: () -> Unit,
    onDismissPick: (String) -> Unit,
    onRemoveLibrary: (String) -> Unit,
    onRefreshInsights: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    val dragOffsetY = remember(detail.id) { Animatable(0f) }
    val backProgressAnim = remember(detail.id) { Animatable(0f) }
    val dismissThresholdPx = with(density) { 120.dp.toPx() }

    PredictiveBackHandler(enabled = true) { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                backProgressAnim.snapTo(backEvent.progress.coerceIn(0f, 1f))
            }
            onDismiss()
        } catch (_: CancellationException) {
            scope.launch { backProgressAnim.animateTo(0f, Motion.settle()) }
        }
    }

    var contentRevealed by remember(detail.id) { mutableStateOf(reduceMotion) }
    val closeIconRotation = remember(detail.id) { Animatable(if (reduceMotion) 0f else -45f) }
    val closeIconScale = remember(detail.id) { Animatable(if (reduceMotion) 1f else 0.75f) }
    val cutoutOffsetY = remember(detail.id) { Animatable(if (reduceMotion) 0f else with(density) { 10.dp.toPx() }) }

    LaunchedEffect(detail.id, reduceMotion) {
        if (!reduceMotion) {
            launch { closeIconRotation.animateTo(0f, Motion.standard()) }
            launch { closeIconScale.animateTo(1f, Motion.standard()) }
            launch { cutoutOffsetY.animateTo(0f, Motion.standard()) }
            delay(40L)
        }
        contentRevealed = true
    }

    val breakdown = remember(detail.id, settings.likedGames, settings.userTastePrompt) {
        detail.sourceDeal?.let { RecommendationEngine.explainDeal(it, settings) }
    }
    val currentRating = libraryEntry?.rating
    val isOwned = libraryEntry?.isOwned == true
    val savedNote = libraryEntry?.userNote.orEmpty()
    var noteDraft by remember(detail.id, savedNote) { mutableStateOf(savedNote) }
    var noteExpanded by remember(detail.id, savedNote) { mutableStateOf(savedNote.isNotBlank()) }
    var breakdownExpanded by remember(detail.id) { mutableStateOf(false) }

    val savedTargetPrice = libraryEntry?.targetPriceEur
    var targetPriceDraft by remember(detail.id, savedTargetPrice) {
        mutableStateOf(savedTargetPrice?.let { String.format(Locale.US, "%.2f", it) }.orEmpty())
    }
    var alertEditorExpanded by remember(detail.id, savedTargetPrice) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    val combinedScale = (1f - (dragOffsetY.value / 1200f).coerceIn(0f, 0.08f) - backProgressAnim.value * 0.08f)
        .coerceIn(0.88f, 1f)

    val galleryImages = remember(detail.coverUrl, insights?.screenshotUrls) {
        buildList {
            detail.coverUrl?.takeIf { it.isNotBlank() }?.let(::add)
            insights?.screenshotUrls.orEmpty().forEach { shot ->
                if (shot.isNotBlank() && shot !in this) add(shot)
            }
        }
    }
    val heroPagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { galleryImages.size.coerceAtLeast(1) }
    )
    var lightboxIndex by remember(detail.id) { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.paper)
            .graphicsLayer {
                translationY = dragOffsetY.value
                scaleX = combinedScale
                scaleY = combinedScale
            }
            .pointerInput(detail.id) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY.value > dismissThresholdPx) {
                            onDismiss()
                        } else {
                            scope.launch {
                                dragOffsetY.animateTo(0f, Motion.settle())
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { dragOffsetY.animateTo(0f, Motion.settle()) }
                    },
                    onVerticalDrag = { _, dragAmount ->
                        if (scrollState.value == 0 && (dragAmount > 0f || dragOffsetY.value > 0f)) {
                            scope.launch {
                                dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 32.dp)
        ) {
            // Full-width 16:9 swipeable Hero cover + screenshots gallery
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(219.dp)
            ) {
                HorizontalPager(
                    state = heroPagerState,
                    userScrollEnabled = galleryImages.size > 1,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    if (page == 0) {
                        with(sharedTransitionScope) {
                            Cover(
                                url = galleryImages.firstOrNull() ?: detail.coverUrl,
                                title = detail.title,
                                radius = 0.dp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = detail.sharedCoverKey),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = { _, _ -> Motion.emphasized(reduceMotion) },
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                                            ContentScale.Crop,
                                            Alignment.Center
                                        )
                                    )
                                    .clickable(enabled = galleryImages.isNotEmpty()) { lightboxIndex = 0 }
                            )
                        }
                    } else {
                        AsyncImage(
                            model = galleryImages[page],
                            contentDescription = "${detail.title} immagine ${page + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(c.bannerBg)
                                .clickable { lightboxIndex = page }
                        )
                    }
                }

                if (detail.rank != null) {
                    RankCutout(
                        rank = detail.rank,
                        fontSize = 54.sp,
                        color = c.accent,
                        corner = CutoutCorner.BottomStart,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .offset { IntOffset(0, cutoutOffsetY.value.roundToInt()) }
                    )
                }

                if (galleryImages.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { lightboxIndex = heroPagerState.currentPage }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${heroPagerState.currentPage + 1} / ${galleryImages.size}",
                            style = SpType.captionBold.copy(fontSize = 11.5.sp, fontFeatureSettings = "tnum"),
                            color = Color.White
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 12.dp, end = 12.dp)
                        .size(44.dp)
                        .graphicsLayer {
                            rotationZ = closeIconRotation.value
                            scaleX = closeIconScale.value
                            scaleY = closeIconScale.value
                        }
                        .clip(CircleShape)
                        .background(c.paper.copy(alpha = 0.92f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.detail_close_cd),
                        tint = c.ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp)
            ) {
                // Meta row + Title render immediately on frame 1 with screen entrance
                val metaParts = listOfNotNull(
                    detail.platformLabel?.uppercase(),
                    detail.publisher.takeIf { it.isNotBlank() }?.uppercase()
                )
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = SpType.label.copy(fontWeight = FontWeight.SemiBold),
                        color = c.muted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = detail.title,
                    style = SpType.detailTitle,
                    color = c.ink
                )

                if (detail.discountedPriceEur != null) {
                    AnimatedDetailBlock(visible = contentRevealed, delayMs = 40) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PriceLine(
                                discountedPriceEur = detail.discountedPriceEur,
                                regularPriceEur = detail.regularPriceEur,
                                discountPercent = detail.discountPercent ?: 0,
                                size = PriceSize.L,
                                previousDiscountedPriceEur = detail.previousDiscountedPriceEur
                            )
                            if (detail.isHistoricalLow) {
                                Text(
                                    text = stringResource(R.string.detail_historical_low),
                                    style = SpType.captionBold,
                                    color = c.accentText
                                )
                            }
                        }
                        val expiryText = formatDetailExpiry(detail.endsAtIso)
                        if (expiryText != null) {
                            val isUrgent = DealFilterEngine.isUrgentExpiry(detail.endsAtIso)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = expiryText,
                                style = SpType.caption.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isUrgent) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (isUrgent) c.urgent else c.muted
                            )
                        }
                    }
                }

                // CTAs: Open eShop + Owned/Hide
                AnimatedDetailBlock(visible = contentRevealed, delayMs = 80) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val eshopUrl = detail.eshopUrl
                    if (!eshopUrl.isNullOrBlank()) {
                        PrimaryButton(
                            text = stringResource(R.string.detail_open_eshop),
                            onClick = { onOpenEshop(eshopUrl) },
                            trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                            height = 50.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OwnedToggleButton(
                            owned = isOwned,
                            onToggle = { onSetOwned(!isOwned) },
                            modifier = Modifier.weight(1f),
                            height = 44.dp
                        )
                        when (detail.origin) {
                            GameDetailOrigin.FEED_DEAL -> {
                                SecondaryButton(
                                    text = stringResource(R.string.detail_hide),
                                    onClick = {
                                        onHideDeal()
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 44.dp
                                )
                            }
                            GameDetailOrigin.DISCOVERY_PICK -> {
                                SecondaryButton(
                                    text = stringResource(R.string.detail_dismiss_pick),
                                    onClick = {
                                        onDismissPick(detail.title)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 44.dp
                                )
                            }
                            GameDetailOrigin.LIBRARY_GAME -> {
                                SecondaryButton(
                                    text = stringResource(R.string.action_remove),
                                    onClick = {
                                        onRemoveLibrary(libraryEntry?.id ?: detail.id)
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 44.dp
                                )
                            }
                        }
                    }
                }

                // Keep the ranking AI verdict & explanation permanently; use insights summary only as fallback.
                val aiScore = detail.aiScore
                val (headline, longReason) = GameDetailModel.resolveDescriptionTexts(detail, insights)

                if (aiScore != null || headline != null || longReason != null) {
                    AnimatedDetailBlock(visible = contentRevealed, delayMs = 120) {
                        Spacer(modifier = Modifier.height(22.dp))
                        HorizontalDivider(thickness = 1.dp, color = c.line)
                        Spacer(modifier = Modifier.height(18.dp))
                        DetailForYouSection(
                            score = aiScore,
                            headline = headline,
                            reason = longReason
                        )
                    }
                }

                // Data trio: Metacritic · Durata · Steam
                val metaScore = insights?.metacriticScore ?: detail.metacriticScore
                val hltbMain = insights?.hltbMainHours?.takeIf { it.isNotBlank() }
                val steamPct = detail.steamPositivePercent
                val showMetricsTrio = isLoadingInsights || metaScore != null || hltbMain != null || steamPct != null
                if (showMetricsTrio) {
                    AnimatedDetailBlock(visible = contentRevealed, delayMs = 160) {
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(thickness = 1.dp, color = c.line)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val metaLabel = when (insights?.metacriticOrigin) {
                                MetacriticOrigin.STEAM_PC -> stringResource(R.string.detail_metacritic_pc)
                                else -> stringResource(R.string.detail_metacritic)
                            }
                            MetricColumn(
                                label = metaLabel,
                                value = metaScore?.toString(),
                                isLoading = isLoadingInsights && metaScore == null,
                                withLeftDivider = false,
                                modifier = Modifier.weight(1f)
                            )
                            val durLabel = if (insights?.hltbIsEstimated == true) {
                                stringResource(R.string.detail_duration_est)
                            } else {
                                stringResource(R.string.detail_duration)
                            }
                            MetricColumn(
                                label = durLabel,
                                value = formatDurationShort(hltbMain),
                                isLoading = isLoadingInsights && hltbMain == null,
                                withLeftDivider = true,
                                modifier = Modifier.weight(1f)
                            )
                            MetricColumn(
                                label = stringResource(R.string.detail_steam),
                                value = steamPct?.let { "${it}%" },
                                isLoading = false,
                                withLeftDivider = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        HorizontalDivider(thickness = 1.dp, color = c.line)
                    }
                }

                // Screenshots gallery strip ("Immagini")
                val extraScreenshots = remember(galleryImages) { galleryImages.drop(1) }
                if (extraScreenshots.isNotEmpty()) {
                    AnimatedDetailBlock(visible = contentRevealed, delayMs = 180) {
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionLabel(text = stringResource(R.string.detail_gallery_title))
                            Text(
                                text = "${extraScreenshots.size}",
                                style = SpType.captionBold.copy(fontFeatureSettings = "tnum"),
                                color = c.muted
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(extraScreenshots) { idx, shotUrl ->
                                Box(
                                    modifier = Modifier
                                        .width(168.dp)
                                        .height(95.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(c.bannerBg)
                                        .clickable { lightboxIndex = idx + 1 }
                                ) {
                                    AsyncImage(
                                        model = shotUrl,
                                        contentDescription = "${detail.title} screenshot ${idx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Rating + expandable comment + optional wishlist alert
                AnimatedDetailBlock(visible = contentRevealed, delayMs = 200) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.detail_your_rating),
                                style = SpType.rowTitle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                color = c.ink
                            )
                            val labelRes = ratingLabelResId(currentRating)
                            if (labelRes != null) {
                                Text(
                                    text = stringResource(labelRes),
                                    style = SpType.caption,
                                    color = c.muted
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .clickable { noteExpanded = !noteExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (savedNote.isNotBlank()) {
                                    stringResource(R.string.detail_edit_comment)
                                } else {
                                    stringResource(R.string.detail_add_comment)
                                },
                                style = SpType.captionBold.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                color = c.ink
                            )
                            val plusRot = if (noteExpanded) 45f else 0f
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = c.ink,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = plusRot }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    StarRating(
                        rating = currentRating,
                        onRate = onSetRating,
                        starSize = 28.dp,
                        touchSize = 44.dp
                    )

                    AnimatedVisibility(
                        visible = noteExpanded,
                        enter = expandVertically(Motion.standard(reduceMotion)) + fadeIn(Motion.standard(reduceMotion)),
                        exit = shrinkVertically(Motion.exit(reduceMotion)) + fadeOut(Motion.quick(reduceMotion))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = noteDraft,
                                onValueChange = { if (it.length <= 240) noteDraft = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.detail_comment_placeholder),
                                        style = SpType.body,
                                        color = c.muted
                                    )
                                },
                                minLines = 2,
                                maxLines = 3,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        keyboard?.hide()
                                        onSetNote(noteDraft.trim().takeIf { it.isNotEmpty() })
                                    }
                                ),
                                shape = RoundedCornerShape(4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = c.ink,
                                    unfocusedBorderColor = c.border,
                                    focusedTextColor = c.ink,
                                    unfocusedTextColor = c.ink
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SecondaryButton(
                                    text = stringResource(R.string.action_save),
                                    onClick = {
                                        keyboard?.hide()
                                        onSetNote(noteDraft.trim().takeIf { it.isNotEmpty() })
                                    },
                                    enabled = noteDraft.trim() != savedNote.trim(),
                                    height = 40.dp
                                )
                                if (noteDraft.isNotBlank() || savedNote.isNotBlank()) {
                                    SecondaryButton(
                                        text = stringResource(R.string.action_clear),
                                        onClick = {
                                            noteDraft = ""
                                            onSetNote(null)
                                        },
                                        height = 40.dp
                                    )
                                }
                            }
                        }
                    }

                    // Wishlist price alert compact disclosure when not owned
                    if (!isOwned) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .clickable { alertEditorExpanded = !alertEditorExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (savedTargetPrice != null) {
                                    stringResource(R.string.detail_price_alert_active, DealFilterEngine.formatEur(savedTargetPrice))
                                } else {
                                    stringResource(R.string.detail_price_alert_title)
                                },
                                style = SpType.captionBold.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                color = if (savedTargetPrice != null) c.accentText else c.muted
                            )
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = c.muted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .graphicsLayer { rotationZ = if (alertEditorExpanded) 180f else 0f }
                            )
                        }
                        AnimatedVisibility(visible = alertEditorExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(20.0, 15.0, 10.0, 5.0).forEach { preset ->
                                        val selected = savedTargetPrice != null && abs(savedTargetPrice - preset) < 0.05
                                        Box(
                                            modifier = Modifier
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(if (selected) c.ink else Color.Transparent)
                                                .border(
                                                    BorderStroke(1.dp, if (selected) c.ink else c.border),
                                                    RoundedCornerShape(18.dp)
                                                )
                                                .clickable { onSetTargetPrice(if (selected) null else preset) }
                                                .padding(horizontal = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "< ${preset.toInt()} €",
                                                style = SpType.captionBold,
                                                color = if (selected) c.paper else c.ink
                                            )
                                        }
                                    }
                                    if (savedTargetPrice != null) {
                                        SecondaryButton(
                                            text = stringResource(R.string.action_clear),
                                            onClick = { onSetTargetPrice(null) },
                                            height = 36.dp
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = targetPriceDraft,
                                        onValueChange = { if (it.length <= 6) targetPriceDraft = it },
                                        placeholder = { Text("es. 14.99", style = SpType.body, color = c.muted) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Decimal,
                                            imeAction = ImeAction.Done
                                        ),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.weight(1f)
                                    )
                                    SecondaryButton(
                                        text = stringResource(R.string.action_save),
                                        onClick = {
                                            keyboard?.hide()
                                            val parsed = targetPriceDraft.trim().replace(',', '.').toDoubleOrNull()
                                            onSetTargetPrice(parsed?.takeIf { it > 0.0 })
                                        },
                                        height = 44.dp
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable "Come è calcolato il punteggio"
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 1.dp, color = c.line)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { breakdownExpanded = !breakdownExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.detail_how_scored),
                        style = SpType.rowTitle.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = c.ink
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = c.ink,
                        modifier = Modifier
                            .size(18.dp)
                            .graphicsLayer { rotationZ = if (breakdownExpanded) 180f else 0f }
                    )
                }

                AnimatedVisibility(
                    visible = breakdownExpanded,
                    enter = expandVertically(Motion.standard(reduceMotion)) + fadeIn(),
                    exit = shrinkVertically(Motion.exit(reduceMotion)) + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (breakdown != null) {
                            SimilarityBarRow(
                                label = stringResource(R.string.detail_similar_high),
                                percent = breakdown.positiveMatchPercent,
                                barColor = c.ink
                            )
                            SimilarityBarRow(
                                label = stringResource(R.string.detail_similar_low),
                                percent = breakdown.negativeMatchPercent,
                                barColor = c.starOff
                            )
                            if (breakdown.sharedPositiveLabels.isNotEmpty()) {
                                val anchorPart = breakdown.closestLikedTitle?.let { " ($it)" }.orEmpty()
                                Text(
                                    text = "Tratti in comune$anchorPart: ${breakdown.sharedPositiveLabels.joinToString(", ")}",
                                    style = SpType.caption,
                                    color = c.muted
                                )
                            }
                        }
                        val sourceLabel = insights?.sourceLabel?.takeIf { it.isNotBlank() }
                        if (sourceLabel != null) {
                            Text(
                                text = "Fonte: $sourceLabel",
                                style = SpType.caption,
                                color = c.muted
                            )
                        }
                        val encoded = android.net.Uri.encode(DealTitleNormalizer.normalize(detail.title))
                        val mcUrl = insights?.metacriticUrl
                            ?: "https://www.metacritic.com/search/$encoded/"
                        val hltbUrl = "https://howlongtobeat.com/?q=$encoded"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SecondaryButton(
                                text = "Metacritic",
                                onClick = { onOpenEshop(mcUrl) },
                                modifier = Modifier.weight(1f),
                                height = 40.dp
                            )
                            SecondaryButton(
                                text = "HowLongToBeat",
                                onClick = { onOpenEshop(hltbUrl) },
                                modifier = Modifier.weight(1f),
                                height = 40.dp
                            )
                            SecondaryButton(
                                text = stringResource(R.string.detail_recalculate),
                                onClick = onRefreshInsights,
                                enabled = !isLoadingInsights,
                                height = 40.dp
                            )
                        }
                    }
                }
            }
        }

        val activeLightboxIndex = lightboxIndex
        if (activeLightboxIndex != null && galleryImages.isNotEmpty()) {
            FullscreenGalleryDialog(
                images = galleryImages,
                initialIndex = activeLightboxIndex,
                title = detail.title,
                onDismiss = { lightboxIndex = null }
            )
        }
    }
}

@Composable
private fun FullscreenGalleryDialog(
    images: List<String>,
    initialIndex: Int,
    title: String,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size }
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss)
        ) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = images[page],
                        contentDescription = "$title immagine ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 12.dp, end = 12.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.detail_close_cd),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${images.size}",
                    style = SpType.captionBold.copy(fontSize = 13.sp, fontFeatureSettings = "tnum"),
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun AnimatedDetailBlock(
    visible: Boolean,
    delayMs: Int,
    content: @Composable () -> Unit
) {
    val reduceMotion = LocalReduceMotion.current
    if (reduceMotion) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(100))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(240, delayMillis = delayMs, easing = Motion.Out)) +
                slideInVertically(
                    animationSpec = tween(240, delayMillis = delayMs, easing = Motion.Out),
                    initialOffsetY = { 20 }
                ),
            exit = fadeOut(Motion.quick())
        ) {
            Column(modifier = Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun DetailForYouSection(
    score: Int?,
    headline: String?,
    reason: String?
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    var replayCount by remember(score) { mutableStateOf(0) }
    val scoreAnim = remember(score) { Animatable(if (score == null || reduceMotion) (score ?: 0).toFloat() else 0f) }

    LaunchedEffect(score, replayCount, reduceMotion) {
        if (score != null && !reduceMotion) {
            scoreAnim.snapTo(0f)
            scoreAnim.animateTo(score.toFloat(), tween(700, easing = Motion.Out))
        } else if (score != null) {
            scoreAnim.snapTo(score.toFloat())
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (score != null) {
            Column(
                modifier = Modifier
                    .clickable { replayCount++ },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = scoreAnim.value.roundToInt().toString(),
                    style = SpType.score,
                    color = c.accent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.detail_for_you_label).uppercase(),
                    style = SpType.label.copy(fontSize = 11.sp),
                    color = c.muted
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            if (!headline.isNullOrBlank()) {
                Text(
                    text = headline,
                    style = SpType.cardTitle.copy(fontSize = 19.sp, lineHeight = 23.sp),
                    color = c.ink
                )
            }
            if (!reason.isNullOrBlank()) {
                if (!headline.isNullOrBlank()) Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = reason,
                    style = SpType.body.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                    color = c.ink2
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String?,
    isLoading: Boolean,
    withLeftDivider: Boolean,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (withLeftDivider) {
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(42.dp)
                    .background(c.line)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .padding(start = if (withLeftDivider) 14.dp else 0.dp)
        ) {
            Text(
                text = label,
                style = SpType.caption.copy(fontSize = 11.5.sp),
                color = c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (isLoading) {
                SkeletonBox(modifier = Modifier.width(44.dp).height(22.dp))
            } else {
                RollingDigitsText(
                    value = value ?: "--",
                    style = SpType.rowPrice.copy(fontSize = 20.sp, lineHeight = 22.sp),
                    color = c.ink
                )
            }
        }
    }
}

@Composable
private fun SimilarityBarRow(
    label: String,
    percent: Int,
    barColor: Color
) {
    val c = SpTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = label,
            style = SpType.caption.copy(fontWeight = FontWeight.Medium),
            color = c.ink2,
            modifier = Modifier.width(134.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .background(c.track)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percent / 100f).coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .background(barColor)
            )
        }
        Text(
            text = "${percent}%",
            style = SpType.captionBold.copy(fontFeatureSettings = "tnum"),
            color = c.ink,
            modifier = Modifier.width(36.dp)
        )
    }
}

private fun formatDetailExpiry(endsAtIso: String?): String? {
    if (endsAtIso.isNullOrBlank()) return null
    val end = runCatching { Instant.parse(endsAtIso) }.getOrNull() ?: return null
    val now = Instant.now()
    val hours = java.time.Duration.between(now, end).toHours()
    if (hours < 0) return null
    val zoned = end.atZone(ZoneId.systemDefault())
    return when {
        hours < 24 -> "Offerta valida fino a oggi, ${zoned.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ITALY))}"
        hours < 48 -> "Offerta valida fino a domani, ${zoned.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ITALY))}"
        else -> "Offerta valida fino al ${zoned.format(DateTimeFormatter.ofPattern("d MMMM", Locale.ITALY))}"
    }
}

private fun formatDurationShort(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return raw
        .replace(Regex("\\s*ore?\\b", RegexOption.IGNORE_CASE), " h")
        .trim()
}
