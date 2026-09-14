package com.ihy2ln.weaverse.data.repo

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strictly increasing timestamps for scene row writes, shared by every writer
 * (write editor, AI prompt insert, plan editor). Wall-clock reads can repeat
 * within the same millisecond, which would make a later write look older than
 * an earlier one; the +1 bump keeps ordering monotonic so Room echoes can be
 * told apart from stale snapshots racing back to observers.
 */
@Singleton
class SceneWriteStamps @Inject constructor() {
    private var last = 0L

    fun next(): Long = synchronized(this) {
        last = maxOf(System.currentTimeMillis(), last + 1)
        last
    }
}
