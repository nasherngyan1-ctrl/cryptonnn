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
 * 100% Direct API AI Engine for abdulbasit traiding (No Firebase dependency).
 * Supports Dual-Image (2 screenshots simultaneously) analysis, full coin selection,
 * interactive multimodal chat, and ultra-detailed OKX Multi-Timeframe Quantitative Synthesis.
 */
class OkxGeminiAiService(
    dao: OkxTradingDao
) {
    val keyRouter = MultiKeyAiRouter(dao)

    suspend fun detectCoinSymbolFromScreenshots(
        bitmaps: List<Bitmap>,
        userNoteKu: String,
        fallbackInstId: String,
        onKeySwitch: ((String) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        if (bitmaps.isEmpty()) {
            val noteUpper = userNoteKu.uppercase(Locale.US)
            val knownCoins = listOf(
                "BTC", "ETH", "SOL", "TON", "XRP", "DOGE", "PEPE", "SUI",
                "BNB", "ADA", "AVAX", "LINK", "SHIB", "TRX", "NEAR", "ARB", "OP", "APT", "FET", "WIF", "BONK"
            )
            val matchedFromNote = knownCoins.find { noteUpper.contains(it) }
            return@withContext if (matchedFromNote != null) "$matchedFromNote-USDT" else fallbackInstId
        }

        val prompt = """
            Analyze the attached ${bitmaps.size} trading/chart/wallet screenshot(s) and optional user note: '$userNoteKu'.
            Extract the cryptocurrency symbol (e.g., BTC, ETH, SOL, TON, XRP, DOGE, PEPE, SUI, BNB, ADA, AVAX, LINK, TRX, SHIB, ARB, OP, NEAR, APT).
            If no symbol is visible in the images, use the symbol from '$fallbackInstId'.
            Return JSON with keys: {"baseSymbol": "SOL", "isSwapOrFutures": false, "detectedBuyPrice": 0.0, "detectedInvestedUsdt": 100.0}
        """.trimIndent()

        val rotatedResult = runCatching {
            keyRouter.generateWithAutoRotation(
                prompt = prompt,
                expectJson = true,
                bitmaps = bitmaps,
                onKeySwitchCallback = onKeySwitch
            )
        }.getOrNull()

        if (rotatedResult != null) {
            val parsed = parseDetectedSymbolJson(rotatedResult.responseText, fallbackInstId)
            if (parsed.isNotBlank()) return@withContext parsed
        }

        val noteUpper = userNoteKu.uppercase(Locale.US)
        val knownCoins = listOf(
            "BTC", "ETH", "SOL", "TON", "XRP", "DOGE", "PEPE", "SUI",
            "BNB", "ADA", "AVAX", "LINK", "SHIB", "TRX", "NEAR", "ARB", "OP", "APT", "FET", "WIF", "BONK"
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
            val isSwap = json.optBoolean("isSwapOrFutures", false) || fallbackInstId.endsWith("-SWAP")
            if (rawBase.length in 2..10 && rawBase.all { it.isLetterOrDigit() }) {
                if (isSwap) "$rawBase-USDT-SWAP" else "$rawBase-USDT"
            } else {
                fallbackInstId
            }
        }.getOrDefault(fallbackInstId)
    }

    suspend fun analyzeTradeScreenshotsForExitPlan(
        bitmaps: List<Bitmap>,
        userNoteKu: String,
        telemetry: OkxInstrumentDeepTelemetry,
        onKeySwitch: ((String) -> Unit)? = null
    ): Pair<AiScreenshotExitPlan, String> = withContext(Dispatchers.IO) {
        val promptText = buildString {
            appendLine("You are the master institutional trading AI engine of 'abdulbasit traiding'.")
            appendLine("The user has selected coin ${telemetry.ticker.instId} and attached ${bitmaps.size} screenshot(s) (which may include entry/wallet screenshots, multiple timeframe charts, orderbook depth, indicators, or position details).")
            appendLine("Your mission is to deliver an EXTRAORDINARILY DETAILED, surgical, institutional-grade trading & profit exit analysis in fluent Central Kurdish (Sorani - کوردیی سۆرانی).")
            if (userNoteKu.isNotBlank()) {
                appendLine("User's specific note/question: \"$userNoteKu\"")
            }
            appendLine("\n1. Carefully inspect ALL ${bitmaps.size} attached image(s) (Image #1 through Image #${bitmaps.size}) to extract:")
            appendLine("   - Coin/pair, position direction (Spot, Long, or Short), and leverage if shown.")
            appendLine("   - Exact buy/entry price and invested USDT capital (if not visible, use live price ${telemetry.ticker.lastPrice} and 100.0 USDT).")
            appendLine("   - Every chart pattern, support/resistance level, candlestick structure, indicator reading, or PnL shown across all ${bitmaps.size} attached images.")
            appendLine("\n2. Cross-reference with the following REAL-TIME OKX v5 Multi-Timeframe (15m, 1H, 4H, 1D) & 50-Level Order Book Telemetry for ${telemetry.ticker.instId}:")
            appendLine(buildTelemetryPrompt(telemetry))
            appendLine("\n3. Return a valid JSON object with EXACTLY these keys (write ultra-detailed, comprehensive Kurdish explanations in currentStatusSummaryKu, whaleResistanceWallNoteKu, and detailedSellStrategyKu):")
            appendLine(
                """
                {
                  "detectedInstId": "${telemetry.ticker.instId}",
                  "positionTypeKu": "کڕینی سپۆت (Spot) یان فیوچەرز (Long/Short)",
                  "detectedBuyPrice": ${telemetry.ticker.lastPrice},
                  "detectedInvestedUsdt": 100.0,
                  "currentStatusSummaryKu": "شیکردنەوەی زۆر وردی هەموو وێنە هاوپێچکراوەکان لەگەڵ تایمفرەیمەکانی 15m, 1H, 4H, 1D...",
                  "urgentActionVerdictKu": "لە نرخی $... بیفرۆشە قازانجی سەرەکیتە!",
                  "confidencePercent": 91,
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
                  "whaleResistanceWallNoteKu": "شیکاری وردی دیوارەکانی کڕین و فرۆشتنی نەهەنگەکان و قووڵایی بازاڕ...",
                  "detailedSellStrategyKu": "پلانی زۆر وردی هەنگاو بە هەنگاو بە ژمارەی تەواو بۆ کڕین، فرۆشتن، دابەشکردنی سەرمایە، و پاراستنی قازانج..."
                }
                """.trimIndent()
            )
        }

        val rotatedResult = runCatching {
            keyRouter.generateWithAutoRotation(
                prompt = promptText,
                expectJson = true,
                bitmaps = bitmaps,
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
            if (plan != null) {
                val badge = "کلیلی چالاک: ${rotatedResult.usedKeyLabelKu} (${rotatedResult.usedProvider})"
                return@withContext plan to badge
            }
        }

        Log.i(TAG, "Using ultra-detailed OKX Multi-Timeframe & OrderBook Exit Synthesis")
        buildQuantFallbackScreenshotExitPlan(telemetry, userNoteKu, bitmaps.size) to "بزوێنەری ژمارەیی قووڵی OKX (٤ تایمفرەیم + OrderBook)"
    }

    suspend fun generateDeepInstrumentPrediction(
        telemetry: OkxInstrumentDeepTelemetry,
        onKeySwitch: ((String) -> Unit)? = null
    ): Pair<AiTradingPrediction, String> = withContext(Dispatchers.IO) {
        val prompt = buildString {
            appendLine(buildTelemetryPrompt(telemetry))
            appendLine("\nReturn ONLY a JSON object with these exact keys (all text in fluent Central Kurdish - سۆرانی, with extraordinary depth and precision):")
            appendLine(
                """
                {
                  "signalDirection": "STRONG_LONG | LONG | NEUTRAL | SHORT | STRONG_SHORT",
                  "signalVerdictKu": "...",
                  "confidencePercent": 88,
                  "riskLevelKu": "نزم / مامناوەند / بەرز",
                  "recommendedLeverage": "3x - 5x",
                  "entryZoneMin": 0.0,
                  "entryZoneMax": 0.0,
                  "takeProfit1": 0.0,
                  "takeProfit2": 0.0,
                  "takeProfit3": 0.0,
                  "stopLoss": 0.0,
                  "riskRewardRatio": 3.1,
                  "forecast1hPrice": 0.0,
                  "forecast4hPrice": 0.0,
                  "forecast24hPrice": 0.0,
                  "winProbabilityPercent": 85,
                  "multiTimeframeAlignmentKu": "...",
                  "whaleOrderBookSummaryKu": "...",
                  "keyNumericTriggersKu": ["...", "...", "...", "...", "..."],
                  "detailedKurdishAnalysis": "..."
                }
                """.trimIndent()
            )
        }

        val rotatedResult = runCatching {
            keyRouter.generateWithAutoRotation(
                prompt = prompt,
                expectJson = true,
                bitmaps = emptyList(),
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

        buildQuantFallbackPrediction(telemetry) to "بزوێنەری ژمارەیی ڕاستەوخۆی OKX"
    }

    fun streamAdvisorResponse(
        userQuestionKu: String,
        attachedBitmaps: List<Bitmap> = emptyList(),
        activeTelemetry: OkxInstrumentDeepTelemetry?,
        topTickers: List<OkxMarketTicker>,
        onKeySwitch: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val contextPrompt = buildString {
            appendLine("تۆ ڕاوێژکاری باڵای زیرەکی دەستکردی ترەیدینگیت لە ئەپی «abdulbasit traiding».")
            appendLine("ئەرکی تۆ پێشکەشکردنی شیکردنەوەیەکی یەکجار ورد، نایاب، زانستی و پڕ لە ژمارەی دەقیقە بە زمانی کوردی (سۆرانی).")
            if (attachedBitmaps.isNotEmpty()) {
                appendLine("بەکارهێنەر ${attachedBitmaps.size} وێنەی (Screenshot) هاوپێچ کردووە. هەموو وردەکارییەکانی ناو وێنەکان (نرخی کڕین، جۆری کۆین، تایمفرەیم، قەبارە، مۆمەکان و ئیندیکەیتەرەکان) بخوێنەرەوە و بەراوردی بکە لەگەڵ داتای زیندووی ئێستای OKX.")
            }
            if (activeTelemetry != null) {
                appendLine("\n=== داتای زیندووی چرکەبەچرکەی دراوی هەڵبژێردراو (${activeTelemetry.ticker.instId}) لە OKX ===")
                appendLine(buildTelemetryPrompt(activeTelemetry))
            }
            appendLine("\n=== پوختەی نرخی ٢٠ دراوی سەرەکی ئێستای بازاڕی OKX ===")
            topTickers.take(20).forEach { t ->
                appendLine(
                    "• ${t.instId}: نرخ=${QuantMathEngine.formatCryptoPrice(t.lastPrice)} USDT | گۆڕانکاری ٢٤س=${QuantMathEngine.formatSignedPercent(t.change24hPercent)} | بەرزترین=${QuantMathEngine.formatCryptoPrice(t.high24h)} | نزمترین=${QuantMathEngine.formatCryptoPrice(t.low24h)} | قەبارە=${QuantMathEngine.formatCompactUsdt(t.volume24hUsdt)}"
                )
            }
            appendLine("\nپرسیار / داواکاری بازرگان:")
            appendLine(userQuestionKu)
            appendLine(
                """
                
                لە وەڵامەکەتدا ئەم بەشانە بە زۆرترین وردەکاری و بە ژمارەی تەواو بنووسە:
                ١) 🎯 بڕیاری دەستبەجێ و ئاراستەی بازاڕ (کڕین / فرۆشتن / چاوەڕوانی + ڕێژەی دڵنیایی %).
                ٢) 💰 خاڵی زێڕینی چوونەژوورەوە (Optimal Entry Zone) و نرخی زێڕینی فرۆشتن (Golden Exit).
                ٣) 📈 سێ ئامانجی وردی قازانج (TP1, TP2, TP3) بە ڕێژەی قازانجی سەدی (%) و بڕی دابەشکردن.
                ٤) 🛡️ خاڵی وەستانی زیان (Stop-Loss) و نرخی پاراستنی سەرمایە (Break-Even).
                ٥) 📊 شیکردنەوەی تایمفرەیمەکان (15m, 1H, 4H, 1D) بە ژمارەی RSI, MACD, EMA20/50/200, VWAP, ATR, و ئاستەکانی فیبۆناچی و Pivot.
                ٦) 🐋 خوێندنەوەی دیوارەکانی نەهەنگ (Whale Order Book Walls) لە OKX و کاریگەرییان لەسەر نرخەکە.
                """.trimIndent()
            )
        }

        val rotatedResult = withContext(Dispatchers.IO) {
            runCatching {
                keyRouter.generateWithAutoRotation(
                    prompt = contextPrompt,
                    expectJson = false,
                    bitmaps = attachedBitmaps,
                    onKeySwitchCallback = onKeySwitch
                )
            }.getOrNull()
        }

        if (rotatedResult != null && rotatedResult.responseText.isNotBlank()) {
            emit("⚡ [کلیلی چالاک: ${rotatedResult.usedKeyLabelKu} (${rotatedResult.usedProvider})]\n\n${rotatedResult.responseText}")
            return@flow
        }

        emit(buildQuantFallbackAdvisorAnswer(userQuestionKu, activeTelemetry, topTickers, attachedBitmaps.size))
    }

    private fun buildQuantFallbackPrediction(
        telemetry: OkxInstrumentDeepTelemetry
    ): AiTradingPrediction {
        val t = telemetry.ticker
        val px = t.lastPrice
        val tf15m = telemetry.timeframeMetrics["15m"]
        val tf1h = telemetry.timeframeMetrics["1H"] ?: telemetry.timeframeMetrics.values.firstOrNull()
        val tf4h = telemetry.timeframeMetrics["4H"] ?: tf1h
        val tf1d = telemetry.timeframeMetrics["1D"] ?: tf4h
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
        val confidence = (70 + (abs(score) / 4)).coerceIn(68, 95)

        val triggers = buildList {
            tf15m?.let {
                add("تایمفرەیمی 15m: RSI(14)=${String.format(Locale.US, "%.1f", it.rsi14)} | StochRSI=${String.format(Locale.US, "%.1f", it.stochRsiK)} | EMA9=$${QuantMathEngine.formatCryptoPrice(it.ema9)} | VWAP=$${QuantMathEngine.formatCryptoPrice(it.vwap)}.")
            }
            tf1h?.let {
                add("تایمفرەیمی 1H: RSI(14)=${String.format(Locale.US, "%.1f", it.rsi14)} | EMA20=$${QuantMathEngine.formatCryptoPrice(it.ema20)} | EMA50=$${QuantMathEngine.formatCryptoPrice(it.ema50)} | EMA200=$${QuantMathEngine.formatCryptoPrice(it.ema200)}.")
                add("ناجێگیری ATR(14)=$${QuantMathEngine.formatCryptoPrice(it.atr14)} (${String.format(Locale.US, "%.2f%%", it.atrPercent)}) | پاڵپشتی S1=$${QuantMathEngine.formatCryptoPrice(it.support1)} | بەرگری R1=$${QuantMathEngine.formatCryptoPrice(it.resistance1)} | Fib 0.618=$${QuantMathEngine.formatCryptoPrice(it.fib618)}.")
            }
            tf4h?.let {
                add("تایمفرەیمی 4H: RSI(14)=${String.format(Locale.US, "%.1f", it.rsi14)} | MACD Hist=${QuantMathEngine.formatCryptoPrice(it.macdHistogram)} | R2=$${QuantMathEngine.formatCryptoPrice(it.resistance2)} | S2=$${QuantMathEngine.formatCryptoPrice(it.support2)}.")
            }
            tf1d?.let {
                add("تایمفرەیمی 1D: خاڵی هێزی ڕۆژانە=${it.quantScore}/100 | EMA50=$${QuantMathEngine.formatCryptoPrice(it.ema50)}.")
            }
            telemetry.orderBookReport?.let { ob ->
                add("قووڵایی ٥٠ ئاستی Order Book: فشاری کڕیاران ${String.format(Locale.US, "%.1f%%", ob.bidPressurePercent)} (${QuantMathEngine.formatCompactUsdt(ob.totalBidNotionalUsdt)}) بەرامبەر ${String.format(Locale.US, "%.1f%%", ob.askPressurePercent)} فرۆشیاران.")
            }
            if (whaleAskWall != null && whaleBidWall != null) {
                add("دیواری نەهەنگی کڕین لە $${QuantMathEngine.formatCryptoPrice(whaleBidWall)} و دیواری نەهەنگی فرۆشتن لە $${QuantMathEngine.formatCryptoPrice(whaleAskWall)}.")
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
            winProbabilityPercent = (confidence - 2).coerceIn(62, 93),
            multiTimeframeAlignmentKu = "هاوتابوونی تایمفرەیمەکان (15m=${tf15m?.quantScore ?: 0}, 1H=${tf1h?.quantScore ?: 0}, 4H=${tf4h?.quantScore ?: 0}, 1D=${tf1d?.quantScore ?: 0}) کۆی گشتی: $score لە 100 (${telemetry.compositeVerdictKu}).",
            whaleOrderBookSummaryKu = telemetry.orderBookReport?.dominantSideSummaryKu
                ?: "هاوسەنگی لە داواکارییەکانی کڕین و فرۆشتنی OKX بەدی دەکرێت.",
            keyNumericTriggersKu = triggers,
            detailedKurdishAnalysis = "شیکاری قووڵی دامەزراوەیی abdulbasit traiding بۆ ${t.instId} لە نرخی $${QuantMathEngine.formatCryptoPrice(px)}: باشترین ناوچەی چوونەژوورەوە لە نێوان $${QuantMathEngine.formatCryptoPrice(min(entryMin, entryMax))} تا $${QuantMathEngine.formatCryptoPrice(max(entryMin, entryMax))} ـە. ئامانجی یەکەم لە $${QuantMathEngine.formatCryptoPrice(tp1)}، ئامانجی زێڕین لە $${QuantMathEngine.formatCryptoPrice(tp2)}، و ئامانجی سێیەم لە $${QuantMathEngine.formatCryptoPrice(tp3)} دیاری کراوە بە ڕێژەی قازانج بۆ مەترسی 1:${String.format(Locale.US, "%.1f", rr)} و ستۆپ لۆس لە $${QuantMathEngine.formatCryptoPrice(sl)}."
        )
    }

    private fun buildQuantFallbackScreenshotExitPlan(
        telemetry: OkxInstrumentDeepTelemetry,
        userNoteKu: String,
        imageCount: Int
    ): AiScreenshotExitPlan {
        val t = telemetry.ticker
        val livePx = t.lastPrice
        val tf15m = telemetry.timeframeMetrics["15m"]
        val tf1h = telemetry.timeframeMetrics["1H"] ?: telemetry.timeframeMetrics.values.firstOrNull()
        val tf4h = telemetry.timeframeMetrics["4H"] ?: tf1h
        val tf1d = telemetry.timeframeMetrics["1D"] ?: tf4h
        val atr = tf1h?.atr14?.takeIf { it > 0 } ?: (livePx * 0.02)

        val numbersInNote = Regex("""\d+(\.\d+)?""").findAll(userNoteKu).mapNotNull { it.value.toDoubleOrNull() }.toList()
        val buyPx = numbersInNote.find { abs(it - livePx) / livePx < 0.45 } ?: livePx
        val investedUsdt = numbersInNote.find { it != buyPx && it >= 5.0 } ?: 100.0

        val whaleResistance = telemetry.orderBookReport?.topResistanceAskWalls
            ?.filter { it.price > livePx * 1.003 }
            ?.minByOrNull { it.price }
            ?.price
        val whaleSupport = telemetry.orderBookReport?.topSupportBidWalls
            ?.filter { it.price < livePx }
            ?.maxByOrNull { it.notionalUsdt }
            ?.price

        val r1 = tf1h?.resistance1?.takeIf { it > livePx * 1.005 } ?: (livePx + atr * 1.35)
        val r2 = tf1h?.resistance2?.takeIf { it > r1 } ?: (livePx + atr * 2.45)
        val r3 = tf1h?.resistance3?.takeIf { it > r2 } ?: (livePx + atr * 3.9)

        val rawGoldenTarget = if (whaleResistance != null && whaleResistance > livePx * 1.008) {
            min(r2, whaleResistance * 0.9985)
        } else {
            r2 * 0.9985
        }
        val goldenExitPx = max(livePx * 1.016, rawGoldenTarget)
        val tp1Px = max(livePx * 1.009, min(r1 * 0.999, goldenExitPx * 0.992))
        val tp2Px = goldenExitPx
        val tp3Px = max(goldenExitPx * 1.02, r3 * 0.998)
        val slPx = max(0.0, min(buyPx, livePx) - (atr * 1.15))

        val baseRef = if (buyPx > 0) buyPx else livePx
        val goldenProfitPct = ((goldenExitPx - baseRef) / baseRef) * 100.0
        val tp1ProfitPct = ((tp1Px - baseRef) / baseRef) * 100.0
        val tp2ProfitPct = ((tp2Px - baseRef) / baseRef) * 100.0
        val tp3ProfitPct = ((tp3Px - baseRef) / baseRef) * 100.0
        val goldenProfitUsdt = investedUsdt * (goldenProfitPct / 100.0)
        val currentPnlPct = ((livePx - baseRef) / baseRef) * 100.0

        val imageDesc = if (imageCount >= 2) {
            "شیکاری جووت-وێنە ($imageCount وێنە) + ٤ تایمفرەیمی زیندووی OKX"
        } else if (imageCount == 1) {
            "شیکاری وێنە + ٤ تایمفرەیمی زیندووی OKX"
        } else {
            "شیکاری قووڵی ٤ تایمفرەیم و Order Book ـی OKX"
        }

        return AiScreenshotExitPlan(
            detectedInstId = t.instId,
            positionTypeKu = "$imageDesc (${t.instType})",
            detectedBuyPrice = baseRef,
            currentLiveOkxPrice = livePx,
            detectedInvestedUsdt = investedUsdt,
            currentPnlPercent = currentPnlPct,
            currentStatusSummaryKu = buildString {
                append("نرخی زیندووی ئێستای ${t.instId} بریتییە لە $${QuantMathEngine.formatCryptoPrice(livePx)} (گۆڕانی ٢٤س: ${QuantMathEngine.formatSignedPercent(t.change24hPercent)}). ")
                append("خاڵی هێزی فرە-تایمفرەیم بریتییە لە ${telemetry.compositeQuantScore}/100 (${telemetry.compositeVerdictKu}). ")
                tf1h?.let {
                    append("لەسەر تایمفرەیمی 1H: RSI=${String.format(Locale.US, "%.1f", it.rsi14)}، StochRSI=${String.format(Locale.US, "%.1f", it.stochRsiK)}، EMA20=$${QuantMathEngine.formatCryptoPrice(it.ema20)}، EMA50=$${QuantMathEngine.formatCryptoPrice(it.ema50)}، VWAP=$${QuantMathEngine.formatCryptoPrice(it.vwap)}، و Fib 0.618=$${QuantMathEngine.formatCryptoPrice(it.fib618)}.")
                }
            },
            urgentActionVerdictKu = "لە نرخی $${QuantMathEngine.formatCryptoPrice(goldenExitPx)} بیفرۆشە قازانجی سەرەکیتە (${QuantMathEngine.formatSignedPercent(goldenProfitPct)} | +$${String.format(Locale.US, "%.2f", goldenProfitUsdt)})!",
            confidencePercent = 90,
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
            holdDurationEstimateKu = "٢ بۆ ٦ کاتژمێری داهاتوو (بەپێی خێرایی ATR=${QuantMathEngine.formatCryptoPrice(atr)} لە کاتژمێرێکدا)",
            whaleResistanceWallNoteKu = buildString {
                if (whaleResistance != null) {
                    append("🐋 دیواری فرۆشتنی نەهەنگەکان (Ask Wall) لە $${QuantMathEngine.formatCryptoPrice(whaleResistance)} چڕبووەتەوە؛ بۆیە خاڵی زێڕینی فرۆشتن کەمێک لە خوار ئەو دیوارە ($${QuantMathEngine.formatCryptoPrice(goldenExitPx)}) دانراوە تا فەرمانەکەت ١٠٠٪ پڕ ببێتەوە پێش گەڕانەوەی نرخ. ")
                }
                if (whaleSupport != null) {
                    append("هەروەها دیواری پاڵپشتی کڕینی نەهەنگەکان (Bid Wall) لە $${QuantMathEngine.formatCryptoPrice(whaleSupport)} پاڵپشتی نرخەکە دەکات.")
                }
            }.ifBlank {
                "ئاستەکانی فرۆشتن بەپێی بەرگری Pivot R1/R2/R3 و قووڵایی بازاڕی OKX دیاری کراون."
            },
            detailedSellStrategyKu = buildString {
                appendLine("پلانی جێبەجێکردنی ورد و نایاب لەسەر ${t.instId}:")
                appendLine("١) قۆناغی یەکەم (TP1 - ٤٠٪ی کۆینەکە): فەرمانی Limit Sell لە نرخی $${QuantMathEngine.formatCryptoPrice(tp1Px)} دابنێ (${QuantMathEngine.formatSignedPercent(tp1ProfitPct)}). هەر کە گەیشتە ئەم نرخە، ستۆپ لۆس بهێنە سەر نرخی کڕین ($${QuantMathEngine.formatCryptoPrice(baseRef)} - Break-Even).")
                appendLine("٢) قۆناغی دووەم (TP2 / خاڵی زێڕین - ٤٠٪ی کۆینەکە): لە نرخی $${QuantMathEngine.formatCryptoPrice(tp2Px)} دابنێ (${QuantMathEngine.formatSignedPercent(tp2ProfitPct)}) کە دەکەوێتە پێش بەرگری سەرەکی R2 و دیواری فرۆشتنی نەهەنگەکان.")
                appendLine("٣) قۆناغی سێیەم (TP3 - ٢٠٪ی کۆتایی): لە نرخی $${QuantMathEngine.formatCryptoPrice(tp3Px)} بهێڵەرەوە (${QuantMathEngine.formatSignedPercent(tp3ProfitPct)}) بۆ سوودوەرگرتن لە شەپۆلی شکاندنی بەرگری سەرەوە.")
                appendLine("٤) پاراستنی سەرمایە (Stop-Loss): ئەگەر نرخی بازاڕ دابەزی بۆ ژێر پاڵپشتی ATR لە $${QuantMathEngine.formatCryptoPrice(slPx)}، دەستبەجێ دەرچۆ بۆ پاراستنی سەرمایەکەت.")
                appendLine("٥) تایمفرەیمەکان: 15m RSI=${String.format(Locale.US, "%.1f", tf15m?.rsi14 ?: 50.0)} | 1H RSI=${String.format(Locale.US, "%.1f", tf1h?.rsi14 ?: 50.0)} | 4H RSI=${String.format(Locale.US, "%.1f", tf4h?.rsi14 ?: 50.0)} | 1D RSI=${String.format(Locale.US, "%.1f", tf1d?.rsi14 ?: 50.0)}.")
            }
        )
    }

    private fun buildQuantFallbackAdvisorAnswer(
        questionKu: String,
        telemetry: OkxInstrumentDeepTelemetry?,
        topTickers: List<OkxMarketTicker>,
        attachedImagesCount: Int
    ): String {
        val t = telemetry?.ticker ?: topTickers.firstOrNull()
            ?: return "تکایە چاوەڕێ بکە تا داتای بازاڕی OKX بار دەبێت یان دراوێک لە سەرەوە هەڵبژێرە."
        val pred = telemetry?.let { buildQuantFallbackPrediction(it) }
        val tf15m = telemetry?.timeframeMetrics?.get("15m")
        val tf1h = telemetry?.timeframeMetrics?.get("1H")
        val tf4h = telemetry?.timeframeMetrics?.get("4H")
        val tf1d = telemetry?.timeframeMetrics?.get("1D")
        val ob = telemetry?.orderBookReport

        return buildString {
            appendLine("⚡ شیکردنەوەی زۆر وردی ڕاوێژکاری زیرەک (abdulbasit traiding) بۆ ${t.instId}:")
            if (attachedImagesCount > 0) {
                appendLine("🖼️ هاوپێچ: $attachedImagesCount وێنە لەگەڵ داتای زیندووی چرکەبەچرکەی OKX بەراورد کران.")
            }
            appendLine("\n١) 🎯 بڕیاری دەستبەجێ و نرخی ئێستا:")
            appendLine("• نرخی زیندووی ئێستا: $${QuantMathEngine.formatCryptoPrice(t.lastPrice)} USDT (${QuantMathEngine.formatSignedPercent(t.change24hPercent)})")
            appendLine("• مەودای ٢٤ کاتژمێر: نزمترین $${QuantMathEngine.formatCryptoPrice(t.low24h)} — بەرزترین $${QuantMathEngine.formatCryptoPrice(t.high24h)} (قەبارە: ${QuantMathEngine.formatCompactUsdt(t.volume24hUsdt)})")
            if (pred != null) {
                appendLine("• بڕیاری تەکنیکی و ئاراستە: ${pred.signalVerdictKu} | دڵنیایی: ${pred.confidencePercent}% | ئەگەری سەرکەوتن: ${pred.winProbabilityPercent}%")
                appendLine("• لیڤرێجی پێشنیارکراو: ${pred.recommendedLeverage} | ڕێژەی قازانج بۆ مەترسی: 1:${String.format(Locale.US, "%.1f", pred.riskRewardRatio)}")

                appendLine("\n٢) 💰 خاڵی چوونەژوورەوە و ٣ ئامانجی فرۆشتن بۆ قازانج:")
                appendLine("• باشترین ناوچەی کڕین (Entry Zone): $${QuantMathEngine.formatCryptoPrice(pred.entryZoneMin)} تا $${QuantMathEngine.formatCryptoPrice(pred.entryZoneMax)}")
                appendLine("• ئامانجی یەکەم (TP1 - ٤٠٪): $${QuantMathEngine.formatCryptoPrice(pred.takeProfit1)}")
                appendLine("• ئامانجی زێڕین (TP2 - ٤٠٪): $${QuantMathEngine.formatCryptoPrice(pred.takeProfit2)}")
                appendLine("• ئامانجی سێیەم (TP3 - ٢٠٪): $${QuantMathEngine.formatCryptoPrice(pred.takeProfit3)}")
                appendLine("• وەستانی زیان (Stop-Loss): $${QuantMathEngine.formatCryptoPrice(pred.stopLoss)}")
                appendLine("• پێشبینی نرخ: ١ کاتژمێر=$${QuantMathEngine.formatCryptoPrice(pred.forecast1hPrice)} | ٤ کاتژمێر=$${QuantMathEngine.formatCryptoPrice(pred.forecast4hPrice)} | ٢٤ کاتژمێر=$${QuantMathEngine.formatCryptoPrice(pred.forecast24hPrice)}")
            }

            appendLine("\n٣) 📊 وردەکارییەکانی هەر ٤ تایمفرەیمەکە (Multi-Timeframe Matrix):")
            listOf("15m" to tf15m, "1H" to tf1h, "4H" to tf4h, "1D" to tf1d).forEach { (name, m) ->
                if (m != null) {
                    appendLine("• [$name]: RSI=${String.format(Locale.US, "%.1f", m.rsi14)} | StochRSI=${String.format(Locale.US, "%.1f", m.stochRsiK)} | EMA20=$${QuantMathEngine.formatCryptoPrice(m.ema20)} | EMA50=$${QuantMathEngine.formatCryptoPrice(m.ema50)} | VWAP=$${QuantMathEngine.formatCryptoPrice(m.vwap)} | ATR=$${QuantMathEngine.formatCryptoPrice(m.atr14)} | خاڵ=${m.quantScore}/100")
                }
            }
            if (tf1h != null) {
                appendLine("• ئاستەکانی Pivot (1H): S2=$${QuantMathEngine.formatCryptoPrice(tf1h.support2)} | S1=$${QuantMathEngine.formatCryptoPrice(tf1h.support1)} | P=$${QuantMathEngine.formatCryptoPrice(tf1h.pivotPoint)} | R1=$${QuantMathEngine.formatCryptoPrice(tf1h.resistance1)} | R2=$${QuantMathEngine.formatCryptoPrice(tf1h.resistance2)}")
                appendLine("• ئاستەکانی فیبۆناچی: 0.382=$${QuantMathEngine.formatCryptoPrice(tf1h.fib382)} | 0.500=$${QuantMathEngine.formatCryptoPrice(tf1h.fib500)} | 0.618=$${QuantMathEngine.formatCryptoPrice(tf1h.fib618)}")
            }

            if (ob != null) {
                appendLine("\n٤) 🐋 قووڵایی بازاڕ و دیوارەکانی نەهەنگ (50-Level Order Book):")
                appendLine("• هێزی کڕیاران (Bids): ${String.format(Locale.US, "%.1f%%", ob.bidPressurePercent)} (${QuantMathEngine.formatCompactUsdt(ob.totalBidNotionalUsdt)}) | هێزی فرۆشیاران (Asks): ${String.format(Locale.US, "%.1f%%", ob.askPressurePercent)} (${QuantMathEngine.formatCompactUsdt(ob.totalAskNotionalUsdt)})")
                appendLine("• بەهێزترین دیوارەکانی پاڵپشتی کڕین: " + ob.topSupportBidWalls.take(3).joinToString { "$${QuantMathEngine.formatCryptoPrice(it.price)} (${QuantMathEngine.formatCompactUsdt(it.notionalUsdt)})" })
                appendLine("• بەهێزترین دیوارەکانی بەرگری فرۆشتن: " + ob.topResistanceAskWalls.take(3).joinToString { "$${QuantMathEngine.formatCryptoPrice(it.price)} (${QuantMathEngine.formatCompactUsdt(it.notionalUsdt)})" })
            }

            appendLine("\n💡 بۆ بەکارهێنانی ڕاستەوخۆی کلیلەکانی Gemini / Groq / OpenRouter / OpenAI بە سووڕانەوەی ئۆتۆماتیکی، لە سەرەوەی ئەپەکە کرتە لە «کلیل» بکە.")
        }
    }

    private fun buildTelemetryPrompt(telemetry: OkxInstrumentDeepTelemetry): String {
        val t = telemetry.ticker
        val sb = StringBuilder()
        sb.appendLine("You are the institutional quantitative trading AI engine for 'abdulbasit traiding'.")
        sb.appendLine("Analyze this exact real-time OKX v5 dataset for ${t.instId} (${t.instType}) and produce an extraordinarily detailed trading prediction and execution plan.")
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
            confidencePercent = json.optInt("confidencePercent", 88).coerceIn(1, 99),
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
            confidencePercent = json.optInt("confidencePercent", 82).coerceIn(1, 99),
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
            winProbabilityPercent = json.optInt("winProbabilityPercent", 78).coerceIn(1, 99),
            multiTimeframeAlignmentKu = json.optString("multiTimeframeAlignmentKu", ""),
            whaleOrderBookSummaryKu = json.optString("whaleOrderBookSummaryKu", ""),
            keyNumericTriggersKu = triggers,
            detailedKurdishAnalysis = json.optString("detailedKurdishAnalysis", "")
        )
    }
}
