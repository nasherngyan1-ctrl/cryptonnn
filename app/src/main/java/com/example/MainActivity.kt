package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AiScannerAndAdvisorScreen
import com.example.ui.screens.ApiKeyManagerDialog
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OkxLimeDim
import com.example.ui.theme.OkxNeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
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
                    AbdulbasitTraidingApp()
                }
            }
        }
    }
}

@Composable
fun AbdulbasitTraidingApp(
    viewModel: OkxAiViewModel = viewModel()
) {
    val isSettingsOpen by viewModel.isApiKeyModalOpen.collectAsStateWithLifecycle()
    val isBackgroundServiceRunning by viewModel.isBackgroundServiceRunning.collectAsStateWithLifecycle()
    val watchedCoinsList by viewModel.watchedCoinsList.collectAsStateWithLifecycle()

    val apiKeys by viewModel.apiKeys.collectAsStateWithLifecycle()
    val activeAiEngineBadge by viewModel.activeAiEngineBadge.collectAsStateWithLifecycle()
    val testingKeyId by viewModel.testingKeyId.collectAsStateWithLifecycle()

    val advisorState by viewModel.advisorState.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val statusBannerKu by viewModel.statusBannerKu.collectAsStateWithLifecycle()

    if (isSettingsOpen) {
        ApiKeyManagerDialog(
            apiKeys = apiKeys,
            activeAiEngineBadge = activeAiEngineBadge,
            testingKeyId = testingKeyId,
            isBackgroundServiceRunning = isBackgroundServiceRunning,
            watchedCoinsList = watchedCoinsList,
            onToggleBackgroundService = viewModel::toggleBackgroundService,
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
            AbdulbasitTopBar(
                isBackgroundActive = isBackgroundServiceRunning,
                showClearButton = chatHistory.size > 1,
                onClearChat = viewModel::clearChatHistory,
                onOpenSettings = { viewModel.setApiKeyModalOpen(true) }
            )
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
                            .padding(horizontal = 12.dp, vertical = 4.dp)
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
                AiScannerAndAdvisorScreen(
                    advisorState = advisorState,
                    chatHistory = chatHistory,
                    onAskAdvisor = viewModel::askAiAdvisor
                )
            }
        }
    }
}

@Composable
private fun AbdulbasitTopBar(
    isBackgroundActive: Boolean,
    showClearButton: Boolean,
    onClearChat: () -> Unit,
    onOpenSettings: () -> Unit
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isBackgroundActive) BullishGreen else TextMuted)
                )
                Text(
                    text = "abdulbasit traiding",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (showClearButton) {
                    IconButton(
                        onClick = onClearChat,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "پاککردنەوەی چات",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Unified Settings Button (Background Service + API Keys)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ObsidianSurfaceVariant,
                    modifier = Modifier
                        .border(1.dp, ObsidianCardBorder, RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenSettings)
                        .testTag("btn_open_settings_top")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "سێتینگ",
                            tint = OkxNeonLime,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "سێتینگ",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}
