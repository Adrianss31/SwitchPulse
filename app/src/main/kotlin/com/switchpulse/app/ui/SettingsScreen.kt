package com.switchpulse.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
    onSaveGeminiApiKey: (String) -> Unit,
    onSelectRerankModel: (String) -> Unit,
    onSelectInsightsModel: (String) -> Unit,
    onSelectSoulModel: (String) -> Unit,
    onRefreshGeminiModels: () -> Unit,
    onTestGeminiConnection: (String) -> Unit,
    onRerankWithGeminiNow: () -> Unit,
    onSaveUserTastePrompt: (String) -> Unit = {},
    onOpenLibraryTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var repoInput by remember(state.settings.githubRepo) {
        mutableStateOf(state.settings.githubRepo)
    }
    var geminiKeyInput by remember(state.settings.geminiApiKey) {
        mutableStateOf(state.settings.geminiApiKey)
    }
    var tastePromptInput by remember(state.settings.userTastePrompt) {
        mutableStateOf(state.settings.userTastePrompt)
    }
    var showGeminiKey by remember { mutableStateOf(false) }
    var activeTaskPicker by remember { mutableStateOf<AiTaskSlot?>(null) }
    val resolvedModels = remember(
        state.settings.availableGeminiModels,
        state.settings.geminiRerankModel,
        state.settings.geminiInsightsModel,
        state.settings.geminiSoulModel
    ) {
        GeminiModels.resolveModelsList(
            dynamicModels = state.settings.availableGeminiModels,
            selectedIds = listOf(
                state.settings.geminiRerankModel,
                state.settings.geminiInsightsModel,
                state.settings.geminiSoulModel
            )
        )
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
                        text = "Modelli Gemini per task (BYOK)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    FilledTonalButton(
                        onClick = onRefreshGeminiModels,
                        enabled = state.settings.geminiApiKey.isNotBlank() && !state.isLoadingModels,
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
                Text(
                    text = if (state.settings.availableGeminiModels.isNotEmpty()) {
                        "Lista live da Google (${state.settings.availableGeminiModels.size} modelli compatibili): assegna un modello leggero alle analisi brevi e uno potente a Anima & Consigli."
                    } else {
                        "Assegna modelli diversi per task. Inserisci la chiave e tocca \"Aggiorna\" per scaricare dinamicamente nuovi modelli da Google senza aggiornare l'app."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskModelSelectorRow(
                        slot = AiTaskSlot.RERANK,
                        selectedId = state.settings.geminiRerankModel,
                        models = resolvedModels,
                        onClick = { activeTaskPicker = AiTaskSlot.RERANK }
                    )
                    TaskModelSelectorRow(
                        slot = AiTaskSlot.INSIGHTS,
                        selectedId = state.settings.geminiInsightsModel,
                        models = resolvedModels,
                        onClick = { activeTaskPicker = AiTaskSlot.INSIGHTS }
                    )
                    TaskModelSelectorRow(
                        slot = AiTaskSlot.SOUL,
                        selectedId = state.settings.geminiSoulModel,
                        models = resolvedModels,
                        onClick = { activeTaskPicker = AiTaskSlot.SOUL }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = geminiKeyInput,
                    onValueChange = { geminiKeyInput = it },
                    label = { Text("Gemini API Key (AIza...)") },
                    singleLine = true,
                    visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                            Icon(
                                imageVector = if (showGeminiKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showGeminiKey) "Nascondi" else "Mostra"
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
                        onClick = { onSaveGeminiApiKey(geminiKeyInput) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Salva chiave")
                    }
                    FilledTonalButton(
                        onClick = { onTestGeminiConnection(geminiKeyInput) },
                        enabled = (geminiKeyInput.isNotBlank() || state.settings.geminiApiKey.isNotBlank()) && !state.isTestingGemini,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (state.isTestingGemini) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Testa chiave")
                        }
                    }
                }

                if (state.settings.geminiApiKey.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onRerankWithGeminiNow,
                            enabled = !state.isRefreshing,
                            colors = ButtonDefaults.buttonColors(containerColor = EshopCrimson),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ricalcola AI ora")
                        }
                        TextButton(
                            onClick = {
                                geminiKeyInput = ""
                                onSaveGeminiApiKey("")
                            }
                        ) {
                            Text("Rimuovi", color = EshopCrimson)
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
            AiTaskSlot.RERANK -> state.settings.geminiRerankModel
            AiTaskSlot.INSIGHTS -> state.settings.geminiInsightsModel
            AiTaskSlot.SOUL -> state.settings.geminiSoulModel
        }
        ModelPickerBottomSheet(
            slot = currentPickerSlot,
            models = resolvedModels,
            selectedModelId = currentSelection,
            hasLiveModels = state.settings.availableGeminiModels.isNotEmpty(),
            isRefreshingModels = state.isLoadingModels,
            canRefreshModels = state.settings.geminiApiKey.isNotBlank(),
            onRefreshModels = onRefreshGeminiModels,
            onSelectModel = { modelId ->
                when (currentPickerSlot) {
                    AiTaskSlot.RERANK -> onSelectRerankModel(modelId)
                    AiTaskSlot.INSIGHTS -> onSelectInsightsModel(modelId)
                    AiTaskSlot.SOUL -> onSelectSoulModel(modelId)
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
    selectedId: String,
    models: List<GeminiModelInfo>,
    onClick: () -> Unit
) {
    val display = models.firstOrNull { it.id == selectedId }?.displayName ?: selectedId
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (display != selectedId) "$display · $selectedId" else selectedId,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = EshopCrimson,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
    models: List<GeminiModelInfo>,
    selectedModelId: String,
    hasLiveModels: Boolean,
    isRefreshingModels: Boolean,
    canRefreshModels: Boolean,
    onRefreshModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterText by remember { mutableStateOf("") }
    val filteredModels = remember(models, filterText) {
        val q = filterText.trim().lowercase()
        if (q.isEmpty()) models
        else models.filter {
            it.id.lowercase().contains(q) ||
                it.displayName.lowercase().contains(q) ||
                it.description.lowercase().contains(q)
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
                        text = if (hasLiveModels) {
                            "${models.size} modelli caricati da Google"
                        } else {
                            "Elenco base (aggiorna con la tua API key per nuovi modelli)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canRefreshModels) {
                    IconButton(onClick = onRefreshModels, enabled = !isRefreshingModels) {
                        if (isRefreshingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Aggiorna da Google")
                        }
                    }
                }
            }

            if (models.size > 5) {
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    placeholder = { Text("Filtra modello (es. flash, lite, hydrogen, 2.5...)") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredModels, key = { it.id }) { model ->
                    val isSelected = model.id == selectedModelId
                    val isRecommended = model.id == slot.recommendedId
                    Surface(
                        onClick = { onSelectModel(model.id) },
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
