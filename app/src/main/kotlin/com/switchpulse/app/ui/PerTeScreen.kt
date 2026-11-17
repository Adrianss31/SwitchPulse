package com.switchpulse.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switchpulse.app.R
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.model.AiDiscoveryPick
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.data.model.GamerSoulProfile
import com.switchpulse.app.ui.components.Cover
import com.switchpulse.app.ui.components.EmptyState
import com.switchpulse.app.ui.components.PrimaryButton
import com.switchpulse.app.ui.components.SecondaryButton
import com.switchpulse.app.ui.components.SectionLabel
import com.switchpulse.app.ui.components.SkeletonBox
import com.switchpulse.app.ui.motion.LocalReduceMotion
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.motion.hapticConfirm
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PerTeScreen(
    state: MainUiState,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onGenerateSoul: () -> Unit,
    onSaveUserTastePrompt: (String) -> Unit,
    onRefreshDiscoveryPicks: () -> Unit,
    onOpenDiscoveryDetail: (AiDiscoveryPick) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAddGame: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState()
) {
    val c = SpTheme.colors
    var showEditWordsSheet by remember { mutableStateOf(false) }

    val hasAnyKey = state.settings.hasAnyAiKey()
    val hasSignals = state.settings.likedGames.isNotEmpty() || state.settings.userTastePrompt.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.paper)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
    ) {
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = stringResource(R.string.for_you_title),
            style = SpType.screenTitle,
            color = c.ink
        )

        if (!hasAnyKey) {
            Spacer(modifier = Modifier.height(14.dp))
            EmptyState(
                title = stringResource(R.string.for_you_no_ai_title),
                body = stringResource(R.string.for_you_no_ai_body),
                actionLabel = stringResource(R.string.for_you_no_ai_cta),
                onAction = onOpenSettings,
                centered = false,
                modifier = Modifier.background(c.bannerBg)
            )
            return@Column
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 1. Gamer Soul section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(text = stringResource(R.string.for_you_soul_section))
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clickable(enabled = !state.isGeneratingSoul && hasSignals, onClick = onGenerateSoul)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = if (hasSignals) c.ink else c.muted,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.action_refresh),
                    style = SpType.captionBold.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = if (hasSignals) c.ink else c.muted
                )
            }
        }

        val soul = state.settings.gamerSoulProfile
        when {
            state.isGeneratingSoul -> {
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.75f).height(28.dp))
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(16.dp))
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.9f).height(16.dp))
            }
            !hasSignals -> {
                Spacer(modifier = Modifier.height(10.dp))
                EmptyState(
                    title = stringResource(R.string.library_empty_title),
                    body = stringResource(R.string.library_empty_body),
                    actionLabel = stringResource(R.string.library_empty_cta),
                    onAction = onOpenAddGame,
                    centered = false,
                    modifier = Modifier.background(c.bannerBg)
                )
            }
            soul == null -> {
                Spacer(modifier = Modifier.height(10.dp))
                EmptyState(
                    title = stringResource(R.string.for_you_soul_section),
                    body = "Genera il tuo profilo giocatore a partire dai titoli votati e dalle tue parole.",
                    actionLabel = stringResource(R.string.action_refresh),
                    onAction = onGenerateSoul,
                    centered = false,
                    modifier = Modifier.background(c.bannerBg)
                )
            }
            else -> {
                SoulProfileBlock(soul = soul)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(thickness = 1.dp, color = c.line)

        // 2. "Le tue parole"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.for_you_your_words),
                    style = SpType.captionBold.copy(fontSize = 13.sp),
                    color = c.ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                val words = state.settings.userTastePrompt.trim()
                Text(
                    text = if (words.isNotBlank()) "“$words”" else stringResource(R.string.for_you_words_empty),
                    style = SpType.body.copy(
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp,
                        fontStyle = FontStyle.Italic
                    ),
                    color = if (words.isNotBlank()) c.ink2 else c.muted
                )
            }
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clickable { showEditWordsSheet = true }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.for_you_edit),
                    style = SpType.captionBold.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = c.ink
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = c.line)
        Spacer(modifier = Modifier.height(16.dp))

        // 3. "Da scoprire" 2-column grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.for_you_discover_title),
                style = SpType.cardTitle,
                color = c.ink
            )
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .clickable(enabled = !state.isDiscoveringGames, onClick = onRefreshDiscoveryPicks)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.for_you_discover_more),
                    style = SpType.captionBold.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = if (state.isDiscoveringGames) c.muted else c.ink
                )
            }
        }
        Text(
            text = stringResource(R.string.for_you_discover_subtitle),
            style = SpType.caption.copy(fontSize = 13.sp),
            color = c.muted
        )

        Spacer(modifier = Modifier.height(12.dp))

        val visiblePicks = remember(state.settings.discoveryPicks, state.settings.dismissedDiscoveryTitles) {
            val dismissedNorm = state.settings.dismissedDiscoveryTitles.map { DealTitleNormalizer.normalize(it) }.toSet()
            state.settings.discoveryPicks.filter { DealTitleNormalizer.normalize(it.title) !in dismissedNorm }
        }

        if (state.isDiscoveringGames && visiblePicks.isEmpty()) {
            DiscoverySkeletonGrid()
        } else if (visiblePicks.isEmpty()) {
            PrimaryButton(
                text = stringResource(R.string.for_you_discover_more),
                onClick = onRefreshDiscoveryPicks,
                enabled = hasSignals,
                height = 48.dp
            )
        } else {
            val rows = visiblePicks.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                rows.forEachIndexed { rowIdx, pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEachIndexed { colIdx, pick ->
                            DiscoveryGridCard(
                                pick = pick,
                                indexInGrid = rowIdx * 2 + colIdx,
                                sharedTransitionScope = sharedTransitionScope,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onClick = { onOpenDiscoveryDetail(pick) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showEditWordsSheet) {
        EditTasteWordsSheet(
            initialText = state.settings.userTastePrompt,
            onDismiss = { showEditWordsSheet = false },
            onSave = { next ->
                onSaveUserTastePrompt(next)
                showEditWordsSheet = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoulProfileBlock(soul: GamerSoulProfile) {
    val c = SpTheme.colors
    val view = LocalView.current
    val reduceMotion = LocalReduceMotion.current
    val words = remember(soul.archetypeTitle) {
        soul.archetypeTitle.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    var visibleWords by remember(soul.archetypeTitle, reduceMotion) {
        mutableIntStateOf(if (reduceMotion) words.size else 0)
    }
    var summaryVisible by remember(soul.archetypeTitle, reduceMotion) {
        mutableStateOf(reduceMotion)
    }

    LaunchedEffect(soul.archetypeTitle, reduceMotion) {
        if (!reduceMotion && words.isNotEmpty()) {
            visibleWords = 0
            summaryVisible = false
            for (i in 1..words.size) {
                delay(60L)
                visibleWords = i
            }
            delay(80L)
            summaryVisible = true
            view.hapticConfirm()
        } else {
            visibleWords = words.size
            summaryVisible = true
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        words.forEachIndexed { idx, word ->
            val shown = idx < visibleWords
            val dy = if (shown || reduceMotion) 0f else 6f
            val alpha = if (shown) 1f else 0f
            Text(
                text = word,
                style = SpType.sheetTitle.copy(
                    fontFamily = com.switchpulse.app.ui.theme.BricolageSemi,
                    fontSize = 32.sp,
                    lineHeight = 34.sp
                ),
                color = c.accent,
                modifier = Modifier.graphicsLayer {
                    translationY = dy
                    this.alpha = alpha
                }
            )
        }
    }

    AnimatedVisibility(
        visible = summaryVisible,
        enter = fadeIn(Motion.standard(reduceMotion))
    ) {
        Column {
            if (soul.soulSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = soul.soulSummary,
                    style = SpType.body.copy(lineHeight = 21.sp),
                    color = c.ink2
                )
            }
            if (soul.corePillars.isNotEmpty() || soul.redFlags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.for_you_seek),
                            style = SpType.captionBold.copy(fontSize = 13.sp),
                            color = c.ink
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        soul.corePillars.take(4).forEach { pillar ->
                            Text(
                                text = "• $pillar",
                                style = SpType.body.copy(fontSize = 13.sp, lineHeight = 20.sp),
                                color = c.ink2
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.for_you_avoid),
                            style = SpType.captionBold.copy(fontSize = 13.sp),
                            color = c.ink
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        soul.redFlags.take(4).forEach { avoid ->
                            Text(
                                text = "• $avoid",
                                style = SpType.body.copy(fontSize = 13.sp, lineHeight = 20.sp),
                                color = c.ink2
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DiscoveryGridCard(
    pick: AiDiscoveryPick,
    indexInGrid: Int,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val initialOffsetX = with(density) { 24.dp.toPx() }
    val slideX = remember(pick.title) { Animatable(if (reduceMotion) 0f else initialOffsetX) }
    val alphaAnim = remember(pick.title) { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(pick.title, reduceMotion) {
        if (!reduceMotion) {
            delay((indexInGrid.coerceAtMost(7) * 40L))
            launch { slideX.animateTo(0f, Motion.standard()) }
            launch { alphaAnim.animateTo(1f, Motion.standard()) }
        }
    }

    val sharedKey = "pick-${DealTitleNormalizer.normalize(pick.title)}"
    Column(
        modifier = modifier
            .graphicsLayer {
                translationX = slideX.value
                alpha = alphaAnim.value
            }
            .clickable(onClick = onClick)
    ) {
        with(sharedTransitionScope) {
            Cover(
                url = pick.imageUrl,
                title = pick.title,
                radius = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .sharedBounds(
                        sharedContentState = rememberSharedContentState(key = sharedKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ -> Motion.emphasized(reduceMotion) },
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                            ContentScale.Crop,
                            Alignment.Center
                        )
                    )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = pick.title,
            style = SpType.rowTitle,
            color = c.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (pick.pitchHook.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = pick.pitchHook,
                style = SpType.caption,
                color = c.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        val price = pick.currentPriceEur
        if (price != null && price > 0.0) {
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = DealFilterEngine.formatEur(price),
                    style = SpType.rowPrice.copy(fontSize = 14.sp),
                    color = c.ink
                )
                if (pick.discountPercent > 0) {
                    Text(
                        text = "−${pick.discountPercent}%",
                        style = SpType.captionBold,
                        color = c.accentText
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverySkeletonGrid() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(2) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(95.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.75f).height(14.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.55f).height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTasteWordsSheet(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val c = SpTheme.colors
    val scope = rememberCoroutineScope()
    var draft by remember(initialText) { mutableStateOf(initialText) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen: (() -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.paper,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.for_you_your_words),
                style = SpType.sheetTitle,
                color = c.ink
            )
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 600) draft = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.for_you_words_empty),
                        style = SpType.body,
                        color = c.muted
                    )
                },
                minLines = 3,
                maxLines = 6,
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
                    text = stringResource(R.string.action_clear),
                    onClick = { hideThen { onSave("") } },
                    height = 48.dp
                )
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { hideThen { onSave(draft.trim()) } },
                    modifier = Modifier.weight(1f),
                    height = 48.dp
                )
            }
        }
    }
}
