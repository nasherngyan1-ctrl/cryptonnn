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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.data.local.ApiKeyEntity
import com.example.data.local.PriceAlertEntity
import com.example.data.local.SavedAiSignalEntity
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
import com.example.ui.theme.OkxNeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.util.Locale
import kotlin.math.max

@Composable
fun SavedSignalsAndCalcScreen(
    activeInstId: String,
    activeSpotPrice: Double,
    liveTickers: List<OkxMarketTicker>,
    savedSignals: List<SavedAiSignalEntity>,
    priceAlerts: List<PriceAlertEntity>,
    apiKeys: List<ApiKeyEntity>,
    activeAiEngineBadge: String,
    testingKeyId: Int?,
    onAddApiKeys: (String, String, String, String) -> Unit,
    onToggleApiKeyEnabled: (ApiKeyEntity) -> Unit,
    onDeleteApiKey: (Int) -> Unit,
    onTestApiKey: (ApiKeyEntity) -> Unit,
    onResetKeyCooldowns: () -> Unit,
    onDeleteSignal: (Int) -> Unit,
    onAddPriceAlert: (String, Double, String) -> Unit,
    onDeletePriceAlert: (Int) -> Unit,
    onInspectCoin: (String) -> Unit
) {
    val livePriceMap = remember(liveTickers) {
        liveTickers.associateBy({ it.instId }, { it.lastPrice })
    }

    var alertPairInput by remember(activeInstId) { mutableStateOf(activeInstId) }
    var alertPriceInput by remember(activeSpotPrice) {
        mutableStateOf(if (activeSpotPrice > 0) QuantMathEngine.formatCryptoPrice(activeSpotPrice).replace(",", "") else "")
    }
    var alertNoteInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("saved_signals_calc_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 0. Multi-Provider API Key Manager & Automatic Key Rotator
        item {
            ApiKeyRotationManagerCard(
                apiKeys = apiKeys,
                activeAiEngineBadge = activeAiEngineBadge,
                testingKeyId = testingKeyId,
                onAddApiKeys = onAddApiKeys,
                onToggleKeyEnabled = onToggleApiKeyEnabled,
                onDeleteKey = onDeleteApiKey,
                onTestKey = onTestApiKey,
                onResetCooldowns = onResetKeyCooldowns
            )
        }

        // 1. OKX Futures Position, Leverage & Liquidation Risk Calculator
        item {
            OkxFuturesRiskCalculatorCard(
                defaultEntryPrice = activeSpotPrice
            )
        }

        // 2. Saved AI Signals Live Performance Tracker
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "سیگناڵە پاشەکەوتکراوەکانی AI و بەراوردکردنی زیندوو (${savedSignals.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = OkxNeonLime
                )
                if (savedSignals.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = "هیچ سیگناڵێکی AI پاشەکەوت نەکراوە. لە بەشی «پێشبینی AI و داتا» کرتە لە دوگمەی پاشەکەوتکردنی سیگناڵ بکە بۆ چاودێریکردنی ئەنجامەکەی لە بازاڕی ڕاستەقینەدا.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        items(
            items = savedSignals,
            key = { "sig_${it.id}" }
        ) { sig ->
            val currentLivePrice = livePriceMap[sig.instId]
                ?: livePriceMap[sig.instId.removeSuffix("-SWAP")]
                ?: sig.spotPriceAtSignal

            SavedAiSignalTrackerCard(
                signal = sig,
                currentLivePrice = currentLivePrice,
                onInspect = { onInspectCoin(sig.instId) },
                onDelete = { onDeleteSignal(sig.id) }
            )
        }

        // 3. Price Alerts Manager Card
        item {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = WarningAmber
                        )
                        Text(
                            text = "ئاگادارکەرەوەی ئاستی نرخ (OKX Price Alerts)",
                            style = MaterialTheme.typography.titleMedium,
                            color = WarningAmber
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = alertPairInput,
                            onValueChange = { alertPairInput = it.uppercase(Locale.US) },
                            label = { Text("دراو (نموونە BTC-USDT)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarningAmber,
                                unfocusedBorderColor = ObsidianCardBorder
                            )
                        )
                        OutlinedTextField(
                            value = alertPriceInput,
                            onValueChange = { alertPriceInput = it },
                            label = { Text("نرخی ئامانج (USDT)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarningAmber,
                                unfocusedBorderColor = ObsidianCardBorder
                            )
                        )
                    }

                    OutlinedTextField(
                        value = alertNoteInput,
                        onValueChange = { alertNoteInput = it },
                        label = { Text("تێبینی (ئارەزوومەندانە)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarningAmber,
                            unfocusedBorderColor = ObsidianCardBorder
                        )
                    )

                    Button(
                        onClick = {
                            val targetPx = alertPriceInput.toDoubleOrNull()
                            if (alertPairInput.isNotBlank() && targetPx != null && targetPx > 0) {
                                onAddPriceAlert(alertPairInput.trim(), targetPx, alertNoteInput.trim())
                                alertNoteInput = ""
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_create_price_alert"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarningAmber,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = "زیادکردنی ئاگادارکەرەوەی نرخ",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    priceAlerts.forEach { alert ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianSurfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${alert.instId} • ئامانج: $${QuantMathEngine.formatCryptoPrice(alert.targetPrice)}",
                                    fontFamily = MonoNumberFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (alert.isTriggered) BullishGreen else TextPrimary
                                )
                                Text(
                                    text = if (alert.isTriggered) "گەیشتە ئامانج! (${alert.noteKu})" else alert.noteKu,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = if (alert.isTriggered) BullishGreen else TextSecondary
                                )
                            }
                            IconButton(onClick = { onDeletePriceAlert(alert.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "سڕینەوە",
                                    tint = BearishRed
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
private fun SavedAiSignalTrackerCard(
    signal: SavedAiSignalEntity,
    currentLivePrice: Double,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val isLong = signal.signalDirection.contains("LONG")
    val rawMovePct = if (signal.spotPriceAtSignal > 0) {
        ((currentLivePrice - signal.spotPriceAtSignal) / signal.spotPriceAtSignal) * 100.0
    } else 0.0
    val signedPnlPct = if (isLong) rawMovePct else -rawMovePct

    val statusTextKu = when {
        isLong && currentLivePrice >= signal.takeProfit3 -> "گەیشتە ئامانجی سێیەم (TP3 Hit!)"
        isLong && currentLivePrice >= signal.takeProfit2 -> "گەیشتە ئامانجی دووەم (TP2 Hit!)"
        isLong && currentLivePrice >= signal.takeProfit1 -> "گەیشتە ئامانجی یەکەم (TP1 Hit!)"
        isLong && currentLivePrice <= signal.stopLoss -> "گەیشتە ستۆپ لۆس (SL Hit)"
        !isLong && currentLivePrice <= signal.takeProfit3 -> "گەیشتە ئامانجی سێیەم (TP3 Hit!)"
        !isLong && currentLivePrice <= signal.takeProfit2 -> "گەیشتە ئامانجی دووەم (TP2 Hit!)"
        !isLong && currentLivePrice <= signal.takeProfit1 -> "گەیشتە ئامانجی یەکەم (TP1 Hit!)"
        !isLong && currentLivePrice >= signal.stopLoss -> "گەیشتە ستۆپ لۆس (SL Hit)"
        signedPnlPct >= 0 -> "لە قازانجدایە (Active In Profit)"
        else -> "چالاکە (Active Tracking)"
    }

    val pnlColor = if (signedPnlPct >= 0) BullishGreen else BearishRed

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, pnlColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable(onClick = onInspect)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${signal.instId} • ${signal.signalVerdictKu}",
                        style = MaterialTheme.typography.titleMedium,
                        color = pnlColor
                    )
                    Text(
                        text = "$statusTextKu • دڵنیایی AI: ${signal.confidencePercent}%",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = QuantMathEngine.formatSignedPercent(signedPnlPct),
                        fontFamily = MonoNumberFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = pnlColor
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "سڕینەوەی سیگناڵ",
                            tint = TextMuted
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TrackerNumberCol("نرخی سیگناڵ", QuantMathEngine.formatCryptoPrice(signal.spotPriceAtSignal), TextSecondary)
                TrackerNumberCol("نرخی ئێستای OKX", QuantMathEngine.formatCryptoPrice(currentLivePrice), AiCyan)
                TrackerNumberCol("ئامانج TP1", QuantMathEngine.formatCryptoPrice(signal.takeProfit1), BullishGreen)
                TrackerNumberCol("ئامانج TP2", QuantMathEngine.formatCryptoPrice(signal.takeProfit2), BullishGreen)
                TrackerNumberCol("ستۆپ لۆس SL", QuantMathEngine.formatCryptoPrice(signal.stopLoss), BearishRed)
            }
        }
    }
}

@Composable
private fun TrackerNumberCol(
    label: String,
    value: String,
    color: Color
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
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = color
        )
    }
}

@Composable
private fun OkxFuturesRiskCalculatorCard(
    defaultEntryPrice: Double
) {
    var isLongPosition by remember { mutableStateOf(true) }
    var marginUsdtInput by remember { mutableStateOf("100") }
    var leverageSlider by remember { mutableFloatStateOf(5f) }
    var entryPxInput by remember(defaultEntryPrice) {
        mutableStateOf(
            if (defaultEntryPrice > 0) QuantMathEngine.formatCryptoPrice(defaultEntryPrice).replace(",", "")
            else "65000"
        )
    }
    var tpPxInput by remember(defaultEntryPrice) {
        mutableStateOf(
            if (defaultEntryPrice > 0) QuantMathEngine.formatCryptoPrice(defaultEntryPrice * 1.03).replace(",", "")
            else "67000"
        )
    }
    var slPxInput by remember(defaultEntryPrice) {
        mutableStateOf(
            if (defaultEntryPrice > 0) QuantMathEngine.formatCryptoPrice(defaultEntryPrice * 0.985).replace(",", "")
            else "64000"
        )
    }

    val marginUsdt = marginUsdtInput.toDoubleOrNull()?.coerceAtLeast(1.0) ?: 100.0
    val leverage = leverageSlider.toInt().coerceIn(1, 100)
    val entryPx = entryPxInput.toDoubleOrNull()?.coerceAtLeast(1e-8) ?: 1.0
    val tpPx = tpPxInput.toDoubleOrNull()?.coerceAtLeast(1e-8) ?: entryPx
    val slPx = slPxInput.toDoubleOrNull()?.coerceAtLeast(1e-8) ?: entryPx

    val notionalPositionUsdt = marginUsdt * leverage
    val coinQuantity = notionalPositionUsdt / entryPx
    val maintenanceMarginRate = 0.005 // Standard OKX 0.5% MMR
    val liquidationPrice = if (isLongPosition) {
        max(0.0, entryPx * (1.0 - (1.0 / leverage) + maintenanceMarginRate))
    } else {
        entryPx * (1.0 + (1.0 / leverage) - maintenanceMarginRate)
    }

    // OKX Futures Taker Fee (0.05% entry + 0.05% exit = 0.10% round-trip notional)
    val roundTripFeeUsdt = notionalPositionUsdt * 0.001

    val rawProfitUsdt = if (isLongPosition) {
        (tpPx - entryPx) * coinQuantity
    } else {
        (entryPx - tpPx) * coinQuantity
    }
    val netProfitUsdt = rawProfitUsdt - roundTripFeeUsdt
    val roeProfitPercent = (netProfitUsdt / marginUsdt) * 100.0

    val rawLossUsdt = if (isLongPosition) {
        (slPx - entryPx) * coinQuantity
    } else {
        (entryPx - slPx) * coinQuantity
    }
    val netLossUsdt = rawLossUsdt - roundTripFeeUsdt
    val roeLossPercent = (netLossUsdt / marginUsdt) * 100.0

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = OkxNeonLime
                )
                Column {
                    Text(
                        text = "بژمێری وردی گرێبەست و نرخی لیکویدەیشن لە OKX",
                        style = MaterialTheme.typography.titleMedium,
                        color = OkxNeonLime
                    )
                    Text(
                        text = "حسابکردنی قەبارەی گرێبەست، کارمزد (0.05%)، نرخی سووتان، و قازانج/زیان",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }
            }

            // Long / Short Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isLongPosition) BullishGreen else ObsidianSurfaceVariant)
                        .clickable { isLongPosition = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "کڕین / بەرزبوونەوە (LONG)",
                        fontWeight = FontWeight.Bold,
                        color = if (isLongPosition) Color.Black else TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isLongPosition) BearishRed else ObsidianSurfaceVariant)
                        .clickable { isLongPosition = false }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "فرۆشتن / دابەزین (SHORT)",
                        fontWeight = FontWeight.Bold,
                        color = if (!isLongPosition) Color.White else TextSecondary
                    )
                }
            }

            // Leverage Slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ئاستی لیڤرێج (Leverage)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${leverage}x",
                        fontFamily = MonoNumberFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = OkxNeonLime
                    )
                }
                Slider(
                    value = leverageSlider,
                    onValueChange = { leverageSlider = it },
                    valueRange = 1f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = OkxNeonLime,
                        activeTrackColor = OkxNeonLime
                    )
                )
            }

            // Inputs Row 1: Margin & Entry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = marginUsdtInput,
                    onValueChange = { marginUsdtInput = it },
                    label = { Text("سەرمایەی مارجن (USDT)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = entryPxInput,
                    onValueChange = { entryPxInput = it },
                    label = { Text("نرخی چوونەژوورەوە") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Inputs Row 2: Take Profit & Stop Loss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = tpPxInput,
                    onValueChange = { tpPxInput = it },
                    label = { Text("نرخی ئامانج (TP)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = slPxInput,
                    onValueChange = { slPxInput = it },
                    label = { Text("نرخی ستۆپ لۆس (SL)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Results Matrix
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TrackerNumberCol(
                        label = "قەبارەی گرێبەست",
                        value = QuantMathEngine.formatCompactUsdt(notionalPositionUsdt),
                        color = AiCyan
                    )
                    TrackerNumberCol(
                        label = "نرخی لیکویدەیشن (سووتان)",
                        value = "$${QuantMathEngine.formatCryptoPrice(liquidationPrice)}",
                        color = WarningAmber
                    )
                    TrackerNumberCol(
                        label = "کارمزدی OKX",
                        value = String.format(Locale.US, "$%.2f", roundTripFeeUsdt),
                        color = TextSecondary
                    )
                }

                HorizontalDivider(color = ObsidianCardBorder)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "قازانجی صافی لە TP (پاش کارمزد)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%+.2f USDT (%+.1f%% ROE)", netProfitUsdt, roeProfitPercent),
                            fontFamily = MonoNumberFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BullishGreen
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "زیانی صافی لە SL (پاش کارمزد)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                        Text(
                            text = String.format(Locale.US, "%+.2f USDT (%+.1f%% ROE)", netLossUsdt, roeLossPercent),
                            fontFamily = MonoNumberFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = BearishRed
                        )
                    }
                }
            }
        }
    }
}
