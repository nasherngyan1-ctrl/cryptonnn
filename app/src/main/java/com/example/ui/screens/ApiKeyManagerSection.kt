package com.example.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
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
    onAddApiKeys: (labelKu: String, providerCode: String, rawKeys: String, customModel: String) -> Unit,
    onToggleKeyEnabled: (ApiKeyEntity) -> Unit,
    onDeleteKey: (Int) -> Unit,
    onTestKey: (ApiKeyEntity) -> Unit,
    onResetCooldowns: () -> Unit,
    onDismiss: () -> Unit
) {
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
                    .border(1.dp, AiCyan.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
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
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = OkxNeonLime
                        )
                        Column {
                            Text(
                                text = "بەڕێوەبەری کلیلەکانی API و سووڕانەوەی ئۆتۆماتیکی",
                                style = MaterialTheme.typography.titleMedium,
                                color = OkxNeonLime
                            )
                            Text(
                                text = "چەند جۆرە کلیلێک داخڵ بکە تا ئەپەکە خۆکارانە لە نێوانیاندا بیگۆڕێت",
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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
    val enabledCount = apiKeys.count { it.isEnabled }
    val readyCount = apiKeys.count { it.isEnabled && it.cooldownUntilMs <= now }
    val cooldownCount = apiKeys.count { it.isEnabled && it.cooldownUntilMs > now }
    val totalSuccess = apiKeys.sumOf { it.successCount }
    val totalRotations = apiKeys.sumOf { it.rotationFailoverCount }

    val providerOptions = remember {
        listOf(
            "AUTO" to "ناسینەوەی ئۆتۆماتیکی (Auto)",
            ApiProviderType.GEMINI.id to "Google Gemini",
            ApiProviderType.GROQ.id to "Groq (خێرا)",
            ApiProviderType.OPENROUTER.id to "OpenRouter",
            ApiProviderType.OPENAI.id to "OpenAI (GPT)",
            ApiProviderType.DEEPSEEK.id to "DeepSeek"
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AiCyan.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Autorenew,
                        contentDescription = null,
                        tint = AiCyan
                    )
                    Column {
                        Text(
                            text = "سیستەمی فرە-کلیلی API و گۆڕینی ئۆتۆماتیکی (Auto-Key Rotation)",
                            style = MaterialTheme.typography.titleMedium,
                            color = AiCyan
                        )
                        Text(
                            text = "پشتگیری Gemini، Groq، OpenRouter، OpenAI و DeepSeek دەکات بە سووڕانەوەی خۆکارانە",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = TextSecondary
                        )
                    }
                }
            }

            // Live Rotation Status Telemetry Box
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ObsidianSurfaceVariant)
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (readyCount > 0) BullishGreen else OkxNeonLime)
                    )
                    Text(
                        text = "بزوێنەری چالاکی ئێستا: $activeAiEngineBadge",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = OkxNeonLime
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    KeyMetricBadge("کلیلە ئامادەکان", "$readyCount / ${apiKeys.size}", BullishGreen)
                    KeyMetricBadge("لە پشوودا (429)", "$cooldownCount", if (cooldownCount > 0) WarningAmber else TextSecondary)
                    KeyMetricBadge("وەڵامی سەرکەوتوو", "$totalSuccess", AiCyan)
                    KeyMetricBadge("گۆڕینی ئۆتۆماتیکی", "$totalRotations", OkxNeonLime)
                }

                if (cooldownCount > 0) {
                    OutlinedButton(
                        onClick = onResetCooldowns,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("چالاککردنەوەی دەستبەجێی کلیلە وەستاوەکان ($cooldownCount)")
                    }
                }
            }

            // Provider Selector Chips
            Text(
                text = "١. جۆری کلیل هەڵبژێرە (یان لێگەڕێ ئەپەکە خۆی بیناسێتەوە):",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                providerOptions.forEach { (code, titleKu) ->
                    val isSelected = selectedProviderCode == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AiCyan else ObsidianSurfaceVariant)
                            .border(
                                1.dp,
                                if (isSelected) AiCyan else ObsidianCardBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                selectedProviderCode = code
                                if (code != "AUTO") {
                                    val p = ApiProviderType.fromId(code)
                                    customModelInput = p.defaultModel
                                } else {
                                    customModelInput = ""
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = titleKu,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Multi-Key Input Field
            OutlinedTextField(
                value = rawKeysInput,
                onValueChange = { rawKeysInput = it },
                label = {
                    Text("٢. کلیلی API لێرە دابنێ (دەتوانیت چەند کلیلێک لەسەر هێڵی جیاواز دابنێیت)")
                },
                placeholder = {
                    Text(
                        text = "AIzaSy...\ngsk_...\nsk-or-v1-...",
                        fontFamily = MonoNumberFontFamily,
                        fontSize = 12.sp
                    )
                },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_api_keys_bulk"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OkxNeonLime,
                    unfocusedBorderColor = ObsidianCardBorder
                )
            )

            // Optional Label & Custom Model Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text("ناوی کلیل (ئارەزوومەندانە)") },
                    placeholder = { Text("نموونە: کلیلی سەرەکی") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AiCyan,
                        unfocusedBorderColor = ObsidianCardBorder
                    )
                )
                OutlinedTextField(
                    value = customModelInput,
                    onValueChange = { customModelInput = it },
                    label = { Text("مۆدێل (ئۆتۆماتیکی)") },
                    placeholder = {
                        val hintModel = if (selectedProviderCode == "AUTO") {
                            "gemini-2.5-flash"
                        } else {
                            ApiProviderType.fromId(selectedProviderCode).defaultModel
                        }
                        Text(hintModel, fontFamily = MonoNumberFontFamily, fontSize = 11.sp)
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AiCyan,
                        unfocusedBorderColor = ObsidianCardBorder
                    )
                )
            }

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
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "زیادکردنی کلیل و چالاککردنی سووڕانەوەی ئۆتۆماتیکی",
                    fontWeight = FontWeight.Bold
                )
            }

            // Saved Keys List
            if (apiKeys.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = OkxLimeDim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, OkxNeonLime.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = "هێشتا هیچ کلیلێکی تایبەت داخڵ نەکراوە. ئەپەکە لە کاتی نەبوونی کلیلدا بزوێنەری ناوخۆیی و ژمێریاری فرە-تایمفرەیمی OKX بەکاردەهێنێت. بۆ بەکارهێنانی ڕاستەوخۆی زیرەکی دەستکرد لەسەر مۆبایلەکەت، ١ یان چەند کلیلێکی بێبەرامبەری Google Gemini (لە aistudio.google.com) یان Groq یان OpenRouter لە سەرەوە داخڵ بکە.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextPrimary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Text(
                    text = "کلیلە پاشەکەوتکراوەکان لە سیستەمی سووڕانەوەدا (${apiKeys.size}):",
                    style = MaterialTheme.typography.labelLarge,
                    color = OkxNeonLime
                )

                apiKeys.forEachIndexed { idx, item ->
                    val inCooldown = item.cooldownUntilMs > now
                    val remainingCooldownSec = ((item.cooldownUntilMs - now) / 1000L).coerceAtLeast(0L)
                    val borderColor = when {
                        !item.isEnabled -> ObsidianCardBorder
                        inCooldown -> WarningAmber.copy(alpha = 0.7f)
                        else -> BullishGreen.copy(alpha = 0.6f)
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AiCyanDim)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "#${idx + 1} ${item.providerType}",
                                                fontFamily = MonoNumberFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = AiCyan
                                            )
                                        }
                                        Text(
                                            text = item.labelKu,
                                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                                            color = if (item.isEnabled) TextPrimary else TextMuted
                                        )
                                    }

                                    Text(
                                        text = "کلیل: ${maskApiKey(item.apiKey)} • مۆدێل: ${item.modelName}",
                                        fontFamily = MonoNumberFontFamily,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }

                                Switch(
                                    checked = item.isEnabled,
                                    onCheckedChange = { onToggleKeyEnabled(item) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = BullishGreen
                                    )
                                )
                            }

                            // Status & Counters Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val statusDisplay = if (inCooldown) {
                                    "لە پشووی کاتیدایە (${remainingCooldownSec}s ماوە) • چووە سەر کلیلی دواتر"
                                } else {
                                    item.lastStatusKu
                                }
                                Text(
                                    text = statusDisplay,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = when {
                                        !item.isEnabled -> TextMuted
                                        inCooldown -> WarningAmber
                                        else -> BullishGreen
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "سەرکەوتوو: ${item.successCount} جار  |  گۆڕانی ئۆتۆماتیکی: ${item.rotationFailoverCount} جار",
                                    fontFamily = MonoNumberFontFamily,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onTestKey(item) },
                                        enabled = testingKeyId != item.id,
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AiCyan)
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
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text(
                                            text = "پشکنین",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    IconButton(onClick = { onDeleteKey(item.id) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "سڕینەوەی کلیل",
                                            tint = BearishRed
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
}

@Composable
private fun KeyMetricBadge(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
            color = TextMuted
        )
        Text(
            text = value,
            fontFamily = MonoNumberFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = color
        )
    }
}

private fun maskApiKey(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.length <= 10) return "****"
    return "${trimmed.take(6)}...${trimmed.takeLast(4)}"
}
