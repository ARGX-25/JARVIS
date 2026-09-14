package com.example.jarvis.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.jarvis.R
import com.example.jarvis.ui.theme.JARVISTheme
import com.example.jarvis.ui.theme.JarvisColors
import com.example.jarvis.ui.theme.JarvisIcons
import com.example.jarvis.ui.voice.VoiceWaveform
import com.example.jarvis.ui.voice.rememberMicLevel
import com.example.jarvis.viewmodel.ChatMessage
import com.example.jarvis.viewmodel.ChatRole
import com.example.jarvis.viewmodel.JarvisUiState
import com.example.jarvis.viewmodel.JarvisViewModel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JarvisScreen(viewModel: JarvisViewModel? = null) {
    val uiState = viewModel?.uiState?.collectAsState()?.value ?: JarvisUiState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val inputFocus = remember { FocusRequester() }
    val imeVisible = WindowInsets.isImeVisible
    var reopenKeyboard by remember { mutableStateOf(false) }

    // The system permission dialog takes window focus and closes the keyboard; bring it back afterwards.
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel?.setVoiceActive(granted)
        if (!granted) {
            Toast.makeText(context, R.string.voice_permission_denied, Toast.LENGTH_SHORT).show()
        }
        if (reopenKeyboard) {
            inputFocus.requestFocus()
            keyboard?.show()
            reopenKeyboard = false
        }
    }

    JarvisContent(
        uiState = uiState,
        inputFocus = inputFocus,
        onInputChanged = { viewModel?.onInputChanged(it) },
        onSend = { viewModel?.send() },
        onAttach = { Toast.makeText(context, R.string.attach_unavailable, Toast.LENGTH_SHORT).show() },
        onToggleVoice = {
            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            when {
                uiState.isVoiceActive -> viewModel?.setVoiceActive(false)
                hasMic -> viewModel?.setVoiceActive(true)
                else -> {
                    reopenKeyboard = imeVisible
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    )
}

@Composable
private fun JarvisContent(
    uiState: JarvisUiState,
    inputFocus: FocusRequester,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onToggleVoice: () -> Unit,
    previewMicLevel: Float? = null
) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) listState.animateScrollToItem(uiState.messages.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(JarvisColors.NightDeep, JarvisColors.NightNavy, JarvisColors.NightDeep)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            JarvisHeader(uiState)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(uiState.messages) { message -> MessageRow(message) }
            }
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            // The card is added above the composer instead of replacing it, so typing and the keyboard stay available.
            AnimatedVisibility(
                visible = uiState.isVoiceActive,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                VoiceAssistantCard(
                    onClose = onToggleVoice,
                    previewMicLevel = previewMicLevel,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            Composer(
                input = uiState.input,
                isSending = uiState.isSending,
                isVoiceActive = uiState.isVoiceActive,
                inputFocus = inputFocus,
                onInputChanged = onInputChanged,
                onSend = onSend,
                onAttach = onAttach,
                onToggleVoice = onToggleVoice
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun JarvisHeader(uiState: JarvisUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(JarvisColors.Royal, JarvisColors.Cyan))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "J",
                color = JarvisColors.NightDeep,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                color = JarvisColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.jarvis_subtitle),
                color = JarvisColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        MemoryStatusConsole(
            isMemoryLoaded = uiState.isMemoryLoaded,
            tokenEstimate = uiState.memoryTokenEstimate
        )
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    if (message.role == ChatRole.USER) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = message.text,
                color = JarvisColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp))
                    .background(JarvisColors.UserBubble)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    } else {
        // Replies are just the text: no speaker label or routing meta (the header already names Cuddy/Cameron).
        Text(
            text = message.text,
            color = JarvisColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 24.dp)
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun VoiceAssistantCard(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    previewMicLevel: Float? = null
) {
    val liveLevel by rememberMicLevel(active = previewMicLevel == null)
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(JarvisColors.SurfaceRaised, JarvisColors.Midnight)))
            .border(1.dp, JarvisColors.Outline, shape)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.voice_live_label),
                color = JarvisColors.TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose, modifier = Modifier.focusProperties { canFocus = false }) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.voice_stop),
                    tint = JarvisColors.TextSecondary
                )
            }
        }
        Text(
            text = stringResource(R.string.voice_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                brush = Brush.horizontalGradient(listOf(JarvisColors.Periwinkle, JarvisColors.Cyan, JarvisColors.Azure))
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.voice_subtitle),
            color = JarvisColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        VoiceWaveform(
            level = previewMicLevel ?: liveLevel,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .padding(end = 12.dp)
        )
    }
}

@Composable
private fun LiveDot() {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 800), RepeatMode.Reverse),
        label = "livePulse"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(JarvisColors.Cyan.copy(alpha = pulse))
    )
}

@Composable
private fun Composer(
    input: String,
    isSending: Boolean,
    isVoiceActive: Boolean,
    inputFocus: FocusRequester,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onToggleVoice: () -> Unit
) {
    val pill = RoundedCornerShape(26.dp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RoundButton(
            onClick = onAttach,
            description = stringResource(R.string.attach),
            background = Brush.linearGradient(listOf(JarvisColors.Midnight, JarvisColors.Midnight)),
            modifier = Modifier
                .padding(bottom = 4.dp)
                .border(1.dp, JarvisColors.Outline, CircleShape)
        ) {
            Icon(
                imageVector = JarvisIcons.AttachFile,
                contentDescription = null,
                tint = JarvisColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 52.dp)
                .clip(pill)
                .background(JarvisColors.Midnight)
                .border(1.dp, if (isVoiceActive) JarvisColors.Azure.copy(alpha = 0.7f) else JarvisColors.Outline, pill)
                .padding(start = 18.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = onInputChanged,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = JarvisColors.TextPrimary),
                cursorBrush = SolidColor(JarvisColors.Cyan),
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .focusRequester(inputFocus),
                decorationBox = { innerTextField ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                text = stringResource(R.string.jarvis_placeholder),
                                color = JarvisColors.TextSecondary,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (input.isNotBlank() || isSending) {
                RoundButton(
                    onClick = { if (!isSending) onSend() },
                    description = stringResource(R.string.jarvis_send),
                    background = Brush.linearGradient(listOf(JarvisColors.Azure, JarvisColors.Azure)),
                    size = 40
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            color = JarvisColors.NightDeep,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = JarvisColors.NightDeep,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
            RoundButton(
                onClick = onToggleVoice,
                description = stringResource(if (isVoiceActive) R.string.voice_stop else R.string.voice_start),
                background = if (isVoiceActive) {
                    Brush.linearGradient(listOf(JarvisColors.Cyan, JarvisColors.Azure))
                } else {
                    Brush.linearGradient(listOf(JarvisColors.Royal, JarvisColors.Cyan))
                },
                size = 42
            ) {
                VoiceBarsIcon(animated = isVoiceActive, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// Never focusable: tapping it must not pull focus from the text field, which would close the keyboard.
@Composable
private fun RoundButton(
    onClick: () -> Unit,
    description: String,
    background: Brush,
    modifier: Modifier = Modifier,
    size: Int = 44,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .focusProperties { canFocus = false }
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun VoiceBarsIcon(animated: Boolean, modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "bars").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "barsPhase"
    )
    Canvas(modifier = modifier) {
        val slot = size.width / 9f
        val centreY = size.height / 2f
        BarHeights.forEachIndexed { index, height ->
            val wobble = if (animated) 0.55f + 0.45f * abs(sin(phase + index * 0.9f)) else 1f
            val half = (size.height - slot) * height * wobble / 2f
            val x = slot * 2 * index + slot / 2f
            drawLine(
                color = JarvisColors.NightDeep,
                start = Offset(x, centreY - half),
                end = Offset(x, centreY + half),
                strokeWidth = slot,
                cap = StrokeCap.Round
            )
        }
    }
}

private val BarHeights = floatArrayOf(0.35f, 0.7f, 1f, 0.7f, 0.35f)

private fun previewState(voiceActive: Boolean) = JarvisUiState(
    input = if (voiceActive) "Also remind me about" else "",
    messages = listOf(
        ChatMessage(role = ChatRole.USER, text = "What does my morning look like?"),
        ChatMessage(
            role = ChatRole.ASSISTANT,
            text = "Your first lecture begins at ten, Sir. Traffic on the usual route is heavy, so leaving by nine fifteen would be wise.",
            meta = "CAMERON | success"
        )
    ),
    isVoiceActive = voiceActive,
    isMemoryLoaded = true,
    memoryTokenEstimate = 312,
    memorySummaryCount = 4
)

@Preview(showBackground = true, backgroundColor = 0xFF050B1E, widthDp = 390, heightDp = 780)
@Composable
private fun JarvisChatPreview() {
    JARVISTheme {
        JarvisContent(previewState(voiceActive = false), remember { FocusRequester() }, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF050B1E, widthDp = 390, heightDp = 780)
@Composable
private fun JarvisVoicePreview() {
    JARVISTheme {
        JarvisContent(previewState(voiceActive = true), remember { FocusRequester() }, {}, {}, {}, {}, previewMicLevel = 0.55f)
    }
}
