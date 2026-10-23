package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AiDeepIntelligenceScreen
import com.example.ui.screens.AiScannerAndAdvisorScreen
import com.example.ui.screens.ApiKeyManagerDialog
import com.example.ui.screens.MarketDataMatrixScreen
import com.example.ui.screens.SavedSignalsAndCalcScreen
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.MonoNumberFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OkxLimeDim
import com.example.ui.theme.OkxNeonLime
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.OkxAiViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBackground
                ) {
                    OkxKurdAiApp()
                }
            }
        }
    }
}

@Composable
fun OkxKurdAiApp(
    viewModel: OkxAiViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isApiKeyModalOpen by viewModel.isApiKeyModalOpen.collectAsStateWithLifecycle()
    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    val activeAiEngineBadge by viewModel.activeAiEngineBadge.collectAsStateWithLifecycle()
    val testingKeyId by viewModel.testingKeyId.collectAsStateWithLifecycle()

    val selectedInstType by viewModel.selectedInstType.collectAsStateWithLifecycle()
    val tickers by viewModel.tickers.collectAsStateWithLifecycle()
    val isMarketLoading by viewModel.isMarketLoading.collectAsStateWithLifecycle()
    val marketError by viewModel.marketError.collectAsStateWithLifecycle()
    val lastUpdatedMs by viewModel.lastUpdatedMs.collectAsStateWithLifecycle()
    val autoRefreshEnabled by viewModel.autoRefreshEnabled.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val watchlistIds by viewModel.watchlistIds.collectAsStateWithLifecycle()

    val selectedInstId by viewModel.selectedInstId.collectAsStateWithLifecycle()
    val activeTelemetry by viewModel.activeTelemetry.collectAsStateWithLifecycle()
    val isTelemetryLoading by viewModel.isTelemetryLoading.collectAsStateWithLifecycle()
    val telemetryError by viewModel.telemetryError.collectAsStateWithLifecycle()
    val aiPredictionState by viewModel.aiPredictionState.collectAsStateWithLifecycle()

    val screenshotExitState by viewModel.screenshotExitState.collectAsStateWithLifecycle()
    val aiScannerState by viewModel.aiScannerState.collectAsStateWithLifecycle()
    val advisorState by viewModel.advisorState.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()

    val savedSignals by viewModel.savedSignals.collectAsStateWithLifecycle()
    val priceAlerts by viewModel.priceAlerts.collectAsStateWithLifecycle()
    val statusBannerKu by viewModel.statusBannerKu.collectAsStateWithLifecycle()

    // Ensure system back returns to primary Market tab when on secondary tabs
    if (selectedTab != 0) {
        BackHandler {
            viewModel.selectTab(0)
        }
    }

    if (isApiKeyModalOpen) {
        ApiKeyManagerDialog(
            apiKeys = apiKeys,
            activeAiEngineBadge = activeAiEngineBadge,
            testingKeyId = testingKeyId,
            onAddApiKeys = viewModel::addApiKeys,
            onToggleKeyEnabled = viewModel::toggleApiKeyEnabled,
            onDeleteKey = viewModel::deleteApiKey,
            onTestKey = viewModel::testSingleApiKey,
            onResetCooldowns = viewModel::resetAllKeyCooldowns,
            onDismiss = { viewModel.setApiKeyModalOpen(false) }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            OkxTopBrandBar(
                activeInstId = selectedInstId,
                activeAiEngineBadge = activeAiEngineBadge,
                enabledKeysCount = apiKeys.count { it.isEnabled },
                onOpenApiKeyModal = { viewModel.setApiKeyModalOpen(true) },
                onSwitchToAiTab = { viewModel.selectTab(1) }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = ObsidianSurface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(1.dp, ObsidianCardBorder)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    modifier = Modifier.testTag("nav_tab_market"),
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Analytics else Icons.Outlined.Analytics,
                            contentDescription = "بازاڕی OKX"
                        )
                    },
                    label = { Text("بازاڕی OKX", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = OkxNeonLime,
                        indicatorColor = OkxNeonLime,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    modifier = Modifier.testTag("nav_tab_ai_intel"),
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome,
                            contentDescription = "پێشبینی AI و داتا"
                        )
                    },
                    label = { Text("پێشبینی AI", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = AiCyan,
                        indicatorColor = AiCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    modifier = Modifier.testTag("nav_tab_ai_scanner"),
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.Radar else Icons.Outlined.Radar,
                            contentDescription = "سکێنەر و ڕاوێژکاری AI"
                        )
                    },
                    label = { Text("ڕاوێژکاری AI", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = OkxNeonLime,
                        indicatorColor = OkxNeonLime,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { viewModel.selectTab(3) },
                    modifier = Modifier.testTag("nav_tab_tools"),
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.Calculate else Icons.Outlined.Calculate,
                            contentDescription = "کلیل و بژمێر"
                        )
                    },
                    label = { Text("کلیل و بژمێر", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = OkxNeonLime,
                        indicatorColor = OkxNeonLime,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedVisibility(visible = statusBannerKu != null) {
                statusBannerKu?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OkxLimeDim)
                            .border(1.dp, OkxNeonLime, RoundedCornerShape(10.dp))
                            .clickable { viewModel.clearStatusBanner() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OkxNeonLime,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "داخستن",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimary
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> MarketDataMatrixScreen(
                        tickers = tickers,
                        selectedInstType = selectedInstType,
                        isLoading = isMarketLoading,
                        errorMessage = marketError,
                        lastUpdatedMs = lastUpdatedMs,
                        autoRefreshEnabled = autoRefreshEnabled,
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategory,
                        watchlistIds = watchlistIds,
                        onSelectInstType = viewModel::setMarketInstType,
                        onSearchQueryChange = viewModel::updateSearchQuery,
                        onSelectCategory = viewModel::selectFilterCategory,
                        onToggleAutoRefresh = viewModel::toggleAutoRefresh,
                        onManualRefresh = { viewModel.refreshMarketTickers(showSpinner = true) },
                        onToggleWatchlist = viewModel::toggleWatchlist,
                        onInspectInstrumentWithAi = { instId ->
                            viewModel.selectInstrumentAndAnalyze(
                                instId = instId,
                                switchToDetailTab = true,
                                autoRunAi = true
                            )
                        }
                    )

                    1 -> AiDeepIntelligenceScreen(
                        selectedInstId = selectedInstId,
                        selectedInstType = selectedInstType,
                        telemetry = activeTelemetry,
                        isTelemetryLoading = isTelemetryLoading,
                        telemetryError = telemetryError,
                        aiPredictionState = aiPredictionState,
                        onSelectCoin = { instId ->
                            viewModel.selectInstrumentAndAnalyze(
                                instId = instId,
                                switchToDetailTab = false,
                                autoRunAi = true
                            )
                        },
                        onRefreshAll = viewModel::refreshActiveInstrumentAndAi,
                        onTriggerAiPrediction = { viewModel.triggerGeminiAiPrediction() },
                        onSaveAiPrediction = viewModel::saveCurrentAiPrediction,
                        onQuickSetAlert = viewModel::createPriceAlert
                    )

                    2 -> AiScannerAndAdvisorScreen(
                        activeInstId = selectedInstId,
                        screenshotExitState = screenshotExitState,
                        scannerState = aiScannerState,
                        advisorState = advisorState,
                        chatHistory = chatHistory,
                        onAnalyzeScreenshotExit = viewModel::analyzeScreenshotForProfitExit,
                        onSaveExitPlanAsSignal = viewModel::saveScreenshotExitPlanAsSignal,
                        onSetPriceAlert = viewModel::createPriceAlert,
                        onRunMarketScanner = viewModel::runMarketWideAiScanner,
                        onAskAdvisor = viewModel::askAiAdvisor,
                        onInspectCoin = { instId ->
                            viewModel.selectInstrumentAndAnalyze(
                                instId = instId,
                                switchToDetailTab = true,
                                autoRunAi = true
                            )
                        }
                    )

                    3 -> SavedSignalsAndCalcScreen(
                        activeInstId = selectedInstId,
                        activeSpotPrice = activeTelemetry?.ticker?.lastPrice
                            ?: tickers.find { it.instId == selectedInstId }?.lastPrice
                            ?: 0.0,
                        liveTickers = tickers,
                        savedSignals = savedSignals,
                        priceAlerts = priceAlerts,
                        apiKeys = apiKeys,
                        activeAiEngineBadge = activeAiEngineBadge,
                        testingKeyId = testingKeyId,
                        onAddApiKeys = viewModel::addApiKeys,
                        onToggleApiKeyEnabled = viewModel::toggleApiKeyEnabled,
                        onDeleteApiKey = viewModel::deleteApiKey,
                        onTestApiKey = viewModel::testSingleApiKey,
                        onResetKeyCooldowns = viewModel::resetAllKeyCooldowns,
                        onDeleteSignal = viewModel::deleteSavedSignal,
                        onAddPriceAlert = viewModel::createPriceAlert,
                        onDeletePriceAlert = viewModel::deletePriceAlert,
                        onInspectCoin = { instId ->
                            viewModel.selectInstrumentAndAnalyze(
                                instId = instId,
                                switchToDetailTab = true,
                                autoRunAi = true
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OkxTopBrandBar(
    activeInstId: String,
    activeAiEngineBadge: String,
    enabledKeysCount: Int,
    onOpenApiKeyModal: () -> Unit,
    onSwitchToAiTab: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(OkxNeonLime)
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "OKX AI",
                        fontFamily = MonoNumberFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color.Black
                    )
                }
                Column {
                    Text(
                        text = "ئۆکەی ئێکس کورد AI پرۆ",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        color = TextPrimary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(BullishGreen)
                        )
                        Text(
                            text = activeAiEngineBadge,
                            fontFamily = MonoNumberFontFamily,
                            fontSize = 10.sp,
                            color = TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quick API Key Manager Button in Top Bar
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = OkxLimeDim,
                    modifier = Modifier
                        .border(1.dp, OkxNeonLime.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenApiKeyModal)
                        .testTag("btn_open_api_keys_top")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "کلیلەکانی API",
                            tint = OkxNeonLime,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "کلیل ($enabledKeysCount)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = OkxNeonLime
                        )
                    }
                }

                // Active Coin Chip
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AiCyanDim,
                    modifier = Modifier
                        .border(1.dp, AiCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onSwitchToAiTab)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AiCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = activeInstId,
                            fontFamily = MonoNumberFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = AiCyan
                        )
                    }
                }
            }
        }
    }
}
