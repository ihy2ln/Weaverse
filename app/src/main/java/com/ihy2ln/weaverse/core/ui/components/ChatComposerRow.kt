package com.ihy2ln.weaverse.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ihy2ln.weaverse.core.ui.theme.InkSpacing

@Composable
fun ChatComposerRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Message…",
    sendLabel: String = "Send",
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 6,
    leading: @Composable (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (leading != null) {
            leading()
        }
        VoiceToTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(end = InkSpacing.sm),
            placeholder = placeholder,
            enabled = enabled,
            minLines = minLines,
            maxLines = maxLines,
        )
        if (onClear != null) {
            InkModeCapsule(
                label = "Clear Text",
                onClick = onClear,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.padding(end = InkSpacing.sm),
            )
        }
        InkConfirmButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
            label = sendLabel,
            contentDescription = sendLabel,
        )
    }
}
