package com.switchpulse.app.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.switchpulse.app.data.model.LikedGame
import com.switchpulse.app.ui.theme.EshopCrimson
import com.switchpulse.app.update.ReleaseInfo
import com.switchpulse.app.widget.EditorialBentoWidgetReceiver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
    onSearchProfileGames: (String) -> Unit,
    onAddLikedGame: (LikedGame) -> Unit,
    onRemoveLikedGame: (String) -> Unit,
    onSaveGeminiApiKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var repoInput by remember(state.settings.githubRepo) {
        mutableStateOf(state.settings.githubRepo)
    }
    var tasteSearchInput by remember { mutableStateOf("") }
    var geminiKeyInput by remember(state.settings.geminiApiKey) {
        mutableStateOf(state.settings.geminiApiKey)
    }
    var showGeminiKey by remember { mutableStateOf(false) }

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

        // 2b. Taste profile & recommendation engine
        item {
            SettingsCard(title = "Raccomandazioni & I tuoi gusti (${state.settings.likedGames.size})") {
                Text(
                    text = "Tocca il cuore sui giochi in classifica oppure cerca qui i tuoi titoli Switch preferiti per personalizzare il ranking tramite tag Steam, generi e sviluppatori.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Influenza sui consigli", style = MaterialTheme.typography.bodyMedium)
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

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = tasteSearchInput,
                        onValueChange = { tasteSearchInput = it },
                        label = { Text("Aggiungi un gioco preferito (es. Hades)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = { onSearchProfileGames(tasteSearchInput) },
                        enabled = !state.isSearchingProfile && tasteSearchInput.trim().length >= 2
                    ) {
                        if (state.isSearchingProfile) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Search, contentDescription = "Cerca", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (state.profileSearchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val savedIds = remember(state.settings.likedGames) {
                        state.settings.likedGames.map { it.id }.toSet()
                    }
                    state.profileSearchResults.forEachIndexed { idx, candidate ->
                        val alreadyAdded = candidate.id in savedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = candidate.title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val detail = listOfNotNull(
                                    candidate.publisher.takeIf { it.isNotBlank() },
                                    candidate.steamTagNames.take(3).joinToString(" · ").takeIf { it.isNotBlank() }
                                        ?: candidate.gameCategories.take(2).joinToString(" · ").takeIf { it.isNotBlank() }
                                ).joinToString(" · ")
                                if (detail.isNotBlank()) {
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { onAddLikedGame(candidate) },
                                enabled = !alreadyAdded,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (alreadyAdded) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (alreadyAdded) "Salvato" else "Aggiungi")
                            }
                        }
                        if (idx < state.profileSearchResults.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                if (state.settings.likedGames.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Giochi nel tuo profilo gusti",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    state.settings.likedGames.forEachIndexed { idx, liked ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = EshopCrimson,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = liked.title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = liked.steamTagNames.take(3).joinToString(" · ")
                                    .ifBlank { liked.gameCategories.take(2).joinToString(" · ") }
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onRemoveLikedGame(liked.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Rimuovi dai gusti",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        if (idx < state.settings.likedGames.lastIndex) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    text = "Turbo Gemini AI (opzionale, BYOK)",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Facoltativo: aggiungi una chiave gratuita Google AI Studio per affinare ulteriormente il ranking semantico con Gemini 2.5 Flash-Lite. Senza chiave l'app usa comunque il motore locale Steam + Nintendo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
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
                Spacer(modifier = Modifier.height(6.dp))
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
                    if (state.settings.geminiApiKey.isNotBlank()) {
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
