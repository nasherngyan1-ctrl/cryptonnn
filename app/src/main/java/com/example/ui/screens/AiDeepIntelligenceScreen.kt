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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiTradingPrediction
import com.example.data.model.FundingRateReport
import com.example.data.model.LiquidityWallItem
import com.example.data.model.OkxInstrumentDeepTelemetry
import com.example.data.model.OrderBookLiquidityReport
import com.example.data.model.TimeframeQuantMetrics
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
import com.example.ui.theme.WarningAmberDim
import com.example.ui.viewmodel.AiPredictionUiState
import java.util.Locale

private val QUICK_OKX_COINS = listOf(
    "BTC-USDT",
    "ETH-USDT",
    "SOL-USDT",
    "TON-USDT",
    "XRP-USDT",
    "DOGE-USDT",
    "SUI-USDT",
    "BNB-USDT",
    "ADA-USDT",
    "PEPE-USDT",
    "AVAX-USDT",
    "LINK-USDT"
)

@Composable
fun AiDeepIntelligenceScreen(
    selectedInstId: String,
    selectedInstType: String,
    telemetry: OkxInstrumentDeepTelemetry?,
    isTelemetryLoading: Boolean,
    telemetryError: String?,
    aiPredictionState: AiPredictionUiState,
    onSelectCoin: (String) -> Unit,
    onRefreshAll: () -> Unit,
    onTriggerAiPrediction: () -> Unit,
    onSaveAiPrediction: (AiTradingPrediction) -> Unit,
    onQuickSetAlert: (String, Double, String) -> Unit
) {
    var selectedLevelsTimeframe by remember { mutableStateOf("1H") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_deep_intelligence_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Quick Coin Selector Strip + Refresh Button
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "تێرمیناڵی زانیاری ورد و پێشبینی Gemini AI",
                            style = MaterialTheme.typography.titleMedium,
                            color = OkxNeonLime
                        )
                        Text(
                            text = "دراوی چالاک: $selectedInstId (١٠٠٪ ژمارە و داتای ڕاستەوخۆی OKX بێ چارت)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                            color = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onRefreshAll,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ObsidianSurfaceVariant)
                            .testTag("btn_refresh_deep_telemetry")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "نوێکردنەوەی هەموو ژمارەکان و AI",
                            tint = OkxNeonLime
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(QUICK_OKX_COINS) { basePair ->
                        val actualPair = if (selectedInstType == "SWAP") "$basePair-SWAP" else basePair
                        val active = selectedInstId == actualPair || selectedInstId == basePair
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) OkxNeonLime else ObsidianSurface)
                                .border(
                                    1.dp,
                                    if (active) OkxNeonLime else ObsidianCardBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectCoin(actualPair) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = basePair.removeSuffix("-USDT"),
                                fontFamily = MonoNumberFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (active) Color.Black else TextPrimary
                            )
                        }
                    }
                }
            }
        }

        if (isTelemetryLoading && telemetry == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AiCyan)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "خوێندنەوەی ٤ تایمفرەیم و ٥٠ ئاستی Order Book لە OKX...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else if (telemetryError != null && telemetry == null) {
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = telemetryError,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        Button(
                            onClick = onRefreshAll,
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
        }

        if (telemetry != null) {
            // 2. Master AI Prediction Card (Gemini 3.8 Flash)
            item {
                GeminiMasterPredictionCard(
                    telemetry = telemetry,
                    aiState = aiPredictionState,
                    onGenerateAi = onTriggerAiPrediction,
                    onSaveSignal = onSaveAiPrediction,
                    onSetTargetAlert = onQuickSetAlert
                )
            }

            // 3. Live 12-Cell Micro-Telemetry Numerical Grid
            item {
                LiveMicroTelemetryGridCard(
                    telemetry = telemetry
                )
            }

            // 4. Multi-Timeframe Numerical Matrix (15m, 1H, 4H, 1D)
            item {
                MultiTimeframeQuantTableCard(
                    timeframeMap = telemetry.timeframeMetrics
                )
            }

            // 5. Key Price Levels Matrix: Pivot Points (S1-S3, R1-R3) & Fibonacci Retracements
            item {
                val activeMetrics = telemetry.timeframeMetrics[selectedLevelsTimeframe]
                    ?: telemetry.timeframeMetrics["1H"]
                    ?: telemetry.timeframeMetrics.values.firstOrNull()

                if (activeMetrics != null) {
                    KeyPriceLevelsMatrixCard(
                        currentPrice = telemetry.ticker.lastPrice,
                        selectedTf = selectedLevelsTimeframe,
                        metrics = activeMetrics,
                        onSelectTf = { selectedLevelsTimeframe = it },
                        onSetAlertAtLevel = { levelPx, labelKu ->
                            onQuickSetAlert(telemetry.ticker.instId, levelPx, labelKu)
                        }
                    )
                }
            }

            // 6. OKX 50-Level Order Book Liquidity & Whale Walls Table + Funding Rate
            item {
                OrderBookAndFundingTableCard(
                    orderBook = telemetry.orderBookReport,
                    funding = telemetry.fundingReport
                )
            }
        }
    }
}

@Composable
private fun GeminiMasterPredictionCard(
    telemetry: OkxInstrumentDeepTelemetry,
    aiState: AiPredictionUiState,
    onGenerateAi: () -> Unit,
    onSaveSignal: (AiTradingPrediction) -> Unit,
    onSetTargetAlert: (String, Double, String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, AiCyan.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
            .testTag("gemini_master_prediction_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AiCyanDim),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AiCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "بڕیار و پێشبینی زیرەکی دەستکرد (Gemini 3.8 Flash)",
                            style = MaterialTheme.typography.titleMedium,
                            color = AiCyan
                        )
                        Text(
                            text = "ئەنجامی تێکەڵکردنی ٤ تایمفرەیم + Order Book + Funding Rate",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }

                OutlinedButton(
                    onClick = onGenerateAi,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_regenerate_ai")
                ) {
                    Text(
                        text = "نوێکردنەوەی AI",
                        style = MaterialTheme.typography.labelLarge,
                        color = OkxNeonLime
                    )
                }
            }

            when (aiState) {
                is AiPredictionUiState.Idle -> {
                    Button(
                        onClick = onGenerateAi,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AiCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "دەستپێکردنی پێشبینی وردی زیرەکی دەستکرد بۆ ${telemetry.ticker.instId}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is AiPredictionUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceVariant)
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = AiCyan, modifier = Modifier.size(32.dp))
                        Text(
                            text = "زیرەکی دەستکردی Gemini خەریکی شیکردنەوەی ژمارەیی ${telemetry.ticker.instId} ـە...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "پشکنینی RSI, MACD, EMA 9/20/50/200, ATR, Pivot, Fibonacci و ٥٠ ئاستی Order Book",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }

                is AiPredictionUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BearishRedDim)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "کێشە لە وەرگرتنی پێشبینی AI: ${aiState.message}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BearishRed
                        )
                        Button(
                            onClick = onGenerateAi,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OkxNeonLime,
                                contentColor = Color.Black
                            )
                        ) {
                            Text("دووبارە پێشبینی بکەرەوە")
                        }
                    }
                }

                is AiPredictionUiState.Success -> {
                    val pred = aiState.prediction
                    val isLong = pred.signalDirection.contains("LONG")
                    val isShort = pred.signalDirection.contains("SHORT")
                    val signalColor = when {
                        isLong -> BullishGreen
                        isShort -> BearishRed
                        else -> WarningAmber
                    }
                    val signalBg = when {
                        isLong -> BullishGreenDim
                        isShort -> BearishRedDim
                        else -> WarningAmberDim
                    }

                    // Signal Verdict Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(signalBg)
                            .border(1.dp, signalColor, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "بڕیاری کۆتایی زیرەکی دەستکرد",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = TextSecondary
                            )
                            Text(
                                text = pred.signalVerdictKu,
                                style = MaterialTheme.typography.titleLarge,
                                color = signalColor
                            )
                            Text(
                                text = "لیڤرێجی پێشنیارکراو: ${pred.recommendedLeverage} • مەترسی: ${pred.riskLevelKu}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                color = TextPrimary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "دڵنیایی AI",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = TextSecondary
                            )
                            Text(
                                text = "${pred.confidencePercent}%",
                                fontFamily = MonoNumberFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp,
                                color = signalColor
                            )
                            Text(
                                text = "ئەگەری بردنەوە: ${pred.winProbabilityPercent}%",
                                fontFamily = MonoNumberFontFamily,
                                fontSize = 11.sp,
                                color = OkxNeonLime
                            )
                        }
                    }

                    // Exact Execution Plan Table (Entry Zone, TP1, TP2, TP3, Stop-Loss, R:R)
                    Text(
                        text = "خشتەی وردی خاڵی چوونەژوورەوە و ئامانجەکان (AI Execution Matrix)",
                        style = MaterialTheme.typography.titleMedium,
                        color = OkxNeonLime
                    )

                    val refPx = telemetry.ticker.lastPrice
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceVariant)
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExecutionTargetRow(
                            labelKu = "مەودای چوونەژوورەوە (Entry Zone)",
                            priceText = "${QuantMathEngine.formatCryptoPrice(pred.entryZoneMin)} – ${QuantMathEngine.formatCryptoPrice(pred.entryZoneMax)}",
                            pctText = "نرخی ئێستا: ${QuantMathEngine.formatCryptoPrice(refPx)}",
                            valueColor = AiCyan,
                            onSetAlert = {
                                onSetTargetAlert(
                                    pred.instId,
                                    pred.entryZoneMin,
                                    "گەیشتن بە خاڵی چوونەژوورەوەی AI"
                                )
                            }
                        )
                        HorizontalDivider(color = ObsidianCardBorder)
                        ExecutionTargetRow(
                            labelKu = "ئامانجی یەکەم (Take-Profit 1)",
                            priceText = "$${QuantMathEngine.formatCryptoPrice(pred.takeProfit1)}",
                            pctText = QuantMathEngine.formatSignedPercent(((pred.takeProfit1 - refPx) / refPx) * 100.0),
                            valueColor = BullishGreen,
                            onSetAlert = {
                                onSetTargetAlert(pred.instId, pred.takeProfit1, "ئامانجی یەکەمی قازانج (TP1)")
                            }
                        )
                        HorizontalDivider(color = ObsidianCardBorder)
                        ExecutionTargetRow(
                            labelKu = "ئامانجی دووەم (Take-Profit 2)",
                            priceText = "$${QuantMathEngine.formatCryptoPrice(pred.takeProfit2)}",
                            pctText = QuantMathEngine.formatSignedPercent(((pred.takeProfit2 - refPx) / refPx) * 100.0),
                            valueColor = BullishGreen,
                            onSetAlert = {
                                onSetTargetAlert(pred.instId, pred.takeProfit2, "ئامانجی دووەمی قازانج (TP2)")
                            }
                        )
                        HorizontalDivider(color = ObsidianCardBorder)
                        ExecutionTargetRow(
                            labelKu = "ئامانجی سێیەم (Take-Profit 3)",
                            priceText = "$${QuantMathEngine.formatCryptoPrice(pred.takeProfit3)}",
                            pctText = QuantMathEngine.formatSignedPercent(((pred.takeProfit3 - refPx) / refPx) * 100.0),
                            valueColor = BullishGreen,
                            onSetAlert = {
                                onSetTargetAlert(pred.instId, pred.takeProfit3, "ئامانجی سێیەمی قازانج (TP3)")
                            }
                        )
                        HorizontalDivider(color = ObsidianCardBorder)
                        ExecutionTargetRow(
                            labelKu = "وەستانی زیان (Stop-Loss)",
                            priceText = "$${QuantMathEngine.formatCryptoPrice(pred.stopLoss)}",
                            pctText = QuantMathEngine.formatSignedPercent(((pred.stopLoss - refPx) / refPx) * 100.0),
                            valueColor = BearishRed,
                            onSetAlert = {
                                onSetTargetAlert(pred.instId, pred.stopLoss, "ئاستی وەستانی زیان (Stop-Loss)")
                            }
                        )
                        HorizontalDivider(color = ObsidianCardBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ڕێژەی قازانج بەرامبەر مەترسی (Risk/Reward Ratio)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = "1 : ${String.format(Locale.US, "%.2f", pred.riskRewardRatio)}",
                                fontFamily = MonoNumberFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = OkxNeonLime
                            )
                        }
                    }

                    // Multi-Horizon Price Forecast Row (1H, 4H, 24H)
                    Text(
                        text = "پێشبینی نرخی داهاتوو بەپێی مۆدێلی زیرەکی دەستکرد",
                        style = MaterialTheme.typography.titleMedium,
                        color = AiCyan
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ForecastHorizonBox(
                            modifier = Modifier.weight(1f),
                            horizonLabel = "پێشبینی ١ کاتژمێر",
                            forecastPrice = pred.forecast1hPrice,
                            currentPrice = refPx
                        )
                        ForecastHorizonBox(
                            modifier = Modifier.weight(1f),
                            horizonLabel = "پێشبینی ٤ کاتژمێر",
                            forecastPrice = pred.forecast4hPrice,
                            currentPrice = refPx
                        )
                        ForecastHorizonBox(
                            modifier = Modifier.weight(1f),
                            horizonLabel = "پێشبینی ٢٤ کاتژمێر",
                            forecastPrice = pred.forecast24hPrice,
                            currentPrice = refPx
                        )
                    }

                    // Key Numeric Triggers
                    if (pred.keyNumericTriggersKu.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianSurfaceVariant)
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "هۆکارە ژمارەییە سەرەکییەکانی ئەم بڕیارە:",
                                style = MaterialTheme.typography.titleMedium,
                                color = OkxNeonLime
                            )
                            pred.keyNumericTriggersKu.forEach { trigger ->
                                Text(
                                    text = "• $trigger",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }

                    // Detailed Kurdish Analysis & OrderBook Alignment
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pred.multiTimeframeAlignmentKu.isNotBlank()) {
                            Text(
                                text = "هاوتابوونی تایمفرەیمەکان (15m / 1H / 4H / 1D):",
                                style = MaterialTheme.typography.labelLarge,
                                color = AiCyan
                            )
                            Text(
                                text = pred.multiTimeframeAlignmentKu,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        if (pred.whaleOrderBookSummaryKu.isNotBlank()) {
                            Text(
                                text = "خوێندنەوەی نەهەنگەکان و Order Book:",
                                style = MaterialTheme.typography.labelLarge,
                                color = WarningAmber
                            )
                            Text(
                                text = pred.whaleOrderBookSummaryKu,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        if (pred.detailedKurdishAnalysis.isNotBlank()) {
                            Text(
                                text = "ڕاپۆرتی تێروتەسەلی ستراتیژی مامەڵە:",
                                style = MaterialTheme.typography.labelLarge,
                                color = OkxNeonLime
                            )
                            Text(
                                text = pred.detailedKurdishAnalysis,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                    }

                    // Save AI Signal to Local Room DB Button
                    Button(
                        onClick = { onSaveSignal(pred) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_ai_signal"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OkxNeonLime,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "پاشەکەوتکردنی ئەم سیگناڵەی AI بۆ چاودێریکردنی ئەنجامەکەی",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExecutionTargetRow(
    labelKu: String,
    priceText: String,
    pctText: String,
    valueColor: Color,
    onSetAlert: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = labelKu,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = pctText,
                fontFamily = MonoNumberFontFamily,
                fontSize = 11.sp,
                color = valueColor
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = priceText,
                fontFamily = MonoNumberFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = valueColor
            )
            IconButton(
                onClick = onSetAlert,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "دانانی ئاگادارکەرەوەی نرخ",
                    tint = WarningAmber,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ForecastHorizonBox(
    modifier: Modifier = Modifier,
    horizonLabel: String,
    forecastPrice: Double,
    currentPrice: Double
) {
    val diffPct = if (currentPrice > 0) ((forecastPrice - currentPrice) / currentPrice) * 100.0 else 0.0
    val color = if (diffPct >= 0) BullishGreen else BearishRed
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceVariant)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(
            text = horizonLabel,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$${QuantMathEngine.formatCryptoPrice(forecastPrice)}",
            fontFamily = MonoNumberFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = TextPrimary
        )
        Text(
            text = QuantMathEngine.formatSignedPercent(diffPct),
            fontFamily = MonoNumberFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = color
        )
    }
}

@Composable
private fun LiveMicroTelemetryGridCard(
    telemetry: OkxInstrumentDeepTelemetry
) {
    val t = telemetry.ticker
    val isUp = t.change24hPercent >= 0.0
    val scoreColor = when {
        telemetry.compositeQuantScore >= 15 -> BullishGreen
        telemetry.compositeQuantScore <= -15 -> BearishRed
        else -> WarningAmber
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "زانیاری وردی چرکەبەچرکەی ${t.instId}",
                        style = MaterialTheme.typography.titleMedium,
                        color = OkxNeonLime
                    )
                    Text(
                        text = telemetry.compositeVerdictKu,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = scoreColor
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ObsidianSurfaceVariant
                ) {
                    Text(
                        text = "کۆی خاڵی ماتماتیکی: ${telemetry.compositeQuantScore}/100",
                        fontFamily = MonoNumberFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = scoreColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // 3x4 High-Precision Data Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DataGridCell(
                    modifier = Modifier.weight(1f),
                    title = "نرخی ئێستا (Last)",
                    value = "$${QuantMathEngine.formatCryptoPrice(t.lastPrice)}",
                    valueColor = if (isUp) BullishGreen else BearishRed
                )
                DataGridCell(
                    modifier = Modifier.weight(1f),
                    title = "گۆڕانکاری ٢٤س",
                    value = "${QuantMathEngine.formatSignedPercent(t.change24hPercent)} (${QuantMathEngine.formatCryptoPrice(t.change24hAbs)})",
                    valueColor = if (isUp) BullishGreen else BearishRed
                )
                DataGridCell(
                    modifier = Modifier.weight(1f),
                    title = "کرانەوەی ٢٤س (Open)",
                    value = "$${QuantMathEngine.formatCryptoPrice(t.open24h)}",
                    valueColor = TextPrimary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DataGridCell(
                    modifier = Modifier.weight(1f),
                    title = "بەرزترین ٢٤س (High)",
                    value = "$${QuantMathEngine.formatCryptoPrice(t.high24h)}",
                    valueColor = BullishGreen
                )
                DataGridCell(
                    modifier = Modifier.weight(1f),
                    title = "نزمترین ٢٤س (Low)",
                    value = "$${QuantMathEngine.formatCryptoPrice(t.low24h)}",
                    valueColor = BearishRed
                )
                DataGridCell(
                    modifier = Modifier.weight(1f),
                    title = "ناجێگیری ٢٤س (Range)",
                    value = String.format(Locale.US, "%.2f%% (Pos %.0f%%)", t.volatility24hPercent, t.range24hPercent),
                    valueColor = WarningAmber
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DataGridCell(
                    modifier = Modifier.weight(1f),
                    title = "باشترین کڕین (Bid)",
                    value = "${QuantMathEngine.formatCryptoPrice(t.bestBidPrice)} (${String.format(Locale.US, "%.2f", t.bestBidSize)})",
                    valueColor = BullishGreen
                )
                DataGridCell(
                    modifier = Modifier.weight(1f),
                    title = "باشترین فرۆشتن (Ask)",
                    value = "${QuantMathEngine.formatCryptoPrice(t.bestAskPrice)} (${String.format(Locale.US, "%.2f", t.bestAskSize)})",
                    valueColor = BearishRed
                )
                DataGridCell(
                    modifier = Modifier.weight(1f),
                    title = "قەبارەی ٢٤س (USDT)",
                    value = QuantMathEngine.formatCompactUsdt(t.volume24hUsdt),
                    valueColor = AiCyan
                )
            }
        }
    }
}

@Composable
private fun DataGridCell(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    valueColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceVariant)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontFamily = MonoNumberFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = valueColor
        )
    }
}

@Composable
private fun MultiTimeframeQuantTableCard(
    timeframeMap: Map<String, TimeframeQuantMetrics>
) {
    val orderedTfs = listOf("15m", "1H", "4H", "1D").mapNotNull { timeframeMap[it] }
    if (orderedTfs.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "خشتەی نیشاندەرە تەکنیکییەکان لەسەر هەر ٤ تایمفرەیمەکە (15m, 1H, 4H, 1D)",
                style = MaterialTheme.typography.titleMedium,
                color = OkxNeonLime
            )
            Text(
                text = "هەموو ژمارەکان ڕاستەوخۆ لە ١٠٠ مۆمی کۆتایی هەر تایمفرەیمێک لە OKX هەژمار کراون",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                color = TextSecondary
            )

            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianSurfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "نیشاندەر",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.weight(1.3f)
                )
                orderedTfs.forEach { m ->
                    Text(
                        text = m.timeframe,
                        fontFamily = MonoNumberFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = OkxNeonLime,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            MatrixComparisonRow(
                label = "خاڵی تەکنیکی",
                values = orderedTfs.map { "${it.quantScore}" },
                colors = orderedTfs.map {
                    when {
                        it.quantScore >= 15 -> BullishGreen
                        it.quantScore <= -15 -> BearishRed
                        else -> WarningAmber
                    }
                }
            )
            MatrixComparisonRow(
                label = "RSI (14)",
                values = orderedTfs.map { String.format(Locale.US, "%.1f", it.rsi14) },
                colors = orderedTfs.map {
                    when {
                        it.rsi14 <= 32.0 -> BullishGreen
                        it.rsi14 >= 70.0 -> BearishRed
                        else -> TextPrimary
                    }
                }
            )
            MatrixComparisonRow(
                label = "StochRSI (%K)",
                values = orderedTfs.map { String.format(Locale.US, "%.1f", it.stochRsiK) },
                colors = orderedTfs.map { TextPrimary }
            )
            MatrixComparisonRow(
                label = "MACD Hist",
                values = orderedTfs.map { QuantMathEngine.formatCryptoPrice(it.macdHistogram) },
                colors = orderedTfs.map { if (it.macdHistogram >= 0) BullishGreen else BearishRed }
            )
            MatrixComparisonRow(
                label = "EMA (9)",
                values = orderedTfs.map { QuantMathEngine.formatCryptoPrice(it.ema9) },
                colors = orderedTfs.map { if (it.lastClose >= it.ema9) BullishGreen else BearishRed }
            )
            MatrixComparisonRow(
                label = "EMA (20)",
                values = orderedTfs.map { QuantMathEngine.formatCryptoPrice(it.ema20) },
                colors = orderedTfs.map { if (it.lastClose >= it.ema20) BullishGreen else BearishRed }
            )
            MatrixComparisonRow(
                label = "EMA (50)",
                values = orderedTfs.map { QuantMathEngine.formatCryptoPrice(it.ema50) },
                colors = orderedTfs.map { if (it.lastClose >= it.ema50) BullishGreen else BearishRed }
            )
            MatrixComparisonRow(
                label = "EMA (200)",
                values = orderedTfs.map { QuantMathEngine.formatCryptoPrice(it.ema200) },
                colors = orderedTfs.map { if (it.lastClose >= it.ema200) BullishGreen else BearishRed }
            )
            MatrixComparisonRow(
                label = "Bollinger سەرەوە",
                values = orderedTfs.map { QuantMathEngine.formatCryptoPrice(it.bollingerUpper) },
                colors = orderedTfs.map { BearishRed }
            )
            MatrixComparisonRow(
                label = "Bollinger خوارەوە",
                values = orderedTfs.map { QuantMathEngine.formatCryptoPrice(it.bollingerLower) },
                colors = orderedTfs.map { BullishGreen }
            )
            MatrixComparisonRow(
                label = "ATR (14) %",
                values = orderedTfs.map { String.format(Locale.US, "%.2f%%", it.atrPercent) },
                colors = orderedTfs.map { WarningAmber }
            )
            MatrixComparisonRow(
                label = "VWAP میانە",
                values = orderedTfs.map { QuantMathEngine.formatCryptoPrice(it.vwap) },
                colors = orderedTfs.map { AiCyan }
            )
            MatrixComparisonRow(
                label = "گوڕی قەبارە",
                values = orderedTfs.map { String.format(Locale.US, "%.2fx", it.volumeSpikeRatio) },
                colors = orderedTfs.map { if (it.volumeSpikeRatio >= 1.3) OkxNeonLime else TextSecondary }
            )
        }
    }
}

@Composable
private fun MatrixComparisonRow(
    label: String,
    values: List<String>,
    colors: List<Color>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
            color = TextSecondary,
            modifier = Modifier.weight(1.3f)
        )
        values.indices.forEach { idx ->
            Text(
                text = values[idx],
                fontFamily = MonoNumberFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = colors.getOrElse(idx) { TextPrimary },
                modifier = Modifier.weight(1f)
            )
        }
    }
    HorizontalDivider(color = ObsidianCardBorder.copy(alpha = 0.4f))
}

@Composable
private fun KeyPriceLevelsMatrixCard(
    currentPrice: Double,
    selectedTf: String,
    metrics: TimeframeQuantMetrics,
    onSelectTf: (String) -> Unit,
    onSetAlertAtLevel: (Double, String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ئاستەکانی پاڵپشتی، بەرگری و فیبۆناچی",
                        style = MaterialTheme.typography.titleMedium,
                        color = OkxNeonLime
                    )
                    Text(
                        text = "کرتە لە زەنگۆڵەی هەر ئاستێک بکە بۆ دانانی ئاگادارکەرەوەی نرخ",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("15m", "1H", "4H", "1D").forEach { tf ->
                        val active = tf == selectedTf
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) OkxNeonLime else ObsidianSurfaceVariant)
                                .clickable { onSelectTf(tf) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = tf,
                                fontFamily = MonoNumberFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (active) Color.Black else TextSecondary
                            )
                        }
                    }
                }
            }

            // Two-Column Layout: Left = Pivot Support/Resistance, Right = Fibonacci Retracements
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceVariant)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ئاستەکانی Pivot ($selectedTf)",
                        style = MaterialTheme.typography.labelLarge,
                        color = AiCyan
                    )
                    LevelPriceItem("بەرگری ٣ (R3)", metrics.resistance3, currentPrice, BearishRed, onSetAlertAtLevel)
                    LevelPriceItem("بەرگری ٢ (R2)", metrics.resistance2, currentPrice, BearishRed, onSetAlertAtLevel)
                    LevelPriceItem("بەرگری ١ (R1)", metrics.resistance1, currentPrice, BearishRed, onSetAlertAtLevel)
                    LevelPriceItem("تەوەرەی ناوەند (Pivot)", metrics.pivotPoint, currentPrice, WarningAmber, onSetAlertAtLevel)
                    LevelPriceItem("پاڵپشتی ١ (S1)", metrics.support1, currentPrice, BullishGreen, onSetAlertAtLevel)
                    LevelPriceItem("پاڵپشتی ٢ (S2)", metrics.support2, currentPrice, BullishGreen, onSetAlertAtLevel)
                    LevelPriceItem("پاڵپشتی ٣ (S3)", metrics.support3, currentPrice, BullishGreen, onSetAlertAtLevel)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceVariant)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "ئاستەکانی فیبۆناچی ($selectedTf)",
                        style = MaterialTheme.typography.labelLarge,
                        color = OkxNeonLime
                    )
                    LevelPriceItem("لووتکە (0.0%)", metrics.periodHigh, currentPrice, BearishRed, onSetAlertAtLevel)
                    LevelPriceItem("Fib 0.236", metrics.fib236, currentPrice, TextPrimary, onSetAlertAtLevel)
                    LevelPriceItem("Fib 0.382", metrics.fib382, currentPrice, TextPrimary, onSetAlertAtLevel)
                    LevelPriceItem("Fib 0.500 (نیوە)", metrics.fib500, currentPrice, WarningAmber, onSetAlertAtLevel)
                    LevelPriceItem("Fib 0.618 (زێڕین)", metrics.fib618, currentPrice, OkxNeonLime, onSetAlertAtLevel)
                    LevelPriceItem("Fib 0.786", metrics.fib786, currentPrice, BullishGreen, onSetAlertAtLevel)
                    LevelPriceItem("بنکە (100%)", metrics.periodLow, currentPrice, BullishGreen, onSetAlertAtLevel)
                }
            }
        }
    }
}

@Composable
private fun LevelPriceItem(
    label: String,
    levelPrice: Double,
    currentPrice: Double,
    accentColor: Color,
    onSetAlert: (Double, String) -> Unit
) {
    val distPct = if (currentPrice > 0) ((levelPrice - currentPrice) / currentPrice) * 100.0 else 0.0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSetAlert(levelPrice, label) }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
                color = TextSecondary
            )
            Text(
                text = QuantMathEngine.formatSignedPercent(distPct),
                fontFamily = MonoNumberFontFamily,
                fontSize = 9.sp,
                color = TextMuted
            )
        }
        Text(
            text = QuantMathEngine.formatCryptoPrice(levelPrice),
            fontFamily = MonoNumberFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = accentColor
        )
    }
}

@Composable
private fun OrderBookAndFundingTableCard(
    orderBook: OrderBookLiquidityReport?,
    funding: FundingRateReport?
) {
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
            Text(
                text = "قووڵایی بازاڕی OKX (٥٠ ئاستی Order Book) و ڕێژەی فەندینگ",
                style = MaterialTheme.typography.titleMedium,
                color = OkxNeonLime
            )

            if (funding != null) {
                val fColor = if (funding.currentFundingRatePercent >= 0) BullishGreen else BearishRed
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ObsidianSurfaceVariant)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "گرێبەستی هەمیشەیی: ${funding.swapInstId}",
                            style = MaterialTheme.typography.labelLarge,
                            color = AiCyan
                        )
                        Text(
                            text = "ماوە بۆ یەکلاکردنەوە: ${funding.minutesToNextSettlement} خولەک",
                            fontFamily = MonoNumberFontFamily,
                            fontSize = 11.sp,
                            color = WarningAmber
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MiniNumericCell(
                            label = "فەندینگی ئێستا",
                            value = String.format(Locale.US, "%+.4f%%", funding.currentFundingRatePercent),
                            valueColor = fColor
                        )
                        MiniNumericCell(
                            label = "فەندینگی داهاتوو",
                            value = String.format(Locale.US, "%+.4f%%", funding.nextFundingRatePercent),
                            valueColor = TextPrimary
                        )
                        MiniNumericCell(
                            label = "ڕێژەی ساڵانە (APR)",
                            value = String.format(Locale.US, "%+.2f%%", funding.annualizedAprPercent),
                            valueColor = WarningAmber
                        )
                    }
                    Text(
                        text = funding.sentimentLabelKu,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }
            }

            if (orderBook != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "هێزی کڕیاران: ${String.format(Locale.US, "%.1f%%", orderBook.bidPressurePercent)} (${QuantMathEngine.formatCompactUsdt(orderBook.totalBidNotionalUsdt)})",
                            fontFamily = MonoNumberFontFamily,
                            fontSize = 11.sp,
                            color = BullishGreen
                        )
                        Text(
                            text = "هێزی فرۆشیاران: ${String.format(Locale.US, "%.1f%%", orderBook.askPressurePercent)} (${QuantMathEngine.formatCompactUsdt(orderBook.totalAskNotionalUsdt)})",
                            fontFamily = MonoNumberFontFamily,
                            fontSize = 11.sp,
                            color = BearishRed
                        )
                    }

                    // Visual Pressure Ratio Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    ) {
                        val bidWeight = orderBook.bidPressurePercent.toFloat().coerceIn(5f, 95f)
                        val askWeight = (100f - bidWeight).coerceIn(5f, 95f)
                        Box(
                            modifier = Modifier
                                .weight(bidWeight)
                                .fillMaxSize()
                                .background(BullishGreen)
                        )
                        Box(
                            modifier = Modifier
                                .weight(askWeight)
                                .fillMaxSize()
                                .background(BearishRed)
                        )
                    }

                    Text(
                        text = orderBook.dominantSideSummaryKu,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    // Whale Bid Walls & Ask Walls Tables
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WhaleWallsColumn(
                            modifier = Modifier.weight(1f),
                            titleKu = "بەهێزترین دیواری کڕین (Support Walls)",
                            walls = orderBook.topSupportBidWalls,
                            accentColor = BullishGreen
                        )
                        WhaleWallsColumn(
                            modifier = Modifier.weight(1f),
                            titleKu = "بەهێزترین دیواری فرۆشتن (Resistance Walls)",
                            walls = orderBook.topResistanceAskWalls,
                            accentColor = BearishRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhaleWallsColumn(
    modifier: Modifier = Modifier,
    titleKu: String,
    walls: List<LiquidityWallItem>,
    accentColor: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianSurfaceVariant)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = titleKu,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
            color = accentColor
        )
        walls.forEach { wall ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = QuantMathEngine.formatCryptoPrice(wall.price),
                        fontFamily = MonoNumberFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = accentColor
                    )
                    Text(
                        text = "${wall.ordersCount} داواکاری",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp),
                        color = TextMuted
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = QuantMathEngine.formatCompactUsdt(wall.notionalUsdt),
                        fontFamily = MonoNumberFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = QuantMathEngine.formatSignedPercent(wall.distanceFromMidPercent),
                        fontFamily = MonoNumberFontFamily,
                        fontSize = 9.sp,
                        color = TextSecondary
                    )
                }
            }
            HorizontalDivider(color = ObsidianCardBorder.copy(alpha = 0.4f))
        }
    }
}
