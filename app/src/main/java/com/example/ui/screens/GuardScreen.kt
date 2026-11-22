package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.GuardDefaults
import com.example.data.GuardStatus
import com.example.data.PermissionSnapshot
import com.example.ui.GuardUiState
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.GraphiteHighest
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HairlineSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SafeEmeraldDim
import com.example.ui.theme.SignalVermilion
import com.example.ui.theme.SignalVermilionDim
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.ZincMuted
import com.example.ui.theme.ZincSecondary

@Composable
fun GuardScreen(
    uiState: GuardUiState,
    onToggleGuard: (Boolean) -> Unit,
    onSelectInterval: (Int) -> Unit,
    onToggleEscalatingFriction: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestUsagePermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onTestTakeover: () -> Unit,
    onTestHowLongPrompt: () -> Unit,
    onNavigateToApps: () -> Unit,
    onNavigateToMessages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isArmed = uiState.settings.isGuardEnabled
    val runtime = uiState.runtime
    val activeDetoxCount = uiState.settings.activeBlockedApps().size

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("guard_screen_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GuardHeader(isArmed = isArmed)
        }

        item {
            HeroGuardCard(
                isArmed = isArmed,
                status = runtime.status,
                elapsedSeconds = runtime.elapsedSeconds,
                targetSeconds = runtime.targetSeconds,
                activeSessionLabel = runtime.activeSessionLabel,
                onToggleGuard = onToggleGuard
            )
        }

        item {
            PermissionsReadinessCard(
                permissions = uiState.permissions,
                onRequestOverlay = onRequestOverlayPermission,
                onRequestUsage = onRequestUsagePermission,
                onRequestNotification = onRequestNotificationPermission
            )
        }

        item {
            IntervalSelectorCard(
                intervalMinutes = uiState.settings.intervalMinutes,
                onSelectInterval = onSelectInterval
            )
        }

        item {
            EscalatingFrictionCard(
                enabled = uiState.settings.escalatingFrictionEnabled,
                consecutiveOverrides = runtime.consecutiveOverrides,
                onToggle = onToggleEscalatingFriction
            )
        }

        item {
            QuickStatsGrid(
                detoxCount = activeDetoxCount,
                promptedCount = uiState.settings.promptedPackages.size,
                exceptionCount = uiState.settings.exceptionPackages.size,
                messageCount = uiState.settings.reminderMessages.size,
                onAppsClick = onNavigateToApps,
                onMessagesClick = onNavigateToMessages
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TestActionCard(
                    icon = Icons.Filled.Bolt,
                    iconTint = SignalVermilion,
                    iconContainerColor = SignalVermilionDim,
                    iconBorderColor = SignalVermilion.copy(alpha = 0.45f),
                    title = stringResource(R.string.guard_test_takeover),
                    description = stringResource(R.string.guard_test_takeover_desc),
                    testTag = "test_takeover_button",
                    onClick = onTestTakeover
                )
                TestActionCard(
                    icon = Icons.Outlined.Timer,
                    iconTint = AlabasterInk,
                    iconContainerColor = SlateContainer,
                    iconBorderColor = HairlineBorder,
                    title = stringResource(R.string.guard_test_prompt),
                    description = stringResource(R.string.guard_test_prompt_desc),
                    testTag = "test_how_long_button",
                    onClick = onTestHowLongPrompt
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GuardHeader(isArmed: Boolean) {
    val dotColor by animateColorAsState(
        targetValue = if (isArmed) SignalVermilion else ZincMuted,
        label = "armedDot"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.guard_title),
                style = MaterialTheme.typography.headlineLarge,
                color = AlabasterInk
            )
            Text(
                text = stringResource(R.string.guard_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = ZincSecondary
            )
        }

        Surface(
            color = if (isArmed) SignalVermilionDim else CarbonSurface,
            shape = CircleShape,
            border = BorderStroke(
                1.dp,
                if (isArmed) SignalVermilion.copy(alpha = 0.5f) else HairlineBorder
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = if (isArmed) {
                        stringResource(R.string.guard_toggle_on).uppercase()
                    } else {
                        stringResource(R.string.guard_toggle_off).uppercase()
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = AlabasterInk
                )
            }
        }
    }
}

@Composable
private fun HeroGuardCard(
    isArmed: Boolean,
    status: GuardStatus,
    elapsedSeconds: Int,
    targetSeconds: Int,
    activeSessionLabel: String?,
    onToggleGuard: (Boolean) -> Unit
) {
    val rawProgress = if (targetSeconds > 0 && isArmed) {
        (elapsedSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = rawProgress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "timerRingProgress"
    )

    val statusText = when {
        !isArmed -> stringResource(R.string.guard_status_disarmed)
        !activeSessionLabel.isNullOrBlank() && status == GuardStatus.MONITORING ->
            stringResource(R.string.guard_status_session, activeSessionLabel.uppercase())
        status == GuardStatus.PAUSED_EXCEPTION -> stringResource(R.string.guard_status_paused_exception)
        status == GuardStatus.PAUSED_IN_APP -> stringResource(R.string.guard_status_paused_in_app)
        status == GuardStatus.SCREEN_OFF -> stringResource(R.string.guard_status_screen_off)
        status == GuardStatus.TAKEOVER_ACTIVE -> stringResource(R.string.guard_status_takeover)
        else -> stringResource(R.string.guard_status_armed)
    }

    val ringAccent = when {
        !isArmed -> HairlineBorder
        status == GuardStatus.PAUSED_EXCEPTION || status == GuardStatus.PAUSED_IN_APP -> SafeEmerald
        else -> SignalVermilion
    }

    Surface(
        color = CarbonSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (isArmed) SignalVermilion.copy(alpha = 0.35f) else HairlineBorder
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (isArmed) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    SignalVermilion.copy(alpha = 0.14f),
                                    Color.Transparent
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.28f),
                                radius = size.width * 0.65f
                            )
                        )
                    }
                }
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(182.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 8.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

                    drawArc(
                        color = SlateContainer,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    val sweep = if (isArmed) (animatedProgress * 360f).coerceAtLeast(8f) else 0f
                    if (sweep > 0f) {
                        drawArc(
                            color = ringAccent,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = GuardDefaults.formatDuration(if (isArmed) elapsedSeconds else 0),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 35.sp,
                            letterSpacing = (-1).sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = if (isArmed) AlabasterInk else ZincMuted,
                        modifier = Modifier.testTag("guard_elapsed_timer")
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "/ ${GuardDefaults.formatDuration(targetSeconds)}",
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = ZincSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = SlateContainer,
                shape = CircleShape,
                border = BorderStroke(1.dp, HairlineBorder)
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status == GuardStatus.PAUSED_EXCEPTION || status == GuardStatus.PAUSED_IN_APP) {
                        SafeEmerald
                    } else if (isArmed) {
                        AlabasterInk
                    } else {
                        ZincSecondary
                    },
                    modifier = Modifier
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("guard_status_pill")
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(color = HairlineSubtle)
            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleGuard(!isArmed) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = if (isArmed) SignalVermilion else ZincSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.guard_master_label),
                            style = MaterialTheme.typography.titleMedium,
                            color = AlabasterInk
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isArmed) {
                            stringResource(R.string.guard_master_hint_on)
                        } else {
                            stringResource(R.string.guard_master_hint_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = ZincSecondary
                    )
                }

                Switch(
                    checked = isArmed,
                    onCheckedChange = onToggleGuard,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AlabasterInk,
                        checkedTrackColor = SignalVermilion,
                        checkedBorderColor = SignalVermilion,
                        uncheckedThumbColor = ZincSecondary,
                        uncheckedTrackColor = SlateContainer,
                        uncheckedBorderColor = HairlineBorder
                    ),
                    modifier = Modifier.testTag("guard_master_switch")
                )
            }
        }
    }
}

@Composable
private fun EscalatingFrictionCard(
    enabled: Boolean,
    consecutiveOverrides: Int,
    onToggle: (Boolean) -> Unit
) {
    val wordCount = GuardDefaults.challengeWordCountForOverrides(consecutiveOverrides, enabled)
    val level = if (enabled) (consecutiveOverrides + 1).coerceAtMost(4) else 1

    Surface(
        color = CarbonSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            1.dp,
            if (enabled && consecutiveOverrides > 0) {
                SignalVermilion.copy(alpha = 0.5f)
            } else {
                HairlineBorder
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!enabled) }
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.TrendingUp,
                        contentDescription = null,
                        tint = if (enabled) SignalVermilion else ZincSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.guard_escalation_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = AlabasterInk
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.guard_escalation_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZincSecondary
                )
                if (enabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = if (consecutiveOverrides > 0) SignalVermilionDim else SlateContainer,
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            if (consecutiveOverrides > 0) SignalVermilion.copy(alpha = 0.5f) else HairlineBorder
                        )
                    ) {
                        Text(
                            text = if (consecutiveOverrides == 0) {
                                stringResource(R.string.guard_escalation_badge_base)
                            } else {
                                stringResource(R.string.guard_escalation_badge_active, level, wordCount)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (consecutiveOverrides > 0) SignalVermilion else AlabasterInk,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ObsidianCanvas,
                    checkedTrackColor = AlabasterInk,
                    checkedBorderColor = AlabasterInk,
                    uncheckedThumbColor = ZincSecondary,
                    uncheckedTrackColor = SlateContainer,
                    uncheckedBorderColor = HairlineBorder
                ),
                modifier = Modifier.testTag("guard_escalation_switch")
            )
        }
    }
}

@Composable
private fun PermissionsReadinessCard(
    permissions: PermissionSnapshot,
    onRequestOverlay: () -> Unit,
    onRequestUsage: () -> Unit,
    onRequestNotification: () -> Unit
) {
    if (permissions.allGranted) {
        Surface(
            color = SafeEmeraldDim,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, SafeEmerald.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("permissions_all_granted_badge")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SafeEmerald,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.guard_permissions_ready),
                    style = MaterialTheme.typography.titleSmall,
                    color = AlabasterInk
                )
            }
        }
        return
    }

    Surface(
        color = CarbonSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permissions_readiness_card")
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = WarningAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.guard_permissions_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = AlabasterInk
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            PermissionActionRow(
                icon = Icons.Outlined.PhonelinkLock,
                title = stringResource(R.string.guard_perm_overlay_title),
                description = stringResource(R.string.guard_perm_overlay_desc),
                granted = permissions.hasOverlayPermission,
                testTag = "grant_overlay_button",
                onGrantClick = onRequestOverlay
            )

            HorizontalDivider(
                color = HairlineSubtle,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            PermissionActionRow(
                icon = Icons.Outlined.QueryStats,
                title = stringResource(R.string.guard_perm_usage_title),
                description = stringResource(R.string.guard_perm_usage_desc),
                granted = permissions.hasUsageStatsPermission,
                testTag = "grant_usage_button",
                onGrantClick = onRequestUsage
            )

            AnimatedVisibility(visible = !permissions.hasNotificationPermission) {
                Column {
                    HorizontalDivider(
                        color = HairlineSubtle,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    PermissionActionRow(
                        icon = Icons.Outlined.NotificationsActive,
                        title = stringResource(R.string.guard_perm_notif_title),
                        description = stringResource(R.string.guard_perm_notif_desc),
                        granted = permissions.hasNotificationPermission,
                        testTag = "grant_notif_button",
                        onGrantClick = onRequestNotification
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    testTag: String,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) SafeEmerald else ZincSecondary,
                modifier = Modifier.padding(top = 2.dp).size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = AlabasterInk
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZincSecondary
                )
            }
        }

        if (granted) {
            Surface(
                color = SafeEmeraldDim,
                shape = CircleShape,
                border = BorderStroke(1.dp, SafeEmerald.copy(alpha = 0.4f))
            ) {
                Text(
                    text = stringResource(R.string.guard_perm_granted),
                    style = MaterialTheme.typography.labelSmall,
                    color = SafeEmerald,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        } else {
            OutlinedButton(
                onClick = onGrantClick,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AlabasterInk),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlabasterInk),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.testTag(testTag)
            ) {
                Text(
                    text = stringResource(R.string.guard_perm_grant),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntervalSelectorCard(
    intervalMinutes: Int,
    onSelectInterval: (Int) -> Unit
) {
    Surface(
        color = CarbonSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.guard_interval_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = AlabasterInk
                    )
                    Text(
                        text = stringResource(R.string.guard_interval_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZincSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { onSelectInterval(intervalMinutes - 1) },
                        enabled = intervalMinutes > GuardDefaults.MIN_INTERVAL_MINUTES,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = SlateContainer,
                            contentColor = AlabasterInk,
                            disabledContainerColor = ObsidianCanvas,
                            disabledContentColor = ZincMuted
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("interval_decrease_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Decrease interval"
                        )
                    }

                    Text(
                        text = stringResource(R.string.guard_interval_minutes_long, intervalMinutes),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = AlabasterInk,
                        modifier = Modifier.testTag("interval_value_text")
                    )

                    FilledTonalIconButton(
                        onClick = { onSelectInterval(intervalMinutes + 1) },
                        enabled = intervalMinutes < GuardDefaults.MAX_INTERVAL_MINUTES,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = SlateContainer,
                            contentColor = AlabasterInk,
                            disabledContainerColor = ObsidianCanvas,
                            disabledContentColor = ZincMuted
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("interval_increase_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Increase interval"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                GuardDefaults.PRESET_INTERVALS.forEach { preset ->
                    val selected = intervalMinutes == preset
                    Surface(
                        onClick = { onSelectInterval(preset) },
                        color = if (selected) AlabasterInk else SlateContainer,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) AlabasterInk else HairlineBorder
                        ),
                        modifier = Modifier.testTag("interval_preset_${preset}m")
                    ) {
                        Text(
                            text = stringResource(R.string.guard_interval_minutes_short, preset),
                            style = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = if (selected) ObsidianCanvas else AlabasterInk,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ScreenLockPortrait,
                    contentDescription = null,
                    tint = ZincSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.guard_reset_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZincSecondary
                )
            }
        }
    }
}

@Composable
private fun QuickStatsGrid(
    detoxCount: Int,
    promptedCount: Int,
    exceptionCount: Int,
    messageCount: Int,
    onAppsClick: () -> Unit,
    onMessagesClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickStatTile(
                icon = Icons.Outlined.Lock,
                tint = if (detoxCount > 0) SignalVermilion else AlabasterInk,
                label = stringResource(R.string.guard_stat_detox, detoxCount),
                onClick = onAppsClick,
                testTag = "quick_stat_detox",
                modifier = Modifier.weight(1f)
            )
            QuickStatTile(
                icon = Icons.Outlined.Timer,
                tint = AlabasterInk,
                label = stringResource(R.string.guard_stat_prompted, promptedCount),
                onClick = onAppsClick,
                testTag = "quick_stat_prompted",
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickStatTile(
                icon = Icons.Outlined.Apps,
                tint = if (exceptionCount > 0) SafeEmerald else AlabasterInk,
                label = stringResource(R.string.guard_stat_exceptions, exceptionCount),
                onClick = onAppsClick,
                testTag = "quick_stat_exceptions",
                modifier = Modifier.weight(1f)
            )
            QuickStatTile(
                icon = Icons.Outlined.ChatBubbleOutline,
                tint = AlabasterInk,
                label = stringResource(R.string.guard_stat_messages, messageCount),
                onClick = onMessagesClick,
                testTag = "quick_stat_messages",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickStatTile(
    icon: ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = CarbonSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = AlabasterInk
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ZincSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TestActionCard(
    icon: ImageVector,
    iconTint: Color,
    iconContainerColor: Color,
    iconBorderColor: Color,
    title: String,
    description: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = GraphiteHighest,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    color = iconContainerColor,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, iconBorderColor)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.padding(10.dp).size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = AlabasterInk
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZincSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AlabasterInk
            )
        }
    }
}
