package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ApiProviderType
import com.example.data.ai.OkxGeminiAiService
import com.example.data.local.ApiKeyEntity
import com.example.data.local.AppDatabase
import com.example.data.model.OkxMarketTicker
import com.example.data.remote.OkxApiService
import com.example.data.repository.OkxRepository
import com.example.domain.QuantMathEngine
import com.example.service.TradingBackgroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.math.max

private const val TAG = "OkxAiViewModel"

sealed interface AiAdvisorUiState {
    data object Idle : AiAdvisorUiState
    data class Loading(val stageKu: String) : AiAdvisorUiState
    data class Streaming(val partialText: String) : AiAdvisorUiState
    data class Success(val fullText: String) : AiAdvisorUiState
    data class Error(val message: String) : AiAdvisorUiState
}

data class AdvisorChatMessage(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val isUser: Boolean,
    val text: String,
    val detectedCoin: String? = null,
    val livePriceAtAnalysis: Double = 0.0,
    val buyAtPrice: Double = 0.0,
    val sellAtPrice: Double = 0.0,
    val sellTp1Price: Double = 0.0,
    val sellTp3Price: Double = 0.0,
    val stopLossPrice: Double = 0.0,
    val isTrackedInBackground: Boolean = false,
    val attachedImageCount: Int = 0,
    val previewBitmaps: List<Bitmap> = emptyList()
)

class OkxAiViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val dao = database.okxTradingDao()
    private val repository = OkxRepository(
        apiService = OkxApiService.create(),
        dao = dao
    )
    private val aiService = OkxGeminiAiService(dao = dao)

    // Application-scoped background coroutine so AI chat & image analysis finish even if app is minimized
    private val backgroundAiScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // API Key Manager Modal toggle
    private val _isApiKeyModalOpen = MutableStateFlow(false)
    val isApiKeyModalOpen: StateFlow<Boolean> = _isApiKeyModalOpen.asStateFlow()

    // Active AI Engine / Rotated Key Badge
    private val _activeAiEngineBadge = MutableStateFlow("چاتی گشتی دراوەکان + چاودێری باگراوند")
    val activeAiEngineBadge: StateFlow<String> = _activeAiEngineBadge.asStateFlow()

    private val _testingKeyId = MutableStateFlow<Int?>(null)
    val testingKeyId: StateFlow<Int?> = _testingKeyId.asStateFlow()

    // Background Service Status & Watched Coins Count
    val isBackgroundServiceRunning: StateFlow<Boolean> = TradingBackgroundService.isServiceRunning
    val watchedCoinsCount: StateFlow<Int> = TradingBackgroundService.watchedCoinsCount

    // Live OKX Tickers in memory for automatic coin detection & market context
    private val _tickers = MutableStateFlow<List<OkxMarketTicker>>(emptyList())
    val tickers: StateFlow<List<OkxMarketTicker>> = _tickers.asStateFlow()

    // AI Advisor Chat State & Messages
    private val _advisorState = MutableStateFlow<AiAdvisorUiState>(AiAdvisorUiState.Idle)
    val advisorState: StateFlow<AiAdvisorUiState> = _advisorState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<AdvisorChatMessage>>(
        listOf(
            AdvisorChatMessage(
                isUser = false,
                text = "سڵاو لە چاتی زیرەکی «abdulbasit traiding»! 🤖\n\n" +
                    "• ناوی هەر دراوێک بنووسە (وەک BTC, SOL, PEPE, ETH, TON, XRP, SUI...) یان پرسیار بکە تا پێت بڵێم لە چ نرخێکدا بیکڕیت و لە چ نرخێکدا بیفرۆشیت.\n" +
                    "• دەتوانیت بێسنوور وێنە (Screenshot) لە هەر جۆرە چارتێک یان کڕینی دراوێک بەیەکەوە دابنێیت و بنێریت؛ هەموویان بەیەکەوە دەخوێنمەوە و پێت دەڵێم: «کە گەیشتە ئەوەندە بیفرۆشە قازانجتە» یان «لەوەندە بیکڕە».\n" +
                    "• هەر نرخێکی کڕین و فرۆشتنت بۆ دیاری بکەم، لە باگراوند چاودێری دەکەم و کە گەیشتە نرخەکە بە نۆتیفیکەیشن ئاگادارت دەکەمەوە!"
            )
        )
    )
    val chatHistory: StateFlow<List<AdvisorChatMessage>> = _chatHistory.asStateFlow()

    // Status Banner
    private val _statusBannerKu = MutableStateFlow<String?>(null)
    val statusBannerKu: StateFlow<String?> = _statusBannerKu.asStateFlow()

    val apiKeys: StateFlow<List<ApiKeyEntity>> = dao.observeApiKeys().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var tickerLoopJob: Job? = null

    init {
        TradingBackgroundService.registerCoinTargetsInBackground(
            context = getApplication(),
            instId = "BTC-USDT"
        )
        startTickerPollingLoop()
    }

    fun setApiKeyModalOpen(open: Boolean) {
        _isApiKeyModalOpen.value = open
    }

    fun toggleBackgroundService(enable: Boolean) {
        val app = getApplication<Application>()
        if (enable) {
            TradingBackgroundService.registerCoinTargetsInBackground(
                context = app,
                instId = "BTC-USDT"
            )
            _statusBannerKu.value = "چاودێری باگراوند و نۆتیفیکەیشنی نرخی دراوەکان چالاک کرا"
        } else {
            TradingBackgroundService.stopBackgroundMonitor(app)
            _statusBannerKu.value = "چاودێری باگراوند ڕاگیرا"
        }
    }

    fun clearStatusBanner() {
        _statusBannerKu.value = null
    }

    private fun startTickerPollingLoop() {
        tickerLoopJob?.cancel()
        tickerLoopJob = backgroundAiScope.launch {
            while (isActive) {
                try {
                    val list = repository.fetchLiveMarketTickers("SPOT")
                    _tickers.value = list
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Background ticker refresh: ${e.message}")
                }
                delay(8_000L)
            }
        }
    }

    /**
     * Unified General Crypto Chat handler:
     * - Accepts text + UNLIMITED images (any number of screenshots).
     * - Automatically detects which coin the user is asking about (from Kurdish/English text or from the screenshots).
     * - Fetches live 4-timeframe (15m, 1H, 4H, 1D) & 50-level Order Book telemetry from OKX.
     * - Calculates exact Buy ("لەوەندە بیکڕە") and Sell ("کە گەیشتە ئەوەندە بیفرۆشە") targets.
     * - Registers those targets in the 24/7 Background Service so the user gets notified when price hits!
     */
    fun askAiAdvisor(questionKu: String, attachedImageUris: List<Uri> = emptyList()) {
        val trimmed = questionKu.trim().ifBlank {
            when {
                attachedImageUris.size > 1 ->
                    "ئەم ${attachedImageUris.size} وێنەیەم بۆ شیبکەرەوە؛ چ دراوێکە و پێم بڵێ کە گەیشتە چەند بیفرۆشم بۆ قازانج یان لە چەند بیکڕم؟"
                attachedImageUris.size == 1 ->
                    "ئەم دراوەم کڕیوە (یان ئەم وێنەیەم ناردووە)؛ پێم بڵێ کە گەیشتە چەند بیفرۆشم بۆ زۆرترین قازانج یان لە چەند بیکڕم؟"
                else -> ""
            }
        }
        if (trimmed.isEmpty()) return

        backgroundAiScope.launch {
            val totalCount = attachedImageUris.size
            val bitmaps = attachedImageUris.mapNotNull { decodeAndScaleBitmap(it, totalCount) }

            _chatHistory.value = _chatHistory.value + AdvisorChatMessage(
                isUser = true,
                text = trimmed,
                attachedImageCount = bitmaps.size,
                previewBitmaps = bitmaps
            )

            _advisorState.value = AiAdvisorUiState.Loading(
                stageKu = if (bitmaps.isNotEmpty()) {
                    "خوێندنەوەی هەر ${bitmaps.size} وێنەکە و ناسینەوەی دراوەکە بە زیرەکی دەستکرد..."
                } else {
                    "پشکنینی داتای زیندووی بازاڕی دراوەکان..."
                }
            )

            try {
                // Step 1: Detect the coin from user's text or attached screenshot(s)
                val coinFromText = extractCoinFromUserText(trimmed, _tickers.value)
                val detectedInstId = if (bitmaps.isNotEmpty()) {
                    aiService.detectCoinSymbolFromScreenshots(
                        bitmaps = bitmaps,
                        userNoteKu = trimmed,
                        fallbackInstId = coinFromText ?: "BTC-USDT"
                    )
                } else {
                    coinFromText ?: "BTC-USDT"
                }

                _advisorState.value = AiAdvisorUiState.Loading(
                    stageKu = "وەرگرتنی نرخی چرکەبەچرکە و ٤ تایمفرەیم و دیوارەکانی نەهەنگی $detectedInstId..."
                )

                // Step 2: Fetch deep multi-timeframe & order book telemetry for the detected coin
                val currentTickers = if (_tickers.value.isNotEmpty()) {
                    _tickers.value
                } else {
                    runCatching { repository.fetchLiveMarketTickers("SPOT") }.getOrDefault(emptyList()).also {
                        _tickers.value = it
                    }
                }

                val telemetry = runCatching {
                    val fallbackTicker = currentTickers.find { it.instId == detectedInstId }
                    repository.fetchDeepInstrumentTelemetry(detectedInstId, fallbackTicker)
                }.getOrNull()

                // Step 3: Compute exact Buy ("لەوەندە بیکڕە") and Sell ("کە گەیشتە ئەوەندە بیفرۆشە") targets
                var buyTargetPx = 0.0
                var sellTargetPx = 0.0
                var sellTp1Px = 0.0
                var sellTp3Px = 0.0
                var stopLossPx = 0.0
                var livePx = telemetry?.ticker?.lastPrice ?: 0.0

                if (telemetry != null) {
                    if (bitmaps.isNotEmpty()) {
                        _advisorState.value = AiAdvisorUiState.Loading(
                            stageKu = "هەژمارکردنی نرخی وردی فرۆشتن و کڕین بۆ $detectedInstId..."
                        )
                        val (exitPlan, badge) = aiService.analyzeTradeScreenshotsForExitPlan(
                            bitmaps = bitmaps,
                            userNoteKu = trimmed,
                            telemetry = telemetry
                        )
                        _activeAiEngineBadge.value = badge
                        livePx = exitPlan.currentLiveOkxPrice
                        val tf1h = telemetry.timeframeMetrics["1H"]
                        val atr = tf1h?.atr14?.takeIf { it > 0 } ?: (livePx * 0.018)
                        buyTargetPx = tf1h?.support1?.takeIf { it < livePx } ?: (livePx - atr * 0.45)
                        sellTargetPx = exitPlan.goldenExitPrice
                        sellTp1Px = exitPlan.sellTarget1Price
                        sellTp3Px = exitPlan.sellTarget3Price
                        stopLossPx = exitPlan.emergencyStopLossPrice
                    } else {
                        val (prediction, badge) = aiService.generateDeepInstrumentPrediction(telemetry)
                        _activeAiEngineBadge.value = badge
                        livePx = prediction.spotPriceAtAnalysis
                        buyTargetPx = prediction.entryZoneMin
                        sellTargetPx = prediction.takeProfit2
                        sellTp1Px = prediction.takeProfit1
                        sellTp3Px = prediction.takeProfit3
                        stopLossPx = prediction.stopLoss
                    }
                }

                // Step 4: Stream/generate full Kurdish chat answer
                val accumulated = StringBuilder()
                withTimeout(75_000L) {
                    aiService.streamAdvisorResponse(
                        userQuestionKu = trimmed,
                        attachedBitmaps = bitmaps,
                        activeTelemetry = telemetry,
                        topTickers = currentTickers
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _advisorState.value = AiAdvisorUiState.Streaming(accumulated.toString())
                    }
                }

                val finalAnswer = accumulated.toString()
                if (finalAnswer.isNotBlank()) {
                    // Automatically register with Background Monitor so user gets notified when coin hits Buy or Sell price!
                    if (livePx > 0.0 && (buyTargetPx > 0.0 || sellTargetPx > 0.0)) {
                        TradingBackgroundService.registerCoinTargetsInBackground(
                            context = getApplication(),
                            instId = detectedInstId,
                            buyTargetPrice = buyTargetPx,
                            sellTargetPrice = sellTargetPx,
                            stopLossPrice = stopLossPx,
                            referencePrice = livePx
                        )
                    }

                    _advisorState.value = AiAdvisorUiState.Success(finalAnswer)
                    _chatHistory.value = _chatHistory.value + AdvisorChatMessage(
                        isUser = false,
                        text = finalAnswer,
                        detectedCoin = detectedInstId,
                        livePriceAtAnalysis = livePx,
                        buyAtPrice = buyTargetPx,
                        sellAtPrice = sellTargetPx,
                        sellTp1Price = sellTp1Px,
                        sellTp3Price = sellTp3Px,
                        stopLossPrice = stopLossPx,
                        isTrackedInBackground = true
                    )

                    val notifSummary = if (sellTargetPx > 0.0 && buyTargetPx > 0.0) {
                        "$detectedInstId • لە $${QuantMathEngine.formatCryptoPrice(buyTargetPx)} بیکڕە | کە گەیشتە $${QuantMathEngine.formatCryptoPrice(sellTargetPx)} بیفرۆشە!"
                    } else {
                        finalAnswer.lineSequence().firstOrNull { it.isNotBlank() }?.take(120) ?: "وەڵامی نوێ ئامادەیە"
                    }

                    TradingBackgroundService.sendInstantAlertNotification(
                        context = getApplication(),
                        title = "🤖 شیکردنەوەی $detectedInstId ئامادەیە",
                        message = notifSummary
                    )
                } else {
                    _advisorState.value = AiAdvisorUiState.Error("وەڵامی زیرەکی دەستکرد بەتاڵ بوو.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI advisor timed out", e)
                _advisorState.value = AiAdvisorUiState.Error("وەڵامدانەوەی ڕاوێژکاری AI کاتی تەواو بوو. دووبارە هەوڵ بدەرەوە.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI advisor failed", e)
                _advisorState.value = AiAdvisorUiState.Error(
                    e.localizedMessage ?: "هەڵە لە پەیوەندیکردن بە ڕاوێژکاری زیرەکی دەستکرد."
                )
            }
        }
    }

    private fun extractCoinFromUserText(text: String, liveTickers: List<OkxMarketTicker>): String? {
        val upper = text.uppercase(Locale.US)

        // Check Kurdish common coin names first
        val kurdishAliases = mapOf(
            "بیتکۆین" to "BTC-USDT",
            "بیتکوین" to "BTC-USDT",
            "ئیتیریۆم" to "ETH-USDT",
            "ئیثیریۆم" to "ETH-USDT",
            "سۆلانا" to "SOL-USDT",
            "سولانا" to "SOL-USDT",
            "تۆن" to "TON-USDT",
            "ریپڵ" to "XRP-USDT",
            "ڕیپڵ" to "XRP-USDT",
            "دۆج" to "DOGE-USDT",
            "پێپێ" to "PEPE-USDT",
            "شیبا" to "SHIB-USDT",
            "ترۆن" to "TRX-USDT",
            "کاردانۆ" to "ADA-USDT",
            "سوی" to "SUI-USDT"
        )
        for ((kuName, instId) in kurdishAliases) {
            if (text.contains(kuName)) return instId
        }

        // Tokenize English symbols in user text
        val words = Regex("""[A-Z0-9]{2,12}""").findAll(upper).map { it.value }.toList()
        if (words.isNotEmpty()) {
            // Check against live OKX tickers first
            val tickerSymbols = liveTickers.associateBy { it.baseSymbol.uppercase(Locale.US) }
            for (w in words) {
                val clean = w.removeSuffix("USDT")
                if (clean.length >= 2 && tickerSymbols.containsKey(clean)) {
                    return tickerSymbols[clean]!!.instId
                }
            }
            // Fallback to known major symbols
            val commonSymbols = setOf(
                "BTC", "ETH", "SOL", "TON", "XRP", "DOGE", "PEPE", "SUI", "BNB",
                "ADA", "AVAX", "LINK", "TRX", "SHIB", "DOT", "NEAR", "LTC", "BCH",
                "UNI", "APT", "ARB", "OP", "FIL", "ATOM", "INJ", "RNDR", "RENDER",
                "FET", "WIF", "BONK", "FLOKI", "NOT", "DOGS", "HMSTR", "SEI", "TIA"
            )
            for (w in words) {
                val clean = w.removeSuffix("USDT")
                if (clean in commonSymbols) {
                    return "$clean-USDT"
                }
            }
        }
        return null
    }

    fun watchCoinTargetsInBackground(
        instId: String,
        buyPrice: Double,
        sellPrice: Double,
        stopLossPrice: Double,
        livePrice: Double
    ) {
        TradingBackgroundService.registerCoinTargetsInBackground(
            context = getApplication(),
            instId = instId,
            buyTargetPrice = buyPrice,
            sellTargetPrice = sellPrice,
            stopLossPrice = stopLossPrice,
            referencePrice = livePrice
        )
        _statusBannerKu.value = "🔔 چاودێری باگراوند بۆ $instId چالاک کرا (کڕین: $${QuantMathEngine.formatCryptoPrice(buyPrice)} | فرۆشتن: $${QuantMathEngine.formatCryptoPrice(sellPrice)})"
    }

    fun clearChatHistory() {
        _chatHistory.value = listOf(
            AdvisorChatMessage(
                isUser = false,
                text = "چاتەکە پاک کرایەوە. ناوی هەر دراوێک بنووسە یان بێسنوور وێنەی کڕینی دراوەکەت و چارتەکانت بنێرە تا پێت بڵێم لە چ نرخێکدا بیکڕیت و کە گەیشتە چەند بیفرۆشیت بۆ قازانج!"
            )
        )
        _advisorState.value = AiAdvisorUiState.Idle
    }

    fun addApiKeys(
        labelPrefixKu: String,
        providerTypeCode: String,
        rawKeysInput: String,
        customModel: String
    ) {
        val rawLines = rawKeysInput
            .split("\n", ",", ";")
            .map { it.trim() }
            .filter { it.length >= 10 }

        if (rawLines.isEmpty()) {
            _statusBannerKu.value = "تکایە لانیکەم یەک کلیلی API ی دروست بنووسە."
            return
        }

        viewModelScope.launch {
            var addedCount = 0
            rawLines.forEachIndexed { index, singleKey ->
                val resolvedProvider = if (providerTypeCode == "AUTO") {
                    ApiProviderType.autoDetectProvider(singleKey)
                } else {
                    ApiProviderType.fromId(providerTypeCode)
                }
                val finalModel = customModel.trim().ifBlank { resolvedProvider.defaultModel }
                val baseLabel = labelPrefixKu.trim().ifBlank { resolvedProvider.titleKu }
                val finalLabel = if (rawLines.size > 1) "$baseLabel #${index + 1}" else baseLabel

                dao.insertApiKey(
                    ApiKeyEntity(
                        labelKu = finalLabel,
                        providerType = resolvedProvider.id,
                        apiKey = singleKey,
                        modelName = finalModel,
                        isEnabled = true,
                        lastStatusKu = "ئامادەیە بۆ سووڕانەوەی ئۆتۆماتیکی (Ready)"
                    )
                )
                addedCount++
            }
            _statusBannerKu.value = if (addedCount == 1) {
                "١ کلیلی نوێی API زیادکرا و چالاککرا بۆ گۆڕینی ئۆتۆماتیکی"
            } else {
                "$addedCount کلیلی جیاوازی API بەیەکەوە زیادکران بۆ سیستەمی سووڕانەوەی ئۆتۆماتیکی"
            }
        }
    }

    fun toggleApiKeyEnabled(entity: ApiKeyEntity) {
        viewModelScope.launch {
            val updated = entity.copy(
                isEnabled = !entity.isEnabled,
                cooldownUntilMs = 0L,
                lastStatusKu = if (!entity.isEnabled) "چالاک کرایەوە (Enabled)" else "ڕاگیراوە (Paused)"
            )
            dao.updateApiKey(updated)
        }
    }

    fun deleteApiKey(id: Int) {
        viewModelScope.launch {
            dao.deleteApiKey(id)
            _statusBannerKu.value = "کلیلی API سڕایەوە"
        }
    }

    fun testSingleApiKey(entity: ApiKeyEntity) {
        viewModelScope.launch {
            _testingKeyId.value = entity.id
            try {
                val (_, msgKu) = aiService.keyRouter.testSingleKey(entity)
                _statusBannerKu.value = msgKu
            } finally {
                _testingKeyId.value = null
            }
        }
    }

    fun resetAllKeyCooldowns() {
        viewModelScope.launch {
            dao.resetAllKeyCooldowns()
            _statusBannerKu.value = "ساردبوونەوەی هەموو کلیلەکان سفر کرایەوە و دووبارە چالاک کران"
        }
    }

    private suspend fun decodeAndScaleBitmap(uri: Uri, totalCount: Int = 1): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = getApplication<Application>().contentResolver
            val rawBitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return@withContext null

            val maxDim = when {
                totalCount >= 8 -> 800
                totalCount >= 4 -> 1024
                else -> 1280
            }
            val width = rawBitmap.width
            val height = rawBitmap.height
            if (width <= maxDim && height <= maxDim) {
                rawBitmap
            } else {
                val scale = maxDim.toFloat() / max(width, height).toFloat()
                val targetW = (width * scale).toInt().coerceAtLeast(1)
                val targetH = (height * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
            }
        }.getOrNull()
    }
}
