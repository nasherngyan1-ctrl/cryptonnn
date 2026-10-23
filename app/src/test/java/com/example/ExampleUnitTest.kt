package com.example

import com.example.data.model.OkxCandleBar
import com.example.data.model.OkxOrderBookDto
import com.example.data.model.OkxTickerDto
import com.example.domain.QuantMathEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun quantMathEngine_computesMultiTimeframeMetricsAccurately() {
        val candles = (1..60).map { i ->
            val base = 65000.0 + (i * 45.0)
            OkxCandleBar(
                timestampMs = 1700000000000L + (i * 3600_000L),
                open = base - 20.0,
                high = base + 80.0,
                low = base - 50.0,
                close = base + 30.0,
                volumeBase = 120.0 + (i * 2.0),
                volumeUsdt = (120.0 + (i * 2.0)) * base
            )
        }

        val metrics = QuantMathEngine.computeTimeframeMetrics("1H", candles)
        assertNotNull(metrics)
        metrics?.let { m ->
            assertTrue("RSI should be bullish on steady uptrend", m.rsi14 > 60.0)
            assertTrue("EMA9 should be above EMA20 on steady uptrend", m.ema9 > m.ema20)
            assertTrue("Bollinger Upper should be above Middle", m.bollingerUpper > m.bollingerMiddle)
            assertTrue("Resistance 1 should be above Support 1", m.resistance1 > m.support1)
            assertTrue("Fibonacci 0.236 should be higher than 0.618", m.fib236 > m.fib618)
        }
    }

    @Test
    fun quantMathEngine_analyzesOrderBookWhaleWalls() {
        val orderBookDto = OkxOrderBookDto(
            bids = listOf(
                listOf("67500.0", "15.5", "0", "12"),
                listOf("67480.0", "4.2", "0", "5"),
                listOf("67450.0", "25.0", "0", "19")
            ),
            asks = listOf(
                listOf("67510.0", "5.0", "0", "4"),
                listOf("67550.0", "8.0", "0", "7")
            ),
            ts = "1700000000000"
        )

        val report = QuantMathEngine.analyzeOrderBook("BTC-USDT", orderBookDto, 67505.0)
        assertNotNull(report)
        report?.let { r ->
            assertTrue("Bids should dominate in this test book", r.bidPressurePercent > 70.0)
            assertEquals(67450.0, r.topSupportBidWalls.first().price, 0.01)
        }
    }

    @Test
    fun quantMathEngine_mapsTickerDtoCorrectly() {
        val dto = OkxTickerDto(
            instType = "SPOT",
            instId = "SOL-USDT",
            last = "185.50",
            open24h = "175.00",
            high24h = "188.00",
            low24h = "173.50",
            bidPx = "185.48",
            bidSz = "120",
            askPx = "185.52",
            askSz = "95",
            vol24h = "500000",
            volCcy24h = "92500000"
        )
        val ticker = QuantMathEngine.mapTickerDto(dto)
        assertNotNull(ticker)
        assertEquals("SOL", ticker?.baseSymbol)
        assertEquals(6.0, ticker?.change24hPercent ?: 0.0, 0.01)
    }
}
