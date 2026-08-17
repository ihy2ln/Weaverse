package com.ihy2ln.weaverse.ai.context

import com.ihy2ln.weaverse.data.db.entity.CodexEntryEntity
import com.ihy2ln.weaverse.data.db.entity.CodexEntryLoreEntity
import com.ihy2ln.weaverse.data.db.entity.LorePosition
import com.ihy2ln.weaverse.data.db.entity.SelectiveLogic

/** Maps Room's codex entities into [ContextBuilder]'s decoupled input shape — shared by Novel
 * Chat (Phase 10) and Roleplay Chats (Phase 11), the two real callers of [ContextBuilder.build]. */
fun CodexEntryEntity.toContext(lore: CodexEntryLoreEntity?): CodexEntryContext = CodexEntryContext(
    id = id,
    name = name,
    aliases = aliases,
    bodyText = plainText,
    alwaysInclude = alwaysInclude,
    disabled = disabled,
    trackByNameAlias = lore?.trackByNameAlias ?: true,
    keys = lore?.keys.orEmpty(),
    secondaryKeys = lore?.secondaryKeys.orEmpty(),
    selectiveLogic = lore?.selectiveLogic ?: SelectiveLogic.AndAny,
    insertionOrder = lore?.insertionOrder ?: 100,
    position = lore?.position ?: LorePosition.AfterChar,
    depth = lore?.depth ?: 4,
    probability = lore?.probability ?: 100,
    isConstant = lore?.isConstant ?: false,
    caseSensitive = lore?.caseSensitive ?: false,
    matchWholeWords = lore?.matchWholeWords ?: true,
    recursionAllowed = lore?.recursionAllowed ?: true,
    tokenBudgetWeight = lore?.tokenBudgetWeight ?: 1f,
)
