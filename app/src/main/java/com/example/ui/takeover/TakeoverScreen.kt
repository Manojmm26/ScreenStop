package com.example.ui.takeover

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.TakeoverSession
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SignalVermilion
import com.example.ui.theme.SignalVermilionDim
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.ZincMuted
import com.example.ui.theme.ZincSecondary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun TakeoverScreen(
    session: TakeoverSession,
    onDismissConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { /* Absorbed intentionally */ }

    var typedInput by remember(session.triggeredAtMillis) { mutableStateOf("") }
    var showMismatchError by remember(session.triggeredAtMillis) { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val expectedPhrase = session.expectedPhrase.uppercase()
    val normalizedInput = typedInput
        .trim()
        .uppercase()
        .replace(Regex("\\s+"), " ")
    val isExactMatch = normalizedInput == expectedPhrase
    val wordCount = session.challengeWords.size

    fun attemptConfirm() {
        if (isExactMatch) {
            onDismissConfirmed()
        } else {
            showMismatchError = true
            coroutineScope.launch {
                shakeOffset.snapTo(0f)
                listOf(-18f, 18f, -12f, 12f, -6f, 0f).forEach { target ->
                    shakeOffset.animateTo(
                        targetValue = target,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessHigh
                        )
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SignalVermilion.copy(alpha = if (session.escalationLevel > 1) 0.28f else 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.12f),
                        radius = size.width * 0.85f
                    )
                )
            }
            .testTag("takeover_root")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(SignalVermilion)
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = SignalVermilionDim,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = SignalVermilion,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                session.isPreview -> stringResource(R.string.takeover_preview_badge)
                                session.escalationLevel > 1 -> stringResource(
                                    R.string.takeover_escalated_badge,
                                    session.consecutiveOverrides + 1
                                )
                                else -> stringResource(R.string.takeover_badge)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = AlabasterInk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = session.message,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 29.sp,
                        lineHeight = 37.sp
                    ),
                    color = AlabasterInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("takeover_message_text")
                )

                Spacer(modifier = Modifier.height(22.dp))

                Surface(
                    color = CarbonSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ScreenLockPortrait,
                            contentDescription = null,
                            tint = SafeEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.takeover_lock_tip),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZincSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                color = CarbonSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isExactMatch) SafeEmerald.copy(alpha = 0.7f) else HairlineBorder
                ),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (wordCount == 1) {
                            stringResource(R.string.takeover_challenge_label_single)
                        } else {
                            stringResource(R.string.takeover_challenge_label_multi, wordCount)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = ZincSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateContainer, RoundedCornerShape(12.dp))
                            .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                            .padding(vertical = 16.dp, horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val highlightedPhrase = buildAnnotatedString {
                            expectedPhrase.forEachIndexed { index, expectedChar ->
                                val typedChar = normalizedInput.getOrNull(index)
                                val charColor = when {
                                    typedChar == null -> AlabasterInk.copy(alpha = 0.85f)
                                    typedChar == expectedChar -> SafeEmerald
                                    else -> SignalVermilion
                                }
                                withStyle(SpanStyle(color = charColor)) {
                                    append(expectedChar)
                                }
                            }
                        }

                        Text(
                            text = highlightedPhrase,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (wordCount == 1) 25.sp else 19.sp,
                                lineHeight = 28.sp,
                                letterSpacing = if (wordCount == 1) 3.5.sp else 2.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("takeover_challenge_word")
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = typedInput,
                        onValueChange = { updated ->
                            typedInput = updated.replace("\n", "")
                            if (showMismatchError) showMismatchError = false
                        },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.takeover_input_placeholder),
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 15.sp,
                                    color = ZincMuted
                                )
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            letterSpacing = 1.5.sp,
                            color = AlabasterInk
                        ),
                        isError = showMismatchError,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { attemptConfirm() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isExactMatch) SafeEmerald else AlabasterInk,
                            unfocusedBorderColor = HairlineBorder,
                            errorBorderColor = SignalVermilion,
                            focusedContainerColor = ObsidianCanvas,
                            unfocusedContainerColor = ObsidianCanvas,
                            cursorColor = AlabasterInk
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("takeover_word_input")
                    )

                    AnimatedVisibility(visible = showMismatchError) {
                        Text(
                            text = stringResource(R.string.takeover_mismatch_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = SignalVermilion,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { attemptConfirm() },
                        enabled = isExactMatch,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlabasterInk,
                            contentColor = ObsidianCanvas,
                            disabledContainerColor = SlateContainer,
                            disabledContentColor = ZincMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("takeover_confirm_button")
                    ) {
                        if (isExactMatch) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.takeover_confirm_button),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
