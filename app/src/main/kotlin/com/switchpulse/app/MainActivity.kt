package com.switchpulse.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.switchpulse.app.data.model.DealTitleNormalizer
import com.switchpulse.app.ui.AiDiscoverySheet
import com.switchpulse.app.ui.FeedScreen
import com.switchpulse.app.ui.GameDetailSheet
import com.switchpulse.app.ui.LibraryScreen
import com.switchpulse.app.ui.MainViewModel
import com.switchpulse.app.ui.SettingsScreen
import com.switchpulse.app.ui.theme.EshopCrimson
import com.switchpulse.app.ui.theme.SwitchPulseTheme
import com.switchpulse.app.worker.DealsSyncWorker
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openUpdatesInitially = intent?.getBooleanExtra(DealsSyncWorker.EXTRA_OPEN_UPDATES, false) == true

        setContent {
            SwitchPulseTheme {
                SwitchPulseRoot(
                    viewModel = viewModel,
                    initialTab = if (openUpdatesInitially) 2 else 0,
                    onOpenUrl = { url ->
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchPulseRoot(
    viewModel: MainViewModel,
    initialTab: Int,
    onOpenUrl: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var showAddGameSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Column {
                        Text(
                            text = when (selectedTab) {
                                0 -> "Offerte Top 30"
                                1 -> "La tua Libreria"
                                else -> "Filtri & Aggiornamenti"
                            },
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (selectedTab != 1) {
                            Text(
                                text = "Nintendo eShop Italia · Switch & Switch 2",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    when (selectedTab) {
                        0 -> {
                            IconButton(
                                onClick = { viewModel.refreshDeals() },
                                enabled = !state.isRefreshing
                            ) {
                                if (state.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = EshopCrimson
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Aggiorna offerte"
                                    )
                                }
                            }
                        }

                        1 -> {
                            Button(
                                onClick = { showAddGameSheet = true },
                                shape = RoundedCornerShape(999.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EshopCrimson,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .height(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Aggiungi",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                    label = { Text("Classifica") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null) },
                    label = { Text("Libreria") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    label = { Text("Filtri & Update") }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> FeedScreen(
                state = state,
                onSelectPlatform = viewModel::setAppPlatformFilter,
                onOpenDealDetail = viewModel::openDealDetail,
                onHideDeal = { deal ->
                    viewModel.hideDeal(deal)
                    scope.launch {
                        val res = snackbarHostState.showSnackbar(
                            message = "\"${deal.title}\" nascosto",
                            actionLabel = "Annulla",
                            duration = SnackbarDuration.Short
                        )
                        if (res == SnackbarResult.ActionPerformed) {
                            viewModel.unhideDeal(deal.id)
                        }
                    }
                },
                onToggleForYou = viewModel::setForYouOnlyMode,
                onRefresh = viewModel::refreshDeals,
                onResetFilters = viewModel::resetFilters,
                onOpenUpdateTab = { selectedTab = 2 },
                modifier = Modifier.padding(innerPadding)
            )

            1 -> LibraryScreen(
                state = state,
                showAddGameSheet = showAddGameSheet,
                onOpenAddGameSheet = { showAddGameSheet = true },
                onDismissAddGameSheet = {
                    showAddGameSheet = false
                    viewModel.clearProfileSearchResults()
                },
                onSearchGames = viewModel::searchProfileGames,
                onClearSearchResults = viewModel::clearProfileSearchResults,
                onSetGameRating = viewModel::setGameRating,
                onSetGameOwned = viewModel::setGameOwned,
                onSetGameNote = viewModel::setGameNote,
                onRemoveGame = viewModel::removeLikedGame,
                onOpenEshop = onOpenUrl,
                onGenerateGamerSoul = viewModel::generateGamerSoul,
                onOpenDiscoverySheet = viewModel::openDiscoverySheet,
                onRefreshDiscoveryPicks = { viewModel.discoverGamesForSoul(freshBatch = true) },
                modifier = Modifier.padding(innerPadding)
            )

            else -> SettingsScreen(
                state = state,
                onMinDiscountChange = viewModel::setMinDiscount,
                onMinRegularPriceChange = viewModel::setMinRegularPrice,
                onSyncIntervalChange = viewModel::setSyncInterval,
                onUnhideDeal = viewModel::unhideDeal,
                onSaveGithubRepo = viewModel::setGithubRepo,
                onAutoCheckUpdatesChange = viewModel::setAutoCheckUpdates,
                onCheckUpdates = { viewModel.checkForUpdates(silentIfUpToDate = false) },
                onDownloadAndInstall = viewModel::downloadAndInstall,
                onInstallDownloaded = viewModel::installDownloaded,
                onWidgetPinFeedback = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onRecommendationWeightChange = viewModel::setRecommendationWeight,
                onSaveGeminiApiKey = viewModel::setGeminiApiKey,
                onSelectRerankModel = viewModel::setGeminiRerankModel,
                onSelectInsightsModel = viewModel::setGeminiInsightsModel,
                onSelectSoulModel = viewModel::setGeminiSoulModel,
                onRefreshGeminiModels = { viewModel.refreshAvailableGeminiModels(silent = false) },
                onTestGeminiConnection = { pending -> viewModel.testGeminiConnection(pending) },
                onRerankWithGeminiNow = viewModel::rerankWithGeminiNow,
                onSaveUserTastePrompt = viewModel::setUserTastePrompt,
                onOpenLibraryTab = { selectedTab = 1 },
                modifier = Modifier.padding(innerPadding)
            )
        }

        val activeDeal = state.selectedDeal
        if (activeDeal != null) {
            val activeNorm = DealTitleNormalizer.normalize(activeDeal.title)
            val existingEntry = state.settings.likedGames.firstOrNull {
                it.id == activeDeal.id || DealTitleNormalizer.normalize(it.title) == activeNorm
            }
            GameDetailSheet(
                deal = activeDeal,
                settings = state.settings,
                insights = state.detailInsights,
                isLoadingInsights = state.isLoadingInsights,
                libraryEntry = existingEntry,
                onDismiss = viewModel::closeDealDetail,
                onOpenEshop = onOpenUrl,
                onSetRating = { rating ->
                    viewModel.setGameRating(existingEntry ?: activeDeal.toLikedGame(), rating)
                },
                onSetOwned = { owned ->
                    viewModel.setGameOwned(existingEntry ?: activeDeal.toLikedGame(), owned)
                    if (owned) {
                        viewModel.closeDealDetail()
                    }
                },
                onSetNote = { note ->
                    viewModel.setGameNote(existingEntry ?: activeDeal.toLikedGame(), note)
                },
                onHideDeal = { viewModel.hideDeal(activeDeal) },
                onRefreshInsights = { viewModel.refreshDealInsights(activeDeal) }
            )
        }

        if (state.showDiscoverySheet) {
            AiDiscoverySheet(
                soulProfile = state.settings.gamerSoulProfile,
                picks = state.settings.discoveryPicks,
                library = state.settings.likedGames,
                isLoading = state.isDiscoveringGames,
                onDismiss = viewModel::dismissDiscoverySheet,
                onRefreshPicks = { viewModel.discoverGamesForSoul(freshBatch = true) },
                onOpenEshop = onOpenUrl,
                onToggleOwned = viewModel::setGameOwned,
                onQuickRate = { game, stars -> viewModel.setGameRating(game, stars) }
            )
        }
    }
}
