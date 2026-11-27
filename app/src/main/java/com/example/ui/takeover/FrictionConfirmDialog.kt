package com.example.ui.takeover

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
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
import kotlinx.coroutines.delay
import kotlin.math.ceil

sealed interface PendingFrictionAction {
    val challengeWords: List<String>

    data class DisarmGuard(
        override val challengeWords: List<String>
    ) : PendingFrictionAction

    data class UnlockDetoxApp(
        val packageName: String,
        val appLabel: String,
        override val challengeWords: List<String>
    ) : PendingFrictionAction

    data class EndDeepWorkEarly(
        override val challengeWords: List<String>
    ) : PendingFrictionAction
}

private const val HOLD_DURATION_MS = 5_000L

@Composable
fun FrictionConfirmDialog(
    action: PendingFrictionAction,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    val view = LocalView.current
    val expectedPhrase = remember(action) { action.challengeWords.joinToString(" ").uppercase() }
    var typedInput by remember(action) { mutableStateOf("") }
    var isHolding by remember(action) { mutableStateOf(false) }
    var holdProgress by remember(action) { mutableFloatStateOf(0f) }
    var completed by remember(action) { mutableStateOf(false) }

    val normalizedInput = typedInput
        .trim()
        .uppercase()
        .replace(Regex("\\s+"), " ")
    val isExactMatch = normalizedInput == expectedPhrase

    // Continuous 5-second hold timer — releasing early snaps back to 0 immediately.
    LaunchedEffect(isHolding, isExactMatch) {
        if (!isHolding || !isExactMatch || completed) {
            holdProgress = 0f
            return@LaunchedEffect
        }
        val startMs = System.currentTimeMillis()
        var lastSecondHaptic = -1
        while (isHolding && isExactMatch && !completed) {
            val elapsed = System.currentTimeMillis() - startMs
            val progress = (elapsed.toFloat() / HOLD_DURATION_MS.toFloat()).coerceIn(0f, 1f)
            holdProgress = progress

            val currentWholeSecond = (elapsed / 1_000L).toInt()
            if (currentWholeSecond != lastSecondHaptic) {
                lastSecondHaptic = currentWholeSecond
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }

            if (elapsed >= HOLD_DURATION_MS) {
                completed = true
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onConfirmed()
                break
            }
            delay(32L)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            color = CarbonSurface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("friction_confirm_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(SignalVermilion)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = SignalVermilionDim,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.55f))
                    ) {
                        Icon(
                            imageVector = when (action) {
                                is PendingFrictionAction.DisarmGuard -> Icons.Filled.Shield
                                is PendingFrictionAction.UnlockDetoxApp -> Icons.Filled.Lock
                                is PendingFrictionAction.EndDeepWorkEarly -> Icons.Filled.DoNotDisturbOn
                            },
                            contentDescription = null,
                            tint = SignalVermilion,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = when (action) {
                            is PendingFrictionAction.DisarmGuard ->
                                stringResource(R.string.friction_disarm_title)
                            is PendingFrictionAction.UnlockDetoxApp ->
                                stringResource(R.string.friction_detox_title, action.appLabel)
                            is PendingFrictionAction.EndDeepWorkEarly ->
                                stringResource(R.string.friction_deep_work_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = AlabasterInk,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when (action) {
                            is PendingFrictionAction.DisarmGuard ->
                                stringResource(R.string.friction_disarm_subtitle)
                            is PendingFrictionAction.UnlockDetoxApp ->
                                stringResource(R.string.friction_detox_subtitle)
                            is PendingFrictionAction.EndDeepWorkEarly ->
                                stringResource(R.string.friction_deep_work_subtitle)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ZincSecondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = stringResource(
                            R.string.friction_challenge_label,
                            action.challengeWords.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = ZincSecondary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateContainer, RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isExactMatch) SafeEmerald.copy(alpha = 0.7f) else HairlineBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(vertical = 14.dp, horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val highlighted = buildAnnotatedString {
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
                            text = highlighted,
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                lineHeight = 25.sp,
                                letterSpacing = 1.8.sp
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("friction_challenge_phrase")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = typedInput,
                        onValueChange = { updated ->
                            typedInput = updated.replace("\n", "")
                            if (isHolding) isHolding = false
                        },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.takeover_input_placeholder),
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 14.sp,
                                    color = ZincMuted
                                )
                            )
                        },
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            letterSpacing = 1.2.sp,
                            color = AlabasterInk
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (isExactMatch) SafeEmerald else AlabasterInk,
                            unfocusedBorderColor = HairlineBorder,
                            focusedContainerColor = ObsidianCanvas,
                            unfocusedContainerColor = ObsidianCanvas,
                            cursorColor = AlabasterInk
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("friction_word_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Step 2: Continuous 5-second Press-and-Hold Bar
                    val remainingSeconds = ceil(((1f - holdProgress) * HOLD_DURATION_MS) / 1_000f)
                        .toInt()
                        .coerceIn(1, 5)

                    val holdLabel = when {
                        !isExactMatch -> stringResource(R.string.friction_hold_locked)
                        isHolding -> stringResource(R.string.friction_holding_progress, remainingSeconds)
                        action is PendingFrictionAction.DisarmGuard ->
                            stringResource(R.string.friction_hold_disarm)
                        action is PendingFrictionAction.EndDeepWorkEarly ->
                            stringResource(R.string.friction_hold_end_deep_work)
                        else -> stringResource(R.string.friction_hold_unlock)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isExactMatch) SignalVermilionDim else SlateContainer)
                            .border(
                                1.dp,
                                if (isExactMatch) SignalVermilion else HairlineBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .pointerInput(isExactMatch, completed) {
                                if (!isExactMatch || completed) return@pointerInput
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    isHolding = true
                                    waitForUpOrCancellation()
                                    isHolding = false
                                }
                            }
                            .testTag("friction_hold_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (holdProgress > 0f) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .fillMaxWidth(holdProgress)
                                    .background(SignalVermilion)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = holdLabel,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.8.sp
                                ),
                                color = when {
                                    !isExactMatch -> ZincMuted
                                    holdProgress > 0.45f -> ObsidianCanvas
                                    else -> AlabasterInk
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("friction_cancel_button")
                    ) {
                        Text(
                            text = stringResource(R.string.friction_keep_protected),
                            style = MaterialTheme.typography.labelLarge,
                            color = AlabasterInk
                        )
                    }
                }
            }
        }
    }
}
