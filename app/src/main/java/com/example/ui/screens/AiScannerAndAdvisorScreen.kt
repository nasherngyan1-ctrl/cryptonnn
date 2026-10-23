package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Radar
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiMarketOpportunityItem
import com.example.data.model.AiScreenshotExitPlan
import com.example.domain.QuantMathEngine
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiCyanDim
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BearishRedDim
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.BullishGreenDim
import com.example.ui.theme.MonoNumberFontFamily
import com.example.ui.theme.ObsidianCardBorder
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceVariant
import com.example.ui.theme.OkxLimeDim
import com.example.ui.theme.OkxNeonLime
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberDim
import com.example.ui.viewmodel.AdvisorChatMessage
import com.example.ui.viewmodel.AiAdvisorUiState
import com.example.ui.viewmodel.AiScannerUiState
import com.example.ui.viewmodel.ScreenshotExitUiState
import java.util.Locale

@Composable
fun AiScannerAndAdvisorScreen(
    activeInstId: String,
    screenshotExitState: ScreenshotExitUiState,
    scannerState: AiScannerUiState,
    advisorState: AiAdvisorUiState,
    chatHistory: List<AdvisorChatMessage>,
    onAnalyzeScreenshotExit: (Uri, String) -> Unit,
    onSaveExitPlanAsSignal: (AiScreenshotExitPlan) -> Unit,
    onSetPriceAlert: (String, Double, String) -> Unit,
    onRunMarketScanner: () -> Unit,
    onAskAdvisor: (String, Uri?) -> Unit,
    onInspectCoin: (String) -> Unit
) {
    var screenshotNoteInput by remember { mutableStateOf("") }
    var selectedScreenshotUri by remember { mutableStateOf<Uri?>(null) }

    var questionInput by remember { mutableStateOf("") }
    var chatAttachedUri by remember { mutableStateOf<Uri?>(null) }

    // Zero-permission Android Photo Picker for the Screenshot Exit Analyzer
    val screenshotExitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedScreenshotUri = uri
            onAnalyzeScreenshotExit(uri, screenshotNoteInput)
        }
    }

    // Zero-permission Android Photo Picker for attaching an image to the AI Chat Advisor
    val chatPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            chatAttachedUri = uri
        }
    }

    val quickPrompts = remember(activeInstId) {
        listOf(
            "من $activeInstId ـم کڕیوە، لە چ نرخێکدا بیفرۆشم بۆ زۆرترین قازانج؟",
            "باشترین خاڵی کڕین و فرۆشتن بۆ $activeInstId لەم چرکەیەدا چەندە؟",
            "پلانی مامەڵەی فیوچەرز (Long یان Short) بۆ $activeInstId بە لیڤرێجی 5x دابنێ",
            "کام دراوی ناو OKX ئێستا کەمترین مەترسی و بەرزترین ئەگەری قازانجی هەیە؟"
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_scanner_advisor_list"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Featured Card: AI Screenshot Profit & Exit Optimizer ("وێنەی کڕین بنێرە -> لە چەند بیفرۆشم قازانجتە")
        item {
            ScreenshotProfitExitCard(
                screenshotNoteInput = screenshotNoteInput,
                selectedScreenshotUri = selectedScreenshotUri,
                screenshotExitState = screenshotExitState,
                onNoteChange = { screenshotNoteInput = it },
                onPickImage = {
                    screenshotExitLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onReAnalyze = {
                    selectedScreenshotUri?.let { uri ->
                        onAnalyzeScreenshotExit(uri, screenshotNoteInput)
                    }
                },
                onSavePlanAsSignal = onSaveExitPlanAsSignal,
                onSetPriceAlert = onSetPriceAlert,
                onInspectCoin = onInspectCoin
            )
        }

        // 2. AI Market-Wide Opportunity Scanner Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OkxLimeDim),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radar,
                                    contentDescription = null,
                                    tint = OkxNeonLime
                                )
                            }
                            Column {
                                Text(
                                    text = "سکێنەری زیرەکی دەستکرد بۆ تەواوی بازاڕی OKX",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OkxNeonLime
                                )
                                Text(
                                    text = "دۆزینەوەی ٤ بەهێزترین دەرفەتی مامەڵە لەلایەن Gemini 3.8 Flash",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onRunMarketScanner,
                        enabled = scannerState !is AiScannerUiState.Scanning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_run_ai_scanner"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ObsidianSurfaceVariant,
                            contentColor = OkxNeonLime
                        )
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = if (scannerState is AiScannerUiState.Scanning) {
                                "خەریکی پشکنینی زیندووی بازاڕی OKX ـە..."
                            } else {
                                "پشکنینی گشتی بازاڕ و دەرکردنی باشترین سیگناڵەکانی ئێستا"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }

                    when (scannerState) {
                        is AiScannerUiState.Idle -> {
                            Text(
                                text = "کرتە بکە تاوەکو زیرەکی دەستکرد ١٨ پڕمامەڵەترین دراوی OKX بپشکنێت و باشترین خاڵەکانی کڕین و فرۆشتنت پێ بدات.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }

                        is AiScannerUiState.Scanning -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = OkxNeonLime,
                                    modifier = Modifier.size(26.dp)
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Text(
                                    text = "شیکردنەوەی قەبارە، ناجێگیری و گوڕی نرخ لە OKX...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }

                        is AiScannerUiState.Error -> {
                            Text(
                                text = scannerState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BearishRed
                            )
                        }

                        is AiScannerUiState.Success -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianSurfaceVariant)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "دۆخی گشتی بازاڕی OKX:",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AiCyan
                                )
                                Text(
                                    text = scannerState.marketRegimeKu,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }

                            scannerState.opportunities.forEach { opp ->
                                AiOpportunityRowCard(
                                    opportunity = opp,
                                    onClick = { onInspectCoin(opp.instId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Live Streaming AI Quantitative Advisor Section (with Image Attachment support)
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AiCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ڕاوێژکاری ڕاستەوخۆی زیرەکی دەستکرد (دەق + وێنە + داتای OKX)",
                        style = MaterialTheme.typography.titleMedium,
                        color = AiCyan
                    )
                    Text(
                        text = "دەتوانیت پرسیار بنووسیت یان وێنە هاوپێچ بکەیت؛ AI ڕاستەوخۆ ژمارە زیندووەکانی $activeInstId و بازاڕی OKX دەخوێنێتەوە.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(quickPrompts) { prompt ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AiCyanDim)
                                    .border(1.dp, AiCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable { onAskAdvisor(prompt, null) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = prompt,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = AiCyan
                                )
                            }
                        }
                    }

                    if (chatAttachedUri != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OkxLimeDim)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "وێنەیەک ئامادەیە بۆ ناردن لەگەڵ پرسیارەکەت",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OkxNeonLime
                            )
                            Text(
                                text = "لابردن",
                                style = MaterialTheme.typography.labelLarge,
                                color = BearishRed,
                                modifier = Modifier.clickable { chatAttachedUri = null }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                chatPhotoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianSurfaceVariant)
                                .testTag("btn_attach_chat_photo")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "هاوپێچکردنی وێنە",
                                tint = OkxNeonLime
                            )
                        }

                        OutlinedTextField(
                            value = questionInput,
                            onValueChange = { questionInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_advisor_input"),
                            placeholder = {
                                Text(
                                    text = "پرسیار بنووسە یان وێنە بنێرە...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextMuted
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AiCyan,
                                unfocusedBorderColor = ObsidianCardBorder,
                                focusedContainerColor = ObsidianSurfaceVariant,
                                unfocusedContainerColor = ObsidianSurfaceVariant
                            )
                        )

                        IconButton(
                            onClick = {
                                if (questionInput.isNotBlank() || chatAttachedUri != null) {
                                    onAskAdvisor(questionInput, chatAttachedUri)
                                    questionInput = ""
                                    chatAttachedUri = null
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AiCyan)
                                .testTag("btn_send_ai_advisor")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "ناردنی پرسیار بۆ AI",
                                tint = Color.Black
                            )
                        }
                    }

                    when (advisorState) {
                        is AiAdvisorUiState.Loading -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ObsidianSurfaceVariant)
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(color = AiCyan, modifier = Modifier.size(22.dp))
                                Text(
                                    text = "Gemini AI خەریکی ئامادەکردنی وەڵامی ژمارەییە...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }

                        is AiAdvisorUiState.Streaming -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AiCyanDim)
                                    .border(1.dp, AiCyan, RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "وەڵامی ڕاستەوخۆی زیرەکی دەستکرد (پەخشی زیندوو):",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = AiCyan
                                )
                                Text(
                                    text = advisorState.partialText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = TextPrimary
                                )
                            }
                        }

                        is AiAdvisorUiState.Error -> {
                            Text(
                                text = advisorState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BearishRed
                            )
                        }

                        else -> Unit
                    }
                }
            }
        }

        // 4. Chat History Messages
        items(
            items = chatHistory.reversed(),
            key = { it.id }
        ) { msg ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (msg.isUser) ObsidianSurfaceVariant else ObsidianSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (msg.isUser) ObsidianCardBorder else AiCyan.copy(alpha = 0.45f),
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (msg.isUser) {
                                if (msg.hasAttachedImage) "پرسیاری بازرگان + وێنەی هاوپێچ" else "پرسیاری بازرگان"
                            } else {
                                "ڕاپۆرتی زیرەکی دەستکرد (Gemini 3.8 Flash)"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (msg.isUser) OkxNeonLime else AiCyan
                        )
                        if (msg.relatedInstId != null) {
                            Text(
                                text = msg.relatedInstId,
                                fontFamily = MonoNumberFontFamily,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenshotProfitExitCard(
    screenshotNoteInput: String,
    selectedScreenshotUri: Uri?,
    screenshotExitState: ScreenshotExitUiState,
    onNoteChange: (String) -> Unit,
    onPickImage: () -> Unit,
    onReAnalyze: () -> Unit,
    onSavePlanAsSignal: (AiScreenshotExitPlan) -> Unit,
    onSetPriceAlert: (String, Double, String) -> Unit,
    onInspectCoin: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, OkxNeonLime, RoundedCornerShape(16.dp))
            .testTag("screenshot_profit_exit_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OkxLimeDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = OkxNeonLime,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "وێنەی کڕینەکەت بنێرە — پێت دەڵێم لە چەند بیفرۆشە قازانجتە!",
                        style = MaterialTheme.typography.titleMedium,
                        color = OkxNeonLime
                    )
                    Text(
                        text = "زیرەکی دەستکرد وێنەی مامەڵەکەت دەخوێنێتەوە و بەپێی داتای زیندووی ئێستای OKX باشترین نرخی فرۆشتنت بۆ دیاری دەکات",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = TextSecondary
                    )
                }
            }

            OutlinedTextField(
                value = screenshotNoteInput,
                onValueChange = onNoteChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("screenshot_note_input"),
                placeholder = {
                    Text(
                        text = "تێبینی ئارەزوومەندانە (نموونە: بە ١٠٠ دۆلار کڕیومە، کەی بیفرۆشم؟)...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OkxNeonLime,
                    unfocusedBorderColor = ObsidianCardBorder,
                    focusedContainerColor = ObsidianSurfaceVariant,
                    unfocusedContainerColor = ObsidianSurfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPickImage,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_pick_trade_screenshot"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OkxNeonLime,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "هەڵبژاردنی وێنەی کڕین / مامەڵە",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (selectedScreenshotUri != null) {
                    OutlinedButton(
                        onClick = onReAnalyze,
                        modifier = Modifier.testTag("btn_reanalyze_screenshot")
                    ) {
                        Text("دووبارە شیکاری", color = AiCyan)
                    }
                }
            }

            when (screenshotExitState) {
                is ScreenshotExitUiState.Idle -> Unit

                is ScreenshotExitUiState.Analyzing -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceVariant)
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            bitmap = screenshotExitState.previewBitmap.asImageBitmap(),
                            contentDescription = "وێنەی هەڵبژێردراو",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        CircularProgressIndicator(color = OkxNeonLime, modifier = Modifier.size(30.dp))
                        Text(
                            text = screenshotExitState.stageKu,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OkxNeonLime,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is ScreenshotExitUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BearishRedDim)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = screenshotExitState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = BearishRed
                        )
                    }
                }

                is ScreenshotExitUiState.Success -> {
                    val plan = screenshotExitState.plan

                    // Image Thumbnail + Detected Coin & Buy vs Live OKX Price
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceVariant)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            bitmap = screenshotExitState.previewBitmap.asImageBitmap(),
                            contentDescription = "وێنەی کڕین",
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${plan.detectedInstId} • ${plan.positionTypeKu}",
                                    fontFamily = MonoNumberFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = AiCyan,
                                    modifier = Modifier.clickable { onInspectCoin(plan.detectedInstId) }
                                )
                                Text(
                                    text = "دڵنیایی: ${plan.confidencePercent}%",
                                    fontFamily = MonoNumberFontFamily,
                                    fontSize = 11.sp,
                                    color = OkxNeonLime
                                )
                            }
                            Text(
                                text = "نرخی کڕینت: $${QuantMathEngine.formatCryptoPrice(plan.detectedBuyPrice)} | نرخی ئێستای OKX: $${QuantMathEngine.formatCryptoPrice(plan.currentLiveOkxPrice)}",
                                fontFamily = MonoNumberFontFamily,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = plan.currentStatusSummaryKu,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = if (plan.currentPnlPercent >= 0) BullishGreen else WarningAmber
                            )
                        }
                    }

                    // GOLDEN EXIT PRICE BANNER ("لەم نرخە بیفرۆشە قازانجتە!")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BullishGreenDim)
                            .border(1.5.dp, BullishGreen, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "بڕیاری زیرەکی دەستکرد — لەم نرخە بیفرۆشە قازانجتە:",
                            style = MaterialTheme.typography.labelLarge,
                            color = OkxNeonLime
                        )
                        Text(
                            text = plan.urgentActionVerdictKu,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "باشترین نرخی فرۆشتن (Golden Exit Price)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = TextSecondary
                                )
                                Text(
                                    text = "$${QuantMathEngine.formatCryptoPrice(plan.goldenExitPrice)}",
                                    fontFamily = MonoNumberFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = BullishGreen
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "قازانجی چاوەڕوانکراو",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = TextSecondary
                                )
                                Text(
                                    text = "${QuantMathEngine.formatSignedPercent(plan.goldenExitProfitPercent)} (+$${String.format(Locale.US, "%.2f", plan.goldenExitEstimatedProfitUsdt)})",
                                    fontFamily = MonoNumberFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = OkxNeonLime
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onSetPriceAlert(
                                    plan.detectedInstId,
                                    plan.goldenExitPrice,
                                    "ئاگاداری فرۆشتنی زێڕین (Golden Exit) بۆ قازانج"
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BullishGreen,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ئاگادارم بکەرەوە هەر کاتێک گەیشتە $${QuantMathEngine.formatCryptoPrice(plan.goldenExitPrice)}",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 3-Stage Profit-Taking Ladder Table (TP1, TP2, TP3 + Emergency SL)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceVariant)
                            .border(1.dp, ObsidianCardBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "خشتەی فرۆشتنی قۆناغبەندی بۆ زۆرترین قازانج:",
                            style = MaterialTheme.typography.titleMedium,
                            color = AiCyan
                        )

                        StagedSellTargetRow(
                            stageLabel = "ئامانجی فرۆشتنی ١ (مسۆگەرکردن)",
                            allocationLabel = "${plan.sellTarget1AllocationPercent}% بفرۆشە",
                            price = plan.sellTarget1Price,
                            profitPct = plan.sellTarget1ProfitPercent,
                            color = BullishGreen,
                            onAlert = {
                                onSetPriceAlert(plan.detectedInstId, plan.sellTarget1Price, "فرۆشتنی بەشی یەکەم (TP1)")
                            }
                        )
                        HorizontalDivider(color = ObsidianCardBorder)

                        StagedSellTargetRow(
                            stageLabel = "ئامانجی فرۆشتنی ٢ (سەرەکی)",
                            allocationLabel = "${plan.sellTarget2AllocationPercent}% بفرۆشە",
                            price = plan.sellTarget2Price,
                            profitPct = plan.sellTarget2ProfitPercent,
                            color = OkxNeonLime,
                            onAlert = {
                                onSetPriceAlert(plan.detectedInstId, plan.sellTarget2Price, "فرۆشتنی بەشی دووەم (TP2)")
                            }
                        )
                        HorizontalDivider(color = ObsidianCardBorder)

                        StagedSellTargetRow(
                            stageLabel = "ئامانجی فرۆشتنی ٣ (لووتکە)",
                            allocationLabel = "${plan.sellTarget3AllocationPercent}% بفرۆشە",
                            price = plan.sellTarget3Price,
                            profitPct = plan.sellTarget3ProfitPercent,
                            color = AiCyan,
                            onAlert = {
                                onSetPriceAlert(plan.detectedInstId, plan.sellTarget3Price, "فرۆشتنی بەشی سێیەم (TP3)")
                            }
                        )
                        HorizontalDivider(color = ObsidianCardBorder)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "نرخی فرۆشتنی پاراستن (Stop-Loss)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BearishRed,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ماوەی چاوەڕوانکراو: ${plan.holdDurationEstimateKu}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                    color = TextSecondary
                                )
                            }
                            Text(
                                text = "$${QuantMathEngine.formatCryptoPrice(plan.emergencyStopLossPrice)}",
                                fontFamily = MonoNumberFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = BearishRed
                            )
                        }
                    }

                    // Whale Resistance Note + Step-by-Step Sell Strategy
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ObsidianSurfaceVariant)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (plan.whaleResistanceWallNoteKu.isNotBlank()) {
                            Text(
                                text = "دیوارەکانی فرۆشتنی نەهەنگەکان لە OKX:",
                                style = MaterialTheme.typography.labelLarge,
                                color = WarningAmber
                            )
                            Text(
                                text = plan.whaleResistanceWallNoteKu,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        if (plan.detailedSellStrategyKu.isNotBlank()) {
                            Text(
                                text = "پلانی وردی جێبەجێکردن لەناو ئەپی OKX:",
                                style = MaterialTheme.typography.labelLarge,
                                color = OkxNeonLime
                            )
                            Text(
                                text = plan.detailedSellStrategyKu,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                    }

                    Button(
                        onClick = { onSavePlanAsSignal(plan) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AiCyan,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "پاشەکەوتکردنی ئەم پلانەی فرۆشتن بۆ چاودێریکردنی زیندوو",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StagedSellTargetRow(
    stageLabel: String,
    allocationLabel: String,
    price: Double,
    profitPct: Double,
    color: Color,
    onAlert: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stageLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "$allocationLabel • قازانج: ${QuantMathEngine.formatSignedPercent(profitPct)}",
                fontFamily = MonoNumberFontFamily,
                fontSize = 11.sp,
                color = color
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$${QuantMathEngine.formatCryptoPrice(price)}",
                fontFamily = MonoNumberFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = color
            )
            IconButton(
                onClick = onAlert,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ObsidianSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "ئاگادارکەرەوە",
                    tint = WarningAmber,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun AiOpportunityRowCard(
    opportunity: AiMarketOpportunityItem,
    onClick: () -> Unit
) {
    val isLong = opportunity.actionDirection.uppercase(Locale.US) == "LONG"
    val accent = if (isLong) BullishGreen else BearishRed
    val bg = if (isLong) BullishGreenDim else BearishRedDim

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceVariant)
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = opportunity.instId,
                    fontFamily = MonoNumberFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = opportunity.actionLabelKu,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp),
                        color = accent
                    )
                }
            }

            Text(
                text = "دڵنیایی AI: ${opportunity.confidencePercent}%",
                fontFamily = MonoNumberFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = OkxNeonLime
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OpportunityNumberBox("چوونەژوورەوە", QuantMathEngine.formatCryptoPrice(opportunity.entryPrice), AiCyan)
            OpportunityNumberBox("ئامانج (Target)", QuantMathEngine.formatCryptoPrice(opportunity.targetPrice), BullishGreen)
            OpportunityNumberBox("ستۆپ لۆس (SL)", QuantMathEngine.formatCryptoPrice(opportunity.stopLossPrice), BearishRed)
            OpportunityNumberBox(
                "جووڵەی چاوەڕوانکراو",
                QuantMathEngine.formatSignedPercent(opportunity.expectedMovePercent),
                accent
            )
        }

        HorizontalDivider(color = ObsidianCardBorder)

        Text(
            text = opportunity.keyReasonKu,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
    }
}

@Composable
private fun OpportunityNumberBox(
    label: String,
    value: String,
    color: Color
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 10.sp),
            color = TextSecondary
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
