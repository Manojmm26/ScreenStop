package com.example.ui.takeover

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.GuardDefaults
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SignalVermilion
import com.example.ui.theme.SignalVermilionDim
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.ZincSecondary
import kotlinx.coroutines.delay

@Composable
fun DetoxBlockedScreen(
    appLabel: String,
    blockedUntilMillis: Long,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onReturnHome() }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(blockedUntilMillis) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            if (nowMillis >= blockedUntilMillis) {
                onReturnHome()
                break
            }
            delay(1_000L)
        }
    }

    val remaining = (blockedUntilMillis - nowMillis).coerceAtLeast(0L)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(SignalVermilion.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.16f),
                        radius = size.width * 0.85f
                    )
                )
            }
            .testTag("detox_blocked_root")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(SignalVermilion)
                .align(Alignment.TopCenter)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    color = SignalVermilionDim,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.6f))
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = SignalVermilion,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.detox_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = AlabasterInk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = stringResource(R.string.detox_headline, appLabel),
                    style = MaterialTheme.typography.displayMedium,
                    color = AlabasterInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.detox_subcopy),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ZincSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Surface(
                color = CarbonSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 28.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.detox_remaining_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = ZincSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = GuardDefaults.formatRemainingLock(remaining),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 38.sp,
                            letterSpacing = (-1).sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = SignalVermilion,
                        modifier = Modifier.testTag("detox_remaining_countdown")
                    )
                }
            }

            Button(
                onClick = onReturnHome,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlabasterInk,
                    contentColor = ObsidianCanvas
                ),
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag("detox_go_home_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Home,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.detox_go_home),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HowLongPromptScreen(
    appLabel: String,
    onSelectDurationMinutes: (Int) -> Unit,
    onNeverMindGoHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onNeverMindGoHome() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianCanvas)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AlabasterInk.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = size.width * 0.8f
                    )
                )
            }
            .testTag("how_long_prompt_root")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    color = SlateContainer,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, HairlineBorder)
                ) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = AlabasterInk,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.prompt_badge),
                            style = MaterialTheme.typography.labelMedium,
                            color = AlabasterInk
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = stringResource(R.string.prompt_headline, appLabel),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 30.sp,
                        lineHeight = 38.sp
                    ),
                    color = AlabasterInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.prompt_subcopy),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ZincSecondary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GuardDefaults.SESSION_PROMPT_OPTIONS_MINUTES.forEach { minutes ->
                        Surface(
                            onClick = { onSelectDurationMinutes(minutes) },
                            color = CarbonSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.testTag("prompt_duration_${minutes}m")
                        ) {
                            Text(
                                text = stringResource(R.string.guard_interval_minutes_long, minutes),
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                ),
                                color = AlabasterInk,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                OutlinedButton(
                    onClick = onNeverMindGoHome,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AlabasterInk.copy(alpha = 0.65f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlabasterInk),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("prompt_never_mind_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.prompt_never_mind),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
