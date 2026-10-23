package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.remote.OkxApiService
import com.example.domain.QuantMathEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "TradingBgService"

data class WatchedCoinTarget(
    val instId: String,
    val buyTargetPrice: Double = 0.0,
    val sellTargetPrice: Double = 0.0,
    val stopLossPrice: Double = 0.0,
    val referencePriceAtSetup: Double = 0.0
)

class TradingBackgroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorLoopJob: Job? = null
    private val apiService by lazy { OkxApiService.create() }
    private val database by lazy { AppDatabase.getInstance(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_MONITOR
        if (action == ACTION_STOP_MONITOR) {
            _isServiceRunning.value = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val coin = intent?.getStringExtra(EXTRA_INST_ID)?.takeIf { it.isNotBlank() }
        val buyPx = intent?.getDoubleExtra(EXTRA_BUY_TARGET_PRICE, 0.0) ?: 0.0
        val sellPx = intent?.getDoubleExtra(EXTRA_SELL_TARGET_PRICE, 0.0) ?: 0.0
        val slPx = intent?.getDoubleExtra(EXTRA_STOP_LOSS_PRICE, 0.0) ?: 0.0
        val refPx = intent?.getDoubleExtra(EXTRA_REF_PRICE, 0.0) ?: 0.0

        if (coin != null) {
            watchedCoins[coin] = WatchedCoinTarget(
                instId = coin,
                buyTargetPrice = buyPx,
                sellTargetPrice = sellPx,
                stopLossPrice = slPx,
                referencePriceAtSetup = refPx
            )
            _watchedCoinsCount.value = watchedCoins.size
        }

        _isServiceRunning.value = true

        val initialNotification = buildOngoingNotification(
            title = "abdulbasit traiding • چاودێری باگراوند چالاکە",
            body = if (watchedCoins.isEmpty()) {
                "چاودێری بەردەوامی نرخی دراوەکان و ئاگادارکردنەوەی کڕین و فرۆشتن..."
            } else {
                "چاودێری زیندووی: ${watchedCoins.keys.joinToString(", ")}"
            }
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    ONGOING_NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(ONGOING_NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Foreground start fallback: ${e.message}")
        }

        startBackgroundLoop()
        return START_STICKY
    }

    private fun startBackgroundLoop() {
        if (monitorLoopJob?.isActive == true) return
        monitorLoopJob = serviceScope.launch {
            val notifiedEvents = ConcurrentHashMap.newKeySet<String>()

            while (isActive && _isServiceRunning.value) {
                try {
                    val res = apiService.getMarketTickers("SPOT")
                    val tickerMap = res.data
                        .mapNotNull { QuantMathEngine.mapTickerDto(it) }
                        .associateBy { it.instId }

                    // Build live status line for ongoing notification
                    val trackedList = if (watchedCoins.isNotEmpty()) {
                        watchedCoins.keys.toList()
                    } else {
                        listOf("BTC-USDT", "ETH-USDT", "SOL-USDT")
                    }

                    val statusParts = trackedList.take(4).mapNotNull { id ->
                        val baseId = id.removeSuffix("-SWAP")
                        val t = tickerMap[baseId] ?: tickerMap[id]
                        if (t != null) {
                            "${t.baseSymbol}: $${QuantMathEngine.formatCryptoPrice(t.lastPrice)} (${QuantMathEngine.formatSignedPercent(t.change24hPercent)})"
                        } else null
                    }

                    if (statusParts.isNotEmpty()) {
                        val updatedNotification = buildOngoingNotification(
                            title = "abdulbasit traiding • چاودێری نرخی دراوەکان",
                            body = statusParts.joinToString("  |  ")
                        )
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.notify(ONGOING_NOTIFICATION_ID, updatedNotification)
                    }

                    // Check each AI-watched coin for Buy ("لەوەندە بیکڕە") and Sell ("کە گەیشتە ئەوەندە بیفرۆشە") targets
                    for ((instId, target) in watchedCoins) {
                        val lookupId = instId.removeSuffix("-SWAP")
                        val liveTicker = tickerMap[lookupId] ?: continue
                        val livePx = liveTicker.lastPrice

                        // 1. Sell Target Reached ("کە گەیشتە ئەوەندە بیفرۆشە")
                        if (target.sellTargetPrice > 0.0 && livePx >= target.sellTargetPrice) {
                            val key = "SELL:$instId:${target.sellTargetPrice}"
                            if (notifiedEvents.add(key)) {
                                sendInstantAlertNotification(
                                    context = applicationContext,
                                    title = "🎯 کاتی فرۆشتنە بۆ قازانج! ($instId)",
                                    message = "نرخی $instId گەیشتە $${QuantMathEngine.formatCryptoPrice(livePx)}! بەپێی ڕاوێژکاری AI لە $${QuantMathEngine.formatCryptoPrice(target.sellTargetPrice)} بیفرۆشە بۆ قازانج."
                                )
                            }
                        }

                        // 2. Buy Target Reached ("لەوەندە بیکڕە")
                        if (target.buyTargetPrice > 0.0 &&
                            target.referencePriceAtSetup > target.buyTargetPrice &&
                            livePx <= target.buyTargetPrice * 1.002
                        ) {
                            val key = "BUY:$instId:${target.buyTargetPrice}"
                            if (notifiedEvents.add(key)) {
                                sendInstantAlertNotification(
                                    context = applicationContext,
                                    title = "🟢 گەیشتە نرخی کڕین! ($instId)",
                                    message = "نرخی $instId هاتە سەر $${QuantMathEngine.formatCryptoPrice(livePx)}! بەپێی ڕاوێژکاری AI ئێستا خاڵی گونجاوە بۆ کڕین ($${QuantMathEngine.formatCryptoPrice(target.buyTargetPrice)})."
                                )
                            }
                        }

                        // 3. Stop-Loss Warning
                        if (target.stopLossPrice > 0.0 &&
                            target.referencePriceAtSetup > target.stopLossPrice &&
                            livePx <= target.stopLossPrice
                        ) {
                            val key = "SL:$instId:${target.stopLossPrice}"
                            if (notifiedEvents.add(key)) {
                                sendInstantAlertNotification(
                                    context = applicationContext,
                                    title = "⚠️ ئاگاداری ستۆپ لۆس ($instId)",
                                    message = "نرخی $instId دابەزی بۆ $${QuantMathEngine.formatCryptoPrice(livePx)} (ئاستی ستۆپ لۆس: $${QuantMathEngine.formatCryptoPrice(target.stopLossPrice)})."
                                )
                            }
                        }
                    }

                    // Also check Room PriceAlertEntity items
                    val roomAlerts = database.okxTradingDao().getUntriggeredAlertsSnapshot()
                    for (alert in roomAlerts) {
                        val lookupId = alert.instId.removeSuffix("-SWAP")
                        val liveTicker = tickerMap[lookupId] ?: continue
                        val hit = if (alert.isAboveTarget) {
                            liveTicker.lastPrice >= alert.targetPrice
                        } else {
                            liveTicker.lastPrice <= alert.targetPrice
                        }
                        if (hit) {
                            database.okxTradingDao().markAlertTriggered(alert.id)
                            sendInstantAlertNotification(
                                context = applicationContext,
                                title = "🔔 گەیشتە نرخی دیاریکراو (${alert.instId})",
                                message = "${alert.noteKu} • نرخی ئێستا: $${QuantMathEngine.formatCryptoPrice(liveTicker.lastPrice)}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Background poll tick error: ${e.message}")
                }

                delay(12_000L)
            }
        }
    }

    private fun buildOngoingNotification(title: String, body: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ONGOING_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingOpen)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        _isServiceRunning.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ONGOING_ID = "abdulbasit_traiding_bg_channel"
        const val CHANNEL_ALERTS_ID = "abdulbasit_traiding_alerts_channel"
        private const val ONGOING_NOTIFICATION_ID = 4101
        private const val ACTION_START_MONITOR = "ACTION_START_MONITOR"
        private const val ACTION_STOP_MONITOR = "ACTION_STOP_MONITOR"
        private const val EXTRA_INST_ID = "EXTRA_INST_ID"
        private const val EXTRA_BUY_TARGET_PRICE = "EXTRA_BUY_TARGET_PRICE"
        private const val EXTRA_SELL_TARGET_PRICE = "EXTRA_SELL_TARGET_PRICE"
        private const val EXTRA_STOP_LOSS_PRICE = "EXTRA_STOP_LOSS_PRICE"
        private const val EXTRA_REF_PRICE = "EXTRA_REF_PRICE"

        private val watchedCoins = ConcurrentHashMap<String, WatchedCoinTarget>()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _watchedCoinsCount = MutableStateFlow(0)
        val watchedCoinsCount: StateFlow<Int> = _watchedCoinsCount.asStateFlow()

        fun ensureNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val ongoingChannel = NotificationChannel(
                    CHANNEL_ONGOING_ID,
                    "چاودێری باگراوند (abdulbasit traiding)",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "چاودێری بەردەوامی نرخی دراوەکان لە باگراونددا"
                }
                val alertChannel = NotificationChannel(
                    CHANNEL_ALERTS_ID,
                    "ئاگادارکردنەوەی کڕین و فرۆشتنی دراو",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "ئاگادارکردنەوەی دەستبەجێ کاتێک دراوێک دەگاتە نرخی کڕین یان فرۆشتنی قازانج"
                }
                nm.createNotificationChannel(ongoingChannel)
                nm.createNotificationChannel(alertChannel)
            }
        }

        fun registerCoinTargetsInBackground(
            context: Context,
            instId: String,
            buyTargetPrice: Double = 0.0,
            sellTargetPrice: Double = 0.0,
            stopLossPrice: Double = 0.0,
            referencePrice: Double = 0.0
        ) {
            ensureNotificationChannels(context)
            val intent = Intent(context, TradingBackgroundService::class.java).apply {
                action = ACTION_START_MONITOR
                putExtra(EXTRA_INST_ID, instId)
                putExtra(EXTRA_BUY_TARGET_PRICE, buyTargetPrice)
                putExtra(EXTRA_SELL_TARGET_PRICE, sellTargetPrice)
                putExtra(EXTRA_STOP_LOSS_PRICE, stopLossPrice)
                putExtra(EXTRA_REF_PRICE, referencePrice)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start foreground service: ${e.message}")
            }
        }

        fun stopBackgroundMonitor(context: Context) {
            val intent = Intent(context, TradingBackgroundService::class.java).apply {
                action = ACTION_STOP_MONITOR
            }
            runCatching { context.startService(intent) }
        }

        fun sendInstantAlertNotification(
            context: Context,
            title: String,
            message: String
        ) {
            runCatching {
                ensureNotificationChannels(context)
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingOpen = PendingIntent.getActivity(
                    context,
                    1,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setAutoCancel(true)
                    .setContentIntent(pendingOpen)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()

                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify((System.currentTimeMillis() % 10000).toInt() + 5000, notification)
            }
        }
    }
}
