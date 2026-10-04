package com.ihy2ln.weaverse.feature.games

import com.unity3d.player.UnityPlayerActivity

/**
 * The Adams Haven Card Game (Unity as a Library, app/libs/adams-haven-cardgame.aar from
 * S:\AI\Game\Adams Haven Card Game). Runs in its own ":game" process: Unity starts once per process and ends it when
 * the player leaves, which drops them back into Weaverse. The game reads [EXTRA_DATA_DIR] for novel.json.
 */
class CardGameActivity : UnityPlayerActivity() {
    companion object {
        const val EXTRA_DATA_DIR = "cardgame_dir"
    }
}
