package com.example.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.DistractingCategory
import com.example.data.GuardDefaults
import com.example.data.GuardSettings
import com.example.data.InstalledAppInfo
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.HairlineSubtle
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MIN_DEEP_WORK_MINUTES = 10
private const val MAX_DEEP_WORK_MINUTES = 8 * 60
private const val DEFAULT_DEEP_WORK_MINUTES = 45

/**
 * Full-width global Deep Work button docked above the bottom action row on the Minimalist Home screen.
 */
@Composable
fun DeepWorkLaunchBar(
    settings: GuardSettings,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = settings.isDeepWorkActive(nowMillis)
    val remainingMillis = (settings.deepWorkUntilMillis - nowMillis).coerceAtLeast(0L)
    val endClockFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Surface(
        onClick = onClick,
        color = if (isActive) SafeEmerald.copy(alpha = 0.12f) else CarbonSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) SafeEmerald.copy(alpha = 0.55f) else HairlineBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("launcher_deep_work_button")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive) SafeEmerald.copy(alpha = 0.22f) else SlateContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DoNotDisturbOn,
                        contentDescription = null,
                        tint = if (isActive) SafeEmerald else AlabasterInk,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isActive) {
                            stringResource(R.string.deep_work_bar_active_title)
                        } else {
                            stringResource(R.string.deep_work_bar_idle_title)
                        },
                        style = TextStyle(
                            fontFamily = SpaceGroteskFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            letterSpacing = (-0.2).sp
                        ),
                        color = if (isActive) SafeEmerald else AlabasterInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isActive) {
                            val endTime = endClockFormat.format(Date(settings.deepWorkUntilMillis))
                            if (settings.deepWorkStrictAllNonExempt) {
                                stringResource(R.string.deep_work_bar_active_strict_sub, endTime)
                            } else {
                                stringResource(
                                    R.string.deep_work_bar_active_sub,
                                    endTime,
                                    settings.deepWorkCategories.size
                                )
                            }
                        } else {
                            stringResource(R.string.deep_work_bar_idle_sub)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) AlabasterInk.copy(alpha = 0.8f) else ZincSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                color = if (isActive) SafeEmerald.copy(alpha = 0.2f) else SlateContainer,
                shape = CircleShape,
                border = BorderStroke(
                    1.dp,
                    if (isActive) SafeEmerald.copy(alpha = 0.6f) else HairlineBorder
                )
            ) {
                Text(
                    text = if (isActive) {
                        GuardDefaults.formatRemainingLock(remainingMillis)
                    } else {
                        stringResource(R.string.deep_work_bar_start_pill)
                    },
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (isActive) SafeEmerald else AlabasterInk,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * Compact global Deep Work pill shown in the All Apps wheel header.
 */
@Composable
fun DeepWorkCompactChip(
    settings: GuardSettings,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActive = settings.isDeepWorkActive(nowMillis)
    val remainingMillis = (settings.deepWorkUntilMillis - nowMillis).coerceAtLeast(0L)

    Surface(
        onClick = onClick,
        color = if (isActive) SafeEmerald.copy(alpha = 0.16f) else CarbonSurface,
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            if (isActive) SafeEmerald.copy(alpha = 0.6f) else HairlineBorder
        ),
        modifier = modifier.testTag("launcher_drawer_deep_work_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.DoNotDisturbOn,
                contentDescription = stringResource(R.string.deep_work_bar_idle_title),
                tint = if (isActive) SafeEmerald else AlabasterInk,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isActive) {
                    GuardDefaults.formatRemainingLock(remainingMillis)
                } else {
                    stringResource(R.string.deep_work_bar_idle_title)
                },
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = if (isActive) SafeEmerald else AlabasterInk
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeepWorkStartDialog(
    settings: GuardSettings,
    installedApps: List<InstalledAppInfo>,
    hasDndAccess: Boolean,
    nowMillis: Long,
    onOpenDndSettings: () -> Unit,
    onDismiss: () -> Unit,
    onStart: (untilMillis: Long, categories: Set<DistractingCategory>, strictAllNonExempt: Boolean) -> Unit
) {
    var durationMinutes by remember { mutableIntStateOf(DEFAULT_DEEP_WORK_MINUTES) }
    var selectedCategories by remember(settings.deepWorkCategories) {
        mutableStateOf(
            settings.deepWorkCategories.ifEmpty { GuardDefaults.DEFAULT_DEEP_WORK_CATEGORIES }
        )
    }
    var strictAllNonExempt by remember(settings.deepWorkStrictAllNonExempt) {
        mutableStateOf(settings.deepWorkStrictAllNonExempt)
    }

    val categoryCounts = remember(installedApps, settings.exceptionPackages) {
        DistractingCategory.entries.associateWith { cat ->
            installedApps.count { app ->
                app.category == cat && app.packageName !in settings.exceptionPackages
            }
        }
    }

    val targetUntilMillis = nowMillis + durationMinutes * 60_000L
    val endClockFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val endClockText = endClockFormat.format(Date(targetUntilMillis))
    val canStart = strictAllNonExempt || selectedCategories.isNotEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = CarbonSurface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("deep_work_start_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(SafeEmerald)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(SafeEmerald.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DoNotDisturbOn,
                                contentDescription = null,
                                tint = SafeEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.deep_work_dialog_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = AlabasterInk
                            )
                            Text(
                                text = stringResource(R.string.deep_work_dialog_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = ZincSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // End Time Selector with -15m / +15m precision control
                    Text(
                        text = stringResource(R.string.deep_work_until_section),
                        style = MaterialTheme.typography.labelSmall,
                        color = ZincSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = ObsidianCanvas,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    durationMinutes = (durationMinutes - 15)
                                        .coerceAtLeast(MIN_DEEP_WORK_MINUTES)
                                },
                                enabled = durationMinutes > MIN_DEEP_WORK_MINUTES,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("deep_work_minus_15m")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Remove,
                                    contentDescription = stringResource(R.string.deep_work_decrease_cd),
                                    tint = if (durationMinutes > MIN_DEEP_WORK_MINUTES) AlabasterInk else ZincMuted
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.deep_work_until_clock, endClockText),
                                    style = TextStyle(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp,
                                        letterSpacing = (-0.5).sp,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = AlabasterInk,
                                    modifier = Modifier.testTag("deep_work_target_clock")
                                )
                                Text(
                                    text = formatMinutesHuman(durationMinutes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SafeEmerald
                                )
                            }

                            IconButton(
                                onClick = {
                                    durationMinutes = (durationMinutes + 15)
                                        .coerceAtMost(MAX_DEEP_WORK_MINUTES)
                                },
                                enabled = durationMinutes < MAX_DEEP_WORK_MINUTES,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("deep_work_plus_15m")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.deep_work_increase_cd),
                                    tint = if (durationMinutes < MAX_DEEP_WORK_MINUTES) AlabasterInk else ZincMuted
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GuardDefaults.DEEP_WORK_DURATION_PRESETS.forEach { preset ->
                            val presetMin = (preset.durationMillis / 60_000L).toInt()
                            val selected = durationMinutes == presetMin
                            Surface(
                                onClick = { durationMinutes = presetMin },
                                color = if (selected) AlabasterInk else SlateContainer,
                                shape = CircleShape,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) AlabasterInk else HairlineBorder
                                ),
                                modifier = Modifier.testTag("deep_work_preset_${preset.label}")
                            ) {
                                Text(
                                    text = preset.label,
                                    style = TextStyle(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    color = if (selected) ObsidianCanvas else AlabasterInk,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = HairlineSubtle)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Restrict Distracting Categories
                    Text(
                        text = stringResource(R.string.deep_work_categories_section),
                        style = MaterialTheme.typography.labelSmall,
                        color = ZincSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        DistractingCategory.entries.forEach { category ->
                            val isSelected = strictAllNonExempt || category in selectedCategories
                            val count = categoryCounts[category] ?: 0
                            Surface(
                                onClick = {
                                    if (!strictAllNonExempt) {
                                        selectedCategories = if (category in selectedCategories) {
                                            selectedCategories - category
                                        } else {
                                            selectedCategories + category
                                        }
                                    }
                                },
                                color = if (isSelected) SignalVermilionDim else SlateContainer,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) SignalVermilion.copy(alpha = 0.6f) else HairlineBorder
                                ),
                                modifier = Modifier.testTag("deep_work_cat_${category.storageKey}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) AlabasterInk else ZincSecondary
                                    )
                                    if (count > 0) {
                                        Text(
                                            text = count.toString(),
                                            style = TextStyle(
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.sp
                                            ),
                                            color = if (isSelected) SignalVermilion else ZincMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Strict All Non-Exception Apps Toggle
                    Surface(
                        color = SlateContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, HairlineBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { strictAllNonExempt = !strictAllNonExempt }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.deep_work_strict_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = AlabasterInk
                                )
                                Text(
                                    text = stringResource(
                                        R.string.deep_work_strict_desc,
                                        settings.exceptionPackages.size
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ZincSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = strictAllNonExempt,
                                onCheckedChange = { strictAllNonExempt = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianCanvas,
                                    checkedTrackColor = SafeEmerald,
                                    uncheckedThumbColor = ZincSecondary,
                                    uncheckedTrackColor = ObsidianCanvas
                                ),
                                modifier = Modifier.testTag("deep_work_strict_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // System Notification Silencing Status (Do Not Disturb Access)
                    Surface(
                        onClick = { if (!hasDndAccess) onOpenDndSettings() },
                        enabled = !hasDndAccess,
                        color = if (hasDndAccess) {
                            SafeEmerald.copy(alpha = 0.1f)
                        } else {
                            WarningAmber.copy(alpha = 0.12f)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (hasDndAccess) {
                                SafeEmerald.copy(alpha = 0.4f)
                            } else {
                                WarningAmber.copy(alpha = 0.55f)
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("deep_work_dnd_status")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (hasDndAccess) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.NotificationsOff
                                },
                                contentDescription = null,
                                tint = if (hasDndAccess) SafeEmerald else WarningAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (hasDndAccess) {
                                    stringResource(R.string.deep_work_dnd_ready)
                                } else {
                                    stringResource(R.string.deep_work_dnd_prompt)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasDndAccess) AlabasterInk else WarningAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            onStart(
                                System.currentTimeMillis() + durationMinutes * 60_000L,
                                selectedCategories,
                                strictAllNonExempt
                            )
                        },
                        enabled = canStart,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlabasterInk,
                            contentColor = ObsidianCanvas,
                            disabledContainerColor = SlateContainer,
                            disabledContentColor = ZincMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("deep_work_start_button")
                    ) {
                        Text(
                            text = stringResource(R.string.deep_work_start_cta),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.messages_cancel),
                            color = ZincSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeepWorkActiveDialog(
    settings: GuardSettings,
    hasDndAccess: Boolean,
    nowMillis: Long,
    onExtend15m: () -> Unit,
    onRequestEndEarly: () -> Unit,
    onOpenDndSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val remainingMillis = (settings.deepWorkUntilMillis - nowMillis).coerceAtLeast(0L)
    val endClockFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val endClockText = endClockFormat.format(Date(settings.deepWorkUntilMillis))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = CarbonSurface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, SafeEmerald.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("deep_work_active_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(SafeEmerald)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        color = SafeEmerald.copy(alpha = 0.16f),
                        shape = CircleShape,
                        border = BorderStroke(1.dp, SafeEmerald.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DoNotDisturbOn,
                                contentDescription = null,
                                tint = SafeEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.deep_work_bar_active_title),
                                style = MaterialTheme.typography.labelSmall,
                                color = SafeEmerald
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = GuardDefaults.formatRemainingLock(remainingMillis),
                        style = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp,
                            letterSpacing = (-1).sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = AlabasterInk,
                        modifier = Modifier.testTag("deep_work_active_countdown")
                    )

                    Text(
                        text = stringResource(R.string.deep_work_until_clock, endClockText),
                        style = MaterialTheme.typography.labelMedium,
                        color = ZincSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    val scopeSummary = if (settings.deepWorkStrictAllNonExempt) {
                        stringResource(
                            R.string.deep_work_active_scope_strict,
                            settings.exceptionPackages.size
                        )
                    } else {
                        val names = settings.deepWorkCategories.joinToString(" · ") { it.displayName }
                        stringResource(R.string.deep_work_active_scope_categories, names)
                    }

                    Text(
                        text = scopeSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZincSecondary,
                        textAlign = TextAlign.Center
                    )

                    if (!hasDndAccess) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            onClick = onOpenDndSettings,
                            color = WarningAmber.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.deep_work_dnd_prompt),
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningAmber,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onExtend15m,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlabasterInk),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("deep_work_extend_15m_button")
                        ) {
                            Text(
                                text = stringResource(R.string.deep_work_extend_15m),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlabasterInk,
                                contentColor = ObsidianCanvas
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.deep_work_keep_focusing),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onRequestEndEarly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("deep_work_end_early_button")
                    ) {
                        Text(
                            text = stringResource(R.string.deep_work_end_early),
                            style = MaterialTheme.typography.labelLarge,
                            color = SignalVermilion
                        )
                    }
                }
            }
        }
    }
}

private fun formatMinutesHuman(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m focus"
        hours > 0 -> "${hours}h focus"
        else -> "${minutes}m focus"
    }
}
