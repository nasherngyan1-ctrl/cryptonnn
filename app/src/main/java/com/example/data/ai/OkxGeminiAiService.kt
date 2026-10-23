package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.OkxTradingDao
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
 * Supports Unlimited Multi-Image analysis, automatic coin & price recognition from screenshots/text,
 * and real-time OKX Multi-Timeframe Quantitative Synthesis.
 */
class OkxGeminiAiService(
    dao: OkxTradingDao
) {
    val keyRouter = MultiKeyAiRouter(dao)

    companion object {
        // Comprehensive Kurdish, Arabic & English Full-Name to Symbol dictionary
        val MULTI_LANG_COIN_ALIASES: Map<String, String> = mapOf(
            // Kurdish & Arabic names
            "بیتکۆین" to "BTC",
            "بیتکوین" to "BTC",
            "بتکوین" to "BTC",
            "بيتكوين" to "BTC",
            "ئیتیریۆم" to "ETH",
            "ئیثیریۆم" to "ETH",
            "ئیتریۆم" to "ETH",
            "ایتریوم" to "ETH",
            "ايثريوم" to "ETH",
            "سۆلانا" to "SOL",
            "سولانا" to "SOL",
            "تۆن" to "TON",
            "تون" to "TON",
            "تۆنکۆین" to "TON",
            "ریپڵ" to "XRP",
            "ڕیپڵ" to "XRP",
            "ریپل" to "XRP",
            "ئێکس ئاڕ پی" to "XRP",
            "دۆج" to "DOGE",
            "دوج" to "DOGE",
            "دۆجکۆین" to "DOGE",
            "پێپێ" to "PEPE",
            "پیپی" to "PEPE",
            "پێپی" to "PEPE",
            "بيبي" to "PEPE",
            "شیبا" to "SHIB",
            "شیب" to "SHIB",
            "ترۆن" to "TRX",
            "ترون" to "TRX",
            "کاردانۆ" to "ADA",
            "کاردانو" to "ADA",
            "ئەدا" to "ADA",
            "سوی" to "SUI",
            "ئاڤاکس" to "AVAX",
            "ئەڤالانچ" to "AVAX",
            "لینک" to "LINK",
            "چەینلینک" to "LINK",
            "دۆت" to "DOT",
            "پۆڵکادۆت" to "DOT",
            "نێر" to "NEAR",
            "لایتکۆین" to "LTC",
            "یونی" to "UNI",
            "ئاپتۆس" to "APT",
            "ئاربیتڕۆم" to "ARB",
            "ئۆپتیمیزم" to "OP",
            "فایلکۆین" to "FIL",
            "ئاتۆم" to "ATOM",
            "ئەتۆم" to "ATOM",
            "ئینج" to "INJ",
            "ڕێندەر" to "RENDER",
            "فێت" to "FET",
            "ویف" to "WIF",
            "بۆنک" to "BONK",
            "بونک" to "BONK",
            "فلوکی" to "FLOKI",
            "فلۆکی" to "FLOKI",
            "نۆت" to "NOT",
            "نۆتکۆین" to "NOT",
            "دۆگز" to "DOGS",
            "هامستەر" to "HMSTR",
            "سێی" to "SEI",
            "تیا" to "TIA",
            "ئۆندۆ" to "ONDO",
            "ئێنا" to "ENA",
            "گالا" to "GALA",
            "ساند" to "SAND",
            "ماتیک" to "POL",
            "پۆلیگۆن" to "POL",
            "ورڵدکۆین" to "WLD",
            "ئۆردی" to "ORDI",
            "تۆربۆ" to "TURBO",
            "ترەمپ" to "TRUMP",
            "پای" to "PI",
            "کاسپا" to "KAS",
            "ئێچ بار" to "HBAR",
            "ستێلار" to "XLM",
            // English full names
            "BITCOIN" to "BTC",
            "ETHEREUM" to "ETH",
            "SOLANA" to "SOL",
            "RIPPLE" to "XRP",
            "DOGECOIN" to "DOGE",
            "TONCOIN" to "TON",
            "SHIBA" to "SHIB",
            "CARDANO" to "ADA",
            "AVALANCHE" to "AVAX",
            "CHAINLINK" to "LINK",
            "TRON" to "TRX",
            "POLKADOT" to "DOT",
            "LITECOIN" to "LTC",
            "UNISWAP" to "UNI",
            "APTOS" to "APT",
            "ARBITRUM" to "ARB",
            "OPTIMISM" to "OP",
            "FILECOIN" to "FIL",
            "COSMOS" to "ATOM",
            "INJECTIVE" to "INJ",
            "DOGWIFHAT" to "WIF",
            "NOTCOIN" to "NOT",
            "HAMSTER" to "HMSTR",
            "CELESTIA" to "TIA",
            "WORLDCOIN" to "WLD",
            "KASPA" to "KAS",
            "HEDERA" to "HBAR",
            "STELLAR" to "XLM",
            "ALGORAND" to "ALGO",
            "VECHAIN" to "VET",
            "FANTOM" to "FTM",
            "POLYGON" to "POL"
        )

        val KNOWN_CRYPTO_SYMBOLS: Set<String> = setOf(
            "BTC", "ETH", "SOL", "TON", "XRP", "DOGE", "PEPE", "SUI", "BNB",
            "ADA", "AVAX", "LINK", "TRX", "SHIB", "DOT", "NEAR", "LTC", "BCH",
            "UNI", "APT", "ARB", "OP", "FIL", "ATOM", "INJ", "RNDR", "RENDER",
            "FET", "WIF", "BONK", "FLOKI", "NOT", "DOGS", "HMSTR", "SEI", "TIA",
            "ONDO", "ENA", "JASMY", "GALA", "SAND", "MANA", "AAVE", "MKR", "LDO",
            "CRV", "PENDLE", "EIGEN", "WLD", "ORDI", "SATS", "TURBO", "NEIRO",
            "PNUT", "ACT", "TRUMP", "PI", "KAS", "HBAR", "XLM", "ALGO", "ICP",
            "VET", "ETC", "FLOW", "THETA", "EGLD", "XTZ", "EOS", "NEO", "KAVA",
            "MINA", "ROSE", "ZIL", "IOTA", "1INCH", "ENS", "GRT", "IMX", "APE",
            "BLUR", "CHZ", "DYDX", "GMX", "JUP", "RAY", "PYTH", "JTO", "ETHFI",
            "BOME", "PENGU", "MOVE", "VANA", "BIO", "VIRTUAL", "AIXBT", "POL", "FTM"
        )

        private val IGNORED_WORDS = setOf(
            "USDT", "USDC", "USD", "SPOT", "SWAP", "PERP", "PERPETUAL", "LONG", "SHORT",
            "BUY", "SELL", "OKX", "BINANCE", "BYBIT", "TRADING", "CHART", "PRICE", "COIN",
            "CRYPTO", "PROFIT", "LOSS", "STOP", "TAKE", "LIMIT", "MARKET", "ORDER", "HIGH",
            "LOW", "VOL", "VOLUME", "RSI", "MACD", "EMA", "MA", "BOLL", "SAR", "KDJ", "THE",
            "AND", "FOR", "WITH", "FROM", "IMG", "SCREENSHOT", "IMAGE", "PHOTO", "PNG", "JPG",
            "JPEG", "WEBP", "SYMBOL", "NONE", "NULL", "TRUE", "FALSE", "AUTO", "API"
        )

        /**
         * Extracts a valid OKX instrument ID (e.g. "SOL-USDT", "PEPE-USDT") from any raw text,
         * AI vision response, or filename. Returns null if no coin symbol is found.
         */
        fun extractCoinSymbolFromAnyText(
            rawText: String,
            liveTickers: List<OkxMarketTicker>
        ): String? {
            if (rawText.isBlank()) return null
            val upper = rawText.uppercase(Locale.US)
            val tickerMap = liveTickers.associateBy { it.baseSymbol.uppercase(Locale.US) }

            // 1. Check explicit SYMBOL=XYZ or [COIN: XYZ] or "baseSymbol": "XYZ"
            val explicitPatterns = listOf(
                Regex("""SYMBOL\s*[:=]\s*["']?\$?([A-Z0-9]{2,12})"""),
                Regex("""\[COIN\s*[:=]\s*\$?([A-Z0-9]{2,12})(?:-USDT)?]"""),
                Regex(""""baseSymbol"\s*:\s*"\$?([A-Z0-9]{2,12})""")
            )
            for (regex in explicitPatterns) {
                val match = regex.find(upper)
                val candidate = match?.groupValues?.getOrNull(1)
                    ?.removeSuffix("USDT")
                    ?.removeSuffix("USDC")
                    ?.removeSuffix("USD")
                    ?.trim()
                if (!candidate.isNullOrBlank() && candidate !in IGNORED_WORDS) {
                    if (tickerMap.containsKey(candidate)) return tickerMap[candidate]!!.instId
                    if (candidate in KNOWN_CRYPTO_SYMBOLS) return "$candidate-USDT"
                }
            }

            // 2. Check explicit pair format in text/OCR like "SOL/USDT", "PEPE-USDT", "XRP / USDT", "DOGEUSDT"
            val pairRegex = Regex("""\b([A-Z0-9]{2,10})\s*[/\-_]\s*(?:USDT|USDC|USD)\b""")
            val pairMatch = pairRegex.find(upper)
            if (pairMatch != null) {
                val base = pairMatch.groupValues[1].trim()
                if (base !in IGNORED_WORDS) {
                    if (tickerMap.containsKey(base)) return tickerMap[base]!!.instId
                    if (base in KNOWN_CRYPTO_SYMBOLS) return "$base-USDT"
                }
            }

            val concatPairRegex = Regex("""\b([A-Z0-9]{2,10})USDT\b""")
            val concatMatch = concatPairRegex.find(upper)
            if (concatMatch != null) {
                val base = concatMatch.groupValues[1].trim()
                if (base !in IGNORED_WORDS) {
                    if (tickerMap.containsKey(base)) return tickerMap[base]!!.instId
                    if (base in KNOWN_CRYPTO_SYMBOLS) return "$base-USDT"
                }
            }

            // 3. Check Kurdish, Arabic, and English full coin names
            for ((alias, symbol) in MULTI_LANG_COIN_ALIASES) {
                if (rawText.contains(alias, ignoreCase = true)) {
                    return tickerMap[symbol]?.instId ?: "$symbol-USDT"
                }
            }

            // 4. Tokenize alphanumeric words and match against known & live OKX symbols
            val tokens = Regex("""[A-Z0-9]{2,10}""").findAll(upper).map { it.value }.toList()
            for (token in tokens) {
                val clean = token.removeSuffix("USDT").removeSuffix("USDC")
                if (clean.length >= 2 && clean !in IGNORED_WORDS) {
                    if (clean in KNOWN_CRYPTO_SYMBOLS) {
                        return tickerMap[clean]?.instId ?: "$clean-USDT"
                    }
                    if (tickerMap.containsKey(clean) && clean.length >= 3) {
                        return tickerMap[clean]!!.instId
                    }
                }
            }

            return null
        }
    }

    /**
     * Reads the attached screenshot(s) using the user's configured Vision AI key(s)
     * to identify the exact coin symbol AND any entry/buy price shown in the image.
     */
    suspend fun detectCoinSymbolFromScreenshots(
        bitmaps: List<Bitmap>,
        userNoteKu: String,
        liveTickers: List<OkxMarketTicker>,
        imageNamesHint: String = "",
        onKeySwitch: ((String) -> Unit)? = null
    ): Pair<String?, Double?> = withContext(Dispatchers.IO) {
        // 1. First check if the user already wrote the coin name in their text message
        val fromNote = extractCoinSymbolFromAnyText(userNoteKu, liveTickers)

        if (bitmaps.isEmpty()) {
            return@withContext fromNote to null
        }

        // 2. Ask Vision AI to read the exact coin ticker & price from the screenshot(s)
        val prompt = """
            Look carefully at the attached cryptocurrency trading/wallet/chart screenshot(s).
            Read the exact coin/token symbol shown in the screenshot (for example: SOL, PEPE, ETH, XRP, DOGE, TON, SUI, SHIB, ADA, AVAX, TRX, LINK, DOT, NEAR, LTC, APT, ARB, OP, FIL, INJ, FET, WIF, BONK, FLOKI, NOT, DOGS, HMSTR, SEI, TIA, ONDO, ENA, GALA, SAND, ATOM, UNI, BNB, BTC, TRUMP, PI, etc.).
            Also read the entry/buy price or current chart price shown in the image.
            Reply in this exact format on the first two lines:
            SYMBOL=SOL
            PRICE=180.50
        """.trimIndent()

        val rotatedResult = runCatching {
            keyRouter.generateWithAutoRotation(
                prompt = prompt,
                expectJson = false,
                bitmaps = bitmaps,
                onKeySwitchCallback = onKeySwitch
            )
        }.getOrNull()

        if (rotatedResult != null && rotatedResult.responseText.isNotBlank()) {
            val rawReply = rotatedResult.responseText
            val detectedFromAi = extractCoinSymbolFromAnyText(rawReply, liveTickers)
            val priceMatch = Regex("""PRICE\s*[:=]\s*\$?([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
                .find(rawReply)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()

            if (detectedFromAi != null) {
                return@withContext detectedFromAi to priceMatch
            }

            // If AI read a price from the screenshot but the symbol was cropped out, match the price against live OKX tickers!
            if (priceMatch != null && priceMatch > 0.0 && liveTickers.isNotEmpty()) {
                val matchedByPrice = liveTickers.minByOrNull { ticker ->
                    abs(ticker.lastPrice - priceMatch) / max(ticker.lastPrice, 1e-9)
                }
                if (matchedByPrice != null) {
                    val relDiff = abs(matchedByPrice.lastPrice - priceMatch) / max(matchedByPrice.lastPrice, 1e-9)
                    if (relDiff < 0.035) {
                        return@withContext matchedByPrice.instId to priceMatch
                    }
                }
            }
        }

        // 3. Fallback to user note or image filename hint
        val fromFilename = extractCoinSymbolFromAnyText(imageNamesHint, liveTickers)
        (fromNote ?: fromFilename) to null
    }

    fun streamAdvisorResponse(
        userQuestionKu: String,
        attachedBitmaps: List<Bitmap> = emptyList(),
        activeTelemetry: OkxInstrumentDeepTelemetry?,
        detectedBuyPriceFromImage: Double? = null,
        topTickers: List<OkxMarketTicker>,
        onKeySwitch: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        val contextPrompt = buildString {
            appendLine("تۆ ڕاوێژکاری زیرەکی دراوە دیجیتاڵییەکانیت لە ئەپی «abdulbasit traiding».")
            appendLine("وەڵامەکەت با زۆر ڕوون، سادە، کورت و پوخت، و پڕ بێت لە ژمارەی دەقیقی کڕین و فرۆشتن بە زمانی کوردی (سۆرانی).")
            if (attachedBitmaps.isNotEmpty()) {
                appendLine("گرنگ: بەکارهێنەر ${attachedBitmaps.size} وێنەی ناردووە. یەکەم جار بە وردی سەیری ناو وێنەکە بکە بزانە چ دراوێکە (Symbol) و نرخی کڕین یان چارتەکەی چەندە، و لەسەر هێڵی یەکەمی وەڵامەکەت بنووسە: [COIN: ناوی_دراو-USDT]")
                if (detectedBuyPriceFromImage != null && detectedBuyPriceFromImage > 0.0) {
                    appendLine("نرخی خوێندراوە لەناو وێنەکەدا: $$detectedBuyPriceFromImage")
                }
            } else if (activeTelemetry != null) {
                appendLine("لەسەر هێڵی یەکەمی وەڵامەکەت بنووسە: [COIN: ${activeTelemetry.ticker.instId}]")
            }

            if (activeTelemetry != null) {
                appendLine("\n=== داتای زیندووی ئێستای دراوی (${activeTelemetry.ticker.instId}) لە OKX ===")
                appendLine(buildTelemetryPrompt(activeTelemetry))
            }

            appendLine("\n=== نرخی زیندووی ٤٠ دراوی سەرەکی ئێستای بازاڕی OKX ===")
            topTickers.take(40).forEach { t ->
                appendLine(
                    "• ${t.instId}: نرخ=$${QuantMathEngine.formatCryptoPrice(t.lastPrice)} | گۆڕانی ٢٤س=${QuantMathEngine.formatSignedPercent(t.change24hPercent)} | بەرزترین=$${QuantMathEngine.formatCryptoPrice(t.high24h)} | نزمترین=$${QuantMathEngine.formatCryptoPrice(t.low24h)}"
                )
            }

            appendLine("\nپرسیار / داواکاری بەکارهێنەر:")
            appendLine(userQuestionKu)
            appendLine(
                """
                
                لە وەڵامەکەتدا ڕاستەوخۆ و بە سادەیی ئەم خاڵانە بنووسە:
                ١) 📌 ناوی دراوەکە و نرخی زیندووی ئێستا.
                ٢) 🟢 لە چەند بیکڕم؟ (باشترین نرخی کڕین بە ژمارەی تەواو).
                ٣) 🎯 کە گەیشتە چەند بیفرۆشم قازانجمە؟ (نرخی فرۆشتنی قازانج TP1 و TP2 بە ژمارە و ڕێژەی قازانج %).
                ٤) 🛡️ ستۆپ لۆس (Stop-Loss) لە چ نرخێکدا بێت.
                ٥) 💡 هۆکاری کورت و ڕوون (بەپێی تایمفرەیمەکان و دیواری نەهەنگەکان).
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
            emit(rotatedResult.responseText)
            return@flow
        }

        emit(buildQuantFallbackAdvisorAnswer(userQuestionKu, activeTelemetry, topTickers, attachedBitmaps.size))
    }

    /**
     * Computes exact mathematical Buy & Sell targets directly from live OKX 4-Timeframe + Order Book telemetry.
     */
    fun computeTargetsFromTelemetry(
        telemetry: OkxInstrumentDeepTelemetry,
        userBuyPriceHint: Double? = null
    ): AiTradingPrediction {
        return buildQuantFallbackPrediction(telemetry, userBuyPriceHint)
    }

    private fun buildQuantFallbackPrediction(
        telemetry: OkxInstrumentDeepTelemetry,
        userBuyPriceHint: Double? = null
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
            "STRONG_LONG" -> "کڕینی بەهێز (Strong Buy)"
            "LONG" -> "گونجاوە بۆ کڕین (Buy)"
            "STRONG_SHORT" -> "ئاگاداربە لە دابەزین (Sell/Wait)"
            "SHORT" -> "چاوەڕوانی دابەزین بکە بۆ کڕین"
            else -> "چاوەڕوانی خاڵی باشتر (Neutral)"
        }

        // Best Buy Price: slightly below current price at Support 1 / ATR pullback
        val s1 = tf1h?.support1?.takeIf { it in (px * 0.90)..(px * 0.998) } ?: (px - atr * 0.45)
        val entryMin = max(0.0, s1)
        val entryMax = px

        val refBuyPx = if (userBuyPriceHint != null && userBuyPriceHint > 0.0 && abs(userBuyPriceHint - px) / px < 0.5) {
            userBuyPriceHint
        } else {
            px
        }

        val whaleAskWall = telemetry.orderBookReport?.topResistanceAskWalls
            ?.filter { it.price > refBuyPx * 1.005 }
            ?.minByOrNull { it.price }
            ?.price

        val r1 = tf1h?.resistance1?.takeIf { it > refBuyPx * 1.006 } ?: (refBuyPx + atr * 1.3)
        val r2 = tf1h?.resistance2?.takeIf { it > r1 } ?: (refBuyPx + atr * 2.4)

        val tp1 = max(refBuyPx * 1.012, r1)
        val tp2 = if (whaleAskWall != null && whaleAskWall > tp1) {
            min(r2, whaleAskWall * 0.998)
        } else {
            max(tp1 * 1.015, r2)
        }
        val tp3 = max(tp2 * 1.022, refBuyPx + (atr * 3.8))
        val sl = max(0.0, min(px, refBuyPx) - (atr * 1.15))

        val riskDist = abs(px - sl).coerceAtLeast(1e-8)
        val rewardDist = abs(tp2 - px)
        val rr = (rewardDist / riskDist).coerceIn(1.2, 6.0)
        val confidence = (72 + (abs(score) / 4)).coerceIn(70, 95)

        return AiTradingPrediction(
            instId = t.instId,
            spotPriceAtAnalysis = px,
            signalDirection = direction,
            signalVerdictKu = verdictKu,
            confidencePercent = confidence,
            riskLevelKu = if (t.volatility24hPercent > 8.0) "بەرز" else "مامناوەند",
            recommendedLeverage = "Spot / 3x",
            entryZoneMin = entryMin,
            entryZoneMax = entryMax,
            takeProfit1 = tp1,
            takeProfit2 = tp2,
            takeProfit3 = tp3,
            stopLoss = sl,
            riskRewardRatio = rr,
            forecast1hPrice = if (isBull) px + atr * 0.65 else max(0.0, px - atr * 0.65),
            forecast4hPrice = if (isBull) px + atr * 1.55 else max(0.0, px - atr * 1.55),
            forecast24hPrice = if (isBull) px + atr * 2.85 else max(0.0, px - atr * 2.85),
            winProbabilityPercent = (confidence - 2).coerceIn(65, 93),
            multiTimeframeAlignmentKu = "15m=${tf15m?.quantScore ?: 0} | 1H=${tf1h?.quantScore ?: 0} | 4H=${tf4h?.quantScore ?: 0} | 1D=${tf1d?.quantScore ?: 0}",
            whaleOrderBookSummaryKu = telemetry.orderBookReport?.dominantSideSummaryKu.orEmpty(),
            keyNumericTriggersKu = emptyList(),
            detailedKurdishAnalysis = ""
        )
    }

    private fun buildQuantFallbackAdvisorAnswer(
        questionKu: String,
        telemetry: OkxInstrumentDeepTelemetry?,
        topTickers: List<OkxMarketTicker>,
        attachedImagesCount: Int
    ): String {
        val t = telemetry?.ticker ?: topTickers.firstOrNull()
            ?: return "تکایە چەند چرکەیەک چاوەڕێ بکە تا پەیوەندی بازاڕی OKX نوێ دەبێتەوە."
        val pred = telemetry?.let { buildQuantFallbackPrediction(it) }
        val tf1h = telemetry?.timeframeMetrics?.get("1H")
        val tf4h = telemetry?.timeframeMetrics?.get("4H")
        val ob = telemetry?.orderBookReport

        return buildString {
            if (attachedImagesCount > 0) {
                appendLine("💡 تێبینی: بۆ ئەوەی زیرەکی دەستکرد ڕاستەوخۆ وێنەکانت بخوێنێتەوە، لە بەشی ⚙️ سێتینگ کلیلێکی API (وەک Gemini یان Groq) داخڵ بکە، یان ناوی دراوەکە لەگەڵ وێنەکە بنووسە.\n")
            }
            appendLine("📌 شیکاری زیندووی ${t.instId}:")
            appendLine("• نرخی ئێستای بازاڕ: $${QuantMathEngine.formatCryptoPrice(t.lastPrice)} (${QuantMathEngine.formatSignedPercent(t.change24hPercent)})")
            if (pred != null) {
                val profitPct = ((pred.takeProfit2 - t.lastPrice) / max(t.lastPrice, 1e-9)) * 100.0
                appendLine("• بڕیاری بازاڕ: ${pred.signalVerdictKu} (دڵنیایی ${pred.confidencePercent}%)")
                appendLine("• 🟢 باشترین نرخی کڕین: لە $${QuantMathEngine.formatCryptoPrice(pred.entryZoneMin)} بیکڕە")
                appendLine("• 🎯 نرخی فرۆشتن بۆ قازانج: کە گەیشتە $${QuantMathEngine.formatCryptoPrice(pred.takeProfit2)} بیفرۆشە (${QuantMathEngine.formatSignedPercent(profitPct)} قازانج)")
                appendLine("• 📈 ئامانجی باڵا (TP3): $${QuantMathEngine.formatCryptoPrice(pred.takeProfit3)}")
                appendLine("• 🛡️ ستۆپ لۆس (پاراستنی سەرمایە): $${QuantMathEngine.formatCryptoPrice(pred.stopLoss)}")
            }
            if (tf1h != null) {
                appendLine("• دۆخی تەکنیکی: RSI(1H)=${String.format(Locale.US, "%.1f", tf1h.rsi14)} | RSI(4H)=${String.format(Locale.US, "%.1f", tf4h?.rsi14 ?: 50.0)} | پاڵپشتی=$${QuantMathEngine.formatCryptoPrice(tf1h.support1)} | بەرگری=$${QuantMathEngine.formatCryptoPrice(tf1h.resistance1)}")
            }
            if (ob != null) {
                appendLine("• هێزی کڕیاران لە OKX: ${String.format(Locale.US, "%.0f%%", ob.bidPressurePercent)} بەرامبەر ${String.format(Locale.US, "%.0f%%", ob.askPressurePercent)} فرۆشیاران.")
            }
        }
    }

    private fun buildTelemetryPrompt(telemetry: OkxInstrumentDeepTelemetry): String {
        val t = telemetry.ticker
        val sb = StringBuilder()
        sb.appendLine("Instrument: ${t.instId} | Live Price: ${QuantMathEngine.formatCryptoPrice(t.lastPrice)} USDT | 24h Change: ${QuantMathEngine.formatSignedPercent(t.change24hPercent)}")
        sb.appendLine("24h High: ${QuantMathEngine.formatCryptoPrice(t.high24h)} | 24h Low: ${QuantMathEngine.formatCryptoPrice(t.low24h)} | 24h Vol: ${QuantMathEngine.formatCompactUsdt(t.volume24hUsdt)}")
        listOf("15m", "1H", "4H", "1D").forEach { tf ->
            val m = telemetry.timeframeMetrics[tf]
            if (m != null) {
                sb.appendLine("[$tf] RSI:${String.format(Locale.US, "%.1f", m.rsi14)} | EMA20:${QuantMathEngine.formatCryptoPrice(m.ema20)} | EMA50:${QuantMathEngine.formatCryptoPrice(m.ema50)} | S1:${QuantMathEngine.formatCryptoPrice(m.support1)} | R1:${QuantMathEngine.formatCryptoPrice(m.resistance1)} | R2:${QuantMathEngine.formatCryptoPrice(m.resistance2)} | ATR:${QuantMathEngine.formatCryptoPrice(m.atr14)} | Score:${m.quantScore}/100")
            }
        }
        telemetry.orderBookReport?.let { ob ->
            sb.appendLine("OrderBook 50-Level: Bids=${String.format(Locale.US, "%.1f%%", ob.bidPressurePercent)} (${QuantMathEngine.formatCompactUsdt(ob.totalBidNotionalUsdt)}) vs Asks=${String.format(Locale.US, "%.1f%%", ob.askPressurePercent)}")
            sb.appendLine("Whale Support Walls: " + ob.topSupportBidWalls.take(3).joinToString { "${QuantMathEngine.formatCryptoPrice(it.price)}" })
            sb.appendLine("Whale Resistance Walls: " + ob.topResistanceAskWalls.take(3).joinToString { "${QuantMathEngine.formatCryptoPrice(it.price)}" })
        }
        return sb.toString()
    }
}
