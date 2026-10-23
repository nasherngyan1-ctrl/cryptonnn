package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OkxApiResponse<T>(
    @Json(name = "code") val code: String = "0",
    @Json(name = "msg") val msg: String = "",
    @Json(name = "data") val data: List<T> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OkxTickerDto(
    @Json(name = "instType") val instType: String = "",
    @Json(name = "instId") val instId: String = "",
    @Json(name = "last") val last: String = "0",
    @Json(name = "lastSz") val lastSz: String = "0",
    @Json(name = "askPx") val askPx: String = "0",
    @Json(name = "askSz") val askSz: String = "0",
    @Json(name = "bidPx") val bidPx: String = "0",
    @Json(name = "bidSz") val bidSz: String = "0",
    @Json(name = "open24h") val open24h: String = "0",
    @Json(name = "high24h") val high24h: String = "0",
    @Json(name = "low24h") val low24h: String = "0",
    @Json(name = "volCcy24h") val volCcy24h: String = "0",
    @Json(name = "vol24h") val vol24h: String = "0",
    @Json(name = "ts") val ts: String = "0"
)

@JsonClass(generateAdapter = true)
data class OkxOrderBookDto(
    @Json(name = "asks") val asks: List<List<String>> = emptyList(),
    @Json(name = "bids") val bids: List<List<String>> = emptyList(),
    @Json(name = "ts") val ts: String = "0"
)

@JsonClass(generateAdapter = true)
data class OkxFundingRateDto(
    @Json(name = "instId") val instId: String = "",
    @Json(name = "instType") val instType: String = "",
    @Json(name = "fundingRate") val fundingRate: String = "0",
    @Json(name = "nextFundingRate") val nextFundingRate: String = "0",
    @Json(name = "fundingTime") val fundingTime: String = "0",
    @Json(name = "nextFundingTime") val nextFundingTime: String = "0"
)

data class OkxMarketTicker(
    val instId: String,
    val baseSymbol: String,
    val quoteSymbol: String,
    val instType: String, // SPOT or SWAP
    val lastPrice: Double,
    val lastSize: Double,
    val open24h: Double,
    val high24h: Double,
    val low24h: Double,
    val change24hAbs: Double,
    val change24hPercent: Double,
    val range24hPercent: Double, // Where current price sits inside 24h Low..High (0..100%)
    val volatility24hPercent: Double, // (high - low) / low * 100
    val volume24hBase: Double,
    val volume24hUsdt: Double,
    val bestBidPrice: Double,
    val bestBidSize: Double,
    val bestAskPrice: Double,
    val bestAskSize: Double,
    val spreadAbs: Double,
    val spreadBps: Double, // Spread in basis points (1 bps = 0.01%)
    val timestampMs: Long
)

data class OkxCandleBar(
    val timestampMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volumeBase: Double,
    val volumeUsdt: Double
)

data class TimeframeQuantMetrics(
    val timeframe: String, // "15m", "1H", "4H", "1D"
    val timeframeLabelKu: String,
    val lastClose: Double,
    val candleChangePercent: Double,
    val periodHigh: Double,
    val periodLow: Double,
    val rsi14: Double,
    val stochRsiK: Double,
    val macdLine: Double,
    val macdSignal: Double,
    val macdHistogram: Double,
    val ema9: Double,
    val ema20: Double,
    val ema50: Double,
    val ema200: Double,
    val bollingerUpper: Double,
    val bollingerMiddle: Double,
    val bollingerLower: Double,
    val bollingerBandwidthPercent: Double,
    val atr14: Double,
    val atrPercent: Double,
    val vwap: Double,
    val pivotPoint: Double,
    val resistance1: Double,
    val resistance2: Double,
    val resistance3: Double,
    val support1: Double,
    val support2: Double,
    val support3: Double,
    val fib236: Double,
    val fib382: Double,
    val fib500: Double,
    val fib618: Double,
    val fib786: Double,
    val volumeSpikeRatio: Double, // Last candle vol vs 20-bar average vol
    val quantScore: Int, // -100 to +100
    val trendBiasKu: String,
    val isBullish: Boolean
)

data class LiquidityWallItem(
    val side: String, // "BID" or "ASK"
    val price: Double,
    val sizeCoin: Double,
    val notionalUsdt: Double,
    val distanceFromMidPercent: Double,
    val ordersCount: Int
)

data class OrderBookLiquidityReport(
    val instId: String,
    val totalBidNotionalUsdt: Double,
    val totalAskNotionalUsdt: Double,
    val bidPressurePercent: Double, // 0..100%
    val askPressurePercent: Double, // 0..100%
    val imbalanceRatio: Double, // Bids / Asks
    val microPrice: Double, // Volume-weighted mid-price at top of book
    val spreadBps: Double,
    val topSupportBidWalls: List<LiquidityWallItem>,
    val topResistanceAskWalls: List<LiquidityWallItem>,
    val dominantSideSummaryKu: String
)

data class FundingRateReport(
    val swapInstId: String,
    val currentFundingRatePercent: Double, // e.g., +0.0105%
    val nextFundingRatePercent: Double,
    val annualizedAprPercent: Double,
    val nextSettlementTimeMs: Long,
    val minutesToNextSettlement: Long,
    val sentimentLabelKu: String
)

data class OkxInstrumentDeepTelemetry(
    val ticker: OkxMarketTicker,
    val timeframeMetrics: Map<String, TimeframeQuantMetrics>, // Keys: "15m", "1H", "4H", "1D"
    val orderBookReport: OrderBookLiquidityReport?,
    val fundingReport: FundingRateReport?,
    val compositeQuantScore: Int, // Weighted -100..+100 across all timeframes + orderbook
    val compositeVerdictKu: String,
    val fetchedAtMs: Long = System.currentTimeMillis()
)

data class AiTradingPrediction(
    val instId: String,
    val spotPriceAtAnalysis: Double,
    val signalDirection: String, // "STRONG_LONG", "LONG", "NEUTRAL", "SHORT", "STRONG_SHORT"
    val signalVerdictKu: String,
    val confidencePercent: Int,
    val riskLevelKu: String,
    val recommendedLeverage: String,
    val entryZoneMin: Double,
    val entryZoneMax: Double,
    val takeProfit1: Double,
    val takeProfit2: Double,
    val takeProfit3: Double,
    val stopLoss: Double,
    val riskRewardRatio: Double,
    val forecast1hPrice: Double,
    val forecast4hPrice: Double,
    val forecast24hPrice: Double,
    val winProbabilityPercent: Int,
    val multiTimeframeAlignmentKu: String,
    val whaleOrderBookSummaryKu: String,
    val keyNumericTriggersKu: List<String>,
    val detailedKurdishAnalysis: String,
    val generatedAtMs: Long = System.currentTimeMillis()
)

data class AiMarketOpportunityItem(
    val instId: String,
    val currentPrice: Double,
    val change24hPercent: Double,
    val actionDirection: String, // "LONG" or "SHORT"
    val actionLabelKu: String,
    val confidencePercent: Int,
    val entryPrice: Double,
    val targetPrice: Double,
    val stopLossPrice: Double,
    val expectedMovePercent: Double,
    val keyReasonKu: String
)

data class AiScreenshotExitPlan(
    val detectedInstId: String,
    val positionTypeKu: String, // e.g., "کڕینی سپۆت (Spot)" or "گرێبەستی فیوچەرز (Long 10x)"
    val detectedBuyPrice: Double,
    val currentLiveOkxPrice: Double,
    val detectedInvestedUsdt: Double,
    val currentPnlPercent: Double,
    val currentStatusSummaryKu: String,
    val urgentActionVerdictKu: String, // e.g., "لە نرخی $194.80 بیفرۆشە بۆ زۆرترین قازانجی مسۆگەر!"
    val confidencePercent: Int,
    val goldenExitPrice: Double, // Single best optimal price to sell for maximum realistic profit
    val goldenExitProfitPercent: Double,
    val goldenExitEstimatedProfitUsdt: Double,
    val sellTarget1Price: Double,
    val sellTarget1ProfitPercent: Double,
    val sellTarget1AllocationPercent: Int, // e.g. 40%
    val sellTarget2Price: Double,
    val sellTarget2ProfitPercent: Double,
    val sellTarget2AllocationPercent: Int, // e.g. 40%
    val sellTarget3Price: Double,
    val sellTarget3ProfitPercent: Double,
    val sellTarget3AllocationPercent: Int, // e.g. 20%
    val emergencyStopLossPrice: Double,
    val holdDurationEstimateKu: String,
    val whaleResistanceWallNoteKu: String,
    val detailedSellStrategyKu: String,
    val analyzedAtMs: Long = System.currentTimeMillis()
)
