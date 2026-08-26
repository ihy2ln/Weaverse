package com.ihy2ln.weaverse.core.ui.theme

import androidx.compose.ui.unit.dp

object InkSpacing {
    val hairline = 1.dp
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val railMin = 240.dp
    val railDefault = 320.dp
    val railMax = 420.dp
    /** Portrait: keep min ≤ max (S25 ~360dp wide). Never coerceIn(railMin, 220.dp). */
    val railPortraitMin = 140.dp
    val railPortraitMax = 200.dp
    val headerHeight = 64.dp
    val entryRowHeight = 64.dp
    val iconTile = 36.dp
    val touchTarget = 48.dp
    /**
     * Classic's corner rounding. UI code should call `inkRadiusSm()` / `inkRadiusMd()`
     * instead so the active [AppearanceProfile]'s shape language applies.
     */
    val radiusSm = 6.dp
    val radiusMd = 8.dp
}
