package io.github.wingedpanther.cleanmytv.home.customization.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import io.github.wingedpanther.cleanmytv.home.tr
import kotlinx.coroutines.delay

/**
 * Typing on the TV: a name for a row or folder, the greeting, a button's label, or a PIN with [digits].
 * The TV's own keyboard opens with it. [note] says what's wrong with the last try, if anything.
 */
@Composable
fun TextPrompt(
    title: String,
    initial: String = "",
    hint: String = "",
    maxLength: Int = 24,
    digits: Boolean = false,
    note: String = "",
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var focused by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val input = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { input.requestFocus(); keyboard?.show() }
    }
    val done = { keyboard?.hide(); onDone(text.trim()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xBB000000)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(480.dp).clip(RoundedCornerShape(16.dp)).background(DIALOG_BG).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (hint.isNotEmpty()) Text(hint, color = MUTED_COLOR, fontSize = 13.sp)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                        .background(if (focused) Color.White else Color(0x22FFFFFF))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { v -> text = (if (digits) v.filter { it.isDigit() } else v).take(maxLength) },
                        textStyle = TextStyle(color = if (focused) CUSTOMIZATION_BG else Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(if (focused) CUSTOMIZATION_BG else PRIMARY_BUTTON_COLOR),
                        singleLine = true,
                        visualTransformation = if (digits) PasswordVisualTransformation() else VisualTransformation.None,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (digits) KeyboardType.NumberPassword else KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { done() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(input).onFocusChanged {
                            focused = it.isFocused
                            if (it.isFocused) keyboard?.show()
                        }
                    )
                }
                if (note.isNotEmpty()) Text(note, color = Color(0xFFFFB23C), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvPrimaryButton(text = tr("Save"), onClick = { done() })
                    TvSecondaryButton(text = tr("Cancel"), onClick = onDismiss)
                }
            }
        }
    }
}
