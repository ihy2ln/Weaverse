package com.ihy2ln.weaverse.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner radii for the Revision 02 flat chrome (spec §1.1): 6dp on inputs/buttons, 8dp on
 * cards, full-round on segmented pills and chips. */
object CornerRadius {
    val input: Dp = 6.dp
    val card: Dp = 8.dp
    val pill: Dp = 50.dp
}
