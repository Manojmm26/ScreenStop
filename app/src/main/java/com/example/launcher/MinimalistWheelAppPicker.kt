package com.example.launcher

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.GuardDefaults
import com.example.data.GuardSettings
import com.example.data.InstalledAppInfo
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.GraphiteHighest
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SignalVermilion
import com.example.ui.theme.SignalVermilionDim
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.ZincMuted
import com.example.ui.theme.ZincSecondary
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

private val WheelItemHeight: Dp = 64.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MinimalistWheelAppPicker(
    apps: List<InstalledAppInfo>,
    settings: GuardSettings,
    nowMillis: Long,
    onFocusedAppChanged: (InstalledAppInfo?) -> Unit,
    onLaunchApp: (InstalledAppInfo) -> Unit,
    onLongPressApp: (InstalledAppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val snapFlingBehavior = rememberSnapFlingBehavior(
        lazyListState = listState,
        snapPosition = SnapPosition.Center
    )
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // Reset wheel to top whenever the filtered list changes significantly (e.g., typing a search query)
    val listSignature = remember(apps) {
        apps.firstOrNull()?.packageName to apps.size
    }
    LaunchedEffect(listSignature) {
        if (apps.isNotEmpty() && listState.firstVisibleItemIndex >= apps.size) {
            listState.scrollToItem(0)
        } else if (apps.isNotEmpty() && listState.firstVisibleItemIndex != 0) {
            listState.scrollToItem(0)
        }
    }

    val centeredIndex by remember(listState, apps) {
        derivedStateOf {
            resolveCenteredIndex(listState, apps.size)
        }
    }

    val focusedApp = apps.getOrNull(centeredIndex)

    LaunchedEffect(focusedApp) {
        onFocusedAppChanged(focusedApp)
    }

    // Tactile iOS-inspired mechanical click haptic whenever a new app rolls into the center focal lens
    var previousTickIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(centeredIndex, apps.size) {
        if (apps.isNotEmpty() && previousTickIndex != -1 && centeredIndex != previousTickIndex) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        previousTickIndex = centeredIndex
    }

    // Dynamic A-Z index mapping for fast right-edge thumb scrubbing
    val alphabetSections = remember(apps) {
        val map = LinkedHashMap<Char, Int>()
        apps.forEachIndexed { index, app ->
            val first = app.displayLabel.trim().firstOrNull()?.uppercaseChar()
            val key = if (first != null && first in 'A'..'Z') first else '#'
            if (key !in map) {
                map[key] = index
            }
        }
        map.entries.map { it.key to it.value }
    }
    val activeLetter = remember(focusedApp) {
        val first = focusedApp?.displayLabel?.trim()?.firstOrNull()?.uppercaseChar()
        if (first != null && first in 'A'..'Z') first else '#'
    }
    var scrubbingLetter by remember { mutableStateOf<Char?>(null) }

    if (apps.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag("launcher_wheel_empty"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.launcher_wheel_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = ZincMuted
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Subtle live counter & interaction hint
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.launcher_wheel_hint),
                style = MaterialTheme.typography.labelSmall,
                color = ZincMuted
            )
            Text(
                text = stringResource(
                    R.string.launcher_wheel_count,
                    (centeredIndex + 1).coerceIn(1, apps.size),
                    apps.size
                ),
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = ZincSecondary,
                modifier = Modifier.testTag("launcher_wheel_position_counter")
            )
        }

        // Main 3D Cylindrical Roller Drum Viewport + Right A-Z Scrubber
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("launcher_wheel_container")
        ) {
            val viewportHeight = maxHeight
            val verticalPadding = ((viewportHeight - WheelItemHeight) / 2f).coerceAtLeast(0.dp)
            val showScrubber = alphabetSections.size >= 4 && viewportHeight >= 240.dp

            // Center architectural focal lens (modern replacement for iOS UIPickerView gray selection bars)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(end = if (showScrubber) 34.dp else 0.dp)
                    .height(WheelItemHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarbonSurface.copy(alpha = 0.92f))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 1.dp.toPx()
                    // Crisp top & bottom hairline focal rails
                    drawLine(
                        color = AlabasterInk.copy(alpha = 0.24f),
                        start = Offset(16.dp.toPx(), strokePx / 2f),
                        end = Offset(size.width - 16.dp.toPx(), strokePx / 2f),
                        strokeWidth = strokePx
                    )
                    drawLine(
                        color = AlabasterInk.copy(alpha = 0.24f),
                        start = Offset(16.dp.toPx(), size.height - strokePx / 2f),
                        end = Offset(size.width - 16.dp.toPx(), size.height - strokePx / 2f),
                        strokeWidth = strokePx
                    )
                    // Left Swiss precision Signal Vermilion index notch
                    val barWidth = 3.dp.toPx()
                    val barHeight = 24.dp.toPx()
                    drawRoundRect(
                        color = SignalVermilion,
                        topLeft = Offset(10.dp.toPx(), (size.height - barHeight) / 2f),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth, barWidth)
                    )
                }
            }

            // Precision mechanical tick marks along the far-left edge of the wheel
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(8.dp)
                    .fillMaxHeight(0.76f)
            ) {
                val tickCount = 21
                val centerTick = tickCount / 2
                val stepY = if (tickCount > 1) size.height / (tickCount - 1) else 0f
                for (i in 0 until tickCount) {
                    val y = i * stepY
                    val distFromCenter = abs(i - centerTick).toFloat() / centerTick.toFloat()
                    val isCenter = i == centerTick
                    val tickLength = when {
                        isCenter -> size.width
                        i % 2 == 0 -> size.width * 0.62f
                        else -> size.width * 0.36f
                    }
                    val tickColor = if (isCenter) {
                        SignalVermilion
                    } else {
                        ZincMuted.copy(alpha = (1f - distFromCenter * 0.85f).coerceIn(0.1f, 0.55f))
                    }
                    drawLine(
                        color = tickColor,
                        start = Offset(0f, y),
                        end = Offset(tickLength, y),
                        strokeWidth = if (isCenter) 2.dp.toPx() else 1.dp.toPx()
                    )
                }
            }

            // 3D Cylindrical Roller LazyColumn
            LazyColumn(
                state = listState,
                flingBehavior = snapFlingBehavior,
                contentPadding = PaddingValues(vertical = verticalPadding),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = if (showScrubber) 34.dp else 0.dp)
                    .testTag("launcher_drawer_list")
            ) {
                itemsIndexed(
                    items = apps,
                    key = { _, app -> app.packageName }
                ) { index, app ->
                    val isCentered = index == centeredIndex
                    val isLocked = app.isDetoxLocked(nowMillis)
                    val isDeepWorkRestricted = !isLocked && app.isRestrictedByDeepWork(settings, nowMillis)
                    val isBlocked = isLocked || isDeepWorkRestricted
                    val remaining = (app.blockedUntilMillis ?: 0L) - nowMillis

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WheelItemHeight)
                            .graphicsLayer {
                                val layoutInfo = listState.layoutInfo
                                val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                if (itemInfo != null) {
                                    val viewportCenter =
                                        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                                    val halfSpan =
                                        ((layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2f)
                                            .coerceAtLeast(1f)
                                    val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                    val normalized = ((itemCenter - viewportCenter) / halfSpan)
                                        .coerceIn(-1.25f, 1.25f)
                                    val absDist = abs(normalized)

                                    // True 3D cylindrical drum curvature
                                    cameraDistance = 14f * density
                                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    rotationX = -normalized * 48f

                                    val scale = (1f - absDist * 0.22f).coerceIn(0.72f, 1f)
                                    scaleX = scale
                                    scaleY = scale

                                    // Subtle cylindrical foreshortening pulls horizon items closer toward center
                                    val curvedPull = (normalized - sin(normalized * 1.15f) / 1.15f)
                                    translationY = -curvedPull * size.height * 0.85f

                                    alpha = (1f - absDist * 0.82f).coerceIn(0.14f, 1f)
                                }
                            }
                            .clip(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isCentered) {
                                        onLaunchApp(app)
                                    } else {
                                        scope.launch {
                                            listState.animateScrollToItem(index)
                                        }
                                    }
                                },
                                onLongClick = { onLongPressApp(app) }
                            )
                            .padding(start = 16.dp, end = 14.dp)
                            .testTag("launcher_wheel_item_${app.packageName}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = app.displayLabel,
                            style = TextStyle(
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = if (isCentered) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = if (isCentered) 24.sp else 21.sp,
                                letterSpacing = if (isCentered) (-0.5).sp else (-0.2).sp
                            ),
                            color = when {
                                isBlocked -> if (isCentered) ZincSecondary else ZincMuted
                                app.isHiddenInLauncher -> ZincSecondary
                                isCentered -> AlabasterInk
                                else -> ZincSecondary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (app.shouldShowDailyUsage || isBlocked || app.isPrompted || app.isException) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.shouldShowDailyUsage) {
                                    val usageColor = when {
                                        app.todayUsageMinutes >= 60 -> SignalVermilion
                                        app.todayUsageMinutes >= 30 -> WarningAmber
                                        isCentered -> ZincSecondary
                                        else -> ZincMuted
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.launcher_usage_minutes,
                                            app.todayUsageMinutes
                                        ),
                                        style = TextStyle(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = if (isCentered) FontWeight.SemiBold else FontWeight.Medium,
                                            fontSize = if (isCentered) 12.sp else 11.sp,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = usageColor,
                                        modifier = Modifier.testTag("launcher_wheel_usage_${app.packageName}")
                                    )
                                }

                                when {
                                    isLocked -> {
                                        Surface(
                                            color = SignalVermilionDim,
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = GuardDefaults.formatRemainingLock(remaining),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SignalVermilion,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    isDeepWorkRestricted -> {
                                        Surface(
                                            color = SignalVermilionDim,
                                            shape = CircleShape,
                                            border = BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.45f))
                                        ) {
                                            Text(
                                                text = stringResource(R.string.deep_work_pill_tag),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SignalVermilion,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    app.isPrompted -> {
                                        Text(
                                            text = "PROMPT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCentered) AlabasterInk.copy(alpha = 0.8f) else ZincMuted
                                        )
                                    }

                                    app.isException -> {
                                        Text(
                                            text = "EXEMPT",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SafeEmerald
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Top atmospheric obsidian fade vignette
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(end = if (showScrubber) 34.dp else 0.dp)
                    .height((viewportHeight * 0.28f).coerceAtLeast(44.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ObsidianCanvas,
                                ObsidianCanvas.copy(alpha = 0.78f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Bottom atmospheric obsidian fade vignette
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(end = if (showScrubber) 34.dp else 0.dp)
                    .height((viewportHeight * 0.28f).coerceAtLeast(44.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                ObsidianCanvas.copy(alpha = 0.78f),
                                ObsidianCanvas
                            )
                        )
                    )
            )

            // Right-edge tactile A-Z quick-jump scrubber rail
            if (showScrubber) {
                AlphabetScrubberRail(
                    sections = alphabetSections,
                    activeLetter = scrubbingLetter ?: activeLetter,
                    onJumpToSection = { letter, targetIndex ->
                        if (scrubbingLetter != letter) {
                            scrubbingLetter = letter
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            scope.launch {
                                listState.scrollToItem(targetIndex)
                            }
                        }
                    },
                    onScrubEnd = { scrubbingLetter = null },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(0.86f)
                        .width(30.dp)
                        .testTag("launcher_alphabet_scrubber")
                )
            }

            // Floating HUD letter bubble while scrubbing the A-Z rail
            androidx.compose.animation.AnimatedVisibility(
                visible = scrubbingLetter != null,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut() + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 44.dp)
            ) {
                Surface(
                    color = GraphiteHighest,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AlabasterInk.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = (scrubbingLetter ?: activeLetter).toString(),
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        ),
                        color = AlabasterInk,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }
        }

        // Focused App One-Handed Launch & Quick-Actions Strip
        if (focusedApp != null) {
            val isLocked = focusedApp.isDetoxLocked(nowMillis)
            val isDeepWorkRestricted = !isLocked && focusedApp.isRestrictedByDeepWork(settings, nowMillis)
            val isBlocked = isLocked || isDeepWorkRestricted
            val remaining = if (isLocked) {
                (focusedApp.blockedUntilMillis ?: 0L) - nowMillis
            } else {
                (settings.deepWorkUntilMillis - nowMillis).coerceAtLeast(0L)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onLaunchApp(focusedApp) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBlocked) SignalVermilionDim else AlabasterInk,
                        contentColor = if (isBlocked) SignalVermilion else ObsidianCanvas
                    ),
                    border = if (isBlocked) BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.55f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("launcher_wheel_launch_focused")
                ) {
                    if (isBlocked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDeepWorkRestricted) {
                                stringResource(
                                    R.string.deep_work_wheel_locked,
                                    GuardDefaults.formatRemainingLock(remaining)
                                )
                            } else {
                                stringResource(
                                    R.string.launcher_wheel_locked_app,
                                    GuardDefaults.formatRemainingLock(remaining)
                                )
                            },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.launcher_wheel_open_app, focusedApp.displayLabel),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Surface(
                    onClick = { onLongPressApp(focusedApp) },
                    color = CarbonSurface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("launcher_wheel_options_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.MoreHoriz,
                            contentDescription = stringResource(R.string.launcher_wheel_options_cd),
                            tint = AlabasterInk,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlphabetScrubberRail(
    sections: List<Pair<Char, Int>>,
    activeLetter: Char,
    onJumpToSection: (Char, Int) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var railHeightPx by remember { mutableIntStateOf(1) }

    fun jumpForY(y: Float) {
        if (sections.isEmpty() || railHeightPx <= 0) return
        val fraction = (y / railHeightPx.toFloat()).coerceIn(0f, 0.9999f)
        val idx = (fraction * sections.size).toInt().coerceIn(0, sections.lastIndex)
        val (letter, appIndex) = sections[idx]
        onJumpToSection(letter, appIndex)
    }

    Column(
        modifier = modifier
            .clip(CircleShape)
            .background(SlateContainer.copy(alpha = 0.45f))
            .onSizeChanged { railHeightPx = it.height.coerceAtLeast(1) }
            .pointerInput(sections) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    jumpForY(down.position.y)
                    drag(down.id) { change ->
                        change.consume()
                        jumpForY(change.position.y)
                    }
                    onScrubEnd()
                }
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        sections.forEach { (letter, _) ->
            val isActive = letter == activeLetter
            Text(
                text = letter.toString(),
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    fontSize = if (isActive) 11.sp else 9.sp
                ),
                color = if (isActive) SignalVermilion else ZincMuted
            )
        }
    }
}

private fun resolveCenteredIndex(listState: LazyListState, itemCount: Int): Int {
    if (itemCount <= 0) return 0
    val layoutInfo = listState.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) {
        return listState.firstVisibleItemIndex.coerceIn(0, itemCount - 1)
    }
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val closest = visibleItems.minByOrNull { item ->
        val itemCenter = item.offset + item.size / 2f
        abs(itemCenter - viewportCenter)
    }
    return (closest?.index ?: listState.firstVisibleItemIndex).coerceIn(0, itemCount - 1)
}
