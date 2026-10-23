package com.example.ui.screens

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import android.graphics.BitmapFactory
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.example.ui.viewmodel.AdvisorChatMessage
import com.example.ui.viewmodel.AiAdvisorUiState

@Composable
fun AiScannerAndAdvisorScreen(
    advisorState: AiAdvisorUiState,
    chatHistory: List<AdvisorChatMessage>,
    isBackgroundServiceRunning: Boolean,
    watchedCoinsCount: Int,
    onToggleBackgroundService: (Boolean) -> Unit,
    onAskAdvisor: (String, List<Uri>) -> Unit,
    onWatchCoinTargets: (String, Double, Double, Double, Double) -> Unit,
    onClearChatHistory: () -> Unit
) {
    var questionInput by remember { mutableStateOf("") }
    var attachedImageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new chat messages or streaming updates arrive
    LaunchedEffect(chatHistory.size, advisorState) {
        val totalItems = chatHistory.size + (if (advisorState !is AiAdvisorUiState.Idle && advisorState !is AiAdvisorUiState.Success) 1 else 0)
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
        }
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        onToggleBackgroundService(true)
    }

    // Unlimited Multi-Image Photo Picker (appends newly selected images without any 2-image cap)
    val unlimitedPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            attachedImageUris = (attachedImageUris + uris).distinct()
        }
    }

    val quickPrompts = remember {
        listOf(
            "کام دراو ئێستا باشترینە بۆ کڕین؟ پێم بڵێ لە چەند بیکڕم و کە گەیشتە چەند بیفرۆشم؟",
            "بیتکۆین (BTC) لە چ نرخێکدا بکڕم و کە گەیشتە چەند بیفرۆشم بۆ قازانج؟",
            "سۆلانا (SOL) ئێستا بیکڕم یان چاوەڕێ بکەم؟ نرخی کڕین و فرۆشتنم پێ بڵێ",
            "ئیتیریۆم (ETH) و پێپێ (PEPE) شیکاری بکە بۆ کڕین و فرۆشتنی ئەمڕۆ"
        )
    }

    val attachedCount = attachedImageUris.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("general_crypto_ai_chat_screen")
    ) {
        // 1. Compact Background Monitor & Notification Bar
        Surface(
            color = ObsidianSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
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
                        tint = if (isBackgroundServiceRunning) BullishGreen else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Column {
                        Text(
                            text = if (isBackgroundServiceRunning) {
                                "نۆتیفیکەیشن و چاودێری نرخی دراوەکان لە باگراوند چالاکە (${watchedCoinsCount.coerceAtLeast(1)} دراو)"
                            } else {
                                "چاودێری باگراوند و نۆتیفیکەیشنی نرخ ڕاگیراوە"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                            color = if (isBackgroundServiceRunning) BullishGreen else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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

                    if (chatHistory.size > 1) {
                        IconButton(
                            onClick = onClearChatHistory,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "پاککردنەوەی چات",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Full-Screen Chat Messages Thread
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("crypto_chat_messages_list"),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = chatHistory,
                key = { it.id }
            ) { msg ->
                ChatMessageBubble(
                    msg = msg,
                    onWatchCoinTargets = onWatchCoinTargets
                )
            }

            // Streaming / Loading Indicator Bubble at bottom of chat
            when (advisorState) {
                is AiAdvisorUiState.Loading -> {
                    item(key = "chat_loading_bubble") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AiCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = OkxNeonLime,
                                    strokeWidth = 2.5.dp
                                )
                                Column {
                                    Text(
                                        text = advisorState.stageKu,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = OkxNeonLime,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "دەتوانیت لە ئەپەکەش بچیتە دەرەوە؛ شیکردنەوەکە لە باگراوند تەواو دەبێت و نۆتیفیکەیشنت بۆ دێت",
                                        fontSize = 10.sp,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                is AiAdvisorUiState.Streaming -> {
                    item(key = "chat_streaming_bubble") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = ObsidianSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, OkxNeonLime, RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "🤖 ڕاوێژکاری AI (خەریکی نووسینە...)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OkxNeonLime,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = advisorState.partialText,
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }

                is AiAdvisorUiState.Error -> {
                    item(key = "chat_error_bubble") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BearishRedDim,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BearishRed, RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = advisorState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BearishRed,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                else -> {}
            }
        }

        // 3. Bottom Chat Input Composer (Unlimited Multi-Image Attachments + Quick Prompts)
        Surface(
            color = ObsidianSurface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick Suggestion Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickPrompts) { prompt ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianSurfaceVariant,
                            modifier = Modifier
                                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(10.dp))
                                .clickable {
                                    onAskAdvisor(prompt, attachedImageUris)
                                    attachedImageUris = emptyList()
                                }
                        ) {
                            Text(
                                text = prompt,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                                color = AiCyan,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Unlimited Image Attachment Gallery Strip & Action Bar
                if (attachedImageUris.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Collections,
                                    contentDescription = null,
                                    tint = BullishGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$attachedCount وێنە ئامادەیە بۆ شیکردنەوە (بێسنوور)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BullishGreen
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = OkxLimeDim,
                                    modifier = Modifier
                                        .border(1.dp, OkxNeonLime, RoundedCornerShape(8.dp))
                                        .clickable {
                                            unlimitedPhotosLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .testTag("btn_add_more_unlimited_images")
                                ) {
                                    Text(
                                        text = "+ زیادکردنی وێنەی تر",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OkxNeonLime,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BearishRedDim,
                                    modifier = Modifier
                                        .border(1.dp, BearishRed.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                        .clickable { attachedImageUris = emptyList() }
                                        .testTag("btn_clear_all_attached_images")
                                ) {
                                    Text(
                                        text = "سڕینەوەی هەمووی",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BearishRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Horizontal Scrollable Thumbnail Strip of All Attached Images
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(attachedImageUris) { idx, uri ->
                                Box(
                                    modifier = Modifier
                                        .size(width = 88.dp, height = 74.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, BullishGreen, RoundedCornerShape(10.dp))
                                        .background(ObsidianSurfaceVariant)
                                ) {
                                    UriThumbnailPreview(
                                        uri = uri,
                                        index = idx
                                    )

                                    // Index Badge (#1, #2, #3...)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color.Black.copy(alpha = 0.78f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "#${idx + 1}",
                                            fontFamily = MonoNumberFontFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OkxNeonLime
                                        )
                                    }

                                    // Remove Single Image Button
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.82f))
                                            .border(1.dp, BearishRed, CircleShape)
                                            .clickable {
                                                attachedImageUris = attachedImageUris.filterIndexed { i, _ -> i != idx }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "لابردنی وێنەی #${idx + 1}",
                                            tint = BearishRed,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Unlimited Photo Picker Button when no images are attached yet
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, OkxNeonLime.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                            .clickable {
                                unlimitedPhotosLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("btn_attach_unlimited_images")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = OkxNeonLime,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "+ دانانی وێنە (بێسنوور — چەند وێنەت دەوێت بەیەکەوە هەڵبژێرە)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OkxNeonLime
                                )
                            }
                            Text(
                                text = "♾️ بێسنوور",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AiCyan
                            )
                        }
                    }
                }

                // Text Input & Send Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        placeholder = {
                            Text(
                                text = if (attachedCount > 0) {
                                    "$attachedCount وێنە ئامادەیە — کرتە لە ناردن بکە تا هەموویان شیبکاتەوە..."
                                } else {
                                    "ناوی هەر دراوێک بنووسە یان بێسنوور وێنەی کڕینەکانت بنێرە..."
                                },
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_advisor_question"),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OkxNeonLime,
                            unfocusedBorderColor = ObsidianCardBorder
                        )
                    )

                    Button(
                        onClick = {
                            val urisToSend = attachedImageUris
                            onAskAdvisor(questionInput, urisToSend)
                            questionInput = ""
                            attachedImageUris = emptyList()
                        },
                        enabled = advisorState !is AiAdvisorUiState.Loading &&
                            (questionInput.isNotBlank() || attachedCount > 0),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("btn_send_advisor_question"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OkxNeonLime,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "ناردن"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    msg: AdvisorChatMessage,
    onWatchCoinTargets: (String, Double, Double, Double, Double) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (msg.isUser) AiCyanDim else ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (msg.isUser) AiCyan.copy(alpha = 0.55f) else OkxNeonLime.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (msg.isUser) AiCyan else OkxNeonLime,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (msg.isUser) {
                            "تۆ"
                        } else {
                            "ڕاوێژکاری دراوی abdulbasit traiding" + (msg.detectedCoin?.let { " • $it" } ?: "")
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (msg.isUser) AiCyan else OkxNeonLime,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (msg.attachedImageCount > 0) {
                    Text(
                        text = "🖼️ ${msg.attachedImageCount} وێنە",
                        fontSize = 11.sp,
                        color = OkxNeonLime,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Show user's attached images (unlimited horizontal strip) inside the bubble
            if (msg.previewBitmaps.isNotEmpty()) {
                PreviewBitmapsRow(msg.previewBitmaps)
            }

            // If AI message has exact Buy ("لەوەندە بیکڕە") and Sell ("کە گەیشتە ئەوەندە بیفرۆشە") prices, show the highlighted Action Card!
            if (!msg.isUser && msg.detectedCoin != null && (msg.buyAtPrice > 0.0 || msg.sellAtPrice > 0.0)) {
                BuyAndSellVerdictBox(
                    coin = msg.detectedCoin,
                    livePrice = msg.livePriceAtAnalysis,
                    buyAtPrice = msg.buyAtPrice,
                    sellTp1Price = msg.sellTp1Price,
                    sellGoldenPrice = msg.sellAtPrice,
                    sellTp3Price = msg.sellTp3Price,
                    stopLossPrice = msg.stopLossPrice,
                    onActivateBackgroundAlert = {
                        onWatchCoinTargets(
                            msg.detectedCoin,
                            msg.buyAtPrice,
                            msg.sellAtPrice,
                            msg.stopLossPrice,
                            msg.livePriceAtAnalysis
                        )
                    }
                )
            }

            // Message Text
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun BuyAndSellVerdictBox(
    coin: String,
    livePrice: Double,
    buyAtPrice: Double,
    sellTp1Price: Double,
    sellGoldenPrice: Double,
    sellTp3Price: Double,
    stopLossPrice: Double,
    onActivateBackgroundAlert: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianSurfaceVariant)
            .border(1.5.dp, BullishGreen.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📌 خولاصەی بڕیاری کڕین و فرۆشتنی $coin",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                color = OkxNeonLime,
                fontWeight = FontWeight.Bold
            )
            if (livePrice > 0.0) {
                Text(
                    text = "نرخی ئێستا: $${QuantMathEngine.formatCryptoPrice(livePrice)}",
                    fontFamily = MonoNumberFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = AiCyan
                )
            }
        }

        // 1. SELL VERDICT ("کە گەیشتە ئەوەندە بیفرۆشە قازانجتە")
        if (sellGoldenPrice > 0.0) {
            val profitPct = if (livePrice > 0.0) ((sellGoldenPrice - livePrice) / livePrice) * 100.0 else 0.0
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = BullishGreenDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BullishGreen, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🎯 ئەگەر کڕیوتە: کە گەیشتە $${QuantMathEngine.formatCryptoPrice(sellGoldenPrice)} بیفرۆشە قازانجتە! (${QuantMathEngine.formatSignedPercent(profitPct)})",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = BullishGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (sellTp1Price > 0.0) {
                            Text(
                                text = "ئامانجی ١ (TP1): $${QuantMathEngine.formatCryptoPrice(sellTp1Price)}",
                                fontFamily = MonoNumberFontFamily,
                                fontSize = 11.sp,
                                color = TextPrimary
                            )
                        }
                        if (sellTp3Price > 0.0) {
                            Text(
                                text = "ئامانجی باڵا (TP3): $${QuantMathEngine.formatCryptoPrice(sellTp3Price)}",
                                fontFamily = MonoNumberFontFamily,
                                fontSize = 11.sp,
                                color = OkxNeonLime
                            )
                        }
                    }
                }
            }
        }

        // 2. BUY VERDICT ("لەوەندە بیکڕە")
        if (buyAtPrice > 0.0) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = OkxLimeDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, OkxNeonLime.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🟢 ئەگەر دەتەوێت بیکڕیت: لە نرخی $${QuantMathEngine.formatCryptoPrice(buyAtPrice)} بیکڕە",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = OkxNeonLime,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Stop Loss + Background Auto-Monitor Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stopLossPrice > 0.0) {
                Text(
                    text = "🛡️ ستۆپ لۆس: $${QuantMathEngine.formatCryptoPrice(stopLossPrice)}",
                    fontFamily = MonoNumberFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = BearishRed
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AiCyanDim,
                modifier = Modifier
                    .border(1.dp, AiCyan, RoundedCornerShape(8.dp))
                    .clickable(onClick = onActivateBackgroundAlert)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(BullishGreen)
                    )
                    Text(
                        text = "🔔 لە باگراوند چاودێری دەکرێت بۆ نۆتیفیکەیشن",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AiCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewBitmapsRow(bitmaps: List<Bitmap>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(bitmaps) { idx, bmp ->
            Box(
                modifier = Modifier
                    .width(if (bitmaps.size == 1) 240.dp else 150.dp)
                    .height(125.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(10.dp))
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "وێنەی ${idx + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "وێنەی #${idx + 1}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OkxNeonLime
                    )
                }
            }
        }
    }
}

@Composable
private fun UriThumbnailPreview(uri: Uri, index: Int) {
    val context = LocalContext.current
    val thumbBitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
    }

    val bmp = thumbBitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "وێنەی #${index + 1}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = null,
                tint = OkxNeonLime,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

