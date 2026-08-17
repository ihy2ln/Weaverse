package com.ihy2ln.weaverse.core.text

import java.util.UUID

/**
 * Merge the media block at [index] with an adjacent [MediaBlock] or [MediaStackBlock]
 * into a single [MediaStackBlock]. Prefers the neighbor after [index], then before.
 * Returns null when no adjacent media exists.
 */
fun List<Block>.stackMediaWithAdjacent(index: Int): List<Block>? {
    if (index !in indices) return null
    val neighbor = when {
        index + 1 <= lastIndex && mediaIdsAt(index + 1) != null -> index + 1
        index - 1 >= 0 && mediaIdsAt(index - 1) != null -> index - 1
        else -> return null
    }
    // Preserve document order: earlier block is the stack base.
    val first = minOf(index, neighbor)
    val second = maxOf(index, neighbor)
    return stackMediaOnto(fromIndex = second, ontoIndex = first)
}

/**
 * Drag-onto stack: merge media at [fromIndex] onto media at [ontoIndex].
 * Resulting stack keeps the target's grid cell when set.
 */
fun List<Block>.stackMediaOnto(fromIndex: Int, ontoIndex: Int): List<Block>? {
    if (fromIndex !in indices || ontoIndex !in indices || fromIndex == ontoIndex) return null
    val fromIds = mediaIdsAt(fromIndex) ?: return null
    val ontoIds = mediaIdsAt(ontoIndex) ?: return null
    val onto = get(ontoIndex)
    val orderedIds = (ontoIds + fromIds).distinct()
    val gridCol = onto.gridColOrUnset()
    val gridRow = onto.gridRowOrUnset()
    val stack = MediaStackBlock(
        id = "stack-${UUID.randomUUID()}",
        mediaIds = orderedIds,
        currentIndex = 0,
        gridCol = gridCol,
        gridRow = gridRow,
    )
    val first = minOf(fromIndex, ontoIndex)
    val second = maxOf(fromIndex, ontoIndex)
    return toMutableList().apply {
        removeAt(second)
        removeAt(first)
        add(first, stack)
    }
}

fun List<Block>.mediaIdsAt(index: Int): List<String>? = when (val block = getOrNull(index)) {
    is MediaBlock -> listOf(block.mediaId)
    is MediaStackBlock -> block.mediaIds
    is MediaGridBlock -> block.mediaIds
    else -> null
}

fun List<Block>.isMediaBlockAt(index: Int): Boolean = mediaIdsAt(index) != null
