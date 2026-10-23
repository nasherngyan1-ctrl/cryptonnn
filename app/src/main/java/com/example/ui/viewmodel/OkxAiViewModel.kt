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
import com.example.data.local.PriceAlertEntity
import com.example.data.local.SavedAiSignalEntity
import com.example.data.model.AiMarketOpportunityItem
import com.example.data.model.AiScreenshotExitPlan
import com.example.data.model.AiTradingPrediction
import com.example.data.model.OkxInstrumentDeepTelemetry
import com.example.data.model.OkxMarketTicker
import com.example.data.remote.OkxApiService
import com.example.data.repository.OkxRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private const val TAG = "OkxAiViewModel"

enum class MarketFilterCategory(val labelKu: String) {
    ALL("هەمووی"),
    WATCHLIST("دڵخوازەکان"),
    TOP_VOLUME("زۆرترین قەبارە"),
    TOP_GAINERS("بەرزترین گەشە"),
    TOP_LOSERS("زۆرترین دابەزین"),
    HIGH_VOLATILITY("زۆرترین ناجێگیری")
}

sealed interface AiPredictionUiState {
    data object Idle : AiPredictionUiState
    data object Loading : AiPredictionUiState
    data class Success(val prediction: AiTradingPrediction) : AiPredictionUiState
    data class Error(val message: String) : AiPredictionUiState
}

sealed interface AiScannerUiState {
    data object Idle : AiScannerUiState
    data object Scanning : AiScannerUiState
    data class Success(
        val marketRegimeKu: String,
        val opportunities: List<AiMarketOpportunityItem>
    ) : AiScannerUiState
    data class Error(val message: String) : AiScannerUiState
}

sealed interface ScreenshotExitUiState {
    data object Idle : ScreenshotExitUiState
    data class Analyzing(
        val stageKu: String,
        val previewBitmap: Bitmap
    ) : ScreenshotExitUiState
    data class Success(
        val previewBitmap: Bitmap,
        val plan: AiScreenshotExitPlan
    ) : ScreenshotExitUiState
    data class Error(
        val message: String,
        val previewBitmap: Bitmap? = null
    ) : ScreenshotExitUiState
}

sealed interface AiAdvisorUiState {
    data object Idle : AiAdvisorUiState
    data object Loading : AiAdvisorUiState
    data class Streaming(val partialText: String) : AiAdvisorUiState
    data class Success(val fullText: String) : AiAdvisorUiState
    data class Error(val message: String) : AiAdvisorUiState
}

data class AdvisorChatMessage(
    val id: Long = System.currentTimeMillis(),
    val isUser: Boolean,
    val text: String,
    val relatedInstId: String? = null,
    val hasAttachedImage: Boolean = false
)

class OkxAiViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val dao = database.okxTradingDao()
    private val repository = OkxRepository(
        apiService = OkxApiService.create(),
        dao = dao
    )
    private val aiService = OkxGeminiAiService(dao = dao)

    // Navigation Tab: 0 = Market Matrix, 1 = AI Deep Intelligence & Data, 2 = AI Screenshot & Advisor, 3 = Signals, Calc & API Keys
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Quick API Key Manager Modal toggle (accessible from top bar on any screen)
    private val _isApiKeyModalOpen = MutableStateFlow(false)
    val isApiKeyModalOpen: StateFlow<Boolean> = _isApiKeyModalOpen.asStateFlow()

    // Active AI Engine / Rotated Key Badge
    private val _activeAiEngineBadge = MutableStateFlow("سیستەمی ئۆتۆماتیکی AI + کلیلەکان")
    val activeAiEngineBadge: StateFlow<String> = _activeAiEngineBadge.asStateFlow()

    private val _testingKeyId = MutableStateFlow<Int?>(null)
    val testingKeyId: StateFlow<Int?> = _testingKeyId.asStateFlow()

    // Market Instrument Type: "SPOT" or "SWAP"
    private val _selectedInstType = MutableStateFlow("SPOT")
    val selectedInstType: StateFlow<String> = _selectedInstType.asStateFlow()

    // Live OKX Tickers
    private val _tickers = MutableStateFlow<List<OkxMarketTicker>>(emptyList())
    val tickers: StateFlow<List<OkxMarketTicker>> = _tickers.asStateFlow()

    private val _isMarketLoading = MutableStateFlow(false)
    val isMarketLoading: StateFlow<Boolean> = _isMarketLoading.asStateFlow()

    private val _marketError = MutableStateFlow<String?>(null)
    val marketError: StateFlow<String?> = _marketError.asStateFlow()

    private val _lastUpdatedMs = MutableStateFlow(0L)
    val lastUpdatedMs: StateFlow<Long> = _lastUpdatedMs.asStateFlow()

    private val _autoRefreshEnabled = MutableStateFlow(true)
    val autoRefreshEnabled: StateFlow<Boolean> = _autoRefreshEnabled.asStateFlow()

    // Search & Filter
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow(MarketFilterCategory.ALL)
    val selectedCategory: StateFlow<MarketFilterCategory> = _selectedCategory.asStateFlow()

    // Active Selected Instrument for Deep Telemetry & AI Prediction
    private val _selectedInstId = MutableStateFlow("BTC-USDT")
    val selectedInstId: StateFlow<String> = _selectedInstId.asStateFlow()

    private val _activeTelemetry = MutableStateFlow<OkxInstrumentDeepTelemetry?>(null)
    val activeTelemetry: StateFlow<OkxInstrumentDeepTelemetry?> = _activeTelemetry.asStateFlow()

    private val _isTelemetryLoading = MutableStateFlow(false)
    val isTelemetryLoading: StateFlow<Boolean> = _isTelemetryLoading.asStateFlow()

    private val _telemetryError = MutableStateFlow<String?>(null)
    val telemetryError: StateFlow<String?> = _telemetryError.asStateFlow()

    // AI Prediction State
    private val aiPredictionCache = mutableMapOf<String, AiTradingPrediction>()
    private val _aiPredictionState = MutableStateFlow<AiPredictionUiState>(AiPredictionUiState.Idle)
    val aiPredictionState: StateFlow<AiPredictionUiState> = _aiPredictionState.asStateFlow()

    // AI Screenshot Exit & Profit Optimizer State
    private val _screenshotExitState = MutableStateFlow<ScreenshotExitUiState>(ScreenshotExitUiState.Idle)
    val screenshotExitState: StateFlow<ScreenshotExitUiState> = _screenshotExitState.asStateFlow()

    // AI Market Scanner State
    private val _aiScannerState = MutableStateFlow<AiScannerUiState>(AiScannerUiState.Idle)
    val aiScannerState: StateFlow<AiScannerUiState> = _aiScannerState.asStateFlow()

    // AI Advisor Streaming Chat State
    private val _advisorState = MutableStateFlow<AiAdvisorUiState>(AiAdvisorUiState.Idle)
    val advisorState: StateFlow<AiAdvisorUiState> = _advisorState.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<AdvisorChatMessage>>(emptyList())
    val chatHistory: StateFlow<List<AdvisorChatMessage>> = _chatHistory.asStateFlow()

    // Status Banner / Toast Message
    private val _statusBannerKu = MutableStateFlow<String?>(null)
    val statusBannerKu: StateFlow<String?> = _statusBannerKu.asStateFlow()

    // Room Flows
    val watchlistIds: StateFlow<Set<String>> = repository.watchlistIdsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = setOf("BTC-USDT", "ETH-USDT", "SOL-USDT", "TON-USDT", "XRP-USDT")
    )

    val savedSignals: StateFlow<List<SavedAiSignalEntity>> = repository.savedSignalsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val priceAlerts: StateFlow<List<PriceAlertEntity>> = repository.priceAlertsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val apiKeys: StateFlow<List<ApiKeyEntity>> = dao.observeApiKeys().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private var tickerLoopJob: Job? = null
    private var deepInspectJob: Job? = null

    init {
        viewModelScope.launch {
            val initialWatchlist = repository.watchlistIdsFlow.first()
            repository.seedDefaultWatchlistIfEmpty(initialWatchlist)
        }
        startTickerPollingLoop()
        selectInstrumentAndAnalyze("BTC-USDT", switchToDetailTab = false, autoRunAi = true)
    }

    fun selectTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun setApiKeyModalOpen(open: Boolean) {
        _isApiKeyModalOpen.value = open
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
            // Re-run AI prediction immediately with the newly added key(s)
            aiPredictionCache.clear()
            triggerGeminiAiPrediction()
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

    fun setMarketInstType(instType: String) {
        if (_selectedInstType.value == instType) return
        _selectedInstType.value = instType
        refreshMarketTickers(showSpinner = true)
        val currentBase = _selectedInstId.value.removeSuffix("-SWAP")
        val nextInstId = if (instType == "SWAP") "$currentBase-SWAP" else currentBase
        selectInstrumentAndAnalyze(nextInstId, switchToDetailTab = false, autoRunAi = false)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilterCategory(category: MarketFilterCategory) {
        _selectedCategory.value = category
    }

    fun toggleAutoRefresh() {
        _autoRefreshEnabled.value = !_autoRefreshEnabled.value
    }

    fun clearStatusBanner() {
        _statusBannerKu.value = null
    }

    private fun startTickerPollingLoop() {
        tickerLoopJob?.cancel()
        tickerLoopJob = viewModelScope.launch {
            refreshMarketTickers(showSpinner = true)
            while (isActive) {
                delay(6_000L)
                if (_autoRefreshEnabled.value) {
                    refreshMarketTickers(showSpinner = false)
                }
            }
        }
    }

    fun refreshMarketTickers(showSpinner: Boolean = true) {
        viewModelScope.launch {
            if (showSpinner || _tickers.value.isEmpty()) {
                _isMarketLoading.value = true
            }
            try {
                val list = repository.fetchLiveMarketTickers(_selectedInstType.value)
                _tickers.value = list
                _marketError.value = null
                _lastUpdatedMs.value = System.currentTimeMillis()
                checkPriceAlertsAgainstLiveTickers(list)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch OKX market tickers", e)
                if (_tickers.value.isEmpty()) {
                    _marketError.value = e.localizedMessage
                        ?: "هەڵە لە وەرگرتنی داتای بازاڕی OKX. دڵنیابەرەوە لە هێڵی ئینتەرنێت."
                }
            } finally {
                _isMarketLoading.value = false
            }
        }
    }

    fun selectInstrumentAndAnalyze(
        instId: String,
        switchToDetailTab: Boolean = true,
        autoRunAi: Boolean = true
    ) {
        _selectedInstId.value = instId
        if (switchToDetailTab) {
            _selectedTab.value = 1
        }

        val cachedPred = aiPredictionCache[instId]
        if (cachedPred != null) {
            _aiPredictionState.value = AiPredictionUiState.Success(cachedPred)
        } else {
            _aiPredictionState.value = AiPredictionUiState.Idle
        }

        deepInspectJob?.cancel()
        deepInspectJob = viewModelScope.launch {
            _isTelemetryLoading.value = true
            _telemetryError.value = null
            try {
                val fallbackTicker = _tickers.value.find { it.instId == instId }
                val telemetry = repository.fetchDeepInstrumentTelemetry(instId, fallbackTicker)
                _activeTelemetry.value = telemetry
                _isTelemetryLoading.value = false

                if (autoRunAi && aiPredictionCache[instId] == null) {
                    triggerGeminiAiPrediction(telemetry)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load deep telemetry for $instId", e)
                _isTelemetryLoading.value = false
                _telemetryError.value = e.localizedMessage
                    ?: "هەڵە لە خوێندنەوەی وردەکارییەکانی $instId لە OKX."
            }
        }
    }

    fun refreshActiveInstrumentAndAi() {
        val currentId = _selectedInstId.value
        aiPredictionCache.remove(currentId)
        selectInstrumentAndAnalyze(currentId, switchToDetailTab = false, autoRunAi = true)
    }

    fun triggerGeminiAiPrediction(telemetryOverride: OkxInstrumentDeepTelemetry? = null) {
        val telemetry = telemetryOverride ?: _activeTelemetry.value ?: return
        viewModelScope.launch {
            _aiPredictionState.value = AiPredictionUiState.Loading
            try {
                val (prediction, engineBadge) = aiService.generateDeepInstrumentPrediction(telemetry)
                _activeAiEngineBadge.value = engineBadge
                aiPredictionCache[telemetry.ticker.instId] = prediction
                _aiPredictionState.value = AiPredictionUiState.Success(prediction)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI prediction timed out", e)
                _aiPredictionState.value = AiPredictionUiState.Error(
                    "پێشبینی زیرەکی دەستکرد کاتی زۆری خایاند (٦٠ چرکە). تکایە دووبارە هەوڵ بدەرەوە."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI prediction failed", e)
                _aiPredictionState.value = AiPredictionUiState.Error(
                    e.localizedMessage ?: "دروستکردنی پێشبینی AI سەرکەوتوو نەبوو."
                )
            }
        }
    }

    fun analyzeScreenshotForProfitExit(
        imageUri: Uri,
        userNoteKu: String = ""
    ) {
        viewModelScope.launch {
            var decodedBitmap: Bitmap? = null
            try {
                decodedBitmap = decodeAndScaleBitmap(imageUri)
                    ?: throw IllegalStateException("نەتوانرا وێنەکە بخوێنرێتەوە. تکایە وێنەیەکی تر هەڵبژێرە.")

                _screenshotExitState.value = ScreenshotExitUiState.Analyzing(
                    stageKu = "قۆناغی ١/٣: خوێندنەوەی کۆین و نرخی کڕین لە وێنەکەتدا بە سووڕانەوەی کلیلەکانی AI...",
                    previewBitmap = decodedBitmap
                )

                val detectedInstId = aiService.detectCoinSymbolFromScreenshot(
                    bitmap = decodedBitmap,
                    userNoteKu = userNoteKu,
                    fallbackInstId = _selectedInstId.value
                )

                _screenshotExitState.value = ScreenshotExitUiState.Analyzing(
                    stageKu = "قۆناغی ٢/٣: وەرگرتنی نرخی زیندووی ئێستا و دیوارەکانی فرۆشتنی $detectedInstId لە OKX...",
                    previewBitmap = decodedBitmap
                )

                val telemetry = runCatching {
                    val fallbackTicker = _tickers.value.find { it.instId == detectedInstId }
                    repository.fetchDeepInstrumentTelemetry(detectedInstId, fallbackTicker)
                }.getOrElse {
                    _activeTelemetry.value
                        ?: repository.fetchDeepInstrumentTelemetry(_selectedInstId.value, null)
                }

                _selectedInstId.value = telemetry.ticker.instId
                _activeTelemetry.value = telemetry

                _screenshotExitState.value = ScreenshotExitUiState.Analyzing(
                    stageKu = "قۆناغی ٣/٣: هەژمارکردنی وردترین نرخی فرۆشتن و زۆرترین قازانج...",
                    previewBitmap = decodedBitmap
                )

                val exitPlan = aiService.analyzeTradeScreenshotForExitPlan(
                    bitmap = decodedBitmap,
                    userNoteKu = userNoteKu,
                    telemetry = telemetry
                )

                _screenshotExitState.value = ScreenshotExitUiState.Success(
                    previewBitmap = decodedBitmap,
                    plan = exitPlan
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Screenshot exit analysis timed out", e)
                _screenshotExitState.value = ScreenshotExitUiState.Error(
                    message = "شیکردنەوەی وێنەکە کاتی زۆری خایاند (٦٠ چرکە). تکایە دووبارە هەوڵ بدەرەوە.",
                    previewBitmap = decodedBitmap
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot exit analysis failed", e)
                _screenshotExitState.value = ScreenshotExitUiState.Error(
                    message = e.localizedMessage ?: "هەڵە لە شیکردنەوەی وێنەی کڕینەکە.",
                    previewBitmap = decodedBitmap
                )
            }
        }
    }

    fun saveScreenshotExitPlanAsSignal(plan: AiScreenshotExitPlan) {
        viewModelScope.launch {
            repository.saveAiSignal(
                SavedAiSignalEntity(
                    instId = plan.detectedInstId,
                    signalDirection = "LONG",
                    signalVerdictKu = "پلانی فرۆشتنی وێنە: ${plan.urgentActionVerdictKu}",
                    confidencePercent = plan.confidencePercent,
                    spotPriceAtSignal = plan.detectedBuyPrice,
                    entryZoneMin = plan.detectedBuyPrice,
                    entryZoneMax = plan.currentLiveOkxPrice,
                    takeProfit1 = plan.sellTarget1Price,
                    takeProfit2 = plan.sellTarget2Price,
                    takeProfit3 = plan.sellTarget3Price,
                    stopLoss = plan.emergencyStopLossPrice,
                    riskRewardRatio = 3.0,
                    forecast24hPrice = plan.goldenExitPrice,
                    recommendedLeverage = plan.positionTypeKu,
                    summaryKu = plan.detailedSellStrategyKu
                )
            )
            _statusBannerKu.value = "پلانی فرۆشتنی ${plan.detectedInstId} لە لیستی سیگناڵەکان پاشەکەوت کرا"
        }
    }

    fun runMarketWideAiScanner() {
        viewModelScope.launch {
            _aiScannerState.value = AiScannerUiState.Scanning
            try {
                val currentTickers = if (_tickers.value.isNotEmpty()) {
                    _tickers.value
                } else {
                    repository.fetchLiveMarketTickers(_selectedInstType.value)
                }
                val (regimeKu, opportunities) = aiService.scanTopMarketOpportunities(
                    tickers = currentTickers,
                    activeDeepTelemetry = _activeTelemetry.value
                )
                _aiScannerState.value = AiScannerUiState.Success(
                    marketRegimeKu = regimeKu,
                    opportunities = opportunities
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI scanner timed out", e)
                _aiScannerState.value = AiScannerUiState.Error(
                    "پشکنینی گشتی بازاڕ بە AI کاتی تەواو بوو (٦٠ چرکە). تکایە دووبارە هەوڵ بدەرەوە."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI scanner failed", e)
                _aiScannerState.value = AiScannerUiState.Error(
                    e.localizedMessage ?: "هەڵە لە پشکنینی بازاڕ بە زیرەکی دەستکرد."
                )
            }
        }
    }

    fun askAiAdvisor(questionKu: String, attachedImageUri: Uri? = null) {
        val trimmed = questionKu.trim().ifBlank {
            if (attachedImageUri != null) {
                "ئەم کۆینەم کڕیوە لەم وێنەیەدا؛ بەپێی داتای زیندووی ئێستای OKX لە چ نرخێکدا بیفرۆشم بۆ زۆرترین قازانج؟"
            } else ""
        }
        if (trimmed.isEmpty()) return

        val activeId = _activeTelemetry.value?.ticker?.instId
        _chatHistory.value = _chatHistory.value + AdvisorChatMessage(
            isUser = true,
            text = trimmed,
            relatedInstId = activeId,
            hasAttachedImage = attachedImageUri != null
        )

        viewModelScope.launch {
            _advisorState.value = AiAdvisorUiState.Loading
            val accumulated = StringBuilder()
            try {
                val bitmap = if (attachedImageUri != null) {
                    decodeAndScaleBitmap(attachedImageUri)
                } else null

                kotlinx.coroutines.withTimeout(60_000L) {
                    aiService.streamAdvisorResponse(
                        userQuestionKu = trimmed,
                        attachedBitmap = bitmap,
                        activeTelemetry = _activeTelemetry.value,
                        topTickers = _tickers.value
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _advisorState.value = AiAdvisorUiState.Streaming(accumulated.toString())
                    }
                }
                val finalAnswer = accumulated.toString()
                if (finalAnswer.isNotBlank()) {
                    _advisorState.value = AiAdvisorUiState.Success(finalAnswer)
                    _chatHistory.value = _chatHistory.value + AdvisorChatMessage(
                        isUser = false,
                        text = finalAnswer,
                        relatedInstId = activeId
                    )
                } else {
                    _advisorState.value = AiAdvisorUiState.Error("وەڵامی زیرەکی دەستکرد بەتاڵ بوو.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI advisor streaming timed out", e)
                _advisorState.value = AiAdvisorUiState.Error(
                    "وەڵامدانەوەی ڕاوێژکاری AI کاتی تەواو بوو (٦٠ چرکە)."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI advisor streaming failed", e)
                _advisorState.value = AiAdvisorUiState.Error(
                    e.localizedMessage ?: "هەڵە لە پەیوەندیکردن بە ڕاوێژکاری زیرەکی دەستکرد."
                )
            }
        }
    }

    private suspend fun decodeAndScaleBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val contentResolver = getApplication<Application>().contentResolver
            val rawBitmap = contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: return@withContext null

            val maxDim = 1280
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

    fun toggleWatchlist(instId: String) {
        viewModelScope.launch {
            val currentSet = watchlistIds.value
            val wasInWatchlist = currentSet.contains(instId)
            repository.toggleWatchlist(instId, currentSet)
            _statusBannerKu.value = if (wasInWatchlist) {
                "$instId لە لیستی دڵخوازەکان لابرا"
            } else {
                "$instId زیادکرا بۆ لیستی دڵخوازەکان"
            }
        }
    }

    fun saveCurrentAiPrediction(prediction: AiTradingPrediction) {
        viewModelScope.launch {
            repository.saveAiSignal(
                SavedAiSignalEntity(
                    instId = prediction.instId,
                    signalDirection = prediction.signalDirection,
                    signalVerdictKu = prediction.signalVerdictKu,
                    confidencePercent = prediction.confidencePercent,
                    spotPriceAtSignal = prediction.spotPriceAtAnalysis,
                    entryZoneMin = prediction.entryZoneMin,
                    entryZoneMax = prediction.entryZoneMax,
                    takeProfit1 = prediction.takeProfit1,
                    takeProfit2 = prediction.takeProfit2,
                    takeProfit3 = prediction.takeProfit3,
                    stopLoss = prediction.stopLoss,
                    riskRewardRatio = prediction.riskRewardRatio,
                    forecast24hPrice = prediction.forecast24hPrice,
                    recommendedLeverage = prediction.recommendedLeverage,
                    summaryKu = prediction.multiTimeframeAlignmentKu.ifBlank { prediction.detailedKurdishAnalysis }
                )
            )
            _statusBannerKu.value = "سیگناڵی زیرەکی دەستکرد بۆ ${prediction.instId} بە سەرکەوتوویی پاشەکەوت کرا"
        }
    }

    fun deleteSavedSignal(id: Int) {
        viewModelScope.launch {
            repository.deleteSavedSignal(id)
        }
    }

    fun createPriceAlert(instId: String, targetPrice: Double, noteKu: String) {
        viewModelScope.launch {
            val livePx = _tickers.value.find { it.instId == instId }?.lastPrice
                ?: _activeTelemetry.value?.ticker?.lastPrice
                ?: 0.0
            val isAbove = targetPrice >= livePx
            repository.addPriceAlert(
                instId = instId,
                targetPrice = targetPrice,
                isAbove = isAbove,
                noteKu = noteKu.ifBlank {
                    if (isAbove) "ئاگادارکردنەوە لە کاتی بەرزبوونەوە" else "ئاگادارکردنەوە لە کاتی دابەزین"
                }
            )
            _statusBannerKu.value = "ئاگادارکەرەوەی نرخ بۆ $instId لە ئاستی $targetPrice چالاک کرا"
        }
    }

    fun deletePriceAlert(id: Int) {
        viewModelScope.launch {
            repository.deletePriceAlert(id)
        }
    }

    private suspend fun checkPriceAlertsAgainstLiveTickers(liveTickers: List<OkxMarketTicker>) {
        val currentAlerts = priceAlerts.value.filter { !it.isTriggered }
        if (currentAlerts.isEmpty()) return
        val priceMap = liveTickers.associateBy({ it.instId }, { it.lastPrice })
        for (alert in currentAlerts) {
            val currentPx = priceMap[alert.instId] ?: continue
            val hit = if (alert.isAboveTarget) {
                currentPx >= alert.targetPrice
            } else {
                currentPx <= alert.targetPrice
            }
            if (hit) {
                repository.markAlertTriggered(alert.id)
                _statusBannerKu.value = "ئاگاداری نرخ: ${alert.instId} گەیشتە نرخی دیاریکراو (${alert.targetPrice})!"
            }
        }
    }
}
