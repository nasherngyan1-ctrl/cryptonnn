package com.example.data.repository

import com.example.data.local.OkxTradingDao
import com.example.data.local.PriceAlertEntity
import com.example.data.local.SavedAiSignalEntity
import com.example.data.local.WatchlistEntity
import com.example.data.model.OkxInstrumentDeepTelemetry
import com.example.data.model.OkxMarketTicker
import com.example.data.model.TimeframeQuantMetrics
import com.example.data.remote.OkxApiService
import com.example.domain.QuantMathEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OkxRepository(
    private val apiService: OkxApiService,
    private val dao: OkxTradingDao
) {
    val watchlistIdsFlow: Flow<Set<String>> = dao.observeWatchlist()
        .map { list -> list.map { it.instId }.toSet() }

    val savedSignalsFlow: Flow<List<SavedAiSignalEntity>> = dao.observeSavedSignals()

    val priceAlertsFlow: Flow<List<PriceAlertEntity>> = dao.observePriceAlerts()

    suspend fun fetchLiveMarketTickers(instType: String = "SPOT"): List<OkxMarketTicker> =
        withContext(Dispatchers.IO) {
            val response = apiService.getMarketTickers(instType = instType)
            if (response.code != "0") {
                throw IllegalStateException("OKX API Error (${response.code}): ${response.msg}")
            }
            response.data
                .filter { dto ->
                    if (instType == "SWAP") {
                        dto.instId.endsWith("-USDT-SWAP")
                    } else {
                        dto.instId.endsWith("-USDT")
                    }
                }
                .mapNotNull { QuantMathEngine.mapTickerDto(it) }
                .sortedByDescending { it.volume24hUsdt }
        }

    suspend fun fetchDeepInstrumentTelemetry(
        instId: String,
        fallbackTicker: OkxMarketTicker? = null
    ): OkxInstrumentDeepTelemetry = withContext(Dispatchers.IO) {
        coroutineScope {
            val tickerDeferred = async {
                runCatching {
                    val res = apiService.getSingleTicker(instId)
                    res.data.firstOrNull()?.let { QuantMathEngine.mapTickerDto(it) }
                }.getOrNull() ?: fallbackTicker
            }

            val candles15mDeferred = async {
                runCatching {
                    val res = apiService.getMarketCandles(instId = instId, bar = "15m", limit = 100)
                    val bars = QuantMathEngine.parseCandles(res.data)
                    QuantMathEngine.computeTimeframeMetrics("15m", bars)
                }.getOrNull()
            }

            val candles1hDeferred = async {
                runCatching {
                    val res = apiService.getMarketCandles(instId = instId, bar = "1H", limit = 100)
                    val bars = QuantMathEngine.parseCandles(res.data)
                    QuantMathEngine.computeTimeframeMetrics("1H", bars)
                }.getOrNull()
            }

            val candles4hDeferred = async {
                runCatching {
                    val res = apiService.getMarketCandles(instId = instId, bar = "4H", limit = 100)
                    val bars = QuantMathEngine.parseCandles(res.data)
                    QuantMathEngine.computeTimeframeMetrics("4H", bars)
                }.getOrNull()
            }

            val candles1dDeferred = async {
                runCatching {
                    val res = apiService.getMarketCandles(instId = instId, bar = "1D", limit = 100)
                    val bars = QuantMathEngine.parseCandles(res.data)
                    QuantMathEngine.computeTimeframeMetrics("1D", bars)
                }.getOrNull()
            }

            val orderBookDeferred = async {
                runCatching {
                    apiService.getOrderBook(instId = instId, size = 50).data.firstOrNull()
                }.getOrNull()
            }

            val swapInstId = if (instId.endsWith("-SWAP")) instId else "$instId-SWAP"
            val fundingDeferred = async {
                runCatching {
                    val res = apiService.getFundingRate(instId = swapInstId)
                    res.data.firstOrNull()?.let { QuantMathEngine.parseFundingRate(it) }
                }.getOrNull()
            }

            val resolvedTicker = tickerDeferred.await()
                ?: throw IllegalStateException("نەتوانرا زانیاری نرخی $instId لە OKX وەربگیرێت.")

            val tfMap = linkedMapOf<String, TimeframeQuantMetrics>()
            candles15mDeferred.await()?.let { tfMap["15m"] = it }
            candles1hDeferred.await()?.let { tfMap["1H"] = it }
            candles4hDeferred.await()?.let { tfMap["4H"] = it }
            candles1dDeferred.await()?.let { tfMap["1D"] = it }

            val obDto = orderBookDeferred.await()
            val obReport = obDto?.let {
                QuantMathEngine.analyzeOrderBook(instId, it, resolvedTicker.lastPrice)
            }
            val fundingReport = fundingDeferred.await()

            // Compute weighted composite score across 15m (15%), 1H (30%), 4H (35%), 1D (20%) + OrderBook adjustment
            val s15 = tfMap["15m"]?.quantScore ?: 0
            val s1h = tfMap["1H"]?.quantScore ?: 0
            val s4h = tfMap["4H"]?.quantScore ?: 0
            val s1d = tfMap["1D"]?.quantScore ?: 0
            val obBonus = when {
                (obReport?.bidPressurePercent ?: 50.0) >= 58.0 -> 10
                (obReport?.askPressurePercent ?: 50.0) >= 58.0 -> -10
                else -> 0
            }
            val compositeScore = ((s15 * 0.15) + (s1h * 0.30) + (s4h * 0.35) + (s1d * 0.20) + obBonus)
                .toInt()
                .coerceIn(-100, 100)

            val compositeVerdictKu = when {
                compositeScore >= 45 -> "کڕینی بەهێز لەسەر کۆی تایمفرەیمەکان (Strong Multi-TF Long)"
                compositeScore >= 15 -> "ئاراستەی گشتی ئەرێنی و گونجاو بۆ کڕین (Bullish Bias)"
                compositeScore <= -45 -> "فرۆشتنی بەهێز لەسەر کۆی تایمفرەیمەکان (Strong Multi-TF Short)"
                compositeScore <= -15 -> "ئاراستەی گشتی نەرێنی و گونجاو بۆ فرۆشتن (Bearish Bias)"
                else -> "بازاڕ لە قۆناغی کۆکردنەوە و چاوەڕوانیدایە (Range / Neutral)"
            }

            OkxInstrumentDeepTelemetry(
                ticker = resolvedTicker,
                timeframeMetrics = tfMap,
                orderBookReport = obReport,
                fundingReport = fundingReport,
                compositeQuantScore = compositeScore,
                compositeVerdictKu = compositeVerdictKu
            )
        }
    }

    suspend fun toggleWatchlist(instId: String, currentSet: Set<String>) {
        if (currentSet.contains(instId)) {
            dao.removeWatchlist(instId)
        } else {
            dao.upsertWatchlist(WatchlistEntity(instId = instId))
        }
    }

    suspend fun seedDefaultWatchlistIfEmpty(currentSet: Set<String>) {
        if (currentSet.isEmpty()) {
            listOf("BTC-USDT", "ETH-USDT", "SOL-USDT", "TON-USDT", "XRP-USDT").forEach { id ->
                dao.upsertWatchlist(WatchlistEntity(instId = id))
            }
        }
    }

    suspend fun saveAiSignal(signal: SavedAiSignalEntity) {
        dao.insertSavedSignal(signal)
    }

    suspend fun deleteSavedSignal(id: Int) {
        dao.deleteSavedSignal(id)
    }

    suspend fun addPriceAlert(instId: String, targetPrice: Double, isAbove: Boolean, noteKu: String) {
        dao.insertPriceAlert(
            PriceAlertEntity(
                instId = instId,
                targetPrice = targetPrice,
                isAboveTarget = isAbove,
                noteKu = noteKu
            )
        )
    }

    suspend fun markAlertTriggered(id: Int) {
        dao.markAlertTriggered(id)
    }

    suspend fun deletePriceAlert(id: Int) {
        dao.deletePriceAlert(id)
    }
}
