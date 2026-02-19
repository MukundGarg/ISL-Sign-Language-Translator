package com.example.isltranslator.ui
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signtranslator.MainViewModel
import com.example.signtranslator.UiState
import kotlinx.coroutines.delay
val BackgroundStart = Color(0xFF0D0B2E)
val BackgroundEnd = Color(0xFF1A1145)
val PrimaryAccent = Color(0xFF7C3AED)
val AccentGlow = Color(0xFFA78BFA)
val SurfaceGlass = Color(0x0FFFFFFF)
val SurfaceBorder = Color(0x1AFFFFFF)
val TextPrimary = Color(0xFFF1F0FF)
val TextSecondary = Color(0xFF9B97C2)
val ConfidenceGreen = Color(0xFF34D399)
val ConfidenceOrange = Color(0xFFFBBF24)
val ConfidenceRed = Color(0xFFF87171)
val ButtonSurface = Color(0xFF2D2660)
@Composable
fun ISLTranslatorScreen(
    viewModel: MainViewModel,
    cameraPreview: @Composable (Modifier) -> Unit
) {
    val uiState by viewModel.uiState.observeAsState(UiState())
    var metricsExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BackgroundStart, BackgroundEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            TopBar()
            Spacer(modifier = Modifier.height(16.dp))
            CameraPreviewSection(
                cameraPreview = cameraPreview,
                detectedLetter = uiState.rawLabel ?: "",
                isProcessing = uiState.inferenceTime > 0
            )
            Spacer(modifier = Modifier.height(16.dp))
            ConfidenceIndicator(confidence = uiState.confidence)
            Spacer(modifier = Modifier.height(16.dp))
            SentenceFormationCard(sentence = uiState.sentence)
            Spacer(modifier = Modifier.height(16.dp))
            ControlButtons(
                onSpaceClick = { viewModel.addSpace() },
                onDeleteClick = { viewModel.deleteLastCharacter() },
                onClearClick = { viewModel.clearSentence() }
            )
            Spacer(modifier = Modifier.height(16.dp))
            AIMetricsPanel(
                fps = uiState.fps,
                inferenceTimeMs = uiState.inferenceTime.toInt(),
                processingMode = uiState.processingMode,
                isExpanded = metricsExpanded,
                onToggle = { metricsExpanded = !metricsExpanded }
            )
        }
    }
}
@Composable
private fun TopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(PrimaryAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "I",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ISL Translator",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-0.5).sp
            )
        }
        IconButton(onClick = { }) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
@Composable
private fun CameraPreviewSection(
    cameraPreview: @Composable (Modifier) -> Unit,
    detectedLetter: String,
    isProcessing: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(332.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
    ) {
        cameraPreview(Modifier.fillMaxSize())
        LiveAIBadge(isProcessing = isProcessing)
        if (detectedLetter.isNotEmpty()) {
            DetectionBoundingBox()
        }
        if (detectedLetter.isNotEmpty()) {
            DetectedLetterOverlay(letter = detectedLetter)
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, BackgroundStart.copy(alpha = 0.9f))
                    )
                )
        )
    }
}
@Composable
private fun LiveAIBadge(isProcessing: Boolean) {
    var isPulsing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isPulsing = true
            delay(600)
            isPulsing = false
            delay(600)
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (isPulsing) 1.2f else 1.0f,
        animationSpec = tween(600),
        label = "pulse"
    )
    Row(
        modifier = Modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PrimaryAccent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .scale(scale)
                .background(ConfidenceRed, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "LIVE AI",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )
    }
}
@Composable
private fun DetectionBoundingBox() {
    var glowPhase by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            glowPhase = (glowPhase + 0.02f) % 1f
            delay(16)
        }
    }
    val glowAlpha = (Math.sin(glowPhase * Math.PI * 2) * 0.3 + 0.5).toFloat()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp)
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                val dashLength = 8.dp.toPx()
                val cornerRadius = 12.dp.toPx()
                drawRoundRect(
                    color = AccentGlow.copy(alpha = glowAlpha),
                    topLeft = Offset(-10f, -10f),
                    size = Size(size.width + 20f, size.height + 20f),
                    cornerRadius = CornerRadius(cornerRadius),
                    style = Stroke(
                        width = strokeWidth * 3,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, dashLength))
                    )
                )
                drawRoundRect(
                    color = AccentGlow,
                    cornerRadius = CornerRadius(cornerRadius),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength, dashLength))
                    )
                )
            }
    )
}
@Composable
private fun DetectedLetterOverlay(letter: String) {
    var currentLetter by remember { mutableStateOf("") }
    LaunchedEffect(letter) {
        currentLetter = letter
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 76.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedContent(
            targetState = currentLetter,
            transitionSpec = {
                (fadeIn(animationSpec = tween(200)) + scaleIn(
                    initialScale = 0.5f,
                    animationSpec = spring(
                        dampingRatio = 0.6f,
                        stiffness = Spring.StiffnessLow
                    )
                )).togetherWith(fadeOut(animationSpec = tween(100)))
            },
            label = "letter_animation"
        ) { targetLetter ->
            Text(
                text = targetLetter,
                fontSize = 72.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                modifier = Modifier
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    AccentGlow.copy(alpha = 0.5f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.minDimension
                            )
                        )
                    }
            )
        }
    }
}
@Composable
private fun ConfidenceIndicator(confidence: Float) {
    val animatedConfidence by animateFloatAsState(
        targetValue = confidence,
        animationSpec = tween(300),
        label = "confidence"
    )
    val confidenceColor by animateColorAsState(
        targetValue = when {
            confidence >= 0.8f -> ConfidenceGreen
            confidence >= 0.5f -> ConfidenceOrange
            else -> ConfidenceRed
        },
        animationSpec = tween(400),
        label = "confidence_color"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlass)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confidence",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${(animatedConfidence * 100).toInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = confidenceColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0x0AFFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedConfidence)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(confidenceColor)
                )
            }
        }
    }
}
@Composable
private fun SentenceFormationCard(sentence: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceGlass)
            .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(20.dp))
            .blur(24.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "FORMED SENTENCE",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .horizontalScroll(rememberScrollState()),
                contentAlignment = if (sentence.isEmpty()) Alignment.CenterStart else Alignment.CenterStart
            ) {
                if (sentence.isEmpty()) {
                    Text(
                        text = "Start signing to form words...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        color = TextSecondary.copy(alpha = 0.5f),
                        letterSpacing = 0.2.sp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sentence,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            letterSpacing = 0.2.sp
                        )
                        CursorBlink()
                    }
                }
            }
        }
    }
}
@Composable
private fun CursorBlink() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            visible = !visible
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .offset(x = 4.dp)
                .background(PrimaryAccent)
        )
    }
}
@Composable
private fun ControlButtons(
    onSpaceClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ControlButton(
            icon = Icons.Default.SpaceBar,
            label = "Space",
            color = AccentGlow,
            onClick = onSpaceClick,
            modifier = Modifier.weight(1f)
        )
        ControlButton(
            icon = Icons.Default.Backspace,
            label = "Delete",
            color = ConfidenceOrange,
            onClick = onDeleteClick,
            modifier = Modifier.weight(1f)
        )
        ControlButton(
            icon = Icons.Default.DeleteSweep,
            label = "Clear",
            color = ConfidenceRed,
            onClick = onClearClick,
            modifier = Modifier.weight(1f)
        )
    }
}
@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = tween(100),
        label = "button_scale"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isPressed) Color(0xFF3D3480) else ButtonSurface,
        animationSpec = tween(100),
        label = "button_bg"
    )
    Box(
        modifier = modifier
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(16.dp))
            .clickable {
                isPressed = true
                onClick()
            }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                letterSpacing = 0.5.sp
            )
        }
    }
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}
@Composable
private fun AIMetricsPanel(
    fps: Int,
    inferenceTimeMs: Int,
    processingMode: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AI Metrics ${if (isExpanded) "▴" else "▾"}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(250)) + slideInHorizontally(),
            exit = fadeOut(animationSpec = tween(250)) + slideOutHorizontally()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceGlass)
                    .border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricItem(
                        label = "FPS",
                        value = fps.toString(),
                        color = if (fps >= 24) ConfidenceGreen else ConfidenceOrange
                    )
                    MetricItem(
                        label = "Latency",
                        value = "${inferenceTimeMs}ms",
                        color = if (inferenceTimeMs < 50) ConfidenceGreen else ConfidenceOrange
                    )
                    MetricItem(
                        label = "Mode",
                        value = processingMode,
                        color = if (processingMode == "GPU") ConfidenceGreen else ConfidenceOrange
                    )
                }
            }
        }
    }
}
@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )
    }
}
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ISLTranslatorScreenPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BackgroundStart, BackgroundEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 24.dp, bottom = 16.dp)
        ) {
            TopBar()
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(332.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E88E5),
                                Color(0xFF1565C0)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Camera Preview", color = Color.White, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            ConfidenceIndicator(confidence = 0.87f)
            Spacer(modifier = Modifier.height(16.dp))
            SentenceFormationCard(sentence = "Hello World")
            Spacer(modifier = Modifier.height(16.dp))
            ControlButtons(onSpaceClick = {}, onDeleteClick = {}, onClearClick = {})
            Spacer(modifier = Modifier.height(16.dp))
            AIMetricsPanel(
                fps = 30,
                inferenceTimeMs = 45,
                processingMode = "GPU",
                isExpanded = false,
                onToggle = {}
            )
        }
    }
}
