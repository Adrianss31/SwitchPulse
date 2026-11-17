package com.switchpulse.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switchpulse.app.BuildConfig
import com.switchpulse.app.R
import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.AiTaskTarget
import com.switchpulse.app.data.model.BackgroundRerankPolicy
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.data.model.GeminiModelInfo
import com.switchpulse.app.data.model.PlatformFilter
import com.switchpulse.app.data.remote.GeminiModels
import com.switchpulse.app.ui.components.PrimaryButton
import com.switchpulse.app.ui.components.SecondaryButton
import com.switchpulse.app.ui.components.SectionLabel
import com.switchpulse.app.ui.components.SegmentedChoice
import com.switchpulse.app.ui.components.SettingsRow
import com.switchpulse.app.ui.components.SpSwitch
import com.switchpulse.app.ui.motion.Motion
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import com.switchpulse.app.update.ReleaseInfo
import com.switchpulse.app.widget.CarouselWidgetReceiver
import com.switchpulse.app.widget.TopCoverWidgetReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class AiTaskSlot(
    val title: String,
    val subtitle: String,
    val recommendedId: String
) {
    RERANK(
        title = "Classifica offerte",
        subtitle = "Analisi brevi top 40 offerte",
        recommendedId = GeminiModels.FLASH_LITE
    ),
    INSIGHTS(
        title = "Schede dettaglio gioco",
        subtitle = "Metacritic, durata & verdetto",
        recommendedId = GeminiModels.FLASH_LITE
    ),
    SOUL(
        title = "Anima & Consigli su misura",
        subtitle = "Profilo giocatore e scoperte",
        recommendedId = GeminiModels.HYDROGEN
    )
}

@Composable
fun SettingsScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onSaveProviderApiKey: (AiProvider, String) -> Unit,
    onTestProviderConnection: (AiProvider, String) -> Unit,
    onSelectRerankTarget: (AiProvider, String) -> Unit,
    onSelectInsightsTarget: (AiProvider, String) -> Unit,
    onSelectSoulTarget: (AiProvider, String) -> Unit,
    onRefreshModels: (AiProvider?) -> Unit,
    onRerankWithGeminiNow: () -> Unit,
    onBackgroundRerankPolicyChange: (BackgroundRerankPolicy) -> Unit,
    onWidgetPlatformFilterChange: (PlatformFilter) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadAndInstall: (ReleaseInfo) -> Unit,
    onInstallDownloaded: (ReleaseInfo, File) -> Unit,
    onSaveGithubRepo: (String) -> Unit,
    onExportBackupJson: () -> String,
    onImportBackupJson: (String) -> Unit,
    onFeedback: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val backDragOffsetPx = remember { Animatable(0f) }
    val backProgressRatio = remember { Animatable(0f) }
    val maxPreviewShiftPx = with(density) { 48.dp.toPx() }
    PredictiveBackHandler(enabled = true) { progressFlow ->
        try {
            progressFlow.collect { ev ->
                val p = ev.progress.coerceIn(0f, 1f)
                backProgressRatio.snapTo(p)
                backDragOffsetPx.snapTo(p * maxPreviewShiftPx)
            }
            onBack()
        } catch (_: CancellationException) {
            scope.launch {
                launch { backDragOffsetPx.animateTo(0f, Motion.settle()) }
                launch { backProgressRatio.animateTo(0f, Motion.settle()) }
            }
        }
    }

    var editingProvider by remember { mutableStateOf<AiProvider?>(null) }
    var showModelsSheet by remember { mutableStateOf(false) }
    var activeTaskPicker by remember { mutableStateOf<AiTaskSlot?>(null) }
    var showBgAiSheet by remember { mutableStateOf(false) }
    var showWidgetPinSheet by remember { mutableStateOf(false) }
    var showWidgetPlatformSheet by remember { mutableStateOf(false) }
    var showSyncIntervalSheet by remember { mutableStateOf(false) }
    var showAdvancedSheet by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val payload = onExportBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
            } ?: error("Impossibile aprire il file di destinazione")
        }.onSuccess {
            onFeedback("Backup JSON esportato correttamente (chiavi API escluse)")
        }.onFailure { err ->
            onFeedback("Errore esportazione backup: ${err.message ?: "sconosciuto"}")
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: error("Impossibile leggere il file selezionato")
        }.onSuccess(onImportBackupJson).onFailure { err ->
            onFeedback("Errore lettura backup: ${err.message ?: "sconosciuto"}")
        }
    }

    val modelsByProvider = remember(
        state.settings.availableGeminiModels,
        state.settings.availableOpenRouterModels,
        state.settings.availableDeepSeekModels,
        state.settings.rerankTarget,
        state.settings.insightsTarget,
        state.settings.soulTarget
    ) {
        val selectedAll = listOf(
            state.settings.rerankTarget,
            state.settings.insightsTarget,
            state.settings.soulTarget
        )
        AiProvider.entries.associateWith { provider ->
            GeminiModels.resolveModelsList(
                dynamicModels = state.settings.availableModelsFor(provider),
                selectedIds = selectedAll.filter { it.provider == provider }.map { it.modelId },
                provider = provider
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = backProgressRatio.value
                translationX = backDragOffsetPx.value
                val scale = (1f - p * 0.04f).coerceIn(0.92f, 1f)
                scaleX = scale
                scaleY = scale
            }
            .background(c.paper)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Header with Back arrow + "Impostazioni"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 20.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = c.ink,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                style = SpType.sheetTitle,
                color = c.ink
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Group 1: Intelligenza artificiale
            Spacer(modifier = Modifier.height(14.dp))
            SectionLabel(text = stringResource(R.string.settings_group_ai))
            Spacer(modifier = Modifier.height(2.dp))

            AiProvider.entries.forEach { provider ->
                val connected = state.settings.hasKeyFor(provider)
                SettingsRow(
                    title = provider.shortLabel,
                    value = stringResource(
                        if (connected) R.string.settings_connected else R.string.settings_not_connected
                    ),
                    valueStrong = connected,
                    onClick = { editingProvider = provider }
                )
            }

            val isDefaultModels = state.settings.rerankTarget == AiTaskTarget(AiProvider.GEMINI, GeminiModels.FLASH_LITE) &&
                state.settings.insightsTarget == AiTaskTarget(AiProvider.GEMINI, GeminiModels.FLASH_LITE) &&
                state.settings.soulTarget == AiTaskTarget(AiProvider.GEMINI, GeminiModels.HYDROGEN)
            SettingsRow(
                title = stringResource(R.string.settings_models_row),
                subtitle = stringResource(R.string.settings_models_sub),
                value = stringResource(
                    if (isDefaultModels) R.string.settings_models_auto else R.string.settings_models_custom
                ),
                onClick = { showModelsSheet = true }
            )

            SettingsRow(
                title = stringResource(R.string.settings_bg_ai),
                value = state.settings.backgroundRerankPolicy.label,
                onClick = { showBgAiSheet = true }
            )

            SettingsRow(
                title = stringResource(R.string.settings_rerank_now),
                subtitle = state.settings.geminiStatusSummary.takeIf { it.isNotBlank() },
                value = if (state.isRefreshing) "In corso…" else null,
                onClick = { if (!state.isRefreshing) onRerankWithGeminiNow() }
            )

            // Group 2: Widget
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(text = stringResource(R.string.settings_group_widget))
            Spacer(modifier = Modifier.height(2.dp))

            SettingsRow(
                title = stringResource(R.string.settings_widget_pin),
                onClick = { showWidgetPinSheet = true }
            )

            val widgetPlatLabel = when (state.settings.widgetPlatformFilter) {
                PlatformFilter.ALL -> stringResource(R.string.ranking_platform_all)
                PlatformFilter.SWITCH_2 -> stringResource(R.string.ranking_platform_sw2)
                PlatformFilter.SWITCH_1 -> stringResource(R.string.ranking_platform_sw1)
            }
            SettingsRow(
                title = stringResource(R.string.settings_widget_platform),
                value = widgetPlatLabel,
                onClick = { showWidgetPlatformSheet = true }
            )

            // Group 3: Offerte
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(text = stringResource(R.string.settings_group_deals))
            Spacer(modifier = Modifier.height(2.dp))

            SettingsRow(
                title = stringResource(R.string.settings_sync_auto),
                subtitle = formatLastSyncDetail(state.settings.lastSyncEpochMs),
                value = "Ogni ${state.settings.syncIntervalHours} h",
                onClick = { showSyncIntervalSheet = true }
            )

            // Group 4: App & Backup
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(text = stringResource(R.string.settings_group_app))
            Spacer(modifier = Modifier.height(2.dp))

            SettingsRow(
                title = stringResource(R.string.settings_updates_notify),
                showChevron = false,
                trailing = {
                    SpSwitch(
                        checked = state.settings.autoCheckUpdates,
                        onCheckedChange = onAutoCheckUpdatesChange
                    )
                },
                onClick = { onAutoCheckUpdatesChange(!state.settings.autoCheckUpdates) }
            )

            when (val u = state.updateState) {
                is UpdateUiState.Available -> {
                    SettingsRow(
                        title = "Scarica e installa ${u.release.tagName}",
                        subtitle = u.release.changelog.lineSequence().firstOrNull()?.take(80),
                        value = "Installa",
                        valueStrong = true,
                        onClick = { onDownloadAndInstall(u.release) }
                    )
                }
                is UpdateUiState.Downloading -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Download ${u.release.tagName}…",
                                style = SpType.rowTitle.copy(fontSize = 15.sp),
                                color = c.ink
                            )
                            Text(
                                text = "${(u.progress * 100).roundToInt()}%",
                                style = SpType.captionBold,
                                color = c.ink
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { u.progress },
                            color = c.ink,
                            trackColor = c.track,
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = c.line)
                }
                is UpdateUiState.ReadyToInstall -> {
                    SettingsRow(
                        title = "Installa ${u.release.tagName}",
                        value = "Installa",
                        valueStrong = true,
                        onClick = { onInstallDownloaded(u.release, u.apkFile) }
                    )
                }
                else -> {
                    val versionSub = when (u) {
                        UpdateUiState.Checking -> "Controllo release su GitHub…"
                        is UpdateUiState.UpToDate -> "Aggiornata (v${u.version})"
                        is UpdateUiState.Error -> u.message
                        else -> "Da GitHub · ${state.settings.githubRepo.ifBlank { BuildConfig.DEFAULT_GITHUB_REPO }}"
                    }
                    SettingsRow(
                        title = "Versione ${BuildConfig.VERSION_NAME}",
                        subtitle = versionSub,
                        value = "Controlla",
                        valueStrong = true,
                        onClick = onCheckUpdates
                    )
                }
            }

            SettingsRow(
                title = stringResource(R.string.settings_backup_export),
                subtitle = "Libreria, preferenze, Anima e nascosti (escluse chiavi API)",
                onClick = { exportBackupLauncher.launch("switchpulse-backup.json") }
            )

            SettingsRow(
                title = stringResource(R.string.settings_backup_import),
                onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/plain")) }
            )

            SettingsRow(
                title = stringResource(R.string.settings_advanced),
                subtitle = state.settings.githubRepo.ifBlank { BuildConfig.DEFAULT_GITHUB_REPO },
                onClick = { showAdvancedSheet = true }
            )
        }
    }

    // Sub-sheets
    val prov = editingProvider
    if (prov != null) {
        ProviderApiKeySheet(
            provider = prov,
            savedKey = state.settings.apiKeyFor(prov),
            isTesting = state.isTestingGemini,
            diagnosticText = state.geminiTestResult ?: state.settings.geminiStatusSummary,
            onDismiss = { editingProvider = null },
            onSave = { newKey ->
                onSaveProviderApiKey(prov, newKey)
                editingProvider = null
            },
            onTest = { pending -> onTestProviderConnection(prov, pending) },
            onRemove = {
                onSaveProviderApiKey(prov, "")
                editingProvider = null
            }
        )
    }

    if (showModelsSheet) {
        TaskModelsOverviewSheet(
            settings = state.settings,
            onPickSlot = { slot ->
                showModelsSheet = false
                activeTaskPicker = slot
            },
            onDismiss = { showModelsSheet = false }
        )
    }

    val currentPickerSlot = activeTaskPicker
    if (currentPickerSlot != null) {
        val currentSelection = when (currentPickerSlot) {
            AiTaskSlot.RERANK -> state.settings.rerankTarget
            AiTaskSlot.INSIGHTS -> state.settings.insightsTarget
            AiTaskSlot.SOUL -> state.settings.soulTarget
        }
        EditorialModelPickerSheet(
            slot = currentPickerSlot,
            settings = state.settings,
            modelsByProvider = modelsByProvider,
            currentTarget = currentSelection,
            isRefreshingModels = state.isLoadingModels,
            onRefreshModels = { provider -> onRefreshModels(provider) },
            onSelectModel = { provider, modelId ->
                when (currentPickerSlot) {
                    AiTaskSlot.RERANK -> onSelectRerankTarget(provider, modelId)
                    AiTaskSlot.INSIGHTS -> onSelectInsightsTarget(provider, modelId)
                    AiTaskSlot.SOUL -> onSelectSoulTarget(provider, modelId)
                }
                activeTaskPicker = null
            },
            onDismiss = { activeTaskPicker = null }
        )
    }

    if (showBgAiSheet) {
        OptionPickerSheet(
            title = stringResource(R.string.settings_bg_ai),
            options = BackgroundRerankPolicy.entries.map { it.label },
            selectedIndex = BackgroundRerankPolicy.entries.indexOf(state.settings.backgroundRerankPolicy),
            onSelect = { idx ->
                onBackgroundRerankPolicyChange(BackgroundRerankPolicy.entries[idx])
                showBgAiSheet = false
            },
            onDismiss = { showBgAiSheet = false }
        )
    }

    if (showWidgetPlatformSheet) {
        val options = listOf(
            PlatformFilter.ALL to stringResource(R.string.ranking_platform_all),
            PlatformFilter.SWITCH_2 to stringResource(R.string.ranking_platform_sw2),
            PlatformFilter.SWITCH_1 to stringResource(R.string.ranking_platform_sw1)
        )
        OptionPickerSheet(
            title = stringResource(R.string.settings_widget_platform),
            options = options.map { it.second },
            selectedIndex = options.indexOfFirst { it.first == state.settings.widgetPlatformFilter }.coerceAtLeast(0),
            onSelect = { idx ->
                onWidgetPlatformFilterChange(options[idx].first)
                showWidgetPlatformSheet = false
            },
            onDismiss = { showWidgetPlatformSheet = false }
        )
    }

    if (showSyncIntervalSheet) {
        val hoursOptions = listOf(6, 12, 24)
        OptionPickerSheet(
            title = stringResource(R.string.settings_sync_auto),
            options = hoursOptions.map { "Ogni $it h" },
            selectedIndex = hoursOptions.indexOf(state.settings.syncIntervalHours).coerceAtLeast(1),
            onSelect = { idx ->
                onSyncIntervalChange(hoursOptions[idx])
                showSyncIntervalSheet = false
            },
            onDismiss = { showSyncIntervalSheet = false }
        )
    }

    if (showWidgetPinSheet) {
        WidgetPinPickerSheet(
            onPin = { widgetCls ->
                val feedback = requestPinWidget(context, widgetCls)
                onFeedback(feedback)
                showWidgetPinSheet = false
            },
            onDismiss = { showWidgetPinSheet = false }
        )
    }

    if (showAdvancedSheet) {
        AdvancedRepoSheet(
            currentRepo = state.settings.githubRepo,
            onSave = { repo ->
                onSaveGithubRepo(repo)
                showAdvancedSheet = false
            },
            onDismiss = { showAdvancedSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderApiKeySheet(
    provider: AiProvider,
    savedKey: String,
    isTesting: Boolean,
    diagnosticText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onTest: (String) -> Unit,
    onRemove: () -> Unit
) {
    val c = SpTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen: (() -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    var keyInput by remember(provider, savedKey) { mutableStateOf(savedKey) }
    var showKey by remember { mutableStateOf(false) }

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
            val hint = when (provider) {
                AiProvider.GEMINI -> "Google AI Studio · piano gratuito disponibile"
                AiProvider.OPENROUTER -> "Aggregator API con modelli gratuiti (:free) e a pagamento"
                AiProvider.DEEPSEEK -> "API DeepSeek V3 / Reasoner a basso costo"
            }
            Text(text = provider.displayName, style = SpType.sheetTitle, color = c.ink)
            Text(text = hint, style = SpType.caption, color = c.muted)

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                placeholder = { Text(provider.keyPlaceholder, style = SpType.body, color = c.muted) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = c.muted
                        )
                    }
                },
                shape = RoundedCornerShape(4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = c.ink,
                    unfocusedBorderColor = c.border,
                    focusedTextColor = c.ink,
                    unfocusedTextColor = c.ink
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (diagnosticText.isNotBlank()) {
                Text(
                    text = diagnosticText,
                    style = SpType.caption,
                    color = if (diagnosticText.startsWith("⚠️")) c.urgent else c.ink2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bannerBg)
                        .padding(10.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecondaryButton(
                    text = "Prova",
                    onClick = { onTest(keyInput) },
                    enabled = !isTesting && (keyInput.isNotBlank() || savedKey.isNotBlank()),
                    height = 48.dp
                )
                if (savedKey.isNotBlank()) {
                    SecondaryButton(
                        text = stringResource(R.string.action_remove),
                        onClick = { hideThen(onRemove) },
                        height = 48.dp
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { hideThen { onSave(keyInput) } },
                    modifier = Modifier.weight(1f),
                    height = 48.dp
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskModelsOverviewSheet(
    settings: FilterSettings,
    onPickSlot: (AiTaskSlot) -> Unit,
    onDismiss: () -> Unit
) {
    val c = SpTheme.colors
    val scope = rememberCoroutineScope()
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
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_models_row),
                style = SpType.sheetTitle,
                color = c.ink
            )
            Spacer(modifier = Modifier.height(10.dp))
            AiTaskSlot.entries.forEach { slot ->
                val target = when (slot) {
                    AiTaskSlot.RERANK -> settings.rerankTarget
                    AiTaskSlot.INSIGHTS -> settings.insightsTarget
                    AiTaskSlot.SOUL -> settings.soulTarget
                }
                SettingsRow(
                    title = slot.title,
                    subtitle = slot.subtitle,
                    value = "${target.provider.shortLabel} · ${target.modelId.substringAfterLast('/').take(20)}",
                    onClick = { hideThen { onPickSlot(slot) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorialModelPickerSheet(
    slot: AiTaskSlot,
    settings: FilterSettings,
    modelsByProvider: Map<AiProvider, List<GeminiModelInfo>>,
    currentTarget: AiTaskTarget,
    isRefreshingModels: Boolean,
    onRefreshModels: (AiProvider) -> Unit,
    onSelectModel: (AiProvider, String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = SpTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen: (() -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    var activeProvider by remember(currentTarget.provider) { mutableStateOf(currentTarget.provider) }
    var filterText by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }

    val providerModels = modelsByProvider[activeProvider].orEmpty()
    val hasProviderKey = settings.hasKeyFor(activeProvider)
    val filteredModels = remember(providerModels, filterText, freeOnly, activeProvider) {
        val q = filterText.trim().lowercase()
        providerModels.filter { model ->
            val matchesFree = !freeOnly || activeProvider != AiProvider.OPENROUTER || model.isFree
            val matchesQuery = q.isEmpty() ||
                model.id.lowercase().contains(q) ||
                model.displayName.lowercase().contains(q)
            matchesFree && matchesQuery
        }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = slot.title, style = SpType.sheetTitle, color = c.ink)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(enabled = !isRefreshingModels) { onRefreshModels(activeProvider) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRefreshingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = c.ink)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = c.ink)
                    }
                }
            }

            SegmentedChoice(
                items = AiProvider.entries.map { p ->
                    if (settings.hasKeyFor(p)) "${p.shortLabel} ✓" else p.shortLabel
                },
                selectedIndex = AiProvider.entries.indexOf(activeProvider),
                onSelect = { idx ->
                    activeProvider = AiProvider.entries[idx]
                    freeOnly = false
                }
            )

            if (!hasProviderKey) {
                Text(
                    text = "Chiave ${activeProvider.shortLabel} non collegata.",
                    style = SpType.caption,
                    color = c.urgent
                )
            }

            if (activeProvider == AiProvider.OPENROUTER) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (freeOnly) c.ink else Color.Transparent)
                        .border(BorderStroke(1.dp, if (freeOnly) c.ink else c.border), RoundedCornerShape(17.dp))
                        .clickable { freeOnly = !freeOnly }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (freeOnly) "✓ Solo gratis (\$0)" else "Filtra solo gratis (\$0)",
                        style = SpType.captionBold,
                        color = if (freeOnly) c.paper else c.ink
                    )
                }
            }

            if (providerModels.size > 4) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("Filtra modello…", style = SpType.body, color = c.muted) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = c.muted) },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(filteredModels, key = { "${it.provider.id}:${it.id}" }) { model ->
                    val isSelected = model.provider == currentTarget.provider && model.id == currentTarget.modelId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hideThen { onSelectModel(model.provider, model.id) } }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = model.displayName,
                                style = SpType.rowTitle.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) c.accentText else c.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (model.isFree) "${model.id} · gratis" else model.id,
                                style = SpType.caption,
                                color = c.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = c.line)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionPickerSheet(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val c = SpTheme.colors
    val scope = rememberCoroutineScope()
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
                .padding(bottom = 24.dp)
        ) {
            Text(text = title, style = SpType.sheetTitle, color = c.ink)
            Spacer(modifier = Modifier.height(10.dp))
            options.forEachIndexed { idx, label ->
                SettingsRow(
                    title = label,
                    showChevron = false,
                    trailing = {
                        if (idx == selectedIndex) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = { hideThen { onSelect(idx) } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetPinPickerSheet(
    onPin: (Class<*>) -> Unit,
    onDismiss: () -> Unit
) {
    val c = SpTheme.colors
    val scope = rememberCoroutineScope()
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
                .padding(bottom = 24.dp)
        ) {
            Text(text = stringResource(R.string.settings_widget_pin), style = SpType.sheetTitle, color = c.ink)
            Spacer(modifier = Modifier.height(10.dp))
            SettingsRow(
                title = stringResource(R.string.widget_w1_label),
                subtitle = "4×4 · Copertina n. 1 protagonista + offerte 2–4",
                onClick = { hideThen { onPin(TopCoverWidgetReceiver::class.java) } }
            )
            SettingsRow(
                title = stringResource(R.string.widget_w12_label),
                subtitle = "4×2 · Carosello interattivo con tutta la Top 10",
                onClick = { hideThen { onPin(CarouselWidgetReceiver::class.java) } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedRepoSheet(
    currentRepo: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = SpTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hideThen: (() -> Unit) -> Unit = { then ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { then() }
    }
    var repoInput by remember(currentRepo) { mutableStateOf(currentRepo) }
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
            Text(text = stringResource(R.string.settings_advanced), style = SpType.sheetTitle, color = c.ink)
            OutlinedTextField(
                value = repoInput,
                onValueChange = { repoInput = it },
                label = { Text("Repository GitHub (owner/repo)") },
                singleLine = true,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            )
            PrimaryButton(
                text = stringResource(R.string.action_save),
                onClick = { hideThen { onSave(repoInput.trim()) } },
                height = 48.dp
            )
        }
    }
}

private fun requestPinWidget(context: Context, receiverClass: Class<*>): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return "Tieni premuto sulla home del telefono e aggiungi il widget SwitchPulse."
    }
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) {
        return "Il tuo launcher non supporta l'aggiunta rapida: aggiungilo dal menu Widget della home."
    }
    val provider = ComponentName(context, receiverClass)
    val requested = manager.requestPinAppWidget(provider, null, null)
    return if (requested) {
        "Conferma l'aggiunta del widget dal popup del launcher."
    } else {
        "Aggiungi il widget SwitchPulse dal menu Widget della home."
    }
}

private fun formatLastSyncDetail(epochMs: Long): String {
    if (epochMs <= 0L) return "Ultimo aggiornamento mai eseguito"
    val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.ITALY)
    return "Ultimo aggiornamento ${fmt.format(Date(epochMs))} · Nintendo eShop Italia"
}
