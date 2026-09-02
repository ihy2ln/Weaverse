package com.ihy2ln.weaverse.feature.prompt

import com.ihy2ln.weaverse.feature.shell.AppMode
import com.ihy2ln.weaverse.feature.shell.NovelDestination
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptSurfaceTest {
    @Test
    fun onlyActiveWritingSurfacesUseGlobalOverlay() {
        NovelDestination.entries.filterNot { it == NovelDestination.Write }.forEach { dest ->
            assertTrue(
                !PromptSurface.usesGlobalOverlay(AppMode.Novel, dest.name),
                "Novel ${dest.name} should not show the shared prompt bar",
            )
        }
        assertTrue(PromptSurface.usesGlobalOverlay(AppMode.Novel, NovelDestination.Write.name))
        assertTrue(PromptSurface.usesGlobalOverlay(AppMode.Roleplay, null))
        assertTrue(!PromptSurface.usesGlobalOverlay(AppMode.Notes, null))
    }
}
