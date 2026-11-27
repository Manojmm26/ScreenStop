package com.example.launcher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.AlabasterInk
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCanvas
import com.example.ui.theme.SignalVermilion
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.ZincMuted
import com.example.ui.theme.ZincSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_FOCUS_MANTRA_CHARS = 240

/**
 * Second swipeable Home Screen view: an uncluttered, large-type editorial display
 * for the user's personal reminder of what matters most.
 */
@Composable
fun MinimalistFocusNotePane(
    focusMantra: String,
    nowMillis: Long,
    onEditClick: () -> Unit,
    onGoHomePage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE · MMM dd", Locale.getDefault()) }
    val timeText = timeFormat.format(Date(nowMillis))
    val dateText = dateFormat.format(Date(nowMillis)).uppercase(Locale.getDefault())

    // Scale display typography gracefully based on note length so both short mantras
    // and multi-line priorities feel commanding and balanced.
    val (heroFontSize, heroLineHeight) = remember(focusMantra) {
        val length = focusMantra.length
        val lines = focusMantra.count { it == '\n' } + 1
        when {
            length <= 55 && lines <= 2 -> 38.sp to 46.sp
            length <= 120 && lines <= 4 -> 32.sp to 41.sp
            else -> 26.sp to 35.sp
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 52.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Editorial Eyebrow + Quiet Clock Whisper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(SignalVermilion)
                )
                Text(
                    text = stringResource(R.string.launcher_focus_eyebrow),
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 1.6.sp
                    ),
                    color = ZincSecondary
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeText,
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = ZincSecondary
                )
                Text(
                    text = dateText,
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = ZincMuted
                )
            }
        }

        // Center Hero Custom Focus Text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(onClick = onEditClick)
                    .padding(vertical = 8.dp)
                    .testTag("launcher_focus_mantra_text")
            ) {
                Text(
                    text = focusMantra,
                    style = TextStyle(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = heroFontSize,
                        lineHeight = heroLineHeight,
                        letterSpacing = (-0.9).sp
                    ),
                    color = AlabasterInk
                )
            }
        }

        // Bottom Quiet Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onGoHomePage,
                color = ObsidianCanvas,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("launcher_focus_back_home")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = ZincMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.launcher_focus_swipe_back_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = ZincMuted
                    )
                }
            }

            Surface(
                onClick = onEditClick,
                color = CarbonSurface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier.testTag("launcher_focus_edit_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = AlabasterInk,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(R.string.launcher_focus_edit_button),
                        style = MaterialTheme.typography.labelLarge,
                        color = AlabasterInk
                    )
                }
            }
        }
    }
}

/**
 * Minimal architectural page indicator anchored at the bottom of the 2-page Home pager.
 */
@Composable
fun HomePagerIndicator(
    currentPage: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val homeCd = stringResource(R.string.launcher_page_home_cd)
    val focusCd = stringResource(R.string.launcher_page_focus_cd)

    Row(
        modifier = modifier.testTag("launcher_home_pager_indicator"),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (page in 0..1) {
            val isSelected = currentPage == page
            val pillWidth by animateDpAsState(
                targetValue = if (isSelected) 22.dp else 6.dp,
                label = "pagerIndicatorWidth"
            )
            val pillColor by animateColorAsState(
                targetValue = if (isSelected) AlabasterInk else ZincMuted.copy(alpha = 0.45f),
                label = "pagerIndicatorColor"
            )
            val cd = if (page == 0) homeCd else focusCd

            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 36.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Tab) { onSelectPage(page) }
                    .semantics { contentDescription = cd }
                    .testTag("launcher_pager_dot_$page"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(pillColor)
                )
            }
        }
    }
}

@Composable
fun EditFocusMantraDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(initialText) { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = AlabasterInk,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = stringResource(R.string.launcher_focus_edit_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= MAX_FOCUS_MANTRA_CHARS) draft = it },
                    minLines = 3,
                    maxLines = 6,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.launcher_focus_edit_hint),
                            color = ZincMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlabasterInk,
                        unfocusedBorderColor = HairlineBorder,
                        focusedContainerColor = ObsidianCanvas,
                        unfocusedContainerColor = ObsidianCanvas,
                        cursorColor = AlabasterInk,
                        focusedTextColor = AlabasterInk,
                        unfocusedTextColor = AlabasterInk
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("launcher_focus_edit_input")
                )
                Text(
                    text = stringResource(R.string.launcher_focus_char_count, draft.length),
                    style = TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = ZincMuted,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(draft) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlabasterInk,
                    contentColor = ObsidianCanvas
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("launcher_focus_save_button")
            ) {
                Text(stringResource(R.string.messages_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.messages_cancel), color = ZincSecondary)
            }
        }
    )
}
