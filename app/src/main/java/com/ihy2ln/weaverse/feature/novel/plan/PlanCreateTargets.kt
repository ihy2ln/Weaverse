package com.ihy2ln.weaverse.feature.novel.plan

/**
 * Resolves which chapter / act / scene Plan's + menu should target
 * without touching Room.
 */
object PlanCreateTargets {
    fun chapterIdForNewScene(outline: List<PlanOutlineNode>, selectedSceneId: String?): String? {
        selectedSceneId?.let { selected ->
            outline.forEach { node ->
                node.chapters.forEach { chapter ->
                    if (chapter.chapter.id == selected || chapter.scenes.any { it.id == selected }) {
                        return chapter.chapter.id
                    }
                }
            }
        }
        return outline.asSequence()
            .flatMap { it.chapters.asSequence() }
            .lastOrNull()
            ?.chapter
            ?.id
    }

    fun actIdForNewChapter(outline: List<PlanOutlineNode>, selectedSceneId: String?): String? {
        selectedSceneId?.let { selected ->
            outline.forEach { node ->
                if (node.act.id == selected ||
                    node.chapters.any { chapter ->
                        chapter.chapter.id == selected || chapter.scenes.any { it.id == selected }
                    }
                ) {
                    return node.act.id
                }
            }
        }
        return outline.lastOrNull()?.act?.id
    }

    fun sceneIdForNewBeat(outline: List<PlanOutlineNode>, selectedSceneId: String?): String? {
        val scenes = outline.asSequence()
            .flatMap { it.chapters.asSequence() }
            .flatMap { it.scenes.asSequence() }
        if (selectedSceneId != null && scenes.any { it.id == selectedSceneId }) {
            return selectedSceneId
        }
        return scenes.lastOrNull()?.id
    }
}
