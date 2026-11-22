package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.GuardDefaults
import com.example.data.InstalledAppInfo
import com.example.launcher.RenameAppDialog
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
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
import com.example.util.InstalledAppsLoader
import kotlinx.coroutines.delay

private enum class AppControlFilter(val labelRes: Int, val tag: String) {
    ALL(R.string.apps_filter_all, "apps_filter_all"),
    DETOX(R.string.apps_filter_detox, "apps_filter_detox"),
    PROMPT(R.string.apps_filter_prompt, "apps_filter_prompt"),
    EXEMPT(R.string.apps_filter_exempt, "apps_filter_exempt")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExceptionsScreen(
    apps: List<InstalledAppInfo>,
    isLoading: Boolean,
    searchQuery: String,
    hasUsagePermission: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleException: (String) -> Unit,
    onTogglePrompted: (String) -> Unit,
    onSetDetoxLock: (String, Long, String) -> Unit,
    onClearDetoxLock: (String) -> Unit,
    onToggleLauncherFavorite: (String) -> Unit,
    onToggleLauncherHidden: (String) -> Unit,
    onRenameApp: (String, String) -> Unit,
    onRequestUsagePermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf(AppControlFilter.ALL) }
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }
    var renamingApp by remember { mutableStateOf<InstalledAppInfo?>(null) }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(5_000L)
        }
    }

    val selectedApp = remember(apps, selectedAppPackage) {
        apps.find { it.packageName == selectedAppPackage }
    }

    val filteredApps = remember(apps, searchQuery, activeFilter, nowMillis) {
        val q = searchQuery.trim()
        apps.filter { app ->
            val matchesQuery = q.isEmpty() ||
                app.displayLabel.contains(q, ignoreCase = true) ||
                app.label.contains(q, ignoreCase = true) ||
                app.packageName.contains(q, ignoreCase = true)
            val matchesFilter = when (activeFilter) {
                AppControlFilter.ALL -> true
                AppControlFilter.DETOX -> app.isDetoxLocked(nowMillis)
                AppControlFilter.PROMPT -> app.isPrompted
                AppControlFilter.EXEMPT -> app.isException
            }
            matchesQuery && matchesFilter
        }.sortedWith(
            compareByDescending<InstalledAppInfo> { it.isDetoxLocked(nowMillis) }
                .thenByDescending { it.isPrompted }
                .thenByDescending { it.isException }
                .thenBy { it.displayLabel.lowercase() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.apps_title),
            style = MaterialTheme.typography.headlineLarge,
            color = AlabasterInk
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.apps_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = ZincSecondary
        )

        Spacer(modifier = Modifier.height(14.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.apps_search_hint),
                    color = ZincMuted
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = ZincSecondary
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.testTag("exceptions_clear_search")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.apps_clear_search_cd),
                            tint = ZincSecondary
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AlabasterInk,
                unfocusedBorderColor = HairlineBorder,
                focusedContainerColor = CarbonSurface,
                unfocusedContainerColor = CarbonSurface,
                cursorColor = AlabasterInk,
                focusedTextColor = AlabasterInk,
                unfocusedTextColor = AlabasterInk
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("exceptions_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter chip strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppControlFilter.entries.forEach { filter ->
                val selected = activeFilter == filter
                Surface(
                    onClick = { activeFilter = filter },
                    color = if (selected) AlabasterInk else CarbonSurface,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (selected) AlabasterInk else HairlineBorder),
                    modifier = Modifier.testTag(filter.tag)
                ) {
                    Text(
                        text = stringResource(filter.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) ObsidianCanvas else ZincSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }

        if (!hasUsagePermission) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = CarbonSurface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.apps_perm_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZincSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onRequestUsagePermission,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AlabasterInk),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlabasterInk),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("exceptions_grant_usage_button")
                    ) {
                        Text(
                            text = stringResource(R.string.guard_perm_grant),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = AlabasterInk,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.apps_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZincSecondary
                        )
                    }
                }
            }

            filteredApps.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = null,
                            tint = ZincMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() ->
                                    stringResource(R.string.apps_empty_search, searchQuery.trim())
                                activeFilter != AppControlFilter.ALL ->
                                    stringResource(R.string.apps_empty_filter)
                                else -> stringResource(R.string.apps_empty_apps)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZincSecondary
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("exceptions_app_list"),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredApps,
                        key = { it.packageName }
                    ) { app ->
                        AppControlRow(
                            app = app,
                            nowMillis = nowMillis,
                            onClick = { selectedAppPackage = app.packageName }
                        )
                    }
                }
            }
        }
    }

    if (selectedApp != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedAppPackage = null },
            sheetState = sheetState,
            containerColor = CarbonSurface,
            contentColor = AlabasterInk
        ) {
            AppRuleSheetContent(
                app = selectedApp,
                nowMillis = nowMillis,
                onSetDetoxLock = { millis, label ->
                    onSetDetoxLock(selectedApp.packageName, millis, label)
                },
                onClearDetoxLock = { onClearDetoxLock(selectedApp.packageName) },
                onTogglePrompted = { onTogglePrompted(selectedApp.packageName) },
                onToggleException = { onToggleException(selectedApp.packageName) },
                onToggleFavorite = { onToggleLauncherFavorite(selectedApp.packageName) },
                onToggleHidden = { onToggleLauncherHidden(selectedApp.packageName) },
                onOpenRename = { renamingApp = selectedApp }
            )
        }
    }

    renamingApp?.let { app ->
        RenameAppDialog(
            app = app,
            onDismiss = { renamingApp = null },
            onSave = { newLabel ->
                onRenameApp(app.packageName, newLabel)
                renamingApp = null
            }
        )
    }
}

@Composable
private fun AppControlRow(
    app: InstalledAppInfo,
    nowMillis: Long,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var appIcon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(app.packageName) {
        appIcon = InstalledAppsLoader.loadAppIcon(context, app.packageName)
    }

    val isLocked = app.isDetoxLocked(nowMillis)
    val remainingLock = ((app.blockedUntilMillis ?: 0L) - nowMillis).coerceAtLeast(0L)

    val borderColor = when {
        isLocked -> SignalVermilion.copy(alpha = 0.55f)
        app.isException -> SafeEmerald.copy(alpha = 0.45f)
        app.isPrompted -> AlabasterInk.copy(alpha = 0.35f)
        else -> HairlineBorder
    }

    Surface(
        onClick = onClick,
        color = CarbonSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exception_app_row_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SlateContainer),
                contentAlignment = Alignment.Center
            ) {
                val icon = appIcon
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = app.displayLabel,
                        modifier = Modifier.size(34.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Android,
                        contentDescription = app.displayLabel,
                        tint = ZincSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = app.displayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = AlabasterInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val hasBadges = isLocked || app.isPrompted || app.isException
                if (hasBadges) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isLocked) {
                            RuleMiniBadge(
                                text = stringResource(
                                    R.string.apps_badge_detox,
                                    GuardDefaults.formatRemainingLock(remainingLock)
                                ),
                                background = SignalVermilionDim,
                                contentColor = SignalVermilion
                            )
                        }
                        if (app.isPrompted) {
                            RuleMiniBadge(
                                text = stringResource(R.string.apps_badge_prompt),
                                background = SlateContainer,
                                contentColor = AlabasterInk
                            )
                        }
                        if (app.isException) {
                            RuleMiniBadge(
                                text = stringResource(R.string.apps_badge_exempt),
                                background = SafeEmeraldDim,
                                contentColor = SafeEmerald
                            )
                        }
                    }
                } else {
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = ZincMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ZincSecondary
            )
        }
    }
}

@Composable
private fun RuleMiniBadge(text: String, background: Color, contentColor: Color) {
    Surface(
        color = background,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppRuleSheetContent(
    app: InstalledAppInfo,
    nowMillis: Long,
    onSetDetoxLock: (Long, String) -> Unit,
    onClearDetoxLock: () -> Unit,
    onTogglePrompted: () -> Unit,
    onToggleException: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onOpenRename: () -> Unit
) {
    val isLocked = app.isDetoxLocked(nowMillis)
    val remaining = ((app.blockedUntilMillis ?: 0L) - nowMillis).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 22.dp, end = 22.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = app.displayLabel,
                style = MaterialTheme.typography.headlineMedium,
                color = AlabasterInk
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = ZincMuted
            )
        }

        // Timed Detox Lock section
        Surface(
            color = ObsidianCanvas,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(
                1.dp,
                if (isLocked) SignalVermilion.copy(alpha = 0.55f) else HairlineBorder
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = SignalVermilion,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.apps_rule_sheet_detox_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = AlabasterInk
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isLocked) {
                        stringResource(
                            R.string.apps_rule_sheet_detox_active,
                            GuardDefaults.formatRemainingLock(remaining)
                        )
                    } else {
                        stringResource(R.string.apps_rule_sheet_detox_desc)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isLocked) SignalVermilion else ZincSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GuardDefaults.DETOX_DURATION_PRESETS.forEach { preset ->
                        Surface(
                            onClick = { onSetDetoxLock(preset.durationMillis, preset.label) },
                            color = SlateContainer,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.testTag("detox_preset_${preset.label}")
                        ) {
                            Text(
                                text = preset.label,
                                style = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = AlabasterInk,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                if (isLocked) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onClearDetoxLock,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SignalVermilion.copy(alpha = 0.6f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalVermilion),
                        modifier = Modifier.testTag("detox_clear_lock_button")
                    ) {
                        Text(text = stringResource(R.string.apps_rule_sheet_detox_unlock))
                    }
                }
            }
        }

        // How Long? Prompt toggle
        SheetToggleRow(
            title = stringResource(R.string.apps_rule_sheet_prompt_title),
            subtitle = stringResource(R.string.apps_rule_sheet_prompt_desc),
            checked = app.isPrompted,
            accentColor = AlabasterInk,
            testTag = "sheet_toggle_prompt",
            onToggle = onTogglePrompted
        )

        HorizontalDivider(color = HairlineSubtle)

        // Exception App toggle
        SheetToggleRow(
            title = stringResource(R.string.apps_rule_sheet_exempt_title),
            subtitle = stringResource(R.string.apps_rule_sheet_exempt_desc),
            checked = app.isException,
            accentColor = SafeEmerald,
            testTag = "sheet_toggle_exempt",
            onToggle = onToggleException
        )

        HorizontalDivider(color = HairlineSubtle)

        // Minimalist Launcher Options
        Text(
            text = stringResource(R.string.apps_rule_sheet_launcher_title).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = ZincSecondary
        )

        SheetToggleRow(
            title = stringResource(R.string.apps_rule_sheet_pin_home),
            subtitle = null,
            checked = app.isPinnedFavorite,
            accentColor = AlabasterInk,
            testTag = "sheet_toggle_pin",
            onToggle = onToggleFavorite
        )

        SheetToggleRow(
            title = stringResource(R.string.apps_rule_sheet_hide_drawer),
            subtitle = null,
            checked = app.isHiddenInLauncher,
            accentColor = ZincSecondary,
            testTag = "sheet_toggle_hide",
            onToggle = onToggleHidden
        )

        Surface(
            onClick = onOpenRename,
            color = SlateContainer,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sheet_rename_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = AlabasterInk,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.apps_rule_sheet_rename),
                    style = MaterialTheme.typography.titleSmall,
                    color = AlabasterInk
                )
            }
        }
    }
}

@Composable
private fun SheetToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    accentColor: Color,
    testTag: String,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = AlabasterInk
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZincSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianCanvas,
                checkedTrackColor = accentColor,
                checkedBorderColor = accentColor,
                uncheckedThumbColor = ZincSecondary,
                uncheckedTrackColor = SlateContainer,
                uncheckedBorderColor = HairlineBorder
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}
