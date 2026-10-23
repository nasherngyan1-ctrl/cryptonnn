package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.ai.ApiProviderType
import com.example.data.local.ApiKeyEntity
import com.example.domain.QuantMathEngine
import com.example.service.WatchedCoinTarget
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.BullishGreenDim
import com.example.ui.theme.MonoNumberFontFamily
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OkxLimeDim
import com.example.ui.theme.OkxNeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber

@Composable
fun ApiKeyManagerDialog(
    apiKeys: List<ApiKeyEntity>,
    activeAiEngineBadge: String,
    testingKeyId: Int?,
    isBackgroundServiceRunning: Boolean,
    watchedCoinsList: List<WatchedCoinTarget>,
    onToggleBackgroundService: (Boolean) -> Unit,
    onAddApiKeys: (labelKu: String, providerCode: String, rawKeys: String, customModel: String) -> Unit,
    onToggleKeyEnabled: (ApiKeyEntity) -> Unit,
    onDeleteKey: (Int) -> Unit,
    onTestKey: (ApiKeyEntity) -> Unit,
    onResetCooldowns: () -> Unit,
    onDismiss: () -> Unit
) {
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onToggleBackgroundService(true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 18.dp),
            shape = RoundedCornerShape(18.dp),
            color = ObsidianBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(18.dp))
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = OkxNeonLime
                        )
                        Column {
                            Text(
                                text = "سێتینگی ئەپ (باگراوند و کلیلەکانی API)",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "چاودێری باگراوند و گۆڕینی ئۆتۆماتیکی کلیلەکان",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("btn_close_api_key_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "داخستن",
                            tint = TextPrimary
                        )
                    }
                }

                HorizontalDivider(color = ObsidianCardBorder)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Background Operation & Notification Settings Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = ObsidianSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isBackgroundServiceRunning) BullishGreen.copy(alpha = 0.6f) else ObsidianCardBorder,
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = if (isBackgroundServiceRunning) BullishGreen else TextMuted
                                    )
                                    Column {
                                        Text(
                                            text = "کارکردن لە باگراوند و نۆتیفیکەیشنی نرخ (24/7)",
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (isBackgroundServiceRunning) {
                                                "چالاکە — تەنانەت کە لە ئەپەکەش دەردەچیت چاودێری نرخ دەکات"
                                            } else {
                                                "ڕاگیراوە — بۆ وەرگرتنی نۆتیفیکەیشنی کڕین و فرۆشتن چالاکی بکە"
                                            },
                                            fontSize = 11.sp,
                                            color = if (isBackgroundServiceRunning) BullishGreen else TextSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = isBackgroundServiceRunning,
                                    onCheckedChange = { enable ->
                                        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onToggleBackgroundService(enable)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = BullishGreen
                                    )
                                )
                            }

                            if (watchedCoinsList.isNotEmpty()) {
                                HorizontalDivider(color = ObsidianCardBorder)
                                Text(
                                    text = "دراوە چاودێریکراوەکانی ئێستا لە باگراوند (${watchedCoinsList.size}):",
                                    fontSize = 11.sp,
                                    color = AiCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                watchedCoinsList.forEach { target ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ObsidianSurfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = target.instId,
                                            fontFamily = MonoNumberFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = OkxNeonLime
                                        )
                                        val details = buildString {
                                            if (target.buyTargetPrice > 0.0) {
                                                append("کڕین: $${QuantMathEngine.formatCryptoPrice(target.buyTargetPrice)}  ")
                                            }
                                            if (target.sellTargetPrice > 0.0) {
                                                append("فرۆشتن: $${QuantMathEngine.formatCryptoPrice(target.sellTargetPrice)}")
                                            }
                                        }.ifBlank { "چاودێری گشتی نرخ" }
                                        Text(
                                            text = details,
                                            fontFamily = MonoNumberFontFamily,
                                            fontSize = 11.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. API Key & Auto-Rotation Section
                    ApiKeyRotationManagerCard(
                        apiKeys = apiKeys,
                        activeAiEngineBadge = activeAiEngineBadge,
                        testingKeyId = testingKeyId,
                        onAddApiKeys = onAddApiKeys,
                        onToggleKeyEnabled = onToggleKeyEnabled,
                        onDeleteKey = onDeleteKey,
                        onTestKey = onTestKey,
                        onResetCooldowns = onResetCooldowns
                    )
                }
            }
        }
    }
}

@Composable
fun ApiKeyRotationManagerCard(
    apiKeys: List<ApiKeyEntity>,
    activeAiEngineBadge: String,
    testingKeyId: Int?,
    onAddApiKeys: (labelKu: String, providerCode: String, rawKeys: String, customModel: String) -> Unit,
    onToggleKeyEnabled: (ApiKeyEntity) -> Unit,
    onDeleteKey: (Int) -> Unit,
    onTestKey: (ApiKeyEntity) -> Unit,
    onResetCooldowns: () -> Unit
) {
    var selectedProviderCode by remember { mutableStateOf("AUTO") }
    var rawKeysInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var customModelInput by remember { mutableStateOf("") }

    val now = System.currentTimeMillis()
    val readyCount = apiKeys.count { it.isEnabled && it.cooldownUntilMs <= now }
    val cooldownCount = apiKeys.count { it.isEnabled && it.cooldownUntilMs > now }

    val providerOptions = remember {
        listOf(
            "AUTO" to "ئۆتۆماتیکی (Auto)",
            ApiProviderType.GEMINI.id to "Gemini",
            ApiProviderType.GROQ.id to "Groq",
            ApiProviderType.OPENROUTER.id to "OpenRouter",
            ApiProviderType.OPENAI.id to "OpenAI",
            ApiProviderType.DEEPSEEK.id to "DeepSeek"
        )
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Autorenew,
                    contentDescription = null,
                    tint = OkxNeonLime
                )
                Column {
                    Text(
                        text = "کلیلەکانی API و گۆڕینی ئۆتۆماتیکی",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = OkxNeonLime,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "کلیلە ئامادەکان: $readyCount لە ${apiKeys.size} • پشتگیری Gemini, Groq, OpenRouter, OpenAI",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }

            if (cooldownCount > 0) {
                OutlinedButton(
                    onClick = onResetCooldowns,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber)
                ) {
                    Text("چالاککردنەوەی کلیلە وەستاوەکان ($cooldownCount)")
                }
            }

            // Provider Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                providerOptions.forEach { (code, titleKu) ->
                    val isSelected = selectedProviderCode == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) OkxNeonLime else ObsidianSurfaceVariant)
                            .border(
                                1.dp,
                                if (isSelected) OkxNeonLime else ObsidianCardBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                selectedProviderCode = code
                                customModelInput = if (code != "AUTO") {
                                    ApiProviderType.fromId(code).defaultModel
                                } else {
                                    ""
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = titleKu,
                            fontSize = 11.sp,
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            OutlinedTextField(
                value = rawKeysInput,
                onValueChange = { rawKeysInput = it },
                label = {
                    Text("کلیلی API لێرە دابنێ (دەتوانیت چەند کلیلێک بەیەکەوە دابنێیت)")
                },
                placeholder = {
                    Text(
                        text = "AIzaSy...\ngsk_...",
                        fontFamily = MonoNumberFontFamily,
                        fontSize = 12.sp
                    )
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_api_keys_bulk"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OkxNeonLime,
                    unfocusedBorderColor = ObsidianCardBorder
                )
            )

            Button(
                onClick = {
                    if (rawKeysInput.isNotBlank()) {
                        onAddApiKeys(
                            labelInput,
                            selectedProviderCode,
                            rawKeysInput,
                            customModelInput
                        )
                        rawKeysInput = ""
                        labelInput = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_save_api_keys"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OkxNeonLime,
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "زیادکردنی کلیل",
                    fontWeight = FontWeight.Bold
                )
            }

            if (apiKeys.isNotEmpty()) {
                apiKeys.forEachIndexed { idx, item ->
                    val inCooldown = item.cooldownUntilMs > now
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (item.isEnabled) BullishGreen.copy(alpha = 0.4f) else ObsidianCardBorder,
                                RoundedCornerShape(10.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${idx + 1} ${item.providerType} • ${maskApiKey(item.apiKey)}",
                                    fontFamily = MonoNumberFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (item.isEnabled) TextPrimary else TextMuted
                                )
                                Text(
                                    text = if (inCooldown) "لە پشووی کاتیدایە" else item.lastStatusKu,
                                    fontSize = 10.sp,
                                    color = if (inCooldown) WarningAmber else BullishGreen
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { onTestKey(item) },
                                    enabled = testingKeyId != item.id,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    if (testingKeyId == item.id) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = AiCyan
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.NetworkCheck,
                                            contentDescription = "پشکنین",
                                            tint = AiCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Switch(
                                    checked = item.isEnabled,
                                    onCheckedChange = { onToggleKeyEnabled(item) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = BullishGreen
                                    )
                                )

                                IconButton(
                                    onClick = { onDeleteKey(item.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "سڕینەوە",
                                        tint = BearishRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun maskApiKey(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length <= 10) return "****"
    return "${trimmed.take(6)}...${trimmed.takeLast(4)}"
}
