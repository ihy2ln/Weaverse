package com.ihy2ln.weaverse.feature.roleplay.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Always-visible properties for the active paint/cleanup tool. */
@Composable
internal fun CompactPaintControls(color: Int, size: Float, onColor: (Int) -> Unit,
    onSize: (Float) -> Unit, onCustomColor: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            listOf("White" to 0xFFFFFFFF.toInt(), "Black" to 0xFF000000.toInt(),
                "Red" to 0xFFD24840.toInt(), "Blue" to 0xFF3773BE.toInt(), "Cream" to 0xFFF2E6CC.toInt()).forEach { (label, value) ->
                TextButton(onClick = { onColor(value) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = label }) {
                    Box(Modifier.size(20.dp).background(Color(value)))
                    if (color == value) Text(" ✓", color = Color.White)
                    // Label is exposed to accessibility without making the palette wider.
                }
            }
            TextButton(onClick = onCustomColor) { Text("Custom color", color = Color.White, fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Size ${size.roundToInt()}%", color = Color.White, fontSize = 12.sp)
            Slider(value = size, onValueChange = onSize, valueRange = 1f..90f,
                colors = SliderDefaults.colors(thumbColor = Color(0xFFE8C872), activeTrackColor = Color(0xFFE8C872), inactiveTrackColor = Color(0xFF666666)),
                modifier = Modifier.weight(1f).testTag("inline-brush-size"))
        }
    }
}

/** Direct color selection is independent of the optional image eyedropper. */
@Composable
internal fun CleanupBrushControls(color: Int, size: Float, onColor: (Int) -> Unit,
    onSize: (Float) -> Unit, onEyedropper: () -> Unit) {
    var hex by remember(color) { mutableStateOf("#%06X".format(color and 0xFFFFFF)) }
    val valid = hex.matches(Regex("#[0-9a-fA-F]{6}"))
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text("Cleanup color", color = Color.White, fontSize = 14.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("White" to 0xFFFFFFFF.toInt(), "Black" to 0xFF000000.toInt(),
                "Cream" to 0xFFF2E6CC.toInt(), "Red" to 0xFFD24840.toInt(),
                "Blue" to 0xFF3773BE.toInt(), "Green" to 0xFF36915A.toInt()).forEach { (label, value) ->
                FilterChip(selected = color == value, onClick = { onColor(value) },
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color.White, selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF333333), selectedContainerColor = Color(0xFFE8C872)),
                    leadingIcon = { Box(Modifier.size(16.dp).background(Color(value))) },
                    label = { Text(label, fontSize = 13.sp, lineHeight = 16.sp) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = hex, onValueChange = { hex = it.take(7) }, label = { Text("Hex color", fontSize = 12.sp) },
                singleLine = true, isError = !valid, modifier = Modifier.weight(1f).testTag("cleanup-hex"),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedLabelColor = Color.White, unfocusedLabelColor = Color.LightGray),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, lineHeight = 18.sp))
            TextButton(enabled = valid, onClick = { onColor(android.graphics.Color.parseColor(hex)) }) { Text("Set color", fontSize = 13.sp) }
        }
        Text("Brush size ${size.roundToInt()}% of image width/height (shorter side)", color = Color.White, fontSize = 13.sp, lineHeight = 18.sp)
        Slider(value = size, onValueChange = onSize, valueRange = 1f..90f, modifier = Modifier.fillMaxWidth().testTag("cleanup-size"))
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            listOf(2f, 5f, 10f, 20f).forEach { value ->
                TextButton(onClick = { onSize(value) }) { Text("${value.toInt()}%", fontSize = 13.sp) }
            }
            TextButton(onClick = onEyedropper) { Text("Eyedropper", fontSize = 13.sp) }
        }
    }
}
