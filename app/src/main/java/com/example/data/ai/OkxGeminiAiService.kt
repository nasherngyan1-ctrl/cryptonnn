package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.AiMarketOpportunityItem
import com.example.data.model.AiScreenshotExitPlan
import com.example.data.model.AiTradingPrediction
import com.example.data.model.OkxInstrumentDeepTelemetry
import com.example.data.model.OkxMarketTicker
import com.example.domain.QuantMathEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private const val TAG = "OkxGeminiAiService"

class OkxGeminiAiService {

    private val predictionSchema = Schema.obj(
        properties = mapOf(
            "signalDirection" to Schema.enumeration(
                values = listOf("STRONG_LONG", "LONG", "NEUTRAL", "SHORT", "STRONG_SHORT"),
                description = "Quantitative trading direction based on multi-timeframe OKX data"
            ),
            "signalVerdictKu" to Schema.string(
                description = "Short decisive verdict in Central Kurdish (Sorani), e.g., کڕینی بەهێز (Strong Long)"
            ),
            "confidencePercent" to Schema.integer(
                description = "AI confidence percentage from 1 to 99"
            ),
            "riskLevelKu" to Schema.string(
                description = "Risk level in Kurdish, e.g., مامناوەند (Medium) or نزم (Low) or بەرز (High)"
            ),
            "recommendedLeverage" to Schema.string(
                description = "Recommended OKX leverage string, e.g., 3x - 5x (Isolated)"
            ),
            "entryZoneMin" to Schema.double(
                description = "Lower bound of optimal entry price zone"
            ),
            "entryZoneMax" to Schema.double(
                description = "Upper bound of optimal entry price zone"
            ),
            "takeProfit1" to Schema.double(
                description = "Conservative Take Profit 1 target price"
            ),
            "takeProfit2" to Schema.double(
                description = "Medium Take Profit 2 target price"
            ),
            "takeProfit3" to Schema.double(
                description = "Extended Take Profit 3 target price"
            ),
            "stopLoss" to Schema.double(
                description = "Strict Stop-Loss invalidation price"
            ),
            "riskRewardRatio" to Schema.double(
                description = "Calculated Risk-to-Reward ratio (e.g., 2.8)"
            ),
            "forecast1hPrice" to Schema.double(
                description = "Predicted price in 1 hour based on 15m/1H momentum and orderbook"
            ),
            "forecast4hPrice" to Schema.double(
                description = "Predicted price in 4 hours based on 1H/4H structure"
            ),
            "forecast24hPrice" to Schema.double(
                description = "Predicted price in 24 hours based on 4H/1D trend and funding rate"
            ),
            "winProbabilityPercent" to Schema.integer(
                description = "Estimated statistical win probability percentage (1-99)"
            ),
            "multiTimeframeAlignmentKu" to Schema.string(
                description = "Detailed Central Kurdish summary of how 15m, 1H, 4H, and 1D indicators align"
            ),
            "whaleOrderBookSummaryKu" to Schema.string(
                description = "Central Kurdish analysis of OKX 50-level orderbook bid/ask walls and funding rate"
            ),
            "keyNumericTriggersKu" to Schema.array(
                items = Schema.string(),
                description = "4 to 6 ultra-precise bullet points in Kurdish citing exact numbers (RSI, EMA, ATR, Pivot, Whale Walls)"
            ),
            "detailedKurdishAnalysis" to Schema.string(
                description = "Comprehensive institutional trading plan in Central Kurdish (Sorani) explaining exact numbers, invalidation levels, and execution strategy on OKX"
            )
        )
    )

    private val scannerSchema = Schema.obj(
        properties = mapOf(
            "marketRegimeSummaryKu" to Schema.string(
                description = "Overall OKX market regime summary in Central Kurdish (Sorani)"
            ),
            "opportunities" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "instId" to Schema.string(description = "OKX instrument ID, e.g., BTC-USDT"),
                        "currentPrice" to Schema.double(description = "Exact current OKX price"),
                        "change24hPercent" to Schema.double(description = "24h change percentage"),
                        "actionDirection" to Schema.enumeration(
                            values = listOf("LONG", "SHORT"),
                            description = "Trade direction"
                        ),
                        "actionLabelKu" to Schema.string(description = "Kurdish label, e.g., کڕینی بەهێز (Long)"),
                        "confidencePercent" to Schema.integer(description = "AI confidence 1-99"),
                        "entryPrice" to Schema.double(description = "Optimal entry price"),
                        "targetPrice" to Schema.double(description = "Primary target price"),
                        "stopLossPrice" to Schema.double(description = "Stop loss price"),
                        "expectedMovePercent" to Schema.double(description = "Expected move percentage"),
                        "keyReasonKu" to Schema.string(description = "Detailed Kurdish explanation with exact numbers")
                    )
                ),
                description = "Top 4 high-probability trading opportunities on OKX right now"
            )
        )
    )

    private val screenshotSymbolSchema = Schema.obj(
        properties = mapOf(
            "baseSymbol" to Schema.string(
                description = "Detected cryptocurrency ticker symbol from the screenshot (e.g. BTC, ETH, SOL, TON, XRP, PEPE, DOGE, SUI)"
            ),
            "isSwapOrFutures" to Schema.boolean(
                description = "True if the screenshot shows a perpetual swap / futures position, false if spot"
            ),
            "detectedBuyPrice" to Schema.double(
                description = "Detected entry/buy price from the screenshot, or 0.0 if not visible"
            ),
            "detectedInvestedUsdt" to Schema.double(
                description = "Detected invested amount / margin in USDT from the screenshot, or 100.0 if not visible"
            )
        )
    )

    private val screenshotExitPlanSchema = Schema.obj(
        properties = mapOf(
            "detectedInstId" to Schema.string(
                description = "OKX instrument ID, e.g. SOL-USDT or BTC-USDT-SWAP"
            ),
            "positionTypeKu" to Schema.string(
                description = "Position type in Central Kurdish, e.g. کڕینی سپۆت (Spot) or گرێبەستی فیوچەرز (Long)"
            ),
            "detectedBuyPrice" to Schema.double(
                description = "The user's buy/entry price read from the image (or current live price if not shown)"
            ),
            "detectedInvestedUsdt" to Schema.double(
                description = "The user's invested capital in USDT read from the image (or 100.0 default)"
            ),
            "currentStatusSummaryKu" to Schema.string(
                description = "Kurdish summary of the user's current position status comparing their buy price to live OKX price"
            ),
            "urgentActionVerdictKu" to Schema.string(
                description = "Direct, bold Central Kurdish instruction telling the user at what exact price to sell for profit, e.g., لە نرخی $192.40 بیفرۆشە قازانجی سەرەکیتە (+8.4%)"
            ),
            "confidencePercent" to Schema.integer(
                description = "AI confidence percentage (1 to 99)"
            ),
            "goldenExitPrice" to Schema.double(
                description = "The single most optimal price to sell the coin for maximum realistic profit before whale resistance walls"
            ),
            "goldenExitProfitPercent" to Schema.double(
                description = "Exact percentage profit when selling at goldenExitPrice compared to detectedBuyPrice"
            ),
            "goldenExitEstimatedProfitUsdt" to Schema.double(
                description = "Estimated profit in USDT when selling at goldenExitPrice"
            ),
            "sellTarget1Price" to Schema.double(
                description = "First conservative sell price (TP1) just below first resistance"
            ),
            "sellTarget1ProfitPercent" to Schema.double(
                description = "Profit percentage at sellTarget1Price"
            ),
            "sellTarget1AllocationPercent" to Schema.integer(
                description = "Percentage of coins to sell at Target 1 (e.g., 40)"
            ),
            "sellTarget2Price" to Schema.double(
                description = "Second primary sell price (TP2) at medium resistance"
            ),
            "sellTarget2ProfitPercent" to Schema.double(
                description = "Profit percentage at sellTarget2Price"
            ),
            "sellTarget2AllocationPercent" to Schema.integer(
                description = "Percentage of coins to sell at Target 2 (e.g., 40)"
            ),
            "sellTarget3Price" to Schema.double(
                description = "Third extended sell price (TP3) at major breakout resistance"
            ),
            "sellTarget3ProfitPercent" to Schema.double(
                description = "Profit percentage at sellTarget3Price"
            ),
            "sellTarget3AllocationPercent" to Schema.integer(
                description = "Percentage of coins to sell at Target 3 (e.g., 20)"
            ),
            "emergencyStopLossPrice" to Schema.double(
                description = "Exact price to sell/exit if the market drops to protect capital/profit"
            ),
            "holdDurationEstimateKu" to Schema.string(
                description = "Estimated time to reach the sell target in Kurdish, e.g., ٢ بۆ ٦ کاتژمێری داهاتوو"
            ),
            "whaleResistanceWallNoteKu" to Schema.string(
                description = "Kurdish explanation of where the OKX whale sell walls are located so the user sells slightly below them"
            ),
            "detailedSellStrategyKu" to Schema.string(
                description = "Step-by-step Central Kurdish guide on how to place Limit Sell / OCO / Take-Profit orders on OKX at these exact numbers"
            )
        )
    )

    private val structuredPredictionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = predictionSchema
            }
        )
    }

    private val structuredScannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = scannerSchema
            }
        )
    }

    private val screenshotSymbolExtractorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = screenshotSymbolSchema
            }
        )
    }

    private val screenshotExitPlanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = screenshotExitPlanSchema
            }
        )
    }

    private val advisorStreamModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    suspend fun detectCoinSymbolFromScreenshot(
        bitmap: Bitmap,
        userNoteKu: String,
        fallbackInstId: String
    ): String {
        return withTimeout(45_000L) {
            try {
                val response = screenshotSymbolExtractorModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            "Extract the cryptocurrency symbol (e.g., BTC, ETH, SOL, TON, XRP, DOGE, PEPE, SUI, BNB, ADA, AVAX, LINK) from this trading/wallet screenshot and optional user note: '$userNoteKu'. If no symbol is visible, use the symbol from '$fallbackInstId'."
                        )
                    }
                )
                val json = JSONObject(response.text ?: "{}")
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fallback to active instId during screenshot symbol detection", e)
                fallbackInstId
            }
        }
    }

    suspend fun analyzeTradeScreenshotForExitPlan(
        bitmap: Bitmap,
        userNoteKu: String,
        telemetry: OkxInstrumentDeepTelemetry
    ): AiScreenshotExitPlan {
        val promptText = buildString {
            appendLine("You are an elite institutional OKX trading AI assistant.")
            appendLine("The user has bought a coin (or opened a position) and sent you this screenshot so you can tell them the EXACT price at which to sell it for maximum realistic profit ('لەوەندە بیفرۆشە قازانجتە').")
            if (userNoteKu.isNotBlank()) {
                appendLine("User's additional note: \"$userNoteKu\"")
            }
            appendLine("\n1. Read the screenshot carefully to extract:")
            appendLine("   - The coin/pair bought")
            appendLine("   - Their buy/entry price (if visible in the screenshot or user note; if not visible, use the current live OKX price ${telemetry.ticker.lastPrice})")
            appendLine("   - Their invested amount in USDT or coin quantity (if visible; otherwise assume $100 USDT for profit calculation)")
            appendLine("   - Whether it is Spot, Long, or Short.")
            appendLine("\n2. Cross-reference their position with the following REAL-TIME OKX v5 Market & Order Book Telemetry for ${telemetry.ticker.instId}:")
            appendLine(buildTelemetryPrompt(telemetry))
            appendLine("\n3. Calculate the EXACT optimal sell prices:")
            appendLine("   - `goldenExitPrice`: The single best price to sell for maximum high-probability profit (place it slightly BELOW major OKX Whale Ask Resistance Walls or Pivot R1/R2 so the order fills cleanly!).")
            appendLine("   - `sellTarget1Price` (TP1 - Safe lock-in profit), `sellTarget2Price` (TP2 - Core profit target), `sellTarget3Price` (TP3 - Breakout target).")
            appendLine("   - `emergencyStopLossPrice`: The exact protective stop-loss price based on Support S1 / ATR / Whale Bid Support Walls.")
            appendLine("   - Write all explanations (`urgentActionVerdictKu`, `currentStatusSummaryKu`, `whaleResistanceWallNoteKu`, `detailedSellStrategyKu`) in clear, decisive Central Kurdish (Sorani - کوردیی سۆرانی) with exact numbers.")
        }

        return withTimeout(60_000L) {
            try {
                val response = screenshotExitPlanModel.generateContent(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                )
                val rawJson = response.text
                    ?: throw IllegalStateException("وەڵامی زیرەکی دەستکرد بۆ شیکاری وێنەکە بەتاڵ بوو.")
                parseScreenshotExitPlanJson(telemetry.ticker.instId, telemetry.ticker.lastPrice, rawJson)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to analyze trade screenshot with Gemini AI", e)
                throw e
            }
        }
    }

    suspend fun generateDeepInstrumentPrediction(
        telemetry: OkxInstrumentDeepTelemetry
    ): AiTradingPrediction {
        val prompt = buildTelemetryPrompt(telemetry)
        return withTimeout(60_000L) {
            try {
                val response = structuredPredictionModel.generateContent(prompt)
                val rawJson = response.text
                    ?: throw IllegalStateException("وەڵامی زیرەکی دەستکرد بەتاڵ بوو.")
                parsePredictionJson(telemetry.ticker.instId, telemetry.ticker.lastPrice, rawJson)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI prediction failed for ${telemetry.ticker.instId}", e)
                throw e
            }
        }
    }

    suspend fun scanTopMarketOpportunities(
        tickers: List<OkxMarketTicker>,
        activeDeepTelemetry: OkxInstrumentDeepTelemetry?
    ): Pair<String, List<AiMarketOpportunityItem>> {
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
            payloadBuilder.appendLine("\n--- DEEP MULTI-TIMEFRAME TELEMETRY FOR ${activeDeepTelemetry.ticker.instId} ---")
            activeDeepTelemetry.timeframeMetrics.forEach { (tf, m) ->
                payloadBuilder.appendLine(
                    "TF $tf: Close=${QuantMathEngine.formatCryptoPrice(m.lastClose)}, RSI14=${String.format(Locale.US, "%.2f", m.rsi14)}, MACD_Hist=${String.format(Locale.US, "%.6f", m.macdHistogram)}, EMA20=${QuantMathEngine.formatCryptoPrice(m.ema20)}, EMA50=${QuantMathEngine.formatCryptoPrice(m.ema50)}, ATR=${QuantMathEngine.formatCryptoPrice(m.atr14)}, S1=${QuantMathEngine.formatCryptoPrice(m.support1)}, R1=${QuantMathEngine.formatCryptoPrice(m.resistance1)}"
                )
            }
        }

        return withTimeout(60_000L) {
            try {
                val response = structuredScannerModel.generateContent(payloadBuilder.toString())
                val rawJson = response.text
                    ?: throw IllegalStateException("وەڵامی سکێنەری AI بەتاڵ بوو.")
                val root = JSONObject(rawJson)
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
                regimeKu to list
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI market scanner failed", e)
                throw e
            }
        }
    }

    fun streamAdvisorResponse(
        userQuestionKu: String,
        attachedBitmap: Bitmap? = null,
        activeTelemetry: OkxInstrumentDeepTelemetry?,
        topTickers: List<OkxMarketTicker>
    ): Flow<String> {
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

        val inputContent = content {
            if (attachedBitmap != null) {
                image(attachedBitmap)
            }
            text(contextPrompt)
        }

        return advisorStreamModel.generateContentStream(inputContent).map { chunk ->
            chunk.text.orEmpty()
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
        val json = JSONObject(rawJson)
        val detectedBuyPx = json.optDouble("detectedBuyPrice", liveOkxPrice).let {
            if (it <= 0.0 || it.isNaN()) liveOkxPrice else it
        }
        val investedUsdt = json.optDouble("detectedInvestedUsdt", 100.0).let {
            if (it <= 0.0 || it.isNaN()) 100.0 else it
        }
        val currentPnlPct = if (detectedBuyPx > 0) {
            ((liveOkxPrice - detectedBuyPx) / detectedBuyPx) * 100.0
        } else 0.0

        val goldenExitPx = json.optDouble("goldenExitPrice", liveOkxPrice * 1.032)
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
            sellTarget1Price = json.optDouble("sellTarget1Price", liveOkxPrice * 1.018),
            sellTarget1ProfitPercent = json.optDouble("sellTarget1ProfitPercent", 1.8),
            sellTarget1AllocationPercent = json.optInt("sellTarget1AllocationPercent", 40),
            sellTarget2Price = json.optDouble("sellTarget2Price", goldenExitPx),
            sellTarget2ProfitPercent = json.optDouble("sellTarget2ProfitPercent", goldenProfitPct),
            sellTarget2AllocationPercent = json.optInt("sellTarget2AllocationPercent", 40),
            sellTarget3Price = json.optDouble("sellTarget3Price", liveOkxPrice * 1.06),
            sellTarget3ProfitPercent = json.optDouble("sellTarget3ProfitPercent", 6.0),
            sellTarget3AllocationPercent = json.optInt("sellTarget3AllocationPercent", 20),
            emergencyStopLossPrice = json.optDouble("emergencyStopLossPrice", liveOkxPrice * 0.98),
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
        val json = JSONObject(rawJson)
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
            entryZoneMin = json.optDouble("entryZoneMin", spotPrice * 0.997),
            entryZoneMax = json.optDouble("entryZoneMax", spotPrice * 1.002),
            takeProfit1 = json.optDouble("takeProfit1", spotPrice * 1.015),
            takeProfit2 = json.optDouble("takeProfit2", spotPrice * 1.032),
            takeProfit3 = json.optDouble("takeProfit3", spotPrice * 1.055),
            stopLoss = json.optDouble("stopLoss", spotPrice * 0.982),
            riskRewardRatio = json.optDouble("riskRewardRatio", 2.5),
            forecast1hPrice = json.optDouble("forecast1hPrice", spotPrice),
            forecast4hPrice = json.optDouble("forecast4hPrice", spotPrice),
            forecast24hPrice = json.optDouble("forecast24hPrice", spotPrice),
            winProbabilityPercent = json.optInt("winProbabilityPercent", 72).coerceIn(1, 99),
            multiTimeframeAlignmentKu = json.optString("multiTimeframeAlignmentKu", ""),
            whaleOrderBookSummaryKu = json.optString("whaleOrderBookSummaryKu", ""),
            keyNumericTriggersKu = triggers,
            detailedKurdishAnalysis = json.optString("detailedKurdishAnalysis", "")
        )
    }
}
