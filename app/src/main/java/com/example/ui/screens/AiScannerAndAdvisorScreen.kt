package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AdvisorChatMessage
import com.example.ui.viewmodel.AiAdvisorUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AiScannerAndAdvisorScreen(
    advisorState: AiAdvisorUiState,
    chatHistory: List<AdvisorChatMessage>,
    onAskAdvisor: (String, List<Uri>) -> Unit
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

    // Unlimited Multi-Image Photo Picker
    val unlimitedPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            attachedImageUris = (attachedImageUris + uris).distinct()
        }
    }

    val attachedCount = attachedImageUris.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("general_crypto_ai_chat_screen")
    ) {
        // 1. Clean Full-Screen Chat Thread
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("crypto_chat_messages_list"),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = chatHistory,
                key = { it.id }
            ) { msg ->
                ChatMessageBubble(msg = msg)
            }

            // Streaming / Loading Indicator Bubble
            when (advisorState) {
                is AiAdvisorUiState.Loading -> {
                    item(key = "chat_loading_bubble") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ObsidianCardBorder, RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = OkxNeonLime,
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = advisorState.stageKu,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OkxNeonLime,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                is AiAdvisorUiState.Streaming -> {
                    item(key = "chat_streaming_bubble") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = ObsidianSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, OkxNeonLime.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = advisorState.partialText,
                                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                                color = TextPrimary,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                is AiAdvisorUiState.Error -> {
                    item(key = "chat_error_bubble") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BearishRedDim,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BearishRed, RoundedCornerShape(12.dp))
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

        // 2. Minimalist Bottom Chat Composer
        Surface(
            color = ObsidianSurface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ObsidianCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Shown ONLY when images are attached
                if (attachedImageUris.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(attachedImageUris) { idx, uri ->
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, BullishGreen, RoundedCornerShape(10.dp))
                                    .background(ObsidianSurfaceVariant)
                            ) {
                                UriThumbnailPreview(uri = uri, index = idx)

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(3.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.8f))
                                        .clickable {
                                            attachedImageUris = attachedImageUris.filterIndexed { i, _ -> i != idx }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "لابردن",
                                        tint = BearishRed,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Single Clean Input Row: [Attach Unlimited Photos] + [Text Field] + [Send]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Attach Unlimited Photos Button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (attachedCount > 0) BullishGreenDim else ObsidianSurfaceVariant,
                        modifier = Modifier
                            .size(48.dp)
                            .border(
                                1.dp,
                                if (attachedCount > 0) BullishGreen else ObsidianCardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                unlimitedPhotosLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .testTag("btn_attach_unlimited_images")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "دانانی وێنە",
                                tint = if (attachedCount > 0) BullishGreen else OkxNeonLime,
                                modifier = Modifier.size(22.dp)
                            )
                            if (attachedCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clip(CircleShape)
                                        .background(BullishGreen)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$attachedCount",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = questionInput,
                        onValueChange = { questionInput = it },
                        placeholder = {
                            Text(
                                text = if (attachedCount > 0) {
                                    "$attachedCount وێنە ئامادەیە — بنێرە..."
                                } else {
                                    "ناوی دراو بنووسە یان وێنە بنێرە..."
                                },
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_advisor_question"),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = OkxNeonLime,
                            unfocusedBorderColor = ObsidianCardBorder
                        )
                    )

                    val canSend = advisorState !is AiAdvisorUiState.Loading &&
                        (questionInput.isNotBlank() || attachedCount > 0)

                    IconButton(
                        onClick = {
                            val urisToSend = attachedImageUris
                            onAskAdvisor(questionInput, urisToSend)
                            questionInput = ""
                            attachedImageUris = emptyList()
                        },
                        enabled = canSend,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (canSend) OkxNeonLime else ObsidianSurfaceVariant)
                            .testTag("btn_send_advisor_question")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "ناردن",
                            tint = if (canSend) Color.Black else TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    msg: AdvisorChatMessage
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (msg.isUser) AiCyanDim else ObsidianSurface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (msg.isUser) AiCyan.copy(alpha = 0.4f) else ObsidianCardBorder,
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show user's attached images inside the bubble
            if (msg.previewBitmaps.isNotEmpty()) {
                PreviewBitmapsRow(msg.previewBitmaps)
            }

            // If AI message has detected coin & Buy/Sell targets, show a clean, simple Buy/Sell summary card
            if (!msg.isUser && msg.detectedCoin != null && (msg.buyAtPrice > 0.0 || msg.sellAtPrice > 0.0)) {
                SimpleBuySellCard(
                    coin = msg.detectedCoin,
                    livePrice = msg.livePriceAtAnalysis,
                    buyAtPrice = msg.buyAtPrice,
                    sellGoldenPrice = msg.sellAtPrice,
                    stopLossPrice = msg.stopLossPrice
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
private fun SimpleBuySellCard(
    coin: String,
    livePrice: Double,
    buyAtPrice: Double,
    sellGoldenPrice: Double,
    stopLossPrice: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianSurfaceVariant)
            .border(1.dp, BullishGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "📌 دراو: $coin",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = OkxNeonLime
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

        if (sellGoldenPrice > 0.0) {
            val profitPct = if (livePrice > 0.0) ((sellGoldenPrice - livePrice) / livePrice) * 100.0 else 0.0
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = BullishGreenDim,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🎯 بیفرۆشە لە: $${QuantMathEngine.formatCryptoPrice(sellGoldenPrice)} (${QuantMathEngine.formatSignedPercent(profitPct)} قازانج)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = BullishGreen,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        if (buyAtPrice > 0.0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = OkxLimeDim,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🟢 بیکڕە لە: $${QuantMathEngine.formatCryptoPrice(buyAtPrice)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = OkxNeonLime,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        if (stopLossPrice > 0.0) {
            Text(
                text = "🛡️ ستۆپ لۆس: $${QuantMathEngine.formatCryptoPrice(stopLossPrice)} • 🔔 چاودێری باگراوند چالاکە",
                fontSize = 11.sp,
                color = TextSecondary
            )
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
                    .width(if (bitmaps.size == 1) 220.dp else 135.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, ObsidianCardBorder, RoundedCornerShape(10.dp))
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "وێنەی ${idx + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
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
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
