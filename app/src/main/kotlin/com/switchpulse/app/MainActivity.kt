package com.switchpulse.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tune
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.switchpulse.app.ui.FeedScreen
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
                    initialTab = if (openUpdatesInitially) 1 else 0,
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
                            text = if (selectedTab == 0) "Offerte Top 10" else "Filtri & Aggiornamenti",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Nintendo eShop Italia · Switch & Switch 2",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
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
                onOpenDeal = { deal -> onOpenUrl(deal.eshopUrl) },
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
                onRefresh = viewModel::refreshDeals,
                onResetFilters = viewModel::resetFilters,
                onOpenUpdateTab = { selectedTab = 1 },
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
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
