package com.switchpulse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.switchpulse.app.R
import com.switchpulse.app.data.DealFilterEngine
import com.switchpulse.app.data.model.Deal
import com.switchpulse.app.data.model.FeedSortMode
import com.switchpulse.app.data.model.FilterSettings
import com.switchpulse.app.ui.components.Cover
import com.switchpulse.app.ui.components.PrimaryButton
import com.switchpulse.app.ui.components.RollingDigitsText
import com.switchpulse.app.ui.components.SecondaryButton
import com.switchpulse.app.ui.components.SectionLabel
import com.switchpulse.app.ui.components.SegmentedChoice
import com.switchpulse.app.ui.components.SpSlider
import com.switchpulse.app.ui.components.SpSwitch
import com.switchpulse.app.ui.motion.hapticTick
import com.switchpulse.app.ui.theme.SpTheme
import com.switchpulse.app.ui.theme.SpType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersBottomSheet(
    settings: FilterSettings,
    rawCachedDeals: List<Deal>,
    onDismiss: () -> Unit,
    onResetFilters: () -> Unit,
    onSelectSortMode: (FeedSortMode) -> Unit,
    onToggleForYouOnly: (Boolean) -> Unit,
    onToggleExpiringSoonOnly: (Boolean) -> Unit,
    onChangeMinDiscount: (Int) -> Unit,
    onChangeMinRegularPrice: (Float) -> Unit,
    onChangeRecommendationWeight: (Int) -> Unit,
    onOpenHiddenGames: () -> Unit
) {
    val c = SpTheme.colors
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var draftMinDiscount by remember(settings.minDiscountPercent) {
        mutableIntStateOf(settings.minDiscountPercent)
    }
    var draftMinRegularPrice by remember(settings.minRegularPriceEur) {
        mutableFloatStateOf(settings.minRegularPriceEur)
    }
    var draftWeight by remember(settings.recommendationWeight) {
        mutableIntStateOf(settings.recommendationWeight)
    }

    val liveDraftSettings = remember(settings, draftMinDiscount, draftMinRegularPrice, draftWeight) {
        settings.copy(
            minDiscountPercent = draftMinDiscount,
            minRegularPriceEur = draftMinRegularPrice,
            recommendationWeight = draftWeight
        )
    }
    val liveCount = remember(rawCachedDeals, liveDraftSettings) {
        DealFilterEngine.countFeedMatches(rawCachedDeals, liveDraftSettings)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.paper,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.border)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.filters_title),
                    style = SpType.sheetTitle.copy(fontSize = 34.sp),
                    color = c.ink
                )
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clickable {
                            view.hapticTick()
                            onResetFilters()
                        }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.filters_reset),
                        style = SpType.tab.copy(fontWeight = FontWeight.SemiBold),
                        color = c.muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            SectionLabel(text = stringResource(R.string.filters_sort_section))
            Spacer(modifier = Modifier.height(8.dp))

            val sortModes = FeedSortMode.entries
            SegmentedChoice(
                items = listOf(
                    stringResource(R.string.ranking_sort_personal),
                    stringResource(R.string.ranking_sort_discount),
                    stringResource(R.string.ranking_sort_price)
                ),
                selectedIndex = sortModes.indexOf(settings.feedSortMode).coerceAtLeast(0),
                onSelect = { idx -> onSelectSortMode(sortModes[idx]) }
            )

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(thickness = 1.dp, color = c.line)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleForYouOnly(!settings.forYouOnlyMode) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.filters_for_you_title),
                        style = SpType.rowTitle.copy(fontSize = 15.sp),
                        color = c.ink
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.filters_for_you_subtitle),
                        style = SpType.caption.copy(fontSize = 13.sp),
                        color = c.muted
                    )
                }
                SpSwitch(
                    checked = settings.forYouOnlyMode,
                    onCheckedChange = onToggleForYouOnly
                )
            }

            HorizontalDivider(thickness = 1.dp, color = c.line)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpiringSoonOnly(!settings.expiringSoonOnly) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.filters_expiring_title),
                        style = SpType.rowTitle.copy(fontSize = 15.sp),
                        color = c.ink
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.filters_expiring_subtitle),
                        style = SpType.caption.copy(fontSize = 13.sp),
                        color = c.muted
                    )
                }
                SpSwitch(
                    checked = settings.expiringSoonOnly,
                    onCheckedChange = onToggleExpiringSoonOnly
                )
            }

            HorizontalDivider(thickness = 1.dp, color = c.line)
            Spacer(modifier = Modifier.height(16.dp))

            SpSlider(
                label = stringResource(R.string.filters_min_discount),
                valueText = "${draftMinDiscount}%",
                value = draftMinDiscount.toFloat(),
                valueRange = 0f..80f,
                steps = 15,
                onValueChange = { raw ->
                    draftMinDiscount = ((raw / 5f).roundToInt() * 5).coerceIn(0, 80)
                },
                onValueChangeFinished = {
                    onChangeMinDiscount(draftMinDiscount)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SpSlider(
                label = stringResource(R.string.filters_min_price),
                valueText = "${draftMinRegularPrice.roundToInt()} €",
                value = draftMinRegularPrice,
                valueRange = 0f..50f,
                steps = 49,
                onValueChange = { raw ->
                    draftMinRegularPrice = raw.roundToInt().toFloat().coerceIn(0f, 50f)
                },
                onValueChangeFinished = {
                    onChangeMinRegularPrice(draftMinRegularPrice)
                },
                helpText = stringResource(R.string.filters_min_price_help)
            )

            Spacer(modifier = Modifier.height(12.dp))

            SpSlider(
                label = stringResource(R.string.filters_taste_weight),
                valueText = "${draftWeight}%",
                value = draftWeight.toFloat(),
                valueRange = 0f..100f,
                steps = 9,
                onValueChange = { raw ->
                    draftWeight = ((raw / 10f).roundToInt() * 10).coerceIn(0, 100)
                },
                onValueChangeFinished = {
                    onChangeRecommendationWeight(draftWeight)
                },
                helpText = stringResource(R.string.filters_taste_weight_help)
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(thickness = 1.dp, color = c.line)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(onClick = onOpenHiddenGames),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.filters_hidden_games),
                    style = SpType.rowTitle.copy(fontSize = 15.sp),
                    color = c.ink
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = settings.blacklistIds.size.toString(),
                        style = SpType.body.copy(fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                        color = c.muted
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = c.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            PrimaryButton(
                text = stringResource(R.string.filters_show_count, liveCount),
                onClick = onDismiss,
                height = 52.dp
            )
        }
    }
}

@Composable
fun HiddenDealsScreen(
    blacklistIds: Set<String>,
    blacklistTitles: Map<String, String>,
    rawCachedDeals: List<Deal>,
    onUnhideDeal: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = SpTheme.colors
    BackHandler(onBack = onBack)

    val coverById = remember(rawCachedDeals) {
        rawCachedDeals.associateBy({ it.id }, { it.imageUrl })
    }
    val ids = remember(blacklistIds) { blacklistIds.toList() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.paper)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
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
                text = stringResource(R.string.filters_hidden_games),
                style = SpType.sheetTitle,
                color = c.ink
            )
        }

        HorizontalDivider(thickness = 1.dp, color = c.line)

        if (ids.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.hidden_games_empty),
                    style = SpType.body,
                    color = c.muted
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                items(ids, key = { it }) { id ->
                    val title = blacklistTitles[id] ?: "Titolo #$id"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Cover(
                            url = coverById[id],
                            title = title,
                            radius = 3.dp,
                            modifier = Modifier.width(80.dp)
                        )
                        Text(
                            text = title,
                            style = SpType.rowTitle,
                            color = c.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        SecondaryButton(
                            text = stringResource(R.string.action_restore),
                            onClick = { onUnhideDeal(id) },
                            height = 40.dp
                        )
                    }
                    HorizontalDivider(thickness = 1.dp, color = c.line)
                }
            }
        }
    }
}
