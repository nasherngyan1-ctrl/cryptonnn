package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.OkxTradingDao
import com.example.data.model.AiMarketOpportunityItem
import com.example.data.model.AiScreenshotExitPlan
import com.example.data.model.AiTradingPrediction
import com.example.data.model.OkxInstrumentDeepTelemetry
import com.example.data.model.OkxMarketTicker
import com.example.domain.QuantMathEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val TAG = "OkxGeminiAiService"

/**
 * 100% Direct API AI Engine (No Firebase dependency).
 * Routes all requests through MultiKeyAiRouter (rotating across user-entered API keys
 * for Gemini, Groq, OpenRouter, OpenAI, and DeepSeek) with a real-time OKX Multi-Timeframe
 * Quantitative Synthesis fallback when no external key is active.
 */
class OkxGeminiAiService(
    dao: OkxTradingDao
) {
    val keyRouter = MultiKeyAiRouter(dao)

    suspend fun detectCoinSymbolFromScreenshot(
        bitmap: Bitmap,
        userNoteKu: String,
        fallbackInstId: String,
        onKeySwitch: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val prompt = """
            Extract the cryptocurrency symbol (e.g., BTC, ETH, SOL, TON, XRP, DOGE, PEPE, SUI, BNB, ADA, AVAX, LINK) from this trading/wallet screenshot and optional user note: '$userNoteKu'.
            If no symbol is visible, use the symbol from '$fallbackInstId'.
            Return JSON with keys: {"baseSymbol": "SOL", "isSwapOrFutures": false, "detectedBuyPrice": 0.0, "detectedInvestedUsdt": 100.0}
        """.trimIndent()

        // 1. Try User Rotating API Keys
        val rotatedResult = runCatching {
            keyRouter.generateWithAutoRotation(
                prompt = prompt,
                expectJson = true,
                bitmap = bitmap,
                onKeySwitchCallback = onKeySwitch
            )
        }.getOrNull()

        if (rotatedResult != null) {
            val parsed = parseDetectedSymbolJson(rotatedResult.responseText, fallbackInstId)
            if (parsed.isNotBlank()) return@withContext parsed
        }

        // 2. Fallback: extract ticker mentioned in user note or use currently selected OKX instrument
        val noteUpper = userNoteKu.uppercase(Locale.US)
        val knownCoins = listOf(
            "BTC", "ETH", "SOL", "TON", "XRP", "DOGE", "PEPE", "SUI",
            "BNB", "ADA", "AVAX", "LINK", "SHIB", "TRX", "NEAR", "ARB", "OP", "APT"
        )
        val matchedFromNote = knownCoins.find { noteUpper.contains(it) }
        if (matchedFromNote != null) "$matchedFromNote-USDT" else fallbackInstId
    }

    private fun parseDetectedSymbolJson(rawJson: String, fallbackInstId: String): String {
        return runCatching {
            val json = JSONObject(keyRouter.extractCleanJson(rawJson))
            val rawBase = json.optString("baseSymbol", "")
                .uppercase(Locale.US)
                .replace("-USDT", "")
                .replace("-SWAP", "")
                .replace("USDT", "")
                .trim()
            val isSwap = json.optBoolean("isSwapOrFutures", false)
            if (rawBase.length in 2..10 && rawBase.all { it.isLetterOrDigit() }) {
                if (isSwap) "$rawBase-USDT-SWAP" else "$rawBase-USDT"
            } else {
                fallbackInstId
            }
        }.getOrDefault(fallbackInstId)
    }

    suspend fun analyzeTradeScreenshotForExitPlan(
        bitmap: Bitmap,
        userNoteKu: String,
        telemetry: OkxInstrumentDeepTelemetry,
        onKeySwitch: ((String) -> Unit)? = null
    ): AiScreenshotExitPlan = withContext(Dispatchers.IO) {
        val promptText = buildString {
            appendLine("You are an elite institutional OKX trading AI assistant.")
            appendLine("The user has bought a coin (or opened a position) and sent you this screenshot so you can tell them the EXACT price at which to sell it for maximum realistic profit ('لەوەندە بیفرۆشە قازانجتە').")
            if (userNoteKu.isNotBlank()) {
                appendLine("User's additional note: \"$userNoteKu\"")
            }
            appendLine("\n1. Read the screenshot carefully to extract:")
            appendLine("   - The coin/pair bought")
            appendLine("   - Their buy/entry price (if visible in the screenshot or user note; if not visible, use the current live OKX price ${telemetry.ticker.lastPrice})")
            appendLine("   - Their invested amount in USDT or coin quantity (if visible; otherwise assume 100.0 USDT for profit calculation)")
            appendLine("   - Whether it is Spot, Long, or Short.")
            appendLine("\n2. Cross-reference their position with the following REAL-TIME OKX v5 Market & Order Book Telemetry for ${telemetry.ticker.instId}:")
            appendLine(buildTelemetryPrompt(telemetry))
            appendLine("\n3. Return a valid JSON object with EXACTLY these keys (all text in fluent Central Kurdish - سۆرانی):")
            appendLine(
                """
                {
                  "detectedInstId": "${telemetry.ticker.instId}",
                  "positionTypeKu": "کڕینی سپۆت (Spot)",
                  "detectedBuyPrice": ${telemetry.ticker.lastPrice},
                  "detectedInvestedUsdt": 100.0,
                  "currentStatusSummaryKu": "...",
                  "urgentActionVerdictKu": "لە نرخی $... بیفرۆشە قازانجی سەرەکیتە!",
                  "confidencePercent": 88,
                  "goldenExitPrice": 0.0,
                  "goldenExitProfitPercent": 0.0,
                  "goldenExitEstimatedProfitUsdt": 0.0,
                  "sellTarget1Price": 0.0,
                  "sellTarget1ProfitPercent": 0.0,
                  "sellTarget1AllocationPercent": 40,
                  "sellTarget2Price": 0.0,
                  "sellTarget2ProfitPercent": 0.0,
                  "sellTarget2AllocationPercent": 40,
                  "sellTarget3Price": 0.0,
                  "sellTarget3ProfitPercent": 0.0,
                  "sellTarget3AllocationPercent": 20,
                  "emergencyStopLossPrice": 0.0,
                  "holdDurationEstimateKu": "...",
                  "whaleResistanceWallNoteKu": "...",
                  "detailedSellStrategyKu": "..."
                }
                """.trimIndent()
            )
        }

        // 1. Execute via User's Rotating API Keys Pool
        val rotatedResult = runCatching {
            keyRouter.generateWithAutoRotation(
                prompt = promptText,
                expectJson = true,
                bitmap = bitmap,
                onKeySwitchCallback = onKeySwitch
            )
        }.getOrNull()

        if (rotatedResult != null) {
            val plan = runCatching {
                parseScreenshotExitPlanJson(
                    defaultInstId = telemetry.ticker.instId,
                    liveOkxPrice = telemetry.ticker.lastPrice,
                    rawJson = rotatedResult.responseText
                )
            }.getOrNull()
            if (plan != null) return@withContext plan
        }

        // 2. Fallback: Live OKX OrderBook & Pivot Exit Engine
        Log.i(TAG, "Using deterministic OKX OrderBook & Pivot Exit Engine")
        buildQuantFallbackScreenshotExitPlan(telemetry, userNoteKu)
    }

    suspend fun generateDeepInstrumentPrediction(
        telemetry: OkxInstrumentDeepTelemetry,
        onKeySwitch: ((String) -> Unit)? = null
    ): Pair<AiTradingPrediction, String> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine(buildTelemetryPrompt(telemetry))
            appendLine("\nReturn ONLY a JSON object with these exact keys (all text in fluent Central Kurdish - سۆرانی):")
            appendLine(
                """
                {
                  "signalDirection": "STRONG_LONG | LONG | NEUTRAL | SHORT | STRONG_SHORT",
                  "signalVerdictKu": "...",
                  "confidencePercent": 85,
                  "riskLevelKu": "نزم / مامناوەند / بەرز",
                  "recommendedLeverage": "3x - 5x",
                  "entryZoneMin": 0.0,
                  "entryZoneMax": 0.0,
                  "takeProfit1": 0.0,
                  "takeProfit2": 0.0,
                  "takeProfit3": 0.0,
                  "stopLoss": 0.0,
                  "riskRewardRatio": 2.8,
                  "forecast1hPrice": 0.0,
                  "forecast4hPrice": 0.0,
                  "forecast24hPrice": 0.0,
                  "winProbabilityPercent": 82,
                  "multiTimeframeAlignmentKu": "...",
                  "whaleOrderBookSummaryKu": "...",
                  "keyNumericTriggersKu": ["...", "...", "...", "..."],
                  "detailedKurdishAnalysis": "..."
                }
                """.trimIndent()
            )
        }

        // 1. Execute via User's Rotating API Keys Pool
        val rotatedResult = runCatching {
            keyRouter.generateWithAutoRotation(
                prompt = prompt,
                expectJson = true,
                bitmap = null,
                onKeySwitchCallback = onKeySwitch
            )
        }.getOrNull()

        if (rotatedResult != null) {
            val parsed = runCatching {
                parsePredictionJson(
                    instId = telemetry.ticker.instId,
                    spotPrice = telemetry.ticker.lastPrice,
                    rawJson = rotatedResult.responseText
                )
            }.getOrNull()
            if (parsed != null) {
                val engineBadge = "کلیلی چالاک: ${rotatedResult.usedKeyLabelKu} (${rotatedResult.usedProvider})"
                return@withContext parsed to engineBadge
            }
        }

        // 2. Fallback: Live OKX Multi-Timeframe Quantitative Synthesis
        buildQuantFallbackPrediction(telemetry) to "بزوێنەری ژمارەیی ڕاستەوخۆی OKX (کلیلی API داخڵ بکە)"
    }

    suspend fun scanTopMarketOpportunities(
        tickers: List<OkxMarketTicker>,
        activeDeepTelemetry: OkxInstrumentDeepTelemetry?,
        onKeySwitch: ((String) -> Unit)? = null
    ): Pair<String, List<AiMarketOpportunityItem>> = withContext(Dispatchers.IO) {
        val topCandidates = tickers
            .sortedByDescending { it.volume24hUsdt }
            .take(18)

        val payloadBuilder = StringBuilder()
        payloadBuilder.appendLine("You are an elite institutional quantitative crypto analyst for OKX exchange.")
        payloadBuilder.appendLine("Analyze the following REAL-TIME OKX market telemetry and select the top 4 highest-probability trading setups (LONG or SHORT).")
        payloadBuilder.appendLine("Write ALL explanations and labels in fluent, professional Central Kurdish (Sorani - کوردیی ناوەندی) using exact numerical figures from the telemetry.")
        payloadBuilder.appendLine("\n--- LIVE OKX TICKERS SNAPSHOT ---")
        for (t in topCandidates) {
            payloadBuilder.appendLine(
                String.format(
                    Locale.US,
                    "• %s | Last: %s | 24hChg: %.2f%% | 24hHigh: %s | 24hLow: %s | RangePos: %.1f%% | 24hVolatility: %.2f%% | 24hVolUSDT: %s | SpreadBps: %.2f",
                    t.instId,
                    QuantMathEngine.formatCryptoPrice(t.lastPrice),
                    t.change24hPercent,
                    QuantMathEngine.formatCryptoPrice(t.high24h),
                    QuantMathEngine.formatCryptoPrice(t.low24h),
                    t.range24hPercent,
                    t.volatility24hPercent,
                    QuantMathEngine.formatCompactUsdt(t.volume24hUsdt),
                    t.spreadBps
                )
            )
        }
        if (activeDeepTelemetry != null) {
            payloadBuilder.appendLine("\n--- ACTIVE INSTRUMENT DEEP TELEMETRY (${activeDeepTelemetry.ticker.instId}) ---")
            payloadBuilder.appendLine(buildTelemetryPrompt(activeDeepTelemetry))
        }
        payloadBuilder.appendLine(
            """
            Return ONLY valid JSON with this structure:
            {
              "marketRegimeSummaryKu": "...",
              "opportunities": [
                {
                  "instId": "BTC-USDT",
                  "currentPrice": 0.0,
                  "change24hPercent": 0.0,
                  "actionDirection": "LONG",
                  "actionLabelKu": "کڕینی بەهێز (Long)",
                  "confidencePercent": 86,
                  "entryPrice": 0.0,
                  "targetPrice": 0.0,
                  "stopLossPrice": 0.0,
                  "expectedMovePercent": 3.5,
                  "keyReasonKu": "..."
                }
              ]
            }
            """.trimIndent()
        )

        // 1. Execute via User's Rotating API Keys Pool
        val rotatedResult = runCatching {
            keyRouter.generateWithAutoRotation(
                prompt = payloadBuilder.toString(),
                expectJson = true,
                bitmap = null,
                onKeySwitchCallback = onKeySwitch
            )
        }.getOrNull()

        if (rotatedResult != null) {
            val parsed = runCatching { parseScannerJson(rotatedResult.responseText) }.getOrNull()
            if (parsed != null && parsed.second.isNotEmpty()) {
                return@withContext parsed
            }
        }

        // 2. Fallback: Live OKX Market Scanner
        buildQuantFallbackScanner(topCandidates)
    }

    private fun parseScannerJson(rawJson: String): Pair<String, List<AiMarketOpportunityItem>> {
        val root = JSONObject(keyRouter.extractCleanJson(rawJson))
        val regimeKu = root.optString("marketRegimeSummaryKu", "شیکاری گشتی بازاڕی OKX")
        val arr = root.optJSONArray("opportunities")
        val list = mutableListOf<AiMarketOpportunityItem>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val item = arr.getJSONObject(i)
                list.add(
                    AiMarketOpportunityItem(
                        instId = item.optString("instId", "BTC-USDT"),
                        currentPrice = item.optDouble("currentPrice", 0.0),
                        change24hPercent = item.optDouble("change24hPercent", 0.0),
                        actionDirection = item.optString("actionDirection", "LONG"),
                        actionLabelKu = item.optString("actionLabelKu", "کڕین (Long)"),
                        confidencePercent = item.optInt("confidencePercent", 80).coerceIn(1, 99),
                        entryPrice = item.optDouble("entryPrice", 0.0),
                        targetPrice = item.optDouble("targetPrice", 0.0),
                        stopLossPrice = item.optDouble("stopLossPrice", 0.0),
                        expectedMovePercent = item.optDouble("expectedMovePercent", 0.0),
                        keyReasonKu = item.optString("keyReasonKu", "")
                    )
                )
            }
        }
        return regimeKu to list
    }

    fun streamAdvisorResponse(
        userQuestionKu: String,
        attachedBitmap: Bitmap? = null,
        activeTelemetry: OkxInstrumentDeepTelemetry?,
        topTickers: List<OkxMarketTicker>,
        onKeySwitch: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val contextPrompt = buildString {
            appendLine("تۆ پسپۆڕێکی باڵای شیکاری ژمارەیی و زیرەکی دەستکردیت بۆ بازاڕی ترەیدینگی OKX (OKX Kurd AI Pro).")
            appendLine("هەمیشە بە زمانی کوردی (سۆرانی)ی زۆر ڕوون، زانستی، و پڕ لە ژمارەی ورد وەڵام بدەرەوە.")
            if (attachedBitmap != null) {
                appendLine("بەکارهێنەر وێنەیەکی (Screenshot) هاوپێچ کردووە. هەموو ژمارەکانی ناو وێنەکە بخوێنەرەوە و بەراوردی بکە لەگەڵ نرخی زیندووی ئێستای OKX، و پێی بڵێ لە چ نرخێکدا بیفرۆشێت قازانجیەتی!")
            }
            appendLine("پشت بەم داتا ڕاستەقینانەی ئێستای بازاڕی OKX ببەستە کە لە خوارەوە دراون:")
            if (activeTelemetry != null) {
                appendLine("\n=== داتای وردی دراوی هەڵبژێردراو (${activeTelemetry.ticker.instId}) ===")
                appendLine(buildTelemetryPrompt(activeTelemetry))
            }
            appendLine("\n=== پوختەی نرخی دراوە سەرەکییەکانی ئێستای OKX ===")
            topTickers.take(12).forEach { t ->
                appendLine(
                    "• ${t.instId}: نرخ=${QuantMathEngine.formatCryptoPrice(t.lastPrice)} USDT | گۆڕانکاری ٢٤س=${QuantMathEngine.formatSignedPercent(t.change24hPercent)} | بەرزترین=${QuantMathEngine.formatCryptoPrice(t.high24h)} | نزمترین=${QuantMathEngine.formatCryptoPrice(t.low24h)} | قەبارە=${QuantMathEngine.formatCompactUsdt(t.volume24hUsdt)}"
                )
            }
            appendLine("\nپرسیاری بازرگان:")
            appendLine(userQuestionKu)
            appendLine("\nلە وەڵامەکەتدا: خاڵی وردی فرۆشتن بۆ قازانج (Take-Profit 1, 2, 3)، خاڵی چوونەژوورەوە (Entry)، وەستانی زیان (Stop-Loss)، و ژمارە تەکنیکییەکان بە وردی بنووسە.")
        }

        // 1. Execute via User's Rotating API Keys Pool
        val rotatedResult = withContext(Dispatchers.IO) {
            runCatching {
                keyRouter.generateWithAutoRotation(
                    prompt = contextPrompt,
                    expectJson = false,
                    bitmap = attachedBitmap,
                    onKeySwitchCallback = onKeySwitch
                )
            }.getOrNull()
        }

        if (rotatedResult != null && rotatedResult.responseText.isNotBlank()) {
            emit("[بەکارهێنراو: ${rotatedResult.usedKeyLabelKu} (${rotatedResult.usedProvider})]\n\n${rotatedResult.responseText}")
            return@flow
        }

        // 2. Fallback: Live OKX Quantitative Advisor synthesis
        emit(buildQuantFallbackAdvisorAnswer(userQuestionKu, activeTelemetry, topTickers))
    }

    private fun buildQuantFallbackPrediction(
        telemetry: OkxInstrumentDeepTelemetry
    ): AiTradingPrediction {
        val t = telemetry.ticker
        val px = t.lastPrice
        val tf1h = telemetry.timeframeMetrics["1H"] ?: telemetry.timeframeMetrics.values.firstOrNull()
        val atr = tf1h?.atr14?.takeIf { it > 0 } ?: (px * 0.018)
        val score = telemetry.compositeQuantScore

        val isBull = score >= 0
        val direction = when {
            score >= 45 -> "STRONG_LONG"
            score >= 12 -> "LONG"
            score <= -45 -> "STRONG_SHORT"
            score <= -12 -> "SHORT"
            else -> if (t.change24hPercent >= 0) "LONG" else "SHORT"
        }
        val verdictKu = when (direction) {
            "STRONG_LONG" -> "کڕینی بەهێز (Strong Long)"
            "LONG" -> "کڕین / بەرزبوونەوە (Long)"
            "STRONG_SHORT" -> "فرۆشتنی بەهێز (Strong Short)"
            "SHORT" -> "فرۆشتن / دابەزین (Short)"
            else -> "چاوەڕوانی / کۆکردنەوە (Neutral)"
        }

        val entryMin = if (isBull) px - (atr * 0.35) else px - (atr * 0.15)
        val entryMax = if (isBull) px + (atr * 0.15) else px + (atr * 0.35)

        val whaleAskWall = telemetry.orderBookReport?.topResistanceAskWalls?.firstOrNull()?.price
        val whaleBidWall = telemetry.orderBookReport?.topSupportBidWalls?.firstOrNull()?.price

        val tp1 = if (isBull) {
            val r1 = tf1h?.resistance1 ?: (px + atr * 1.2)
            max(px * 1.008, min(r1, px + atr * 1.3))
        } else {
            val s1 = tf1h?.support1 ?: (px - atr * 1.2)
            min(px * 0.992, max(s1, px - atr * 1.3))
        }
        val tp2 = if (isBull) px + (atr * 2.4) else max(0.0, px - (atr * 2.4))
        val tp3 = if (isBull) px + (atr * 3.8) else max(0.0, px - (atr * 3.8))
        val sl = if (isBull) max(0.0, px - (atr * 1.15)) else px + (atr * 1.15)

        val riskDist = abs(px - sl).coerceAtLeast(1e-8)
        val rewardDist = abs(tp2 - px)
        val rr = (rewardDist / riskDist).coerceIn(1.2, 6.0)
        val confidence = (68 + (abs(score) / 4)).coerceIn(65, 94)

        val triggers = buildList {
            tf1h?.let {
                add("لەسەر تایمفرەیمی 1H نیشاندەری RSI(14) لە ئاستی ${String.format(Locale.US, "%.1f", it.rsi14)} دایە و میانەی جووڵاو EMA20 بریتییە لە $${QuantMathEngine.formatCryptoPrice(it.ema20)}.")
                add("ئاستی ناجێگیری ڕاستەقینە ATR(14) بریتییە لە $${QuantMathEngine.formatCryptoPrice(it.atr14)} (${String.format(Locale.US, "%.2f%%", it.atrPercent)}) و نرخی میانەی قەبارەیی VWAP لە $${QuantMathEngine.formatCryptoPrice(it.vwap)} جێگیر بووە.")
                add("ئاستی پاڵپشتی یەکەم (S1): $${QuantMathEngine.formatCryptoPrice(it.support1)} | ئاستی بەرگری یەکەم (R1): $${QuantMathEngine.formatCryptoPrice(it.resistance1)} | زێڕینی فیبۆناچی (0.618): $${QuantMathEngine.formatCryptoPrice(it.fib618)}.")
            }
            telemetry.orderBookReport?.let { ob ->
                add("لە ٥٠ ئاستی Order Book ـی OKX، هێزی کڕیاران ${String.format(Locale.US, "%.1f%%", ob.bidPressurePercent)} (${QuantMathEngine.formatCompactUsdt(ob.totalBidNotionalUsdt)}) ـە بەرامبەر ${String.format(Locale.US, "%.1f%%", ob.askPressurePercent)} فرۆشیاران.")
            }
            if (whaleAskWall != null && whaleBidWall != null) {
                add("بەهێزترین دیواری کڕینی نەهەنگ لە $${QuantMathEngine.formatCryptoPrice(whaleBidWall)} و دیواری فرۆشتن لە $${QuantMathEngine.formatCryptoPrice(whaleAskWall)} دانراوە.")
            }
        }

        return AiTradingPrediction(
            instId = t.instId,
            spotPriceAtAnalysis = px,
            signalDirection = direction,
            signalVerdictKu = verdictKu,
            confidencePercent = confidence,
            riskLevelKu = if (t.volatility24hPercent > 8.0) "بەرز (High Volatility)" else "مامناوەند (Controlled)",
            recommendedLeverage = if (t.volatility24hPercent > 8.0) "2x - 3x (Isolated)" else "3x - 5x (Isolated)",
            entryZoneMin = min(entryMin, entryMax),
            entryZoneMax = max(entryMin, entryMax),
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            takeProfit3 = tp3,
            stopLoss = sl,
            riskRewardRatio = rr,
            forecast1hPrice = if (isBull) px + atr * 0.65 else max(0.0, px - atr * 0.65),
            forecast4hPrice = if (isBull) px + atr * 1.55 else max(0.0, px - atr * 1.55),
            forecast24hPrice = if (isBull) px + atr * 2.85 else max(0.0, px - atr * 2.85),
            winProbabilityPercent = (confidence - 3).coerceIn(60, 92),
            multiTimeframeAlignmentKu = "کۆی خاڵی هاوتابوونی تایمفرەیمەکان (15m, 1H, 4H, 1D) بریتییە لە $score لە 100 (${telemetry.compositeVerdictKu}).",
            whaleOrderBookSummaryKu = telemetry.orderBookReport?.dominantSideSummaryKu
                ?: "هاوسەنگی لە داواکارییەکانی کڕین و فرۆشتنی OKX بەدی دەکرێت.",
            keyNumericTriggersKu = triggers,
            detailedKurdishAnalysis = "بەپێی شیکاری ژمارەیی ڕاستەوخۆی بازاڕی OKX بۆ ${t.instId} لە نرخی $${QuantMathEngine.formatCryptoPrice(px)}، باشترین ناوچەی چوونەژوورەوە لە نێوان $${QuantMathEngine.formatCryptoPrice(min(entryMin, entryMax))} تا $${QuantMathEngine.formatCryptoPrice(max(entryMin, entryMax))} ـە. بۆ وەرگرتنی قازانجی مسۆگەر، قۆناغی یەکەم لە $${QuantMathEngine.formatCryptoPrice(tp1)} و قۆناغی سەرەکی لە $${QuantMathEngine.formatCryptoPrice(tp2)} دابنێ، لەگەڵ دانانی فەرمانی وەستانی زیان (Stop-Loss) لە $${QuantMathEngine.formatCryptoPrice(sl)}. (بۆ چالاککردنی شیکاری فرە-کلیلی زیرەکی دەستکرد، لە سەرەوەی شاشەکە کرتە لە «کلیل» بکە و کلیلەکانت داخڵ بکە)."
        )
    }

    private fun buildQuantFallbackScreenshotExitPlan(
        telemetry: OkxInstrumentDeepTelemetry,
        userNoteKu: String
    ): AiScreenshotExitPlan {
        val t = telemetry.ticker
        val livePx = t.lastPrice
        val tf1h = telemetry.timeframeMetrics["1H"] ?: telemetry.timeframeMetrics.values.firstOrNull()
        val atr = tf1h?.atr14?.takeIf { it > 0 } ?: (livePx * 0.02)

        val numbersInNote = Regex("""\d+(\.\d+)?""").findAll(userNoteKu).mapNotNull { it.value.toDoubleOrNull() }.toList()
        val buyPx = numbersInNote.find { abs(it - livePx) / livePx < 0.45 } ?: livePx
        val investedUsdt = numbersInNote.find { it != buyPx && it >= 5.0 } ?: 100.0

        val whaleResistance = telemetry.orderBookReport?.topResistanceAskWalls
            ?.filter { it.price > livePx * 1.003 }
            ?.minByOrNull { it.price }
            ?.price

        val r1 = tf1h?.resistance1?.takeIf { it > livePx * 1.005 } ?: (livePx + atr * 1.35)
        val r2 = tf1h?.resistance2?.takeIf { it > r1 } ?: (livePx + atr * 2.4)
        val r3 = tf1h?.resistance3?.takeIf { it > r2 } ?: (livePx + atr * 3.8)

        val rawGoldenTarget = if (whaleResistance != null && whaleResistance > livePx * 1.008) {
            min(r2, whaleResistance * 0.9985)
        } else {
            r2 * 0.9985
        }
        val goldenExitPx = max(livePx * 1.015, rawGoldenTarget)
        val tp1Px = max(livePx * 1.009, min(r1 * 0.999, goldenExitPx * 0.992))
        val tp2Px = goldenExitPx
        val tp3Px = max(goldenExitPx * 1.018, r3 * 0.998)
        val slPx = max(0.0, min(buyPx, livePx) - (atr * 1.15))

        val baseRef = if (buyPx > 0) buyPx else livePx
        val goldenProfitPct = ((goldenExitPx - baseRef) / baseRef) * 100.0
        val tp1ProfitPct = ((tp1Px - baseRef) / baseRef) * 100.0
        val tp2ProfitPct = ((tp2Px - baseRef) / baseRef) * 100.0
        val tp3ProfitPct = ((tp3Px - baseRef) / baseRef) * 100.0
        val goldenProfitUsdt = investedUsdt * (goldenProfitPct / 100.0)
        val currentPnlPct = ((livePx - baseRef) / baseRef) * 100.0

        return AiScreenshotExitPlan(
            detectedInstId = t.instId,
            positionTypeKu = "کڕینی دراو لە OKX (${t.instType})",
            detectedBuyPrice = baseRef,
            currentLiveOkxPrice = livePx,
            detectedInvestedUsdt = investedUsdt,
            currentPnlPercent = currentPnlPct,
            currentStatusSummaryKu = "نرخی زیندووی ئێستای ${t.instId} لە OKX بریتییە لە $${QuantMathEngine.formatCryptoPrice(livePx)} (گۆڕانکاری ٢٤س: ${QuantMathEngine.formatSignedPercent(t.change24hPercent)}).",
            urgentActionVerdictKu = "لە نرخی $${QuantMathEngine.formatCryptoPrice(goldenExitPx)} بیفرۆشە قازانجی سەرەکیتە (${QuantMathEngine.formatSignedPercent(goldenProfitPct)})!",
            confidencePercent = 87,
            goldenExitPrice = goldenExitPx,
            goldenExitProfitPercent = goldenProfitPct,
            goldenExitEstimatedProfitUsdt = goldenProfitUsdt,
            sellTarget1Price = tp1Px,
            sellTarget1ProfitPercent = tp1ProfitPct,
            sellTarget1AllocationPercent = 40,
            sellTarget2Price = tp2Px,
            sellTarget2ProfitPercent = tp2ProfitPct,
            sellTarget2AllocationPercent = 40,
            sellTarget3Price = tp3Px,
            sellTarget3ProfitPercent = tp3ProfitPct,
            sellTarget3AllocationPercent = 20,
            emergencyStopLossPrice = slPx,
            holdDurationEstimateKu = "٢ بۆ ٨ کاتژمێری داهاتوو بەپێی خێرایی جووڵەی ATR",
            whaleResistanceWallNoteKu = if (whaleResistance != null) {
                "دیواری فرۆشتنی گەورەی نەهەنگەکان لە نرخی $${QuantMathEngine.formatCryptoPrice(whaleResistance)} دانراوە؛ بۆیە نرخی فرۆشتنی زێڕین کەمێک لە خوار ئەو دیوارە ($${QuantMathEngine.formatCryptoPrice(goldenExitPx)}) دیاری کراوە تاوەکو فەرمانەکەت بێ دواکەوتن جێبەجێ ببێت."
            } else {
                "نرخی فرۆشتن لەسەر بنەمای ئاستەکانی بەرگری Pivot R1/R2 و مەودای جووڵەی ATR دیاری کراوە."
            },
            detailedSellStrategyKu = "١) لەناو ئەپی OKX بڕۆ سەر بەشی فرۆشتن (Sell -> Limit Order).\n٢) ٤٠٪ی کۆینەکەت لە نرخی $${QuantMathEngine.formatCryptoPrice(tp1Px)} دابنێ بۆ مسۆگەرکردنی قازانجی یەکەم.\n٣) ٤٠٪ی تر لە نرخی زێڕینی $${QuantMathEngine.formatCryptoPrice(tp2Px)} دابنێ.\n٤) ٢٠٪ی کۆتایی لە نرخی $${QuantMathEngine.formatCryptoPrice(tp3Px)} بهێڵەرەوە بۆ ئەگەری شکاندنی بەرگری سەرەوە، و ستۆپ لۆس لە $${QuantMathEngine.formatCryptoPrice(slPx)} جێگیر بکە."
        )
    }

    private fun buildQuantFallbackScanner(
        topCandidates: List<OkxMarketTicker>
    ): Pair<String, List<AiMarketOpportunityItem>> {
        val sortedByMomentum = topCandidates.sortedByDescending {
            abs(it.change24hPercent) * (if (it.volume24hUsdt > 10_000_000) 1.2 else 0.8)
        }.take(4)

        val opportunities = sortedByMomentum.map { t ->
            val isLong = t.range24hPercent >= 45.0 || t.change24hPercent >= 0.0
            val movePct = max(2.2, min(7.5, t.volatility24hPercent * 0.45))
            val targetPx = if (isLong) t.lastPrice * (1.0 + movePct / 100.0) else t.lastPrice * (1.0 - movePct / 100.0)
            val stopPx = if (isLong) t.lastPrice * (1.0 - (movePct * 0.45) / 100.0) else t.lastPrice * (1.0 + (movePct * 0.45) / 100.0)

            AiMarketOpportunityItem(
                instId = t.instId,
                currentPrice = t.lastPrice,
                change24hPercent = t.change24hPercent,
                actionDirection = if (isLong) "LONG" else "SHORT",
                actionLabelKu = if (isLong) "کڕین (Long Setup)" else "فرۆشتن (Short Setup)",
                confidencePercent = (78 + (t.range24hPercent / 10.0).toInt()).coerceIn(74, 92),
                entryPrice = t.lastPrice,
                targetPrice = targetPx,
                stopLossPrice = stopPx,
                expectedMovePercent = if (isLong) movePct else -movePct,
                keyReasonKu = "قەبارەی ٢٤ کاتژمێر لە OKX گەیشتووەتە ${QuantMathEngine.formatCompactUsdt(t.volume24hUsdt)} لەگەڵ ناجێگیری ${String.format(Locale.US, "%.2f%%", t.volatility24hPercent)} و شوێنی نرخ لە مەودای ڕۆژانەدا ${String.format(Locale.US, "%.1f%%", t.range24hPercent)} ـە."
            )
        }

        val advancing = topCandidates.count { it.change24hPercent >= 0 }
        val regimeKu = "لە کۆی ${topCandidates.size} دراوی پڕمامەڵەی OKX، $advancing دراو لە بەرزبوونەوەدان. سیولەی گشتی چڕبووەتەوە لەسەر دراوە سەرەکییەکان."
        return regimeKu to opportunities
    }

    private fun buildQuantFallbackAdvisorAnswer(
        questionKu: String,
        telemetry: OkxInstrumentDeepTelemetry?,
        topTickers: List<OkxMarketTicker>
    ): String {
        val t = telemetry?.ticker ?: topTickers.firstOrNull()
            ?: return "تکایە چاوەڕێ بکە تا داتای بازاڕی OKX بار دەبێت یان لە بەشی «کلیلەکانی API» کلیلێکی نوێ زیاد بکە."
        val pred = telemetry?.let { buildQuantFallbackPrediction(it) }
        return buildString {
            appendLine("📊 شیکاری ژمارەیی ڕاستەوخۆی بازاڕی OKX بۆ ${t.instId}:")
            appendLine("• نرخی ئێستا: $${QuantMathEngine.formatCryptoPrice(t.lastPrice)} USDT (${QuantMathEngine.formatSignedPercent(t.change24hPercent)})")
            appendLine("• مەودای ٢٤ کاتژمێر: نزمترین $${QuantMathEngine.formatCryptoPrice(t.low24h)} — بەرزترین $${QuantMathEngine.formatCryptoPrice(t.high24h)}")
            if (pred != null) {
                appendLine("• بڕیاری تەکنیکی: ${pred.signalVerdictKu} (دڵنیایی: ${pred.confidencePercent}%)")
                appendLine("• باشترین مەودای کڕین/چوونەژوورەوە: $${QuantMathEngine.formatCryptoPrice(pred.entryZoneMin)} تا $${QuantMathEngine.formatCryptoPrice(pred.entryZoneMax)}")
                appendLine("• ئامانجی فرۆشتنی ١ (TP1): $${QuantMathEngine.formatCryptoPrice(pred.takeProfit1)}")
                appendLine("• ئامانجی فرۆشتنی ٢ (TP2 - سەرەکی): $${QuantMathEngine.formatCryptoPrice(pred.takeProfit2)}")
                appendLine("• ئامانجی فرۆشتنی ٣ (TP3): $${QuantMathEngine.formatCryptoPrice(pred.takeProfit3)}")
                appendLine("• وەستانی زیان (Stop-Loss): $${QuantMathEngine.formatCryptoPrice(pred.stopLoss)}")
                appendLine("\n💡 تێبینی: بۆ چالاککردنی وەڵامی مۆدێلە دەرەکییەکان لەڕێگەی API (Gemini / Groq / OpenRouter / OpenAI / DeepSeek)، لە سەرەوەی ئەپەکە کرتە لە دوگمەی «کلیل» بکە و کلیلەکانت داخڵ بکە.")
            }
        }
    }

    private fun buildTelemetryPrompt(telemetry: OkxInstrumentDeepTelemetry): String {
        val t = telemetry.ticker
        val sb = StringBuilder()
        sb.appendLine("You are an institutional quantitative trading AI engine for OKX exchange.")
        sb.appendLine("Analyze this exact real-time OKX v5 dataset for ${t.instId} (${t.instType}) and produce a hyper-accurate trading prediction and execution plan.")
        sb.appendLine("IMPORTANT: All text fields MUST be in fluent Central Kurdish (Sorani - کوردیی سۆرانی). All price levels (Entry, TP1, TP2, TP3, StopLoss, 1H/4H/24H forecasts) MUST be mathematically consistent with the current price (${t.lastPrice}), ATR volatility, Pivot levels, Fibonacci levels, and Order Book Whale Walls.")
        sb.appendLine("\n1. LIVE OKX TICKER TELEMETRY:")
        sb.appendLine("- Instrument: ${t.instId} (${t.instType})")
        sb.appendLine("- Current Price (Last): ${QuantMathEngine.formatCryptoPrice(t.lastPrice)} USDT")
        sb.appendLine("- 24h Open: ${QuantMathEngine.formatCryptoPrice(t.open24h)} | 24h High: ${QuantMathEngine.formatCryptoPrice(t.high24h)} | 24h Low: ${QuantMathEngine.formatCryptoPrice(t.low24h)}")
        sb.appendLine("- 24h Change: ${QuantMathEngine.formatSignedPercent(t.change24hPercent)} (${QuantMathEngine.formatCryptoPrice(t.change24hAbs)} USDT)")
        sb.appendLine("- Position inside 24h Range: ${String.format(Locale.US, "%.1f%%", t.range24hPercent)} | 24h Volatility: ${String.format(Locale.US, "%.2f%%", t.volatility24hPercent)}")
        sb.appendLine("- 24h Volume (USDT): ${QuantMathEngine.formatCompactUsdt(t.volume24hUsdt)}")
        sb.appendLine("- Best Bid: ${QuantMathEngine.formatCryptoPrice(t.bestBidPrice)} (Size: ${t.bestBidSize}) | Best Ask: ${QuantMathEngine.formatCryptoPrice(t.bestAskPrice)} (Size: ${t.bestAskSize}) | Spread: ${String.format(Locale.US, "%.2f bps", t.spreadBps)}")

        sb.appendLine("\n2. MULTI-TIMEFRAME MATHEMATICAL INDICATORS (15m, 1H, 4H, 1D):")
        listOf("15m", "1H", "4H", "1D").forEach { tf ->
            val m = telemetry.timeframeMetrics[tf]
            if (m != null) {
                sb.appendLine("--- Timeframe [$tf] ---")
                sb.appendLine("  Close: ${QuantMathEngine.formatCryptoPrice(m.lastClose)} | High: ${QuantMathEngine.formatCryptoPrice(m.periodHigh)} | Low: ${QuantMathEngine.formatCryptoPrice(m.periodLow)}")
                sb.appendLine("  RSI(14): ${String.format(Locale.US, "%.2f", m.rsi14)} | StochRSI: ${String.format(Locale.US, "%.2f", m.stochRsiK)}")
                sb.appendLine("  MACD Line: ${QuantMathEngine.formatCryptoPrice(m.macdLine)} | Signal: ${QuantMathEngine.formatCryptoPrice(m.macdSignal)} | Histogram: ${QuantMathEngine.formatCryptoPrice(m.macdHistogram)}")
                sb.appendLine("  EMA9: ${QuantMathEngine.formatCryptoPrice(m.ema9)} | EMA20: ${QuantMathEngine.formatCryptoPrice(m.ema20)} | EMA50: ${QuantMathEngine.formatCryptoPrice(m.ema50)} | EMA200: ${QuantMathEngine.formatCryptoPrice(m.ema200)}")
                sb.appendLine("  Bollinger Upper: ${QuantMathEngine.formatCryptoPrice(m.bollingerUpper)} | Mid: ${QuantMathEngine.formatCryptoPrice(m.bollingerMiddle)} | Lower: ${QuantMathEngine.formatCryptoPrice(m.bollingerLower)} | Bandwidth: ${String.format(Locale.US, "%.2f%%", m.bollingerBandwidthPercent)}")
                sb.appendLine("  ATR(14): ${QuantMathEngine.formatCryptoPrice(m.atr14)} (${String.format(Locale.US, "%.2f%%", m.atrPercent)}) | VWAP: ${QuantMathEngine.formatCryptoPrice(m.vwap)} | VolSpike: ${String.format(Locale.US, "%.2fx", m.volumeSpikeRatio)}")
                sb.appendLine("  Pivot: ${QuantMathEngine.formatCryptoPrice(m.pivotPoint)} | R1: ${QuantMathEngine.formatCryptoPrice(m.resistance1)} | R2: ${QuantMathEngine.formatCryptoPrice(m.resistance2)} | R3: ${QuantMathEngine.formatCryptoPrice(m.resistance3)} | S1: ${QuantMathEngine.formatCryptoPrice(m.support1)} | S2: ${QuantMathEngine.formatCryptoPrice(m.support2)} | S3: ${QuantMathEngine.formatCryptoPrice(m.support3)}")
                sb.appendLine("  Fibonacci: 0.236=${QuantMathEngine.formatCryptoPrice(m.fib236)}, 0.382=${QuantMathEngine.formatCryptoPrice(m.fib382)}, 0.500=${QuantMathEngine.formatCryptoPrice(m.fib500)}, 0.618=${QuantMathEngine.formatCryptoPrice(m.fib618)}, 0.786=${QuantMathEngine.formatCryptoPrice(m.fib786)}")
                sb.appendLine("  Quant Score: ${m.quantScore}/100")
            }
        }

        telemetry.orderBookReport?.let { ob ->
            sb.appendLine("\n3. OKX 50-LEVEL ORDER BOOK DEPTH & WHALE LIQUIDITY WALLS:")
            sb.appendLine("- Total Bid Liquidity: ${QuantMathEngine.formatCompactUsdt(ob.totalBidNotionalUsdt)} (${String.format(Locale.US, "%.1f%%", ob.bidPressurePercent)})")
            sb.appendLine("- Total Ask Liquidity: ${QuantMathEngine.formatCompactUsdt(ob.totalAskNotionalUsdt)} (${String.format(Locale.US, "%.1f%%", ob.askPressurePercent)})")
            sb.appendLine("- Bid/Ask Imbalance Ratio: ${String.format(Locale.US, "%.2f", ob.imbalanceRatio)} | Micro-Price: ${QuantMathEngine.formatCryptoPrice(ob.microPrice)}")
            sb.appendLine("- Top Whale Bid Support Walls: " + ob.topSupportBidWalls.joinToString { "${QuantMathEngine.formatCryptoPrice(it.price)} (${QuantMathEngine.formatCompactUsdt(it.notionalUsdt)})" })
            sb.appendLine("- Top Whale Ask Resistance Walls: " + ob.topResistanceAskWalls.joinToString { "${QuantMathEngine.formatCryptoPrice(it.price)} (${QuantMathEngine.formatCompactUsdt(it.notionalUsdt)})" })
        }

        telemetry.fundingReport?.let { fr ->
            sb.appendLine("\n4. OKX PERPETUAL SWAP FUNDING RATE:")
            sb.appendLine("- Current Funding Rate: ${String.format(Locale.US, "%.4f%%", fr.currentFundingRatePercent)} | Next Funding Rate: ${String.format(Locale.US, "%.4f%%", fr.nextFundingRatePercent)} | Annualized APR: ${String.format(Locale.US, "%.2f%%", fr.annualizedAprPercent)}")
        }

        return sb.toString()
    }

    private fun parseScreenshotExitPlanJson(
        defaultInstId: String,
        liveOkxPrice: Double,
        rawJson: String
    ): AiScreenshotExitPlan {
        val json = JSONObject(keyRouter.extractCleanJson(rawJson))
        val detectedBuyPx = json.optDouble("detectedBuyPrice", liveOkxPrice).let {
            if (it <= 0.0 || it.isNaN()) liveOkxPrice else it
        }
        val investedUsdt = json.optDouble("detectedInvestedUsdt", 100.0).let {
            if (it <= 0.0 || it.isNaN()) 100.0 else it
        }
        val currentPnlPct = if (detectedBuyPx > 0) {
            ((liveOkxPrice - detectedBuyPx) / detectedBuyPx) * 100.0
        } else 0.0

        val goldenExitPx = json.optDouble("goldenExitPrice", liveOkxPrice * 1.032).let {
            if (it <= 0.0 || it.isNaN()) liveOkxPrice * 1.032 else it
        }
        val goldenProfitPct = json.optDouble(
            "goldenExitProfitPercent",
            if (detectedBuyPx > 0) ((goldenExitPx - detectedBuyPx) / detectedBuyPx) * 100.0 else 3.2
        )
        val goldenProfitUsdt = json.optDouble(
            "goldenExitEstimatedProfitUsdt",
            investedUsdt * (goldenProfitPct / 100.0)
        )

        return AiScreenshotExitPlan(
            detectedInstId = json.optString("detectedInstId", defaultInstId).ifBlank { defaultInstId },
            positionTypeKu = json.optString("positionTypeKu", "کڕینی دراو لە OKX"),
            detectedBuyPrice = detectedBuyPx,
            currentLiveOkxPrice = liveOkxPrice,
            detectedInvestedUsdt = investedUsdt,
            currentPnlPercent = currentPnlPct,
            currentStatusSummaryKu = json.optString("currentStatusSummaryKu", ""),
            urgentActionVerdictKu = json.optString(
                "urgentActionVerdictKu",
                "لە نرخی $${QuantMathEngine.formatCryptoPrice(goldenExitPx)} بیفرۆشە قازانجتە!"
            ),
            confidencePercent = json.optInt("confidencePercent", 86).coerceIn(1, 99),
            goldenExitPrice = goldenExitPx,
            goldenExitProfitPercent = goldenProfitPct,
            goldenExitEstimatedProfitUsdt = goldenProfitUsdt,
            sellTarget1Price = json.optDouble("sellTarget1Price", liveOkxPrice * 1.018).let { if (it <= 0.0) liveOkxPrice * 1.018 else it },
            sellTarget1ProfitPercent = json.optDouble("sellTarget1ProfitPercent", 1.8),
            sellTarget1AllocationPercent = json.optInt("sellTarget1AllocationPercent", 40),
            sellTarget2Price = json.optDouble("sellTarget2Price", goldenExitPx).let { if (it <= 0.0) goldenExitPx else it },
            sellTarget2ProfitPercent = json.optDouble("sellTarget2ProfitPercent", goldenProfitPct),
            sellTarget2AllocationPercent = json.optInt("sellTarget2AllocationPercent", 40),
            sellTarget3Price = json.optDouble("sellTarget3Price", liveOkxPrice * 1.06).let { if (it <= 0.0) liveOkxPrice * 1.06 else it },
            sellTarget3ProfitPercent = json.optDouble("sellTarget3ProfitPercent", 6.0),
            sellTarget3AllocationPercent = json.optInt("sellTarget3AllocationPercent", 20),
            emergencyStopLossPrice = json.optDouble("emergencyStopLossPrice", liveOkxPrice * 0.98).let { if (it <= 0.0) liveOkxPrice * 0.98 else it },
            holdDurationEstimateKu = json.optString("holdDurationEstimateKu", "چەند کاتژمێری داهاتوو"),
            whaleResistanceWallNoteKu = json.optString("whaleResistanceWallNoteKu", ""),
            detailedSellStrategyKu = json.optString("detailedSellStrategyKu", "")
        )
    }

    private fun parsePredictionJson(
        instId: String,
        spotPrice: Double,
        rawJson: String
    ): AiTradingPrediction {
        val json = JSONObject(keyRouter.extractCleanJson(rawJson))
        val triggersArray = json.optJSONArray("keyNumericTriggersKu")
        val triggers = mutableListOf<String>()
        if (triggersArray != null) {
            for (i in 0 until triggersArray.length()) {
                val item = triggersArray.optString(i)
                if (item.isNotBlank()) triggers.add(item)
            }
        }

        return AiTradingPrediction(
            instId = instId,
            spotPriceAtAnalysis = spotPrice,
            signalDirection = json.optString("signalDirection", "NEUTRAL"),
            signalVerdictKu = json.optString("signalVerdictKu", "چاوەڕوانی (Neutral)"),
            confidencePercent = json.optInt("confidencePercent", 75).coerceIn(1, 99),
            riskLevelKu = json.optString("riskLevelKu", "مامناوەند"),
            recommendedLeverage = json.optString("recommendedLeverage", "3x - 5x"),
            entryZoneMin = json.optDouble("entryZoneMin", spotPrice * 0.997).let { if (it <= 0.0) spotPrice * 0.997 else it },
            entryZoneMax = json.optDouble("entryZoneMax", spotPrice * 1.002).let { if (it <= 0.0) spotPrice * 1.002 else it },
            takeProfit1 = json.optDouble("takeProfit1", spotPrice * 1.015).let { if (it <= 0.0) spotPrice * 1.015 else it },
            takeProfit2 = json.optDouble("takeProfit2", spotPrice * 1.032).let { if (it <= 0.0) spotPrice * 1.032 else it },
            takeProfit3 = json.optDouble("takeProfit3", spotPrice * 1.055).let { if (it <= 0.0) spotPrice * 1.055 else it },
            stopLoss = json.optDouble("stopLoss", spotPrice * 0.982).let { if (it <= 0.0) spotPrice * 0.982 else it },
            riskRewardRatio = json.optDouble("riskRewardRatio", 2.5),
            forecast1hPrice = json.optDouble("forecast1hPrice", spotPrice).let { if (it <= 0.0) spotPrice else it },
            forecast4hPrice = json.optDouble("forecast4hPrice", spotPrice).let { if (it <= 0.0) spotPrice else it },
            forecast24hPrice = json.optDouble("forecast24hPrice", spotPrice).let { if (it <= 0.0) spotPrice else it },
            winProbabilityPercent = json.optInt("winProbabilityPercent", 72).coerceIn(1, 99),
            multiTimeframeAlignmentKu = json.optString("multiTimeframeAlignmentKu", ""),
            whaleOrderBookSummaryKu = json.optString("whaleOrderBookSummaryKu", ""),
            keyNumericTriggersKu = triggers,
            detailedKurdishAnalysis = json.optString("detailedKurdishAnalysis", "")
        )
    }
}
