package com.switchpulse.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.switchpulse.app.ui.AddGameSheet
import com.switchpulse.app.ui.FeedScreen
import com.switchpulse.app.ui.FiltersBottomSheet
import com.switchpulse.app.ui.GameDetailScreen
import com.switchpulse.app.ui.HiddenDealsScreen
import com.switchpulse.app.ui.LibraryScreen
import com.switchpulse.app.ui.MainViewModel
import com.switchpulse.app.ui.PerTeScreen
import com.switchpulse.app.ui.SettingsScreen
import com.switchpulse.app.ui.components.SpSnackbarHost
import com.switchpulse.app.ui.motion.LocalReduceMotion
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import com.switchpulse.app.ui.theme.SwitchPulseTheme
import com.switchpulse.app.worker.DealsSyncWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var pendingIntentState by mutableStateOf<Intent?>(null)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingIntentState = intent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val openUpdatesInitially = intent?.getBooleanExtra(DealsSyncWorker.EXTRA_OPEN_UPDATES, false) == true

        setContent {
            SwitchPulseTheme {
                SwitchPulseRoot(
                    viewModel = viewModel,
                    initialSettingsOpen = openUpdatesInitially,
                    pendingIntent = pendingIntentState,
                    onIntentConsumed = { pendingIntentState = null },
                    onOpenUrl = { url ->
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntentState = intent
    }

    companion object {
        const val EXTRA_OPEN_DEAL_ID = "open_deal_id"
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SwitchPulseRoot(
    viewModel: MainViewModel,
    initialSettingsOpen: Boolean,
    pendingIntent: Intent?,
    onIntentConsumed: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val c = SpTheme.colors
    val reduceMotion = LocalReduceMotion.current
    val density = LocalDensity.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(initialSettingsOpen) }
    var showHiddenDeals by rememberSaveable { mutableStateOf(false) }
    var showFiltersSheet by rememberSaveable { mutableStateOf(false) }
    var showAddGameSheet by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(pendingIntent) {
        val current = pendingIntent ?: return@LaunchedEffect
        if (current.getBooleanExtra(DealsSyncWorker.EXTRA_OPEN_UPDATES, false)) {
            selectedTab = 2
            showSettings = true
        }
        val dealId = current.getStringExtra(MainActivity.EXTRA_OPEN_DEAL_ID)?.takeIf { it.isNotBlank() }
        if (dealId != null) {
            selectedTab = 0
            showSettings = false
            showHiddenDeals = false
            viewModel.openDealById(dealId)
        }
        onIntentConsumed()
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.paper),
        contentAlignment = Alignment.TopCenter
    ) {
        SharedTransitionLayout(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
        ) {
            val sharedScope = this
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = c.paper,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SpSnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        EditorialBottomBar(
                            selectedTab = selectedTab,
                            onSelectTab = { next ->
                                if (next != selectedTab) {
                                    viewModel.clearRankDeltas()
                                    selectedTab = next
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    val tabSlidePx = with(density) { 8.dp.roundToPx() }
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            if (reduceMotion) {
                                fadeIn(Motion.quick(true)) togetherWith fadeOut(Motion.quick(true))
                            } else {
                                (fadeIn(androidx.compose.animation.core.tween(200, easing = Motion.Out)) +
                                    slideInVertically(androidx.compose.animation.core.tween(200, easing = Motion.Out)) { tabSlidePx })
                                    .togetherWith(fadeOut(androidx.compose.animation.core.tween(120)))
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                        label = "root_tabs"
                    ) { tab ->
                        when (tab) {
                            0 -> FeedScreen(
                                state = state,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = this@AnimatedContent,
                                onSelectPlatform = viewModel::setAppPlatformFilter,
                                onOpenFilters = { showFiltersSheet = true },
                                onOpenDealDetail = { deal, rank ->
                                    viewModel.openDealDetail(deal, rank, "cover-${deal.id}")
                                },
                                onSwipeHideDeal = { deal ->
                                    viewModel.hideDeal(deal)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val res = snackbarHostState.showSnackbar(
                                            message = "“${deal.title}” nascosto",
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.unhideDeal(deal.id)
                                        }
                                    }
                                },
                                onSwipeOwnedDeal = { deal ->
                                    viewModel.setGameOwned(deal.toLikedGame(), true)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val res = snackbarHostState.showSnackbar(
                                            message = "“${deal.title}” è nei tuoi giochi",
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.setGameOwned(deal.toLikedGame(), false)
                                        }
                                    }
                                },
                                onQuickRateDeal = { deal, stars ->
                                    viewModel.setGameRating(deal.toLikedGame(), stars)
                                },
                                onRefresh = viewModel::refreshDeals,
                                onResetFilters = viewModel::resetFilters,
                                onRerankMissingDeals = viewModel::rerankMissingVisibleDeals,
                                onRerankAllNow = viewModel::rerankWithGeminiNow,
                                onOpenUpdateSettings = { showSettings = true }
                            )

                            1 -> PerTeScreen(
                                state = state,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = this@AnimatedContent,
                                onGenerateSoul = viewModel::generateGamerSoul,
                                onSaveUserTastePrompt = viewModel::setUserTastePrompt,
                                onRefreshDiscoveryPicks = { viewModel.discoverGamesForSoul(freshBatch = true) },
                                onOpenDiscoveryDetail = viewModel::openDiscoveryDetail,
                                onOpenSettings = { showSettings = true },
                                onOpenAddGame = { showAddGameSheet = true }
                            )

                            else -> LibraryScreen(
                                state = state,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = this@AnimatedContent,
                                onOpenSettings = { showSettings = true },
                                onOpenAddGameSheet = { showAddGameSheet = true },
                                onOpenLibraryDetail = viewModel::openLibraryDetail,
                                onSwipeRemoveGame = { game ->
                                    viewModel.removeLikedGame(game.id)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val res = snackbarHostState.showSnackbar(
                                            message = "“${game.title}” rimosso",
                                            actionLabel = undoLabel,
                                            duration = SnackbarDuration.Short
                                        )
                                        if (res == SnackbarResult.ActionPerformed) {
                                            viewModel.setGameOwned(game, game.isOwned)
                                            if (game.rating != null) viewModel.setGameRating(game, game.rating)
                                            if (!game.userNote.isNullOrBlank()) viewModel.setGameNote(game, game.userNote)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Settings full-screen push overlay
                AnimatedVisibility(
                    visible = showSettings,
                    enter = if (reduceMotion) fadeIn(Motion.quick(true)) else slideInHorizontally(Motion.standard()) { it / 3 } + fadeIn(Motion.standard()),
                    exit = if (reduceMotion) fadeOut(Motion.quick(true)) else slideOutHorizontally(Motion.exit()) { it / 3 } + fadeOut(Motion.exit())
                ) {
                    SettingsScreen(
                        state = state,
                        onBack = { showSettings = false },
                        onSaveProviderApiKey = viewModel::setProviderApiKey,
                        onTestProviderConnection = { prov, pending -> viewModel.testProviderConnection(prov, pending) },
                        onSelectRerankTarget = viewModel::setRerankTarget,
                        onSelectInsightsTarget = viewModel::setInsightsTarget,
                        onSelectSoulTarget = viewModel::setSoulTarget,
                        onRefreshModels = { prov -> viewModel.refreshAvailableModels(prov, silent = false) },
                        onRerankWithGeminiNow = viewModel::rerankWithGeminiNow,
                        onBackgroundRerankPolicyChange = viewModel::setBackgroundRerankPolicy,
                        onWidgetPlatformFilterChange = viewModel::setWidgetPlatformFilter,
                        onSyncIntervalChange = viewModel::setSyncInterval,
                        onAutoCheckUpdatesChange = viewModel::setAutoCheckUpdates,
                        onCheckUpdates = { viewModel.checkForUpdates(silentIfUpToDate = false) },
                        onDownloadAndInstall = viewModel::downloadAndInstall,
                        onInstallDownloaded = viewModel::installDownloaded,
                        onSaveGithubRepo = viewModel::setGithubRepo,
                        onExportBackupJson = viewModel::exportBackupJson,
                        onImportBackupJson = viewModel::importBackupJson,
                        onFeedback = viewModel::showStatusMessage
                    )
                }

                // Hidden games full-screen push overlay
                AnimatedVisibility(
                    visible = showHiddenDeals,
                    enter = if (reduceMotion) fadeIn(Motion.quick(true)) else slideInHorizontally(Motion.standard()) { it / 3 } + fadeIn(Motion.standard()),
                    exit = if (reduceMotion) fadeOut(Motion.quick(true)) else slideOutHorizontally(Motion.exit()) { it / 3 } + fadeOut(Motion.exit())
                ) {
                    HiddenDealsScreen(
                        blacklistIds = state.settings.blacklistIds,
                        blacklistTitles = state.settings.blacklistTitles,
                        rawCachedDeals = state.rawCachedDeals,
                        onUnhideDeal = viewModel::unhideDeal,
                        onBack = { showHiddenDeals = false }
                    )
                }

                // Full-screen Game Detail Screen with Shared Element cover transition
                AnimatedVisibility(
                    visible = state.selectedDetail != null,
                    enter = fadeIn(Motion.emphasized(reduceMotion)),
                    exit = fadeOut(androidx.compose.animation.core.tween(if (reduceMotion) 120 else 300, easing = Motion.Away))
                ) {
                    val activeDetail = state.selectedDetail
                    if (activeDetail != null) {
                        val existingEntry = state.settings.likedGames.firstOrNull {
                            it.id == activeDetail.id ||
                                com.switchpulse.app.data.model.DealTitleNormalizer.normalize(it.title) == activeDetail.normalizedTitle
                        }
                        val targetLiked = activeDetail.toLikedGame(existingEntry)
                        GameDetailScreen(
                            detail = activeDetail,
                            settings = state.settings,
                            insights = state.detailInsights,
                            isLoadingInsights = state.isLoadingInsights,
                            libraryEntry = existingEntry,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = this@AnimatedVisibility,
                            onDismiss = viewModel::closeDealDetail,
                            onOpenEshop = onOpenUrl,
                            onSetRating = { rating -> viewModel.setGameRating(targetLiked, rating) },
                            onSetOwned = { owned -> viewModel.setGameOwned(targetLiked, owned) },
                            onSetNote = { note -> viewModel.setGameNote(targetLiked, note) },
                            onSetTargetPrice = { target -> viewModel.setGameTargetPrice(targetLiked, target) },
                            onHideDeal = {
                                activeDetail.sourceDeal?.let(viewModel::hideDeal)
                            },
                            onDismissPick = viewModel::dismissDiscoveryPick,
                            onRemoveLibrary = viewModel::removeLikedGame,
                            onRefreshInsights = viewModel::refreshSelectedDetailInsights
                        )
                    }
                }
            }
        }
    }

    if (showFiltersSheet) {
        FiltersBottomSheet(
            settings = state.settings,
            rawCachedDeals = state.rawCachedDeals,
            onDismiss = { showFiltersSheet = false },
            onResetFilters = viewModel::resetFilters,
            onSelectSortMode = viewModel::setFeedSortMode,
            onToggleForYouOnly = viewModel::setForYouOnlyMode,
            onToggleExpiringSoonOnly = viewModel::setExpiringSoonOnly,
            onChangeMinDiscount = viewModel::setMinDiscount,
            onChangeMinRegularPrice = viewModel::setMinRegularPrice,
            onChangeRecommendationWeight = viewModel::setRecommendationWeight,
            onOpenHiddenGames = {
                showFiltersSheet = false
                showHiddenDeals = true
            }
        )
    }

    if (showAddGameSheet) {
        AddGameSheet(
            state = state,
            onDismiss = {
                showAddGameSheet = false
                viewModel.clearProfileSearchResults()
            },
            onSearchGames = viewModel::searchProfileGames,
            onClearSearchResults = viewModel::clearProfileSearchResults,
            onSaveGameToLibrary = { game, rating, owned, note ->
                viewModel.setGameOwned(game, owned)
                if (rating != null) viewModel.setGameRating(game, rating)
                if (note != null) viewModel.setGameNote(game, note)
            }
        )
    }
}

@Composable
private fun EditorialBottomBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val c = SpTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.paper)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider(thickness = 1.dp, color = c.line)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
        ) {
            BottomNavTabItem(
                icon = Icons.Rounded.Leaderboard,
                label = stringResource(R.string.nav_ranking),
                selected = selectedTab == 0,
                onClick = { onSelectTab(0) },
                modifier = Modifier.weight(1f)
            )
            BottomNavTabItem(
                icon = Icons.Rounded.Explore,
                label = stringResource(R.string.nav_for_you),
                selected = selectedTab == 1,
                onClick = { onSelectTab(1) },
                modifier = Modifier.weight(1f)
            )
            BottomNavTabItem(
                icon = Icons.Rounded.CollectionsBookmark,
                label = stringResource(R.string.nav_library),
                selected = selectedTab == 2,
                onClick = { onSelectTab(2) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BottomNavTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) c.ink else c.faint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = SpType.navLabel.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (selected) c.ink else c.faint,
            maxLines = 1
        )
    }
}
