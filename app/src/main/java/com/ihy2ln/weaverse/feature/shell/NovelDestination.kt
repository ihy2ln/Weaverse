package com.ihy2ln.weaverse.feature.shell

import kotlinx.serialization.Serializable

/** Novel mode's primary destinations (spec §5): Plan · Write · Chat · Review. */
sealed interface NovelDestination {
    @Serializable
    data object Plan : NovelDestination

    /**
     * [sceneId] lets the rail's Manuscript tab and Plan's scene cards open a
     * specific scene rather than whatever Write's own ViewModel would
     * otherwise default to (Revision 02 §1.4: "clicking any node loads it
     * into the large right-hand content area"). Was a parameterless
     * `data object` (see BUILD_NOTES "Phase 10 deviations/gaps" for why, and
     * why that no longer holds once two different rail features need to
     * open a specific scene) — tab-highlight comparisons that used to be
     * plain `==` now compare by [NovelDestination]'s runtime class instead,
     * so `Write(null)` and `Write("some-id")` still highlight the same tab.
     */
    @Serializable
    data class Write(val sceneId: String? = null) : NovelDestination

    @Serializable
    data object Chat : NovelDestination

    @Serializable
    data object Review : NovelDestination
}
