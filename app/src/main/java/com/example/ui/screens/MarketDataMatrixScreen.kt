package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OkxMarketTicker
import com.example.domain.QuantMathEngine
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BearishRedDim
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.BullishGreenDim
import com.example.ui.theme.MonoNumberFontFamily
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OkxLimeDim
import com.example.ui.theme.OkxNeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.MarketFilterCategory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MarketDataMatrixScreen(
    tickers: List<OkxMarketTicker>,
    selectedInstType: String,
    isLoading: Boolean,
    errorMessage: String?,
    lastUpdatedMs: Long,
    autoRefreshEnabled: Boolean,
    searchQuery: String,
    selectedCategory: MarketFilterCategory,
    watchlistIds: Set<String>,
    onSelectInstType: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectCategory: (MarketFilterCategory) -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onManualRefresh: () -> Unit,
    onToggleWatchlist: (String) -> Unit,
    onInspectInstrumentWithAi: (String) -> Unit
) {
    val filteredTickers = remember(tickers, searchQuery, selectedCategory, watchlistIds) {
        val baseList = if (searchQuery.isBlank()) {
            tickers
        } else {
            val q = searchQuery.trim().uppercase(Locale.US)
            tickers.filter {
                it.instId.uppercase(Locale.US).contains(q) ||
                    it.baseSymbol.uppercase(Locale.US).contains(q)
            }
        }

        when (selectedCategory) {
            MarketFilterCategory.ALL -> baseList
            MarketFilterCategory.WATCHLIST -> baseList.filter { watchlistIds.contains(it.instId) }
            MarketFilterCategory.TOP_VOLUME -> baseList.sortedByDescending { it.volume24hUsdt }
            MarketFilterCategory.TOP_GAINERS -> baseList.sortedByDescending { it.change24hPercent }
            MarketFilterCategory.TOP_LOSERS -> baseList.sortedBy { it.change24hPercent }
            MarketFilterCategory.HIGH_VOLATILITY -> baseList.sortedByDescending { it.volatility24hPercent }
        }
    }

    val totalVolumeUsdt = remember(tickers) { tickers.sumOf { it.volume24hUsdt } }
    val advancingCount = remember(tickers) { tickers.count { it.change24hPercent > 0 } }
    val decliningCount = remember(tickers) { tickers.count { it.change24hPercent < 0 } }
    val topGainer = remember(tickers) { tickers.maxByOrNull { it.change24hPercent } }
    val topLoser = remember(tickers) { tickers.minByOrNull { it.change24hPercent } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("market_data_matrix_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Institutional Market Telemetry Header Card
        item {
            MarketSummaryHeaderCard(
                selectedInstType = selectedInstType,
                totalPairs = tickers.size,
                totalVolumeUsdt = totalVolumeUsdt,
                advancingCount = advancingCount,
                decliningCount = decliningCount,
                topGainer = topGainer,
                topLoser = topLoser,
                lastUpdatedMs = lastUpdatedMs,
                autoRefreshEnabled = autoRefreshEnabled,
                onSelectInstType = onSelectInstType,
                onToggleAutoRefresh = onToggleAutoRefresh,
                onManualRefresh = onManualRefresh,
                onSelectCoin = onInspectInstrumentWithAi
            )
        }

        // 2. Search Bar & Filter Categories
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("market_search_input"),
                    placeholder = {
                        Text(
                            text = "گەڕان بەدوای دراو لە OKX (BTC, ETH, SOL, TON, PEPE...)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "گەڕان",
                            tint = OkxNeonLime
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "سڕینەوەی گەڕان",
                                    tint = TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OkxNeonLime,
                        unfocusedBorderColor = ObsidianCardBorder,
                        focusedContainerColor = ObsidianSurface,
                        unfocusedContainerColor = ObsidianSurface
                    )
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(MarketFilterCategory.entries) { category ->
                        val selected = category == selectedCategory
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectCategory(category) },
                            modifier = Modifier.testTag("filter_chip_${category.name}"),
                            label = {
                                Text(
                                    text = category.labelKu,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = OkxLimeDim,
                                selectedLabelColor = OkxNeonLime,
                                containerColor = ObsidianSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = ObsidianCardBorder,
                                selectedBorderColor = OkxNeonLime
                            )
                        )
                    }
                }
            }
        }

        // 3. Loading / Error / Empty states
        if (isLoading && tickers.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = OkxNeonLime)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "وەرگرتنی زانیارییە زیندووەکانی بازاڕی OKX v5...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else if (errorMessage != null && tickers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BearishRedDim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BearishRed, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "کێشە لە پەیوەستبوون بە OKX API",
                            style = MaterialTheme.typography.titleMedium,
                            color = BearishRed
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Button(
                            onClick = onManualRefresh,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OkxNeonLime,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("دووبارە هەوڵبدەرەوە")
                        }
                    }
                }
            }
        } else if (filteredTickers.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "هیچ دراوێک نەدۆزرایەوە بەپێی ئەم فلتەرە",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تکایە وشەی گەڕانەکە بگۆڕە یان پۆلێنی «هەمووی» هەڵبژێرە.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredTickers.take(80),
                key = { it.instId }
            ) { ticker ->
                OkxHighDensityTickerRow(
                    ticker = ticker,
                    isStarred = watchlistIds.contains(ticker.instId),
                    onToggleStar = { onToggleWatchlist(ticker.instId) },
                    onInspectWithAi = { onInspectInstrumentWithAi(ticker.instId) }
                )
            }
        }
    }
}

@Composable
private fun MarketSummaryHeaderCard(
    selectedInstType: String,
    totalPairs: Int,
    totalVolumeUsdt: Double,
    advancingCount: Int,
    decliningCount: Int,
    topGainer: OkxMarketTicker?,
    topLoser: OkxMarketTicker?,
    lastUpdatedMs: Long,
    autoRefreshEnabled: Boolean,
    onSelectInstType: (String) -> Unit,
    onToggleAutoRefresh: () -> Unit,
    onManualRefresh: () -> Unit,
    onSelectCoin: (String) -> Unit
) {
    val timeText = remember(lastUpdatedMs) {
        if (lastUpdatedMs == 0L) "---"
        else SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastUpdatedMs))
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top bar: Spot / Swap Switcher + Live refresh controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianSurfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MarketTypePill(
                        title = "سپۆت (SPOT)",
                        selected = selectedInstType == "SPOT",
                        onClick = { onSelectInstType("SPOT") },
                        tag = "btn_market_spot"
                    )
                    MarketTypePill(
                        title = "فیوچەرز (SWAP)",
                        selected = selectedInstType == "SWAP",
                        onClick = { onSelectInstType("SWAP") },
                        tag = "btn_market_swap"
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (autoRefreshEnabled) BullishGreenDim else ObsidianSurfaceVariant,
                        modifier = Modifier
                            .clickable { onToggleAutoRefresh() }
                            .testTag("toggle_auto_refresh")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (autoRefreshEnabled) BullishGreen else TextMuted)
                            )
                            Text(
                                text = if (autoRefreshEnabled) "زیندووی 6s ($timeText)" else "وەستاو ($timeText)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoRefreshEnabled) BullishGreen else TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onManualRefresh,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianSurfaceVariant)
                            .testTag("btn_manual_refresh")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "نوێکردنەوەی دەستبەجێ",
                            tint = OkxNeonLime,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 4-Column Macro Numerical Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MacroStatBox(
                    modifier = Modifier.weight(1f),
                    label = "قەبارەی گشتی ٢٤س",
                    value = QuantMathEngine.formatCompactUsdt(totalVolumeUsdt),
                    valueColor = OkxNeonLime
                )
                MacroStatBox(
                    modifier = Modifier.weight(1f),
                    label = "بەرزبووەوە / دابەزیو",
                    value = "$advancingCount ↑ / $decliningCount ↓",
                    valueColor = if (advancingCount >= decliningCount) BullishGreen else BearishRed
                )
                MacroStatBox(
                    modifier = Modifier.weight(1f),
                    label = "ژمارەی جووتەکان",
                    value = "$totalPairs جووت",
                    valueColor = AiCyan
                )
            }

            // Top Gainer & Top Loser Quick Numerical Strip
            if (topGainer != null && topLoser != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickMoverPill(
                        modifier = Modifier.weight(1f),
                        titleKu = "بەرزترین گەشەی ٢٤س",
                        instId = topGainer.baseSymbol,
                        priceText = QuantMathEngine.formatCryptoPrice(topGainer.lastPrice),
                        changeText = QuantMathEngine.formatSignedPercent(topGainer.change24hPercent),
                        isPositive = true,
                        onClick = { onSelectCoin(topGainer.instId) }
                    )
                    QuickMoverPill(
                        modifier = Modifier.weight(1f),
                        titleKu = "زۆرترین دابەزینی ٢٤س",
                        instId = topLoser.baseSymbol,
                        priceText = QuantMathEngine.formatCryptoPrice(topLoser.lastPrice),
                        changeText = QuantMathEngine.formatSignedPercent(topLoser.change24hPercent),
                        isPositive = false,
                        onClick = { onSelectCoin(topLoser.instId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketTypePill(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) OkxNeonLime else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.Black else TextSecondary
        )
    }
}

@Composable
private fun MacroStatBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = MonoNumberFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun QuickMoverPill(
    modifier: Modifier = Modifier,
    titleKu: String,
    instId: String,
    priceText: String,
    changeText: String,
    isPositive: Boolean,
    onClick: () -> Unit
) {
    val accent = if (isPositive) BullishGreen else BearishRed
    val bg = if (isPositive) BullishGreenDim else BearishRedDim
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = titleKu,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
                color = TextSecondary
            )
            Text(
                text = "$instId • $$priceText",
                fontFamily = MonoNumberFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = TextPrimary
            )
        }
        Text(
            text = changeText,
            fontFamily = MonoNumberFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = accent
        )
    }
}

@Composable
private fun OkxHighDensityTickerRow(
    ticker: OkxMarketTicker,
    isStarred: Boolean,
    onToggleStar: () -> Unit,
    onInspectWithAi: () -> Unit
) {
    val isUp = ticker.change24hPercent >= 0.0
    val changeColor = if (isUp) BullishGreen else BearishRed
    val changeBg = if (isUp) BullishGreenDim else BearishRedDim

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onInspectWithAi)
            .testTag("ticker_card_${ticker.instId}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Symbol + Market Badge + Exact Last Price + 24h % Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "دڵخواز",
                            tint = if (isStarred) WarningAmber else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = ticker.baseSymbol,
                                fontFamily = MonoNumberFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "/${ticker.quoteSymbol}",
                                fontFamily = MonoNumberFontFamily,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = if (ticker.instType == "SWAP") AiCyanDim else ObsidianSurfaceVariant
                            ) {
                                Text(
                                    text = if (ticker.instType == "SWAP") "SWAP" else "SPOT",
                                    fontFamily = MonoNumberFontFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ticker.instType == "SWAP") AiCyan else TextSecondary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "قەبارەی ٢٤س: ${QuantMathEngine.formatCompactUsdt(ticker.volume24hUsdt)}",
                            fontFamily = MonoNumberFontFamily,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$${QuantMathEngine.formatCryptoPrice(ticker.lastPrice)}",
                            fontFamily = MonoNumberFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${if (ticker.change24hAbs >= 0) "+" else ""}${QuantMathEngine.formatCryptoPrice(ticker.change24hAbs)} USDT",
                            fontFamily = MonoNumberFontFamily,
                            fontSize = 11.sp,
                            color = changeColor
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = changeBg,
                        modifier = Modifier.border(1.dp, changeColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = QuantMathEngine.formatSignedPercent(ticker.change24hPercent),
                            fontFamily = MonoNumberFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = changeColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = ObsidianCardBorder.copy(alpha = 0.6f))

            // Row 2: 6-Point Exact Numerical Telemetry Strip (No charts, pure numbers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniNumericCell(
                    label = "بەرزترین ٢٤س",
                    value = QuantMathEngine.formatCryptoPrice(ticker.high24h),
                    valueColor = BullishGreen
                )
                MiniNumericCell(
                    label = "نزمترین ٢٤س",
                    value = QuantMathEngine.formatCryptoPrice(ticker.low24h),
                    valueColor = BearishRed
                )
                MiniNumericCell(
                    label = "شوێن لە مەودا",
                    value = String.format(Locale.US, "%.1f%%", ticker.range24hPercent),
                    valueColor = TextPrimary
                )
                MiniNumericCell(
                    label = "ناجێگیری ٢٤س",
                    value = String.format(Locale.US, "%.2f%%", ticker.volatility24hPercent),
                    valueColor = WarningAmber
                )
                MiniNumericCell(
                    label = "جیاوازی (Spread)",
                    value = String.format(Locale.US, "%.2f bps", ticker.spreadBps),
                    valueColor = AiCyan
                )
            }

            // Row 3: Best Bid / Best Ask + Direct AI Deep Analysis Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "کڕین (Bid): ${QuantMathEngine.formatCryptoPrice(ticker.bestBidPrice)}",
                        fontFamily = MonoNumberFontFamily,
                        fontSize = 11.sp,
                        color = BullishGreen
                    )
                    Text(
                        text = "فرۆشتن (Ask): ${QuantMathEngine.formatCryptoPrice(ticker.bestAskPrice)}",
                        fontFamily = MonoNumberFontFamily,
                        fontSize = 11.sp,
                        color = BearishRed
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AiCyanDim)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AiCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "پێشبینی AI و داتای ورد",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                        color = AiCyan
                    )
                }
            }
        }
    }
}

@Composable
internal fun MiniNumericCell(
    label: String,
    value: String,
    valueColor: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
            color = TextMuted
        )
        Text(
            text = value,
            fontFamily = MonoNumberFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = valueColor
        )
    }
}
