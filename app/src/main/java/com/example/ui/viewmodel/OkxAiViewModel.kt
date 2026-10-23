package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
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
import com.example.service.WatchedCoinTarget
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
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

    // Settings Modal toggle (includes Background Service Settings + API Keys)
    private val _isApiKeyModalOpen = MutableStateFlow(false)
    val isApiKeyModalOpen: StateFlow<Boolean> = _isApiKeyModalOpen.asStateFlow()

    // Active AI Engine / Rotated Key Badge
    private val _activeAiEngineBadge = MutableStateFlow("چالاک")
    val activeAiEngineBadge: StateFlow<String> = _activeAiEngineBadge.asStateFlow()

    private val _testingKeyId = MutableStateFlow<Int?>(null)
    val testingKeyId: StateFlow<Int?> = _testingKeyId.asStateFlow()

    // Background Service Status & Watched Coins
    val isBackgroundServiceRunning: StateFlow<Boolean> = TradingBackgroundService.isServiceRunning
    val watchedCoinsCount: StateFlow<Int> = TradingBackgroundService.watchedCoinsCount
    val watchedCoinsList: StateFlow<List<WatchedCoinTarget>> = TradingBackgroundService.watchedCoinsList

    // Live OKX Tickers in memory for automatic coin detection & market context
    private val _tickers = MutableStateFlow<List<OkxMarketTicker>>(emptyList())
    val tickers: StateFlow<List<OkxMarketTicker>> = _tickers.asStateFlow()

    // Tracks the last analyzed coin in the conversation and a rotating cursor for general market queries
    private var lastConversationCoin: String? = null
    private val topMoverRotationCursor = AtomicInteger(0)

    // AI Advisor Chat State & Messages
    private val _advisorState = MutableStateFlow<AiAdvisorUiState>(AiAdvisorUiState.Idle)
    val advisorState: StateFlow<AiAdvisorUiState> = _advisorState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<AdvisorChatMessage>>(
        listOf(
            AdvisorChatMessage(
                isUser = false,
                text = "سڵاو لە «abdulbasit traiding» 👋\n" +
                    "ناوی هەر دراوێک بنووسە یان وێنەی (Screenshot) کڕینەکەت بنێرە، خۆکارانە دراوەکە دەخوێنمەوە و پێت دەڵێم لە چەند بیکڕیت و کە گەیشتە چەند بیفرۆشیت بۆ قازانج!"
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
        // Start background monitoring automatically on launch
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
                instId = lastConversationCoin ?: "BTC-USDT"
            )
            _statusBannerKu.value = "کارکردنی باگراوند و نۆتیفیکەیشنی نرخ چالاک کرا"
        } else {
            TradingBackgroundService.stopBackgroundMonitor(app)
            _statusBannerKu.value = "کارکردنی باگراوند ڕاگیرا"
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
     * - Accepts text + UNLIMITED images.
     * - Automatically reads the coin symbol & entry price from attached screenshots or text.
     * - Never gets stuck repeating a single coin.
     */
    fun askAiAdvisor(questionKu: String, attachedImageUris: List<Uri> = emptyList()) {
        val trimmed = questionKu.trim().ifBlank {
            when {
                attachedImageUris.size > 1 ->
                    "ئەم ${attachedImageUris.size} وێنەیەم بۆ بخوێنەرەوە؛ چ دراوێکە و لە چەند بیکڕم یان کە گەیشتە چەند بیفرۆشم بۆ قازانج؟"
                attachedImageUris.size == 1 ->
                    "ئەم وێنەیەم بۆ بخوێنەرەوە؛ چ دراوێکە و کە گەیشتە چەند بیفرۆشم بۆ قازانج یان لە چەند بیکڕم؟"
                else -> ""
            }
        }
        if (trimmed.isEmpty()) return

        backgroundAiScope.launch {
            val totalCount = attachedImageUris.size
            val bitmaps = attachedImageUris.mapNotNull { decodeAndScaleBitmap(it, totalCount) }
            val imageNamesHint = attachedImageUris.joinToString(" ") { queryUriDisplayName(it) }

            _chatHistory.value = _chatHistory.value + AdvisorChatMessage(
                isUser = true,
                text = trimmed,
                attachedImageCount = bitmaps.size,
                previewBitmaps = bitmaps
            )

            _advisorState.value = AiAdvisorUiState.Loading(
                stageKu = if (bitmaps.isNotEmpty()) {
                    "خوێندنەوەی دراوەکە لەناو وێنەکەدا..."
                } else {
                    "پشکنینی نرخی زیندووی دراوەکە لە OKX..."
                }
            )

            try {
                // Step 1: Ensure live OKX tickers are loaded first so all 300+ coins and prices are available
                val currentTickers = if (_tickers.value.isNotEmpty()) {
                    _tickers.value
                } else {
                    runCatching { repository.fetchLiveMarketTickers("SPOT") }.getOrDefault(emptyList()).also {
                        _tickers.value = it
                    }
                }

                // Step 2: Detect coin symbol and optional buy price from screenshot(s) or text
                val (detectedFromInput, buyPriceFromImage) = aiService.detectCoinSymbolFromScreenshots(
                    bitmaps = bitmaps,
                    userNoteKu = trimmed,
                    liveTickers = currentTickers,
                    imageNamesHint = imageNamesHint,
                    onKeySwitch = { badge -> _activeAiEngineBadge.value = badge }
                )

                var resolvedInstId = detectedFromInput
                    ?: matchCoinByPriceInText(trimmed, currentTickers)
                    ?: pickSmartCoinForGeneralQuery(trimmed, currentTickers)

                _advisorState.value = AiAdvisorUiState.Loading(
                    stageKu = "وەرگرتنی نرخی زیندوو و ئامانجی کڕین و فرۆشتنی $resolvedInstId..."
                )

                // Step 3: Fetch live multi-timeframe & order book telemetry for resolvedInstId
                var telemetry = runCatching {
                    val fallbackTicker = currentTickers.find { it.instId == resolvedInstId }
                    repository.fetchDeepInstrumentTelemetry(resolvedInstId, fallbackTicker)
                }.getOrNull()

                // Step 4: Stream/generate the AI advisor response
                val accumulated = StringBuilder()
                withTimeout(75_000L) {
                    aiService.streamAdvisorResponse(
                        userQuestionKu = trimmed,
                        attachedBitmaps = bitmaps,
                        activeTelemetry = telemetry,
                        detectedBuyPriceFromImage = buyPriceFromImage,
                        topTickers = currentTickers,
                        onKeySwitch = { badge -> _activeAiEngineBadge.value = badge }
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        val cleanPreview = stripCoinTag(accumulated.toString())
                        _advisorState.value = AiAdvisorUiState.Streaming(cleanPreview)
                    }
                }

                val rawAnswer = accumulated.toString()
                if (rawAnswer.isNotBlank()) {
                    // Double-check if the AI response itself identified a specific coin from the screenshot (e.g. [COIN: SOL-USDT])
                    val coinFromAiHeader = OkxGeminiAiService.extractCoinSymbolFromAnyText(
                        rawText = rawAnswer.take(220),
                        liveTickers = currentTickers
                    )
                    if (coinFromAiHeader != null && coinFromAiHeader != resolvedInstId) {
                        resolvedInstId = coinFromAiHeader
                        telemetry = runCatching {
                            val fallbackTicker = currentTickers.find { it.instId == resolvedInstId }
                            repository.fetchDeepInstrumentTelemetry(resolvedInstId, fallbackTicker)
                        }.getOrNull()
                    }

                    lastConversationCoin = resolvedInstId

                    // Compute exact Buy ("لەوەندە بیکڕە") and Sell ("کە گەیشتە ئەوەندە بیفرۆشە") targets for the final coin
                    var buyTargetPx = 0.0
                    var sellTargetPx = 0.0
                    var sellTp1Px = 0.0
                    var sellTp3Px = 0.0
                    var stopLossPx = 0.0
                    var livePx = telemetry?.ticker?.lastPrice ?: 0.0

                    if (telemetry != null) {
                        val targets = aiService.computeTargetsFromTelemetry(
                            telemetry = telemetry,
                            userBuyPriceHint = buyPriceFromImage
                        )
                        livePx = targets.spotPriceAtAnalysis
                        buyTargetPx = targets.entryZoneMin
                        sellTargetPx = targets.takeProfit2
                        sellTp1Px = targets.takeProfit1
                        sellTp3Px = targets.takeProfit3
                        stopLossPx = targets.stopLoss
                    }

                    val cleanFinalAnswer = stripCoinTag(rawAnswer)

                    // Automatically register with Background Monitor if background service is enabled
                    if (isBackgroundServiceRunning.value && livePx > 0.0 && (buyTargetPx > 0.0 || sellTargetPx > 0.0)) {
                        TradingBackgroundService.registerCoinTargetsInBackground(
                            context = getApplication(),
                            instId = resolvedInstId,
                            buyTargetPrice = buyTargetPx,
                            sellTargetPrice = sellTargetPx,
                            stopLossPrice = stopLossPx,
                            referencePrice = livePx
                        )
                    }

                    _advisorState.value = AiAdvisorUiState.Success(cleanFinalAnswer)
                    _chatHistory.value = _chatHistory.value + AdvisorChatMessage(
                        isUser = false,
                        text = cleanFinalAnswer,
                        detectedCoin = resolvedInstId,
                        livePriceAtAnalysis = livePx,
                        buyAtPrice = buyTargetPx,
                        sellAtPrice = sellTargetPx,
                        sellTp1Price = sellTp1Px,
                        sellTp3Price = sellTp3Px,
                        stopLossPrice = stopLossPx,
                        isTrackedInBackground = isBackgroundServiceRunning.value
                    )

                    if (isBackgroundServiceRunning.value && sellTargetPx > 0.0 && buyTargetPx > 0.0) {
                        TradingBackgroundService.sendInstantAlertNotification(
                            context = getApplication(),
                            title = "🤖 شیکردنەوەی $resolvedInstId ئامادەیە",
                            message = "لە $${QuantMathEngine.formatCryptoPrice(buyTargetPx)} بیکڕە | کە گەیشتە $${QuantMathEngine.formatCryptoPrice(sellTargetPx)} بیفرۆشە!"
                        )
                    }
                } else {
                    _advisorState.value = AiAdvisorUiState.Error("وەڵامی زیرەکی دەستکرد بەتاڵ بوو.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI advisor timed out", e)
                _advisorState.value = AiAdvisorUiState.Error("وەڵامدانەوەکە کاتی تەواو بوو. دووبارە هەوڵ بدەرەوە.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI advisor failed", e)
                _advisorState.value = AiAdvisorUiState.Error(
                    e.localizedMessage ?: "هەڵە لە پەیوەندیکردن."
                )
            }
        }
    }

    private fun stripCoinTag(text: String): String {
        return text
            .replace(Regex("""\[COIN\s*[:=]\s*[^\]]+]""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    /**
     * If user mentions a price in their text (e.g. "لە 0.145 کڕیومە" or "2.45"), matches it to the closest live OKX coin.
     */
    private fun matchCoinByPriceInText(text: String, liveTickers: List<OkxMarketTicker>): String? {
        if (liveTickers.isEmpty()) return null
        val numbers = Regex("""\d+\.\d+""").findAll(text).mapNotNull { it.value.toDoubleOrNull() }.toList()
        for (num in numbers) {
            if (num > 0.0) {
                val closest = liveTickers.minByOrNull { abs(it.lastPrice - num) / max(it.lastPrice, 1e-9) }
                if (closest != null) {
                    val diffRatio = abs(closest.lastPrice - num) / max(closest.lastPrice, 1e-9)
                    if (diffRatio < 0.03) {
                        return closest.instId
                    }
                }
            }
        }
        return null
    }

    /**
     * When the user asks a general question without naming a specific coin,
     * picks a dynamic top mover from live OKX tickers (or keeps conversation context)
     * so it NEVER repeats the same coin & price every time!
     */
    private fun pickSmartCoinForGeneralQuery(
        questionKu: String,
        liveTickers: List<OkxMarketTicker>
    ): String {
        // If user is asking a short follow-up about the previous coin
        val followUpWords = listOf("ئەی", "کەی", "ئێستا", "چۆنە", "بیفرۆشم", "بیکڕم")
        if (lastConversationCoin != null && questionKu.length < 30 && followUpWords.any { questionKu.contains(it) }) {
            return lastConversationCoin!!
        }

        if (liveTickers.isNotEmpty()) {
            val topActive = liveTickers
                .filter { it.volume24hUsdt > 5_000_000.0 }
                .take(15)
            if (topActive.isNotEmpty()) {
                val idx = topMoverRotationCursor.getAndIncrement().mod(topActive.size)
                return topActive[idx].instId
            }
        }
        return lastConversationCoin ?: "SOL-USDT"
    }

    private fun queryUriDisplayName(uri: Uri): String {
        return runCatching {
            val cr = getApplication<Application>().contentResolver
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return@use cursor.getString(idx).orEmpty()
                }
                ""
            }.orEmpty()
        }.getOrDefault(uri.lastPathSegment.orEmpty())
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
        _statusBannerKu.value = "🔔 چاودێری باگراوند بۆ $instId چالاک کرا"
    }

    fun clearChatHistory() {
        _chatHistory.value = listOf(
            AdvisorChatMessage(
                isUser = false,
                text = "چاتەکە پاک کرایەوە. ناوی هەر دراوێک بنووسە یان وێنەی کڕینی دراوەکەت بنێرە تا پێت بڵێم لە چەند بیکڕیت و کە گەیشتە چەند بیفرۆشیت بۆ قازانج!"
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
                        lastStatusKu = "ئامادەیە (Ready)"
                    )
                )
                addedCount++
            }
            _statusBannerKu.value = if (addedCount == 1) {
                "١ کلیلی نوێی API زیادکرا"
            } else {
                "$addedCount کلیلی API بەیەکەوە زیادکران"
            }
        }
    }

    fun toggleApiKeyEnabled(entity: ApiKeyEntity) {
        viewModelScope.launch {
            val updated = entity.copy(
                isEnabled = !entity.isEnabled,
                cooldownUntilMs = 0L,
                lastStatusKu = if (!entity.isEnabled) "چالاک کرایەوە" else "ڕاگیراوە"
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
            _statusBannerKu.value = "هەموو کلیلەکان چالاک کرانەوە"
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
