package com.switchpulse.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.switchpulse.app.BuildConfig
import com.switchpulse.app.SwitchPulseApp
import com.switchpulse.app.data.local.BackupManager
import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.BackgroundRerankPolicy
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GameExternalInsights
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.data.model.PlatformFilter
import com.switchpulse.app.update.InstallLaunchResult
import com.switchpulse.app.update.ReleaseInfo
import com.switchpulse.app.update.UpdateCheckResult
import com.switchpulse.app.worker.SyncScheduler
import kotlinx.coroutines.Job
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
    val allEligibleDeals: List<Deal> = emptyList(),
    val settings: FilterSettings = FilterSettings(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val statusMessage: String? = null,
    val profileSearchResults: List<LikedGame> = emptyList(),
    val isSearchingProfile: Boolean = false,
    val selectedDeal: Deal? = null,
    val detailInsights: GameExternalInsights? = null,
    val isLoadingInsights: Boolean = false,
    val geminiTestResult: String? = null,
    val isTestingGemini: Boolean = false,
    val isLoadingModels: Boolean = false,
    val isGeneratingSoul: Boolean = false,
    val isDiscoveringGames: Boolean = false,
    val showDiscoverySheet: Boolean = false
)

private data class TransientUiState(
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val updateState: UpdateUiState = UpdateUiState.Idle,
    val statusMessage: String? = null,
    val profileSearchResults: List<LikedGame> = emptyList(),
    val isSearchingProfile: Boolean = false,
    val selectedDeal: Deal? = null,
    val detailInsights: GameExternalInsights? = null,
    val isLoadingInsights: Boolean = false,
    val geminiTestResult: String? = null,
    val isTestingGemini: Boolean = false,
    val isLoadingModels: Boolean = false,
    val isGeneratingSoul: Boolean = false,
    val isDiscoveringGames: Boolean = false,
    val showDiscoverySheet: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val container = SwitchPulseApp.containerFrom(appContext)
    private val repository = container.repository
    private val updater = container.updater
    private var profileSearchJob: Job? = null

    private val transientState = MutableStateFlow(TransientUiState())

    val uiState: StateFlow<MainUiState> = combine(
        repository.filteredAppDealsFlow,
        repository.allEligibleDealsFlow,
        repository.settingsFlow,
        transientState
    ) { deals, allEligibleDeals, settings, transient ->
        val freshSelected = transient.selectedDeal?.let { sel ->
            allEligibleDeals.firstOrNull { it.id == sel.id }
                ?: deals.firstOrNull { it.id == sel.id }
                ?: sel
        }
        MainUiState(
            deals = deals,
            allEligibleDeals = allEligibleDeals,
            settings = settings,
            isRefreshing = transient.isRefreshing,
            errorMessage = transient.errorMessage,
            updateState = transient.updateState,
            statusMessage = transient.statusMessage,
            profileSearchResults = transient.profileSearchResults,
            isSearchingProfile = transient.isSearchingProfile,
            selectedDeal = freshSelected,
            detailInsights = transient.detailInsights,
            isLoadingInsights = transient.isLoadingInsights,
            geminiTestResult = transient.geminiTestResult,
            isTestingGemini = transient.isTestingGemini,
            isLoadingModels = transient.isLoadingModels,
            isGeneratingSoul = transient.isGeneratingSoul,
            isDiscoveringGames = transient.isDiscoveringGames,
            showDiscoverySheet = transient.showDiscoverySheet
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
            val oneDayMs = 24 * 60 * 60 * 1000L
            val needsInitialModels = settings.hasAnyAiKey() &&
                (System.currentTimeMillis() - settings.geminiModelsUpdatedAtMs > oneDayMs ||
                    settings.configuredProviders().any { settings.availableModelsFor(it).isEmpty() })
            if (needsInitialModels) {
                refreshAvailableModels(silent = true)
            }
        }
    }

    fun refreshDeals() {
        if (transientState.value.isRefreshing) return
        viewModelScope.launch {
            transientState.update { it.copy(isRefreshing = true, errorMessage = null) }
            repository.refreshDeals()
                .onFailure { err ->
                    transientState.update {
                        it.copy(errorMessage = err.message ?: "Errore di rete durante il sync con Nintendo eShop")
                    }
                }
            transientState.update { it.copy(isRefreshing = false) }
        }
    }

    fun openDealDetail(deal: Deal) {
        transientState.update {
            it.copy(
                selectedDeal = deal,
                detailInsights = null,
                isLoadingInsights = true
            )
        }
        loadInsights(deal, forceRefresh = false)
    }

    fun refreshDealInsights(deal: Deal) {
        transientState.update { it.copy(isLoadingInsights = true) }
        loadInsights(deal, forceRefresh = true)
    }

    fun closeDealDetail() {
        transientState.update {
            it.copy(
                selectedDeal = null,
                detailInsights = null,
                isLoadingInsights = false
            )
        }
    }

    private fun loadInsights(deal: Deal, forceRefresh: Boolean) {
        viewModelScope.launch {
            val insights = runCatching {
                repository.getOrFetchGameInsights(deal, forceRefresh = forceRefresh)
            }.getOrNull()
            // Apply only if the user is still viewing the same deal.
            transientState.update { state ->
                if (state.selectedDeal?.id == deal.id) {
                    state.copy(detailInsights = insights, isLoadingInsights = false)
                } else {
                    state
                }
            }
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
            transientState.update { it.copy(statusMessage = "Aggiornamento automatico impostato ogni ${hours}h") }
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

    fun setGameRating(game: LikedGame, rating: Int?) {
        viewModelScope.launch {
            repository.preferences.setGameRating(game, rating)
            val msg = if (rating != null) {
                "\"${game.title}\" valutato $rating★"
            } else {
                "Valutazione rimossa per \"${game.title}\""
            }
            transientState.update { it.copy(statusMessage = msg) }
            repository.notifyWidgetChanged()
        }
    }

    fun setGameOwned(game: LikedGame, owned: Boolean) {
        viewModelScope.launch {
            repository.preferences.setGameOwned(game, owned)
            val msg = if (owned) {
                "\"${game.title}\" segnato come già posseduto (escluso dalle offerte)"
            } else {
                "\"${game.title}\" rimosso dai giochi posseduti"
            }
            transientState.update { it.copy(statusMessage = msg) }
            repository.notifyWidgetChanged()
        }
    }

    fun setGameNote(game: LikedGame, note: String?) {
        viewModelScope.launch {
            repository.preferences.setGameNote(game, note)
            val clean = note?.trim().orEmpty()
            val msg = if (clean.isNotEmpty()) {
                "Nota per l'IA salvata su \"${game.title}\""
            } else {
                "Nota rimossa per \"${game.title}\""
            }
            transientState.update { it.copy(statusMessage = msg) }
        }
    }

    fun setUserTastePrompt(prompt: String) {
        viewModelScope.launch {
            repository.preferences.updateUserTastePrompt(prompt)
            val msg = if (prompt.trim().isNotBlank()) {
                "Preferenze AI salvate · Tocca \"Ricalcola AI ora\" per aggiornare la classifica"
            } else {
                "Preferenze AI testuali rimosse"
            }
            transientState.update { it.copy(statusMessage = msg) }
        }
    }

    fun generateGamerSoul() {
        if (transientState.value.isGeneratingSoul) return
        viewModelScope.launch {
            transientState.update { it.copy(isGeneratingSoul = true) }
            val result = repository.generateGamerSoul()
            val msg = result.fold(
                onSuccess = { profile -> "✨ Anima aggiornata: ${profile.archetypeTitle}" },
                onFailure = { err -> "⚠️ ${err.message ?: "Impossibile generare il profilo"}" }
            )
            transientState.update { it.copy(isGeneratingSoul = false, statusMessage = msg) }
        }
    }

    fun openDiscoverySheet() {
        val hasCached = uiState.value.settings.discoveryPicks.isNotEmpty()
        if (hasCached) {
            transientState.update { it.copy(showDiscoverySheet = true) }
        } else {
            discoverGamesForSoul(freshBatch = false)
        }
    }

    fun discoverGamesForSoul(freshBatch: Boolean = false) {
        if (transientState.value.isDiscoveringGames) return
        viewModelScope.launch {
            transientState.update { it.copy(showDiscoverySheet = true, isDiscoveringGames = true) }
            val result = repository.discoverGamesForSoul(freshBatch = freshBatch)
            val errMsg = result.exceptionOrNull()?.message
            transientState.update {
                it.copy(
                    isDiscoveringGames = false,
                    statusMessage = errMsg?.let { msg -> "⚠️ $msg" } ?: it.statusMessage
                )
            }
        }
    }

    fun dismissDiscoverySheet() {
        transientState.update { it.copy(showDiscoverySheet = false) }
    }

    fun searchProfileGames(query: String) {
        profileSearchJob?.cancel()
        if (query.trim().length < 2) {
            transientState.update { it.copy(profileSearchResults = emptyList(), isSearchingProfile = false) }
            return
        }
        profileSearchJob = viewModelScope.launch {
            transientState.update { it.copy(isSearchingProfile = true) }
            val results = runCatching {
                repository.searchGamesForProfile(query)
            }.getOrDefault(emptyList())
            transientState.update { it.copy(profileSearchResults = results, isSearchingProfile = false) }
        }
    }

    fun clearProfileSearchResults() {
        transientState.update { it.copy(profileSearchResults = emptyList()) }
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
            repository.notifyWidgetChanged()
        }
    }

    fun setProviderApiKey(provider: AiProvider, apiKey: String) {
        viewModelScope.launch {
            repository.preferences.updateProviderApiKey(provider, apiKey)
            val clean = apiKey.trim()
            val msg = if (clean.isBlank()) {
                "Chiave ${provider.displayName} rimossa"
            } else {
                "Chiave ${provider.displayName} salvata · Caricamento modelli..."
            }
            transientState.update { it.copy(statusMessage = msg, geminiTestResult = null) }
            if (clean.isNotBlank()) {
                refreshAvailableModels(provider = provider, silent = true)
            }
        }
    }

    fun setRerankTarget(provider: AiProvider, modelId: String) {
        viewModelScope.launch {
            repository.preferences.updateRerankTarget(provider, modelId)
            transientState.update {
                it.copy(
                    statusMessage = "Classifica: ${provider.shortLabel} · $modelId",
                    geminiTestResult = null
                )
            }
        }
    }

    fun setInsightsTarget(provider: AiProvider, modelId: String) {
        viewModelScope.launch {
            repository.preferences.updateInsightsTarget(provider, modelId)
            transientState.update {
                it.copy(
                    statusMessage = "Schede gioco: ${provider.shortLabel} · $modelId",
                    geminiTestResult = null
                )
            }
        }
    }

    fun setSoulTarget(provider: AiProvider, modelId: String) {
        viewModelScope.launch {
            repository.preferences.updateSoulTarget(provider, modelId)
            transientState.update {
                it.copy(
                    statusMessage = "Anima & Consigli: ${provider.shortLabel} · $modelId",
                    geminiTestResult = null
                )
            }
        }
    }

    fun refreshAvailableModels(provider: AiProvider? = null, silent: Boolean = false) {
        if (transientState.value.isLoadingModels) return
        viewModelScope.launch {
            transientState.update { it.copy(isLoadingModels = true) }
            val result = if (provider != null) {
                repository.refreshModelsForProvider(provider).map { it.size }
            } else {
                repository.refreshAllAvailableModels()
            }
            val msg = if (silent) {
                null
            } else {
                result.fold(
                    onSuccess = { count ->
                        val scopeLabel = provider?.displayName ?: "provider AI"
                        "✨ $count modelli caricati da $scopeLabel"
                    },
                    onFailure = { err -> "⚠️ Aggiornamento modelli fallito: ${err.message ?: "errore di rete"}" }
                )
            }
            transientState.update {
                it.copy(
                    isLoadingModels = false,
                    statusMessage = msg ?: it.statusMessage
                )
            }
        }
    }

    fun testProviderConnection(provider: AiProvider, pendingKey: String? = null) {
        if (transientState.value.isTestingGemini) return
        viewModelScope.launch {
            transientState.update { it.copy(isTestingGemini = true, geminiTestResult = null) }
            val trimmedPending = pendingKey?.trim()
            if (!trimmedPending.isNullOrBlank()) {
                val currentSaved = repository.preferences.getSettings().apiKeyFor(provider)
                if (trimmedPending != currentSaved) {
                    repository.preferences.updateProviderApiKey(provider, trimmedPending)
                }
            }
            val resultText = repository.testProviderKey(provider).fold(
                onSuccess = { ok -> ok },
                onFailure = { err -> "⚠️ ${err.message ?: "Errore connessione ${provider.displayName}"}" }
            )
            transientState.update {
                it.copy(
                    isTestingGemini = false,
                    geminiTestResult = resultText,
                    statusMessage = resultText
                )
            }
        }
    }

    fun rerankWithGeminiNow() {
        if (transientState.value.isRefreshing) return
        viewModelScope.launch {
            transientState.update { it.copy(isRefreshing = true) }
            val msg = repository.rerankCachedDealsWithGemini(force = true).fold(
                onSuccess = { summary -> summary },
                onFailure = { err -> "⚠️ ${err.message ?: "Errore analisi AI"}" }
            )
            transientState.update {
                it.copy(
                    isRefreshing = false,
                    geminiTestResult = msg,
                    statusMessage = msg
                )
            }
        }
    }

    fun rerankMissingVisibleDeals() {
        if (transientState.value.isRefreshing) return
        val visible = uiState.value.deals
        if (visible.isEmpty()) return
        viewModelScope.launch {
            transientState.update { it.copy(isRefreshing = true) }
            val msg = repository.rerankMissingVisibleDeals(visible).fold(
                onSuccess = { summary -> summary },
                onFailure = { err -> "⚠️ ${err.message ?: "Errore completamento AI"}" }
            )
            transientState.update {
                it.copy(
                    isRefreshing = false,
                    geminiTestResult = msg,
                    statusMessage = msg
                )
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
            transientState.update { it.copy(statusMessage = "Repository GitHub salvato") }
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
                transientState.update { it.copy(updateState = UpdateUiState.Checking) }
            }
            when (
                val res = updater.checkLatestRelease(
                    repoSlug = repo,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE
                )
            ) {
                is UpdateCheckResult.UpdateAvailable -> {
                    transientState.update { it.copy(updateState = UpdateUiState.Available(res.release)) }
                }
                is UpdateCheckResult.UpToDate -> {
                    if (!silentIfUpToDate) {
                        transientState.update { it.copy(updateState = UpdateUiState.UpToDate(res.currentVersion)) }
                    }
                }
                UpdateCheckResult.NotConfigured -> {
                    if (!silentIfUpToDate) {
                        transientState.update { it.copy(updateState = UpdateUiState.NotConfigured) }
                    }
                }
                UpdateCheckResult.NoApkAsset -> {
                    if (!silentIfUpToDate) {
                        transientState.update {
                            it.copy(updateState = UpdateUiState.Error("La release più recente non contiene alcun file .apk allegato."))
                        }
                    }
                }
                is UpdateCheckResult.Error -> {
                    if (!silentIfUpToDate) {
                        transientState.update { it.copy(updateState = UpdateUiState.Error(res.message)) }
                    }
                }
            }
        }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        viewModelScope.launch {
            runCatching {
                transientState.update { it.copy(updateState = UpdateUiState.Downloading(release, 0f)) }
                val apkFile = updater.downloadApk(
                    context = appContext,
                    downloadUrl = release.apkDownloadUrl,
                    expectedSha256 = release.apkSha256
                ) { progress ->
                    transientState.update { it.copy(updateState = UpdateUiState.Downloading(release, progress)) }
                }
                transientState.update { it.copy(updateState = UpdateUiState.ReadyToInstall(release, apkFile)) }
                val launchRes = updater.launchInstaller(appContext, apkFile)
                if (launchRes == InstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION) {
                    transientState.update {
                        it.copy(statusMessage = "Consenti l'installazione da SwitchPulse e tocca di nuovo Installa.")
                    }
                }
            }.onFailure { err ->
                transientState.update {
                    it.copy(updateState = UpdateUiState.Error(err.message ?: "Download aggiornamento fallito"))
                }
            }
        }
    }

    fun installDownloaded(release: ReleaseInfo, apkFile: File) {
        val launchRes = updater.launchInstaller(appContext, apkFile)
        if (launchRes == InstallLaunchResult.NEEDS_UNKNOWN_SOURCES_PERMISSION) {
            transientState.update {
                it.copy(statusMessage = "Consenti l'installazione da SwitchPulse nelle impostazioni e riprova.")
            }
        }
    }

    fun openDealById(dealId: String) {
        viewModelScope.launch {
            val deal = uiState.value.allEligibleDeals.firstOrNull { it.id == dealId }
                ?: uiState.value.deals.firstOrNull { it.id == dealId }
                ?: repository.preferences.getCachedDeals().firstOrNull { it.id == dealId }
            if (deal != null) {
                openDealDetail(deal)
            } else {
                transientState.update {
                    it.copy(statusMessage = "Offerta non più disponibile in cache.")
                }
            }
        }
    }

    fun setGameTargetPrice(game: LikedGame, targetPriceEur: Double?) {
        viewModelScope.launch {
            repository.preferences.setGameTargetPrice(game, targetPriceEur)
            val msg = if (targetPriceEur != null) {
                "Avviso wishlist impostato sotto €${"%.2f".format(java.util.Locale.US, targetPriceEur)}"
            } else {
                "Avviso prezzo rimosso per ${game.title}"
            }
            transientState.update { it.copy(statusMessage = msg) }
        }
    }

    fun setBackgroundRerankPolicy(policy: BackgroundRerankPolicy) {
        viewModelScope.launch {
            repository.preferences.updateBackgroundRerankPolicy(policy)
        }
    }

    fun dismissDiscoveryPick(title: String) {
        viewModelScope.launch {
            repository.preferences.dismissDiscoveryPick(title)
            transientState.update { it.copy(statusMessage = "Suggerimento scartato: $title") }
        }
    }

    fun exportBackupJson(): String {
        return BackupManager.exportToJson(uiState.value.settings)
    }

    fun importBackupJson(rawJson: String) {
        viewModelScope.launch {
            BackupManager.decodeFromJson(rawJson).fold(
                onSuccess = { backup ->
                    repository.preferences.importBackup(backup)
                    repository.notifyWidgetChanged()
                    transientState.update {
                        it.copy(statusMessage = "Backup ripristinato (${backup.likedGames.size} titoli in libreria)")
                    }
                },
                onFailure = { err ->
                    transientState.update {
                        it.copy(statusMessage = "File backup non valido: ${err.message ?: "formato errato"}")
                    }
                }
            )
        }
    }

    fun showStatusMessage(message: String) {
        transientState.update { it.copy(statusMessage = message) }
    }

    fun clearStatusMessage() {
        transientState.update { it.copy(statusMessage = null) }
    }
}
