package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.InstalledAppInfo
import com.example.ui.GuardUiState
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SafeEmerald
import com.example.ui.theme.SafeEmeraldDim
import com.example.ui.theme.SlateContainer
import com.example.ui.theme.ZincMuted
import com.example.ui.theme.ZincSecondary

@Composable
fun LauncherHubScreen(
    uiState: GuardUiState,
    onSetDefaultHome: () -> Unit,
    onOpenMinimalistHome: () -> Unit,
    onToggleShowGuardStatus: (Boolean) -> Unit,
    onUnpinFavorite: (String) -> Unit,
    onUnhideApp: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appMap = remember(uiState.installedApps) {
        uiState.installedApps.associateBy { it.packageName }
    }
    val favoriteApps = remember(uiState.settings.launcherFavoritePackages, appMap) {
        uiState.settings.launcherFavoritePackages.mapNotNull { appMap[it] }
    }
    val hiddenApps = remember(uiState.installedApps) {
        uiState.installedApps.filter { it.isHiddenInLauncher }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("launcher_hub_list"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = stringResource(R.string.launcher_hub_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = AlabasterInk
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.launcher_hub_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZincSecondary
                )
            }
        }

        // Status & Launch Hero Card
        item {
            Surface(
                color = CarbonSurface,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(
                    1.dp,
                    if (uiState.isDefaultHomeLauncher) {
                        SafeEmerald.copy(alpha = 0.45f)
                    } else {
                        HairlineBorder
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = if (uiState.isDefaultHomeLauncher) SafeEmeraldDim else SlateContainer,
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            if (uiState.isDefaultHomeLauncher) SafeEmerald.copy(alpha = 0.45f) else HairlineBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isDefaultHomeLauncher) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.Home
                                },
                                contentDescription = null,
                                tint = if (uiState.isDefaultHomeLauncher) SafeEmerald else ZincSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (uiState.isDefaultHomeLauncher) {
                                    stringResource(R.string.launcher_status_default)
                                } else {
                                    stringResource(R.string.launcher_status_not_default)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isDefaultHomeLauncher) SafeEmerald else AlabasterInk
                            )
                        }
                    }

                    Button(
                        onClick = onOpenMinimalistHome,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlabasterInk,
                            contentColor = ObsidianCanvas
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("launcher_open_now_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.launcher_open_now),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (!uiState.isDefaultHomeLauncher) {
                        OutlinedButton(
                            onClick = onSetDefaultHome,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlabasterInk),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("launcher_set_default_button")
                        ) {
                            Text(
                                text = stringResource(R.string.launcher_set_default),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Display preference toggle
        item {
            Surface(
                color = CarbonSurface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            onToggleShowGuardStatus(!uiState.settings.showGuardStatusOnHome)
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = stringResource(R.string.launcher_show_guard_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = AlabasterInk
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.launcher_show_guard_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZincSecondary
                        )
                    }
                    Switch(
                        checked = uiState.settings.showGuardStatusOnHome,
                        onCheckedChange = onToggleShowGuardStatus,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianCanvas,
                            checkedTrackColor = AlabasterInk,
                            checkedBorderColor = AlabasterInk,
                            uncheckedThumbColor = ZincSecondary,
                            uncheckedTrackColor = SlateContainer,
                            uncheckedBorderColor = HairlineBorder
                        ),
                        modifier = Modifier.testTag("launcher_show_guard_switch")
                    )
                }
            }
        }

        // Pinned Favorites Section
        item {
            Text(
                text = stringResource(R.string.launcher_favorites_header, favoriteApps.size),
                style = MaterialTheme.typography.labelMedium,
                color = ZincSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (favoriteApps.isEmpty()) {
            item {
                Surface(
                    color = CarbonSurface,
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, HairlineBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.launcher_favorites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZincMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(favoriteApps, key = { "fav_${it.packageName}" }) { app ->
                LauncherManagedRow(
                    app = app,
                    actionLabel = stringResource(R.string.launcher_unpin),
                    onAction = { onUnpinFavorite(app.packageName) }
                )
            }
        }

        // Hidden Apps Section
        if (hiddenApps.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.launcher_hidden_header, hiddenApps.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = ZincSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(hiddenApps, key = { "hidden_${it.packageName}" }) { app ->
                LauncherManagedRow(
                    app = app,
                    actionLabel = stringResource(R.string.launcher_unhide),
                    onAction = { onUnhideApp(app.packageName) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun LauncherManagedRow(
    app: InstalledAppInfo,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        color = CarbonSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.displayLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = AlabasterInk
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZincMuted
                )
            }
            TextButton(onClick = onAction) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = ZincSecondary
                )
            }
        }
    }
}
