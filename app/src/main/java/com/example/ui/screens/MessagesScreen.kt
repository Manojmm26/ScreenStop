package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import com.example.ui.theme.ZincMuted
import com.example.ui.theme.ZincSecondary

private sealed interface MessageEditorState {
    data object Closed : MessageEditorState
    data object Adding : MessageEditorState
    data class Editing(val index: Int, val initialText: String) : MessageEditorState
}

@Composable
fun MessagesScreen(
    messages: List<String>,
    onAddMessage: (String) -> Unit,
    onUpdateMessage: (Int, String) -> Unit,
    onDeleteMessage: (Int) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editorState by remember { mutableStateOf<MessageEditorState>(MessageEditorState.Closed) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("messages_list"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.messages_title),
                            style = MaterialTheme.typography.headlineLarge,
                            color = AlabasterInk,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onResetDefaults,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ZincSecondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("messages_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.messages_reset),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.messages_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZincSecondary
                    )
                }
            }

            itemsIndexed(
                items = messages,
                key = { idx, msg -> "${idx}_${msg.hashCode()}" }
            ) { index, message ->
                ReminderMessageCard(
                    index = index,
                    message = message,
                    canDelete = messages.size > 1,
                    onEdit = {
                        editorState = MessageEditorState.Editing(index = index, initialText = message)
                    },
                    onDelete = { onDeleteMessage(index) }
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { editorState = MessageEditorState.Adding },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.messages_add),
                    style = MaterialTheme.typography.labelLarge
                )
            },
            containerColor = AlabasterInk,
            contentColor = ObsidianCanvas,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("messages_add_fab")
        )
    }

    when (val state = editorState) {
        is MessageEditorState.Closed -> Unit
        is MessageEditorState.Adding -> {
            MessageEditorDialog(
                title = stringResource(R.string.messages_dialog_add_title),
                initialText = "",
                onDismiss = { editorState = MessageEditorState.Closed },
                onSave = { text ->
                    onAddMessage(text)
                    editorState = MessageEditorState.Closed
                }
            )
        }

        is MessageEditorState.Editing -> {
            MessageEditorDialog(
                title = stringResource(R.string.messages_dialog_edit_title),
                initialText = state.initialText,
                onDismiss = { editorState = MessageEditorState.Closed },
                onSave = { text ->
                    onUpdateMessage(state.index, text)
                    editorState = MessageEditorState.Closed
                }
            )
        }
    }
}

@Composable
private fun ReminderMessageCard(
    index: Int,
    message: String,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = CarbonSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_card_$index")
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "%02d".format(index + 1),
                style = TextStyle(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = SignalVermilion,
                modifier = Modifier.padding(top = 4.dp, end = 12.dp)
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = AlabasterInk,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 1.dp, end = 6.dp)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("message_edit_$index")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.messages_edit_cd),
                        tint = ZincSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("message_delete_$index")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.messages_delete_cd),
                        tint = if (canDelete) ZincSecondary else ZincMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageEditorDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember(initialText) { mutableStateOf(initialText) }
    val trimmed = draft.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CarbonSurface,
        titleContentColor = AlabasterInk,
        textContentColor = AlabasterInk,
        shape = MaterialTheme.shapes.large,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 220) draft = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.messages_input_hint),
                            color = ZincMuted
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
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
                        .testTag("message_editor_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${draft.length}/220",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZincMuted,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(trimmed) },
                enabled = trimmed.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlabasterInk,
                    contentColor = ObsidianCanvas
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("message_editor_save")
            ) {
                Text(text = stringResource(R.string.messages_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("message_editor_cancel")
            ) {
                Text(
                    text = stringResource(R.string.messages_cancel),
                    color = ZincSecondary
                )
            }
        }
    )
}
