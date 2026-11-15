package com.switchpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.switchpulse.app.BuildConfig
import com.switchpulse.app.data.DealsRepository
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.PlatformFilter
import com.switchpulse.app.update.GitHubUpdater
import com.switchpulse.app.update.InstallLaunchResult
import com.switchpulse.app.update.ReleaseInfo
import com.switchpulse.app.update.UpdateCheckResult
import com.switchpulse.app.worker.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: ReleaseInfo) : UpdateUiState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateUiState
    data class ReadyToInstall(val release: ReleaseInfo, val apkFile: File) : UpdateUiState
    data class UpToDate(val version: String) : UpdateUiState
    data object NotConfigured : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

data class MainUiState(
    val deals: List<Deal> = emptyList(),
    val settings: FilterSettings = FilterSettings(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val statusMessage: String? = null,
    val profileSearchResults: List<LikedGame> = emptyList(),
    val isSearchingProfile: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repository = DealsRepository(appContext)
    private val updater = GitHubUpdater()

    private val isRefreshing = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val profileSearchResults = MutableStateFlow<List<LikedGame>>(emptyList())
    private val isSearchingProfile = MutableStateFlow(false)

    val uiState: StateFlow<MainUiState> = combine(
        repository.filteredAppDealsFlow,
        repository.settingsFlow,
        combine(isRefreshing, errorMessage) { r, e -> r to e },
        combine(updateState, statusMessage) { u, s -> u to s },
        combine(profileSearchResults, isSearchingProfile) { res, searching -> res to searching }
    ) { deals, settings, (refreshing, error), (update, status), (searchResults, searching) ->
        MainUiState(
            deals = deals,
            settings = settings,
            isRefreshing = refreshing,
            errorMessage = error,
            updateState = update,
            statusMessage = status,
            profileSearchResults = searchResults,
            isSearchingProfile = searching
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState()
    )

    init {
        viewModelScope.launch {
            val settings = repository.preferences.getSettings()
            val cached = repository.preferences.getCachedDeals()
            val twoHoursMs = 2 * 60 * 60 * 1000L
            if (cached.isEmpty() || System.currentTimeMillis() - settings.lastSyncEpochMs > twoHoursMs) {
                refreshDeals()
            }
            if (settings.autoCheckUpdates && settings.githubRepo.isNotBlank()) {
                checkForUpdates(silentIfUpToDate = true)
            }
        }
    }

    fun refreshDeals() {
        if (isRefreshing.value) return
        viewModelScope.launch {
            isRefreshing.value = true
            errorMessage.value = null
            repository.refreshDeals()
                .onFailure { err ->
                    errorMessage.value = err.message ?: "Errore di rete durante il sync con Nintendo eShop"
                }
            isRefreshing.value = false
        }
    }

    fun setAppPlatformFilter(filter: PlatformFilter) {
        viewModelScope.launch {
            repository.preferences.updateAppPlatformFilter(filter)
        }
    }

    fun setMinDiscount(percent: Int) {
        viewModelScope.launch {
            repository.preferences.updateMinDiscount(percent)
            repository.notifyWidgetChanged()
        }
    }

    fun setMinRegularPrice(eur: Float) {
        viewModelScope.launch {
            repository.preferences.updateMinRegularPrice(eur)
            repository.notifyWidgetChanged()
        }
    }

    fun setSyncInterval(hours: Int) {
        viewModelScope.launch {
            repository.preferences.updateSyncIntervalHours(hours)
            SyncScheduler.schedulePeriodic(appContext, hours)
            statusMessage.value = "Aggiornamento automatico impostato ogni ${hours}h"
        }
    }

    fun hideDeal(deal: Deal) {
        viewModelScope.launch {
            repository.preferences.hideDeal(deal.id, deal.title)
            repository.notifyWidgetChanged()
        }
    }

    fun unhideDeal(dealId: String) {
        viewModelScope.launch {
            repository.preferences.unhideDeal(dealId)
            repository.notifyWidgetChanged()
        }
    }

    fun toggleLikeDeal(deal: Deal) {
        viewModelScope.launch {
            val added = repository.preferences.toggleLikedGame(deal.toLikedGame())
            statusMessage.value = if (added) {
                "\"${deal.title}\" aggiunto ai tuoi gusti ♥"
            } else {
                "\"${deal.title}\" rimosso dai preferiti"
            }
            repository.notifyWidgetChanged()
        }
    }

    fun searchProfileGames(query: String) {
        if (query.trim().length < 2) {
            profileSearchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            isSearchingProfile.value = true
            profileSearchResults.value = runCatching {
                repository.searchGamesForProfile(query)
            }.getOrDefault(emptyList())
            isSearchingProfile.value = false
        }
    }

    fun addLikedGame(game: LikedGame) {
        viewModelScope.launch {
            repository.preferences.addLikedGame(game)
            profileSearchResults.value = emptyList()
            statusMessage.value = "\"${game.title}\" salvato nei tuoi preferiti ♥"
            repository.notifyWidgetChanged()
        }
    }

    fun removeLikedGame(gameId: String) {
        viewModelScope.launch {
            repository.preferences.removeLikedGame(gameId)
            repository.notifyWidgetChanged()
        }
    }

    fun setRecommendationWeight(weight: Int) {
        viewModelScope.launch {
            repository.preferences.updateRecommendationWeight(weight)
            repository.notifyWidgetChanged()
        }
    }

    fun setForYouOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.updateForYouMode(enabled)
        }
    }

    fun setGeminiApiKey(apiKey: String) {
        viewModelScope.launch {
            repository.preferences.updateGeminiApiKey(apiKey)
            statusMessage.value = if (apiKey.trim().isBlank()) {
                "Chiave Gemini rimossa (attivo motore locale a micro-tag)"
            } else {
                "Chiave Gemini salvata · Ricarica il feed per attivare il reranking AI"
            }
        }
    }

    fun resetFilters() {
        viewModelScope.launch {
            repository.preferences.resetFilters()
            repository.notifyWidgetChanged()
        }
    }

    fun setGithubRepo(repo: String) {
        viewModelScope.launch {
            repository.preferences.updateGithubRepo(repo)
            statusMessage.value = "Repository GitHub salvato"
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        viewModelScope.launch {
            repository.preferences.updateAutoCheckUpdates(enabled)
        }
    }

    fun checkForUpdates(silentIfUpToDate: Boolean = false) {
        viewModelScope.launch {
            val repo = repository.preferences.getSettings().githubRepo
            if (!silentIfUpToDate) {
                updateState.value = UpdateUiState.Checking
            }
            when (
                val res = updater.checkLatestRelease(
                    repoSlug = repo,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE
                )
            ) {
                is UpdateCheckResult.UpdateAvailable -> {
                    updateState.value = UpdateUiState.Available(res.release)
                }
                is UpdateCheckResult.UpToDate -> {
                    if (!silentIfUpToDate) {
                        updateState.value = UpdateUiState.UpToDate(res.currentVersion)
                    }
                }
                UpdateCheckResult.NotConfigured -> {
                    if (!silentIfUpToDate) {
                        updateState.value = UpdateUiState.NotConfigured
                    }
                }
                UpdateCheckResult.NoApkAsset -> {
                    if (!silentIfUpToDate) {
                        updateState.value = UpdateUiState.Error("La release più recente non contiene alcun file .apk allegato.")
                    }
                }
                is UpdateCheckResult.Error -> {
                    if (!silentIfUpToDate) {
                        updateState.value = UpdateUiState.Error(res.message)
                    }
                }
            }
        }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        viewModelScope.launch {
            runCatching {
                updateState.value = UpdateUiState.Downloading(release, 0f)
                val apkFile = updater.downloadApk(appContext, release.apkDownloadUrl) { progress ->
                    updateState.value = UpdateUiState.Downloading(release, progress)
                }
                updateState.value = UpdateUiState.ReadyToInstall(release, apkFile)
                val launchRes = updater.launchInstaller(appContext, apkFile)
                if (launchRes == InstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION) {
                    statusMessage.value = "Consenti l'installazione da SwitchPulse e tocca di nuovo Installa."
                }
            }.onFailure { err ->
                updateState.value = UpdateUiState.Error(err.message ?: "Download aggiornamento fallito")
            }
        }
    }

    fun installDownloaded(release: ReleaseInfo, apkFile: File) {
        val launchRes = updater.launchInstaller(appContext, apkFile)
        if (launchRes == InstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION) {
            statusMessage.value = "Consenti l'installazione da SwitchPulse nelle impostazioni e riprova."
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }
}
