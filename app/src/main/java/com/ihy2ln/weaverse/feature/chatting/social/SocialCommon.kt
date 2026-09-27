package com.ihy2ln.weaverse.feature.chatting.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ihy2ln.weaverse.core.ui.theme.inkTokens
import com.ihy2ln.weaverse.feature.roleplay.friends.CharacterAvatar

/** True when the app is in a dark theme, so the feeds pick their dark palettes. */
@Composable
fun socialDark(): Boolean = inkTokens().background.luminance() < 0.5f

/** Hashtags, @handles and links in the platform's link colour. */
fun linkified(text: String, link: Color, bold: Boolean = false): AnnotatedString = buildAnnotatedString {
    val token = Regex("(#[A-Za-z][A-Za-z0-9_]*)|(@[A-Za-z0-9_]+)|(https?://\\S+)")
    var cursor = 0
    token.findAll(text).forEach { m ->
        if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
        withStyle(SpanStyle(color = link, fontWeight = if (bold) FontWeight.SemiBold else null)) { append(m.value) }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

/** A round avatar with an optional ring, as both feeds draw them. */
@Composable
fun SocialAvatar(name: String, colorHex: String, size: Dp, ring: Color? = null, modifier: Modifier = Modifier) {
    if (ring == null) {
        CharacterAvatar(name = name, colorHex = colorHex, size = size, modifier = modifier)
    } else {
        Box(
            modifier
                .size(size + 6.dp)
                .clip(CircleShape)
                .border(3.dp, ring, CircleShape)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            CharacterAvatar(name = name, colorHex = colorHex, size = size)
        }
    }
}
