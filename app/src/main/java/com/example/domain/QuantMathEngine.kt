package com.example.domain

import com.example.data.model.FundingRateReport
import com.example.data.model.LiquidityWallItem
import com.example.data.model.OkxCandleBar
import com.example.data.model.OkxFundingRateDto
import com.example.data.model.OkxMarketTicker
import com.example.data.model.OkxOrderBookDto
import com.example.data.model.OkxTickerDto
import com.example.data.model.OrderBookLiquidityReport
import com.example.data.model.TimeframeQuantMetrics
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object QuantMathEngine {

    fun mapTickerDto(dto: OkxTickerDto): OkxMarketTicker? {
        val last = dto.last.toDoubleOrNull() ?: return null
        if (last <= 0.0) return null
        val open24h = dto.open24h.toDoubleOrNull() ?: last
        val high24h = dto.high24h.toDoubleOrNull() ?: last
        val low24h = dto.low24h.toDoubleOrNull() ?: last
        val lastSz = dto.lastSz.toDoubleOrNull() ?: 0.0
        val bidPx = dto.bidPx.toDoubleOrNull() ?: last
        val bidSz = dto.bidSz.toDoubleOrNull() ?: 0.0
        val askPx = dto.askPx.toDoubleOrNull() ?: last
        val askSz = dto.askSz.toDoubleOrNull() ?: 0.0
        val vol24h = dto.vol24h.toDoubleOrNull() ?: 0.0
        val volCcy24h = dto.volCcy24h.toDoubleOrNull() ?: 0.0
        val ts = dto.ts.toLongOrNull() ?: System.currentTimeMillis()

        val changeAbs = last - open24h
        val changePct = if (open24h > 0) (changeAbs / open24h) * 100.0 else 0.0
        val rangeSpan = high24h - low24h
        val rangePosPct = if (rangeSpan > 0) ((last - low24h) / rangeSpan * 100.0).coerceIn(0.0, 100.0) else 50.0
        val volatilityPct = if (low24h > 0) (rangeSpan / low24h) * 100.0 else 0.0

        val spreadAbs = max(0.0, askPx - bidPx)
        val midPx = if (askPx > 0 && bidPx > 0) (askPx + bidPx) / 2.0 else last
        val spreadBps = if (midPx > 0) (spreadAbs / midPx) * 10000.0 else 0.0

        val parts = dto.instId.split("-")
        val baseSymbol = parts.getOrNull(0) ?: dto.instId
        val quoteSymbol = parts.getOrNull(1) ?: "USDT"

        // In OKX SPOT, volCcy24h is USDT volume; in SWAP, volCcy24h is base coin volume, so calculate USDT volume cleanly
        val volumeUsdt = if (dto.instType == "SWAP") {
            if (volCcy24h * last > vol24h) volCcy24h * last else vol24h * last
        } else {
            if (volCcy24h > 0) volCcy24h else vol24h * last
        }

        return OkxMarketTicker(
            instId = dto.instId,
            baseSymbol = baseSymbol,
            quoteSymbol = quoteSymbol,
            instType = dto.instType.ifEmpty { if (dto.instId.endsWith("SWAP")) "SWAP" else "SPOT" },
            lastPrice = last,
            lastSize = lastSz,
            open24h = open24h,
            high24h = high24h,
            low24h = low24h,
            change24hAbs = changeAbs,
            change24hPercent = changePct,
            range24hPercent = rangePosPct,
            volatility24hPercent = volatilityPct,
            volume24hBase = vol24h,
            volume24hUsdt = volumeUsdt,
            bestBidPrice = bidPx,
            bestBidSize = bidSz,
            bestAskPrice = askPx,
            bestAskSize = askSz,
            spreadAbs = spreadAbs,
            spreadBps = spreadBps,
            timestampMs = ts
        )
    }

    fun parseCandles(rawRows: List<List<String>>): List<OkxCandleBar> {
        // OKX returns newest first; reverse to chronological (oldest -> newest) for accurate indicator math
        return rawRows.mapNotNull { row ->
            if (row.size < 6) return@mapNotNull null
            val ts = row[0].toLongOrNull() ?: return@mapNotNull null
            val o = row[1].toDoubleOrNull() ?: return@mapNotNull null
            val h = row[2].toDoubleOrNull() ?: return@mapNotNull null
            val l = row[3].toDoubleOrNull() ?: return@mapNotNull null
            val c = row[4].toDoubleOrNull() ?: return@mapNotNull null
            val vol = row[5].toDoubleOrNull() ?: 0.0
            val volCcy = row.getOrNull(7)?.toDoubleOrNull()
                ?: row.getOrNull(6)?.toDoubleOrNull()?.times(c)
                ?: (vol * c)
            OkxCandleBar(
                timestampMs = ts,
                open = o,
                high = h,
                low = l,
                close = c,
                volumeBase = vol,
                volumeUsdt = volCcy
            )
        }.sortedBy { it.timestampMs }
    }

    fun computeTimeframeMetrics(
        timeframe: String,
        candles: List<OkxCandleBar>
    ): TimeframeQuantMetrics? {
        if (candles.size < 15) return null
        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val lastBar = candles.last()
        val lastClose = lastBar.close
        val candleChangePct = if (lastBar.open > 0) ((lastBar.close - lastBar.open) / lastBar.open) * 100.0 else 0.0

        val recentSlice = candles.takeLast(min(50, candles.size))
        val periodHigh = recentSlice.maxOf { it.high }
        val periodLow = recentSlice.minOf { it.low }

        // 1. RSI (14) with Wilder's Smoothing
        val rsiSeries = computeRsiSeries(closes, 14)
        val rsi14 = rsiSeries.lastOrNull() ?: 50.0

        // 2. StochRSI (14)
        val recentRsi = rsiSeries.takeLast(14)
        val minRsi = recentRsi.minOrNull() ?: 0.0
        val maxRsi = recentRsi.maxOrNull() ?: 100.0
        val stochRsiK = if (maxRsi - minRsi > 1e-6) {
            ((rsi14 - minRsi) / (maxRsi - minRsi) * 100.0).coerceIn(0.0, 100.0)
        } else 50.0

        // 3. EMA 9, 20, 50, 200
        val ema9 = computeEmaSeries(closes, 9).lastOrNull() ?: lastClose
        val ema20 = computeEmaSeries(closes, 20).lastOrNull() ?: lastClose
        val ema50 = computeEmaSeries(closes, min(50, closes.size)).lastOrNull() ?: lastClose
        val ema200 = computeEmaSeries(closes, min(200, closes.size)).lastOrNull() ?: lastClose

        // 4. MACD (12, 26, 9)
        val ema12Series = computeEmaSeries(closes, 12)
        val ema26Series = computeEmaSeries(closes, 26)
        val macdLineSeries = closes.indices.map { i -> ema12Series[i] - ema26Series[i] }
        val signalSeries = computeEmaSeries(macdLineSeries, 9)
        val macdLine = macdLineSeries.lastOrNull() ?: 0.0
        val macdSignal = signalSeries.lastOrNull() ?: 0.0
        val macdHistogram = macdLine - macdSignal

        // 5. Bollinger Bands (20, 2.0)
        val bbWindow = closes.takeLast(min(20, closes.size))
        val bbMid = bbWindow.average()
        val variance = bbWindow.map { (it - bbMid) * (it - bbMid) }.average()
        val stdDev = sqrt(variance)
        val bbUpper = bbMid + 2.0 * stdDev
        val bbLower = max(0.0, bbMid - 2.0 * stdDev)
        val bbBandwidthPct = if (bbMid > 0) ((bbUpper - bbLower) / bbMid) * 100.0 else 0.0

        // 6. ATR (14)
        val trList = mutableListOf<Double>()
        for (i in 1 until candles.size) {
            val h = highs[i]
            val l = lows[i]
            val prevC = closes[i - 1]
            val tr = max(h - l, max(abs(h - prevC), abs(l - prevC)))
            trList.add(tr)
        }
        val atr14 = if (trList.isNotEmpty()) {
            trList.takeLast(min(14, trList.size)).average()
        } else (lastBar.high - lastBar.low)
        val atrPercent = if (lastClose > 0) (atr14 / lastClose) * 100.0 else 0.0

        // 7. VWAP (Volume-Weighted Average Price over recent 30 bars)
        val vwapBars = candles.takeLast(min(30, candles.size))
        var sumTypicalVol = 0.0
        var sumVol = 0.0
        for (b in vwapBars) {
            val typical = (b.high + b.low + b.close) / 3.0
            val v = if (b.volumeBase > 0) b.volumeBase else 1.0
            sumTypicalVol += typical * v
            sumVol += v
        }
        val vwap = if (sumVol > 0) sumTypicalVol / sumVol else lastClose

        // 8. Classic Floor Pivot Points based on previous completed bar / swing range
        val swingSlice = candles.dropLast(1).takeLast(min(24, max(1, candles.size - 1)))
        val pHigh = swingSlice.maxOf { it.high }
        val pLow = swingSlice.minOf { it.low }
        val pClose = swingSlice.last().close
        val pivot = (pHigh + pLow + pClose) / 3.0
        val r1 = (2.0 * pivot) - pLow
        val s1 = (2.0 * pivot) - pHigh
        val r2 = pivot + (pHigh - pLow)
        val s2 = pivot - (pHigh - pLow)
        val r3 = pHigh + 2.0 * (pivot - pLow)
        val s3 = max(0.0, pLow - 2.0 * (pHigh - pivot))

        // 9. Fibonacci Retracement Levels on active range
        val fibSpan = periodHigh - periodLow
        val fib236 = periodHigh - fibSpan * 0.236
        val fib382 = periodHigh - fibSpan * 0.382
        val fib500 = periodHigh - fibSpan * 0.500
        val fib618 = periodHigh - fibSpan * 0.618
        val fib786 = periodHigh - fibSpan * 0.786

        // 10. Volume Spike Ratio
        val avgVol20 = candles.dropLast(1).takeLast(min(20, max(1, candles.size - 1)))
            .map { it.volumeBase }.average().coerceAtLeast(1e-9)
        val volRatio = (lastBar.volumeBase / avgVol20).coerceIn(0.1, 20.0)

        // 11. Quantitative Score (-100 to +100)
        var score = 0
        if (lastClose > ema20) score += 15 else score -= 15
        if (ema20 > ema50) score += 20 else score -= 20
        if (lastClose > ema200) score += 20 else score -= 20
        if (macdHistogram > 0) score += 15 else score -= 15
        if (lastClose > vwap) score += 10 else score -= 10
        when {
            rsi14 < 30.0 -> score += 20 // Oversold bounce potential
            rsi14 in 52.0..68.0 -> score += 12 // Healthy bullish momentum
            rsi14 > 74.0 -> score -= 20 // Overbought exhaustion
            rsi14 in 32.0..48.0 -> score -= 12 // Bearish momentum
        }
        val clampedScore = score.coerceIn(-100, 100)

        val biasKu = when {
            clampedScore >= 45 -> "بەرزبوونەوەی بەهێز (Strong Bullish)"
            clampedScore >= 15 -> "ئەرێنی / کڕین (Bullish)"
            clampedScore <= -45 -> "دابەزینی بەهێز (Strong Bearish)"
            clampedScore <= -15 -> "نەرێنی / فرۆشتن (Bearish)"
            else -> "هاوسەنگ / چاوەڕوانی (Neutral)"
        }

        val tfLabelKu = when (timeframe) {
            "15m" -> "١٥ خولەک (15m)"
            "1H" -> "١ کاتژمێر (1H)"
            "4H" -> "٤ کاتژمێر (4H)"
            "1D" -> "١ ڕۆژ (1D)"
            else -> timeframe
        }

        return TimeframeQuantMetrics(
            timeframe = timeframe,
            timeframeLabelKu = tfLabelKu,
            lastClose = lastClose,
            candleChangePercent = candleChangePct,
            periodHigh = periodHigh,
            periodLow = periodLow,
            rsi14 = rsi14,
            stochRsiK = stochRsiK,
            macdLine = macdLine,
            macdSignal = macdSignal,
            macdHistogram = macdHistogram,
            ema9 = ema9,
            ema20 = ema20,
            ema50 = ema50,
            ema200 = ema200,
            bollingerUpper = bbUpper,
            bollingerMiddle = bbMid,
            bollingerLower = bbLower,
            bollingerBandwidthPercent = bbBandwidthPct,
            atr14 = atr14,
            atrPercent = atrPercent,
            vwap = vwap,
            pivotPoint = pivot,
            resistance1 = r1,
            resistance2 = r2,
            resistance3 = r3,
            support1 = s1,
            support2 = s2,
            support3 = s3,
            fib236 = fib236,
            fib382 = fib382,
            fib500 = fib500,
            fib618 = fib618,
            fib786 = fib786,
            volumeSpikeRatio = volRatio,
            quantScore = clampedScore,
            trendBiasKu = biasKu,
            isBullish = clampedScore >= 0
        )
    }

    fun analyzeOrderBook(
        instId: String,
        dto: OkxOrderBookDto,
        referencePrice: Double
    ): OrderBookLiquidityReport? {
        if (dto.bids.isEmpty() && dto.asks.isEmpty()) return null
        val isSwap = instId.endsWith("SWAP")

        val parsedBids = dto.bids.mapNotNull { row ->
            val px = row.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val sz = row.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val orders = row.getOrNull(3)?.toIntOrNull() ?: 1
            val notional = if (isSwap) sz * px else sz * px
            val distPct = if (referencePrice > 0) ((px - referencePrice) / referencePrice) * 100.0 else 0.0
            LiquidityWallItem(
                side = "BID",
                price = px,
                sizeCoin = sz,
                notionalUsdt = notional,
                distanceFromMidPercent = distPct,
                ordersCount = orders
            )
        }

        val parsedAsks = dto.asks.mapNotNull { row ->
            val px = row.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
            val sz = row.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val orders = row.getOrNull(3)?.toIntOrNull() ?: 1
            val notional = sz * px
            val distPct = if (referencePrice > 0) ((px - referencePrice) / referencePrice) * 100.0 else 0.0
            LiquidityWallItem(
                side = "ASK",
                price = px,
                sizeCoin = sz,
                notionalUsdt = notional,
                distanceFromMidPercent = distPct,
                ordersCount = orders
            )
        }

        val totalBidsUsdt = parsedBids.sumOf { it.notionalUsdt }
        val totalAsksUsdt = parsedAsks.sumOf { it.notionalUsdt }
        val combinedUsdt = (totalBidsUsdt + totalAsksUsdt).coerceAtLeast(1e-6)
        val bidPct = (totalBidsUsdt / combinedUsdt) * 100.0
        val askPct = (totalAsksUsdt / combinedUsdt) * 100.0
        val imbalanceRatio = if (totalAsksUsdt > 0) totalBidsUsdt / totalAsksUsdt else 1.0

        val bestBid = parsedBids.firstOrNull()
        val bestAsk = parsedAsks.firstOrNull()
        val microPrice = if (bestBid != null && bestAsk != null && (bestBid.sizeCoin + bestAsk.sizeCoin) > 0) {
            (bestBid.price * bestAsk.sizeCoin + bestAsk.price * bestBid.sizeCoin) / (bestBid.sizeCoin + bestAsk.sizeCoin)
        } else referencePrice

        val spreadBps = if (bestBid != null && bestAsk != null && referencePrice > 0) {
            ((bestAsk.price - bestBid.price) / referencePrice) * 10000.0
        } else 0.0

        val topBidWalls = parsedBids.sortedByDescending { it.notionalUsdt }.take(4)
        val topAskWalls = parsedAsks.sortedByDescending { it.notionalUsdt }.take(4)

        val summaryKu = when {
            bidPct >= 60.0 -> "کڕیاران و نەهەنگەکان بە توندی زاڵن (${String.format(Locale.US, "%.1f%%", bidPct)} قەبارەی کڕین)"
            bidPct >= 53.0 -> "پاڵپشتی کڕیاران لە Order Book بەهێزترە لە فرۆشیاران"
            askPct >= 60.0 -> "فشاری فرۆشتنی قورس لەسەر دیوارەکانی سەرەوە هەیە (${String.format(Locale.US, "%.1f%%", askPct)} فرۆشتن)"
            askPct >= 53.0 -> "فرۆشیاران کەمێک باڵادەستترن لە ئاستەکانی نزیک نرخ"
            else -> "هاوسەنگی تەواو لە نێوان داواکاری کڕین و فرۆشتندا هەیە"
        }

        return OrderBookLiquidityReport(
            instId = instId,
            totalBidNotionalUsdt = totalBidsUsdt,
            totalAskNotionalUsdt = totalAsksUsdt,
            bidPressurePercent = bidPct,
            askPressurePercent = askPct,
            imbalanceRatio = imbalanceRatio,
            microPrice = microPrice,
            spreadBps = spreadBps,
            topSupportBidWalls = topBidWalls,
            topResistanceAskWalls = topAskWalls,
            dominantSideSummaryKu = summaryKu
        )
    }

    fun parseFundingRate(dto: OkxFundingRateDto): FundingRateReport {
        val currentPct = (dto.fundingRate.toDoubleOrNull() ?: 0.0) * 100.0
        val nextPct = (dto.nextFundingRate.toDoubleOrNull() ?: currentPct / 100.0) * 100.0
        val annualizedApr = currentPct * 3.0 * 365.0 // 3 funding intervals per day
        val nextTimeMs = dto.fundingTime.toLongOrNull()
            ?: dto.nextFundingTime.toLongOrNull()
            ?: (System.currentTimeMillis() + 3600_000L)
        val minsLeft = max(0L, (nextTimeMs - System.currentTimeMillis()) / 60_000L)

        val sentimentKu = when {
            currentPct > 0.03 -> "گەشبینی زۆر بەرز (گرێبەستی Long قەرەباڵغە - ئەگەری Long Squeeze)"
            currentPct > 0.005 -> "فەندینگی ئاسایی و ئەرێنی (بازاڕ مەیلی بەرزبوونەوەی هەیە)"
            currentPct < -0.02 -> "فشاری Short زۆر زۆرە (ئەگەری تەقینەوەی نرخ بۆ سەرەوە - Short Squeeze)"
            currentPct < 0.0 -> "فەندینگی نەرێنی (مامەڵەکارانی Short زاڵن)"
            else -> "فەندینگی بێلایەن و هاوسەنگ"
        }

        return FundingRateReport(
            swapInstId = dto.instId,
            currentFundingRatePercent = currentPct,
            nextFundingRatePercent = nextPct,
            annualizedAprPercent = annualizedApr,
            nextSettlementTimeMs = nextTimeMs,
            minutesToNextSettlement = minsLeft,
            sentimentLabelKu = sentimentKu
        )
    }

    private fun computeEmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val k = 2.0 / (period + 1.0)
        val result = ArrayList<Double>(values.size)
        var currentEma = values.first()
        result.add(currentEma)
        for (i in 1 until values.size) {
            currentEma = values[i] * k + currentEma * (1.0 - k)
            result.add(currentEma)
        }
        return result
    }

    private fun computeRsiSeries(closes: List<Double>, period: Int = 14): List<Double> {
        if (closes.size <= period) return List(closes.size) { 50.0 }
        val rsiValues = MutableList(closes.size) { 50.0 }
        var avgGain = 0.0
        var avgLoss = 0.0

        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) avgGain += diff else avgLoss -= diff
        }
        avgGain /= period
        avgLoss /= period

        rsiValues[period] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))

        for (i in (period + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) -diff else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
            rsiValues[i] = if (avgLoss == 0.0) 100.0 else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }
        return rsiValues
    }

    fun formatCryptoPrice(price: Double): String {
        val absPrice = abs(price)
        return when {
            absPrice == 0.0 -> "0.00"
            absPrice >= 1000.0 -> String.format(Locale.US, "%,.2f", price)
            absPrice >= 10.0 -> String.format(Locale.US, "%,.3f", price)
            absPrice >= 1.0 -> String.format(Locale.US, "%.4f", price)
            absPrice >= 0.01 -> String.format(Locale.US, "%.5f", price)
            absPrice >= 0.0001 -> String.format(Locale.US, "%.6f", price)
            else -> String.format(Locale.US, "%.8f", price)
        }
    }

    fun formatCompactUsdt(amount: Double): String {
        val absAmt = abs(amount)
        return when {
            absAmt >= 1_000_000_000.0 -> String.format(Locale.US, "$%.2fB", amount / 1_000_000_000.0)
            absAmt >= 1_000_000.0 -> String.format(Locale.US, "$%.2fM", amount / 1_000_000.0)
            absAmt >= 1_000.0 -> String.format(Locale.US, "$%.1fK", amount / 1_000.0)
            else -> String.format(Locale.US, "$%.2f", amount)
        }
    }

    fun formatSignedPercent(pct: Double, decimals: Int = 2): String {
        val sign = if (pct > 0) "+" else ""
        return "$sign${String.format(Locale.US, "%.${decimals}f%%", pct)}"
    }
}
