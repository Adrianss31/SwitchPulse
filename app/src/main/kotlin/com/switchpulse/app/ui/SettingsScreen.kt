package com.switchpulse.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.switchpulse.app.BuildConfig
import com.switchpulse.app.data.model.AiProvider
import com.switchpulse.app.data.model.AiTaskTarget
import com.switchpulse.app.data.model.BackgroundRerankPolicy
import com.switchpulse.app.data.model.GeminiModelInfo
import com.switchpulse.app.data.remote.GeminiModels
import com.switchpulse.app.ui.theme.EshopCrimson
import com.switchpulse.app.update.ReleaseInfo
import com.switchpulse.app.widget.EditorialBentoWidgetReceiver
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
        title = "Classifica offerte (Rerank AI)",
        subtitle = "Analisi brevi top 40 offerte",
        recommendedId = GeminiModels.FLASH_LITE
    ),
    INSIGHTS(
        title = "Schede dettaglio gioco",
        subtitle = "Metacritic, HLTB & verdetto singolo",
        recommendedId = GeminiModels.FLASH_LITE
    ),
    SOUL(
        title = "Anima & Consigli su misura",
        subtitle = "Profilo giocatore e 5–10 titoli consigliati",
        recommendedId = GeminiModels.HYDROGEN
    )
}

@Composable
fun SettingsScreen(
    state: MainUiState,
    onMinDiscountChange: (Int) -> Unit,
    onMinRegularPriceChange: (Float) -> Unit,
    onSyncIntervalChange: (Int) -> Unit,
    onUnhideDeal: (String) -> Unit,
    onSaveGithubRepo: (String) -> Unit,
    onAutoCheckUpdatesChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadAndInstall: (ReleaseInfo) -> Unit,
    onInstallDownloaded: (ReleaseInfo, File) -> Unit,
    onWidgetPinFeedback: (String) -> Unit,
    onRecommendationWeightChange: (Int) -> Unit,
    onSaveProviderApiKey: (AiProvider, String) -> Unit,
    onSelectRerankTarget: (AiProvider, String) -> Unit,
    onSelectInsightsTarget: (AiProvider, String) -> Unit,
    onSelectSoulTarget: (AiProvider, String) -> Unit,
    onRefreshModels: (AiProvider?) -> Unit,
    onTestProviderConnection: (AiProvider, String) -> Unit,
    onRerankWithGeminiNow: () -> Unit,
    onSaveUserTastePrompt: (String) -> Unit = {},
    onBackgroundRerankPolicyChange: (BackgroundRerankPolicy) -> Unit = {},
    onExportBackupJson: () -> String = { "" },
    onImportBackupJson: (String) -> Unit = {},
    onBackupFeedback: (String) -> Unit = {},
    onOpenLibraryTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            onBackupFeedback("Backup JSON esportato correttamente (chiavi API escluse)")
        }.onFailure { err ->
            onBackupFeedback("Errore esportazione backup: ${err.message ?: "sconosciuto"}")
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
        }.onSuccess { rawJson ->
            onImportBackupJson(rawJson)
        }.onFailure { err ->
            onBackupFeedback("Errore lettura backup: ${err.message ?: "sconosciuto"}")
        }
    }
    var repoInput by remember(state.settings.githubRepo) {
        mutableStateOf(state.settings.githubRepo)
    }
    var selectedKeyProvider by remember {
        mutableStateOf(
            state.settings.configuredProviders().firstOrNull() ?: AiProvider.GEMINI
        )
    }
    val savedKeyForSelectedProvider = state.settings.apiKeyFor(selectedKeyProvider)
    var providerKeyInput by remember(selectedKeyProvider, savedKeyForSelectedProvider) {
        mutableStateOf(savedKeyForSelectedProvider)
    }
    var tastePromptInput by remember(state.settings.userTastePrompt) {
        mutableStateOf(state.settings.userTastePrompt)
    }
    var showApiKey by remember { mutableStateOf(false) }
    var activeTaskPicker by remember { mutableStateOf<AiTaskSlot?>(null) }

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

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Pin Widget card
        item {
            SettingsCard(title = "Widget sulla Home") {
                Text(
                    text = "Il widget Editorial Bento si adatta automaticamente al tema chiaro o scuro del telefono e mostra la #1 in evidenza seguita dalla Top 10 filtrata.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        val msg = requestPinEditorialWidget(context)
                        onWidgetPinFeedback(msg)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Rounded.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aggiungi widget alla home")
                }
            }
        }

        // 2. Anti-noise filters
        item {
            SettingsCard(title = "Filtri anti-rumore (condivisi con il Widget)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sconto minimo", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${state.settings.minDiscountPercent}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = state.settings.minDiscountPercent.toFloat(),
                    onValueChange = { onMinDiscountChange((it / 5f).roundToInt() * 5) },
                    valueRange = 0f..75f,
                    colors = SliderDefaults.colors(thumbColor = EshopCrimson, activeTrackColor = EshopCrimson)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Prezzo di listino minimo", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${state.settings.minRegularPriceEur.roundToInt()} €",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = state.settings.minRegularPriceEur,
                    onValueChange = { onMinRegularPriceChange(((it / 5f).roundToInt() * 5).toFloat()) },
                    valueRange = 0f..30f,
                    colors = SliderDefaults.colors(thumbColor = EshopCrimson, activeTrackColor = EshopCrimson)
                )
            }
        }

        // 2b. Taste engine weight & Gemini AI configuration
        item {
            val ratedCount = state.settings.likedGames.count { it.isPositiveRating || it.isNegativeRating }
            val ownedCount = state.settings.likedGames.count { it.isOwned }

            SettingsCard(title = "Motore AI & Raccomandazioni") {
                Text(
                    text = "I tuoi voti 1–5★ e i giochi posseduti si gestiscono nella tab Libreria ($ratedCount valutati · $ownedCount posseduti).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    onClick = onOpenLibraryTab,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text("Vai alla Libreria →", color = EshopCrimson)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Influenza dei voti sulla classifica", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${state.settings.recommendationWeight}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Slider(
                    value = state.settings.recommendationWeight.toFloat(),
                    onValueChange = { onRecommendationWeightChange((it / 10f).roundToInt() * 10) },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(thumbColor = EshopCrimson, activeTrackColor = EshopCrimson)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Modelli AI per task (BYOK)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FilledTonalButton(
                        onClick = { onRefreshModels(null) },
                        enabled = !state.isLoadingModels,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        if (state.isLoadingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Aggiorna modelli",
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Aggiorna",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                val totalDynamicCount = AiProvider.entries.sumOf { state.settings.availableModelsFor(it).size }
                Text(
                    text = if (totalDynamicCount > 0) {
                        "$totalDynamicCount modelli live caricati (Gemini, OpenRouter, DeepSeek): scegli provider e modello ideale per ciascun task."
                    } else {
                        "Supporta Google Gemini, OpenRouter e DeepSeek. Scegli provider e modello per ogni task e tocca \"Aggiorna\" per caricare i cataloghi live."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskModelSelectorRow(
                        slot = AiTaskSlot.RERANK,
                        target = state.settings.rerankTarget,
                        hasProviderKey = state.settings.hasKeyFor(state.settings.rerankTarget.provider),
                        models = modelsByProvider[state.settings.rerankTarget.provider].orEmpty(),
                        onClick = { activeTaskPicker = AiTaskSlot.RERANK }
                    )
                    TaskModelSelectorRow(
                        slot = AiTaskSlot.INSIGHTS,
                        target = state.settings.insightsTarget,
                        hasProviderKey = state.settings.hasKeyFor(state.settings.insightsTarget.provider),
                        models = modelsByProvider[state.settings.insightsTarget.provider].orEmpty(),
                        onClick = { activeTaskPicker = AiTaskSlot.INSIGHTS }
                    )
                    TaskModelSelectorRow(
                        slot = AiTaskSlot.SOUL,
                        target = state.settings.soulTarget,
                        hasProviderKey = state.settings.hasKeyFor(state.settings.soulTarget.provider),
                        models = modelsByProvider[state.settings.soulTarget.provider].orEmpty(),
                        onClick = { activeTaskPicker = AiTaskSlot.SOUL }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Chiavi API Provider",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        val isTabSelected = selectedKeyProvider == provider
                        val isConfigured = state.settings.hasKeyFor(provider)
                        Surface(
                            onClick = { selectedKeyProvider = provider },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isTabSelected) {
                                EshopCrimson
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isTabSelected) EshopCrimson else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isConfigured) "✓ ${provider.shortLabel}" else provider.shortLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isTabSelected || isConfigured) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isTabSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = providerKeyInput,
                    onValueChange = { providerKeyInput = it },
                    label = { Text("${selectedKeyProvider.displayName} API Key (${selectedKeyProvider.keyPlaceholder})") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showApiKey) "Nascondi" else "Mostra"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onSaveProviderApiKey(selectedKeyProvider, providerKeyInput) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Salva chiave")
                    }
                    FilledTonalButton(
                        onClick = { onTestProviderConnection(selectedKeyProvider, providerKeyInput) },
                        enabled = (providerKeyInput.isNotBlank() || state.settings.hasKeyFor(selectedKeyProvider)) && !state.isTestingGemini,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isTestingGemini) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Testa chiave")
                        }
                    }
                }

                if (state.settings.hasKeyFor(selectedKeyProvider) || state.settings.hasKeyFor(state.settings.rerankTarget.provider)) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.settings.hasKeyFor(state.settings.rerankTarget.provider)) {
                            Button(
                                onClick = onRerankWithGeminiNow,
                                enabled = !state.isRefreshing,
                                colors = ButtonDefaults.buttonColors(containerColor = EshopCrimson),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Ricalcola AI ora")
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        if (state.settings.hasKeyFor(selectedKeyProvider)) {
                            TextButton(
                                onClick = {
                                    providerKeyInput = ""
                                    onSaveProviderApiKey(selectedKeyProvider, "")
                                }
                            ) {
                                Text("Rimuovi ${selectedKeyProvider.shortLabel}", color = EshopCrimson)
                            }
                        }
                    }
                }

                val diagnosticText = state.geminiTestResult?.takeIf { it.isNotBlank() }
                    ?: state.settings.geminiStatusSummary.takeIf { it.isNotBlank() }
                if (diagnosticText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = diagnosticText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Preferenze personali per l'IA (opzionale)",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Descrivi cosa cerchi o vuoi evitare in generale: Gemini lo combinerà coi tuoi voti e commenti in Libreria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tastePromptInput,
                    onValueChange = { if (it.length <= 600) tastePromptInput = it },
                    label = { Text("Preferenze extra (es. generi, ritmo, portabilità)") },
                    placeholder = { Text("Es. Amo roguelike tattici e metroidvania impegnativi; evito visual novel lunghe e farming simulator.") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onSaveUserTastePrompt(tastePromptInput) },
                        enabled = tastePromptInput.trim() != state.settings.userTastePrompt.trim(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Salva preferenze")
                    }
                    if (tastePromptInput.isNotBlank() || state.settings.userTastePrompt.isNotBlank()) {
                        TextButton(
                            onClick = {
                                tastePromptInput = ""
                                onSaveUserTastePrompt("")
                            }
                        ) {
                            Text("Svuota")
                        }
                    }
                }
            }
        }

        // 3. Background sync frequency
        item {
            SettingsCard(title = "Sincronizzazione in background") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(6, 12, 24).forEach { hours ->
                        val selected = state.settings.syncIntervalHours == hours
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) EshopCrimson
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable { onSyncIntervalChange(hours) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Ogni ${hours}h",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatLastSyncDetail(state.settings.lastSyncEpochMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "Analisi AI in background",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Controlla quando i sync automatici possono interrogare il provider AI per valutare nuovi sconti.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        BackgroundRerankPolicy.WIFI_ONLY to "Solo Wi-Fi",
                        BackgroundRerankPolicy.ALWAYS to "Sempre",
                        BackgroundRerankPolicy.OFF to "Mai"
                    ).forEach { (policy, label) ->
                        val selected = state.settings.backgroundRerankPolicy == policy
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (selected) EshopCrimson
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable { onBackgroundRerankPolicyChange(policy) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // 4. GitHub Auto-Update
        item {
            SettingsCard(title = "Auto-Update da GitHub") {
                Text(
                    text = "Versione installata: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = repoInput,
                    onValueChange = { repoInput = it },
                    label = { Text("Repository GitHub (owner/repo)") },
                    placeholder = { Text("es. adriano/SwitchPulse") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Controlla automaticamente nuove release",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Switch(
                        checked = state.settings.autoCheckUpdates,
                        onCheckedChange = onAutoCheckUpdatesChange
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onSaveGithubRepo(repoInput) },
                        enabled = repoInput.trim() != state.settings.githubRepo
                    ) {
                        Text("Salva repo")
                    }

                    Button(
                        onClick = {
                            if (repoInput.trim() != state.settings.githubRepo) {
                                onSaveGithubRepo(repoInput)
                            }
                            onCheckUpdates()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EshopCrimson),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Controlla")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (val u = state.updateState) {
                    UpdateUiState.Idle -> Unit
                    UpdateUiState.Checking -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Controllo release su GitHub...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    UpdateUiState.NotConfigured -> {
                        Text(
                            text = "Inserisci owner/repo del tuo repository pubblico GitHub per abilitare l'aggiornamento automatico.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is UpdateUiState.UpToDate -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Hai già l'ultima versione (v${u.version}).",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is UpdateUiState.Available -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Disponibile ${u.release.tagName}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (u.release.changelog.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = u.release.changelog.take(240),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { onDownloadAndInstall(u.release) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EshopCrimson),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Scarica e installa (${u.release.apkFileName})")
                                }
                            }
                        }
                    }
                    is UpdateUiState.Downloading -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Download ${u.release.tagName}: ${(u.progress * 100).roundToInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                            LinearProgressIndicator(
                                progress = { u.progress },
                                modifier = Modifier.fillMaxWidth(),
                                color = EshopCrimson
                            )
                        }
                    }
                    is UpdateUiState.ReadyToInstall -> {
                        Button(
                            onClick = { onInstallDownloaded(u.release, u.apkFile) },
                            colors = ButtonDefaults.buttonColors(containerColor = EshopCrimson),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Installa ${u.release.tagName}")
                        }
                    }
                    is UpdateUiState.Error -> {
                        Text(
                            text = u.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = EshopCrimson
                        )
                    }
                }
            }
        }

        // 4b. JSON Backup & Restore
        item {
            SettingsCard(title = "Backup & Ripristino (JSON)") {
                Text(
                    text = "Esporta o ripristina libreria, voti, preferenze, titoli nascosti e Anima del Giocatore. Per sicurezza le chiavi API non vengono mai incluse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { exportBackupLauncher.launch("switchpulse-backup.json") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Esporta JSON")
                    }
                    Button(
                        onClick = { importBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                        colors = ButtonDefaults.buttonColors(containerColor = EshopCrimson),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Importa JSON")
                    }
                }
            }
        }

        // 5. Blacklist management
        item {
            SettingsCard(title = "Titoli nascosti (${state.settings.blacklistIds.size})") {
                if (state.settings.blacklistIds.isEmpty()) {
                    Text(
                        text = "Nessun gioco nascosto. Premi × su qualsiasi riga nella classifica per escluderlo per sempre.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val entries = state.settings.blacklistIds.toList()
                    entries.forEachIndexed { idx, id ->
                        val title = state.settings.blacklistTitles[id] ?: "Titolo #$id"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            TextButton(onClick = { onUnhideDeal(id) }) {
                                Text("Ripristina", color = EshopCrimson)
                            }
                        }
                        if (idx < entries.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }

    val currentPickerSlot = activeTaskPicker
    if (currentPickerSlot != null) {
        val currentSelection = when (currentPickerSlot) {
            AiTaskSlot.RERANK -> state.settings.rerankTarget
            AiTaskSlot.INSIGHTS -> state.settings.insightsTarget
            AiTaskSlot.SOUL -> state.settings.soulTarget
        }
        ModelPickerBottomSheet(
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
}

@Composable
private fun TaskModelSelectorRow(
    slot: AiTaskSlot,
    target: AiTaskTarget,
    hasProviderKey: Boolean,
    models: List<GeminiModelInfo>,
    onClick: () -> Unit
) {
    val display = models.firstOrNull { it.id == target.modelId }?.displayName ?: target.modelId
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = slot.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = slot.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EshopCrimson.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = target.provider.shortLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = EshopCrimson,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = display,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!hasProviderKey) {
                        Text(
                            text = "· chiave mancante",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "Cambia modello",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerBottomSheet(
    slot: AiTaskSlot,
    settings: com.switchpulse.app.data.model.FilterSettings,
    modelsByProvider: Map<AiProvider, List<GeminiModelInfo>>,
    currentTarget: AiTaskTarget,
    isRefreshingModels: Boolean,
    onRefreshModels: (AiProvider) -> Unit,
    onSelectModel: (AiProvider, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeProvider by remember(currentTarget.provider) { mutableStateOf(currentTarget.provider) }
    var filterText by remember { mutableStateOf("") }
    var freeOnly by remember { mutableStateOf(false) }

    val providerModels = modelsByProvider[activeProvider].orEmpty()
    val isLive = settings.availableModelsFor(activeProvider).isNotEmpty()
    val hasProviderKey = settings.hasKeyFor(activeProvider)

    val filteredModels = remember(providerModels, filterText, freeOnly, activeProvider) {
        val q = filterText.trim().lowercase()
        providerModels.filter { model ->
            val matchesFree = !freeOnly || activeProvider != AiProvider.OPENROUTER || model.isFree
            val matchesQuery = q.isEmpty() ||
                model.id.lowercase().contains(q) ||
                model.displayName.lowercase().contains(q) ||
                model.description.lowercase().contains(q)
            matchesFree && matchesQuery
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slot.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isLive) {
                            "${providerModels.size} modelli live da ${activeProvider.displayName}"
                        } else {
                            "Elenco base ${activeProvider.displayName} (tocca ↻ per aggiornare)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onRefreshModels(activeProvider) },
                    enabled = !isRefreshingModels && (activeProvider == AiProvider.OPENROUTER || hasProviderKey)
                ) {
                    if (isRefreshingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna catalogo")
                    }
                }
            }

            // Provider tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AiProvider.entries.forEach { provider ->
                    val selectedTab = provider == activeProvider
                    val hasKey = settings.hasKeyFor(provider)
                    Surface(
                        onClick = {
                            activeProvider = provider
                            freeOnly = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedTab) EshopCrimson else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(
                            1.dp,
                            if (selectedTab) EshopCrimson else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasKey) "✓ ${provider.shortLabel}" else provider.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab) Color.White else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (!hasProviderKey) {
                Text(
                    text = "Chiave ${activeProvider.displayName} non ancora configurata: ricordati di inserirla nella sezione Chiavi API qui sotto.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (activeProvider == AiProvider.OPENROUTER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { freeOnly = !freeOnly },
                        shape = RoundedCornerShape(999.dp),
                        color = if (freeOnly) {
                            EshopCrimson.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            if (freeOnly) EshopCrimson else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Text(
                            text = if (freeOnly) "✓ Solo modelli Gratis (\$0)" else "Filtra solo Gratis (\$0)",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (freeOnly) EshopCrimson else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (providerModels.size > 4) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("Filtra modello (es. free, llama, deepseek, flash...)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 390.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredModels, key = { "${it.provider.id}:${it.id}" }) { model ->
                    val isSelected = model.provider == currentTarget.provider && model.id == currentTarget.modelId
                    val isRecommended = model.provider == AiProvider.GEMINI && model.id == slot.recommendedId
                    Surface(
                        onClick = { onSelectModel(model.provider, model.id) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) {
                            EshopCrimson.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) EshopCrimson else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) EshopCrimson else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (model.isFree) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer
                                        ) {
                                            Text(
                                                text = "GRATIS",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (isRecommended) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "Consigliato",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = model.id,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (model.description.isNotBlank()) {
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

private fun requestPinEditorialWidget(context: Context): String {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return "Tieni premuto sulla home del telefono e aggiungi il widget SwitchPulse."
    }
    val manager = AppWidgetManager.getInstance(context)
    if (!manager.isRequestPinAppWidgetSupported) {
        return "Il tuo launcher non supporta l'aggiunta rapida: aggiungilo dal menu Widget della home."
    }
    val provider = ComponentName(context, EditorialBentoWidgetReceiver::class.java)
    val requested = manager.requestPinAppWidget(provider, null, null)
    return if (requested) {
        "Conferma l'aggiunta del widget dal popup del launcher."
    } else {
        "Aggiungi il widget SwitchPulse dal menu Widget della home."
    }
}

private fun formatLastSyncDetail(epochMs: Long): String {
    if (epochMs <= 0L) return "Ultimo aggiornamento: mai eseguito"
    val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.ITALY)
    return "Ultimo aggiornamento: ${fmt.format(Date(epochMs))} · Nintendo eShop Italia"
}
