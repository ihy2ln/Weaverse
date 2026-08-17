package com.ihy2ln.weaverse.data.db.entity

/**
 * Every constrained-value ("one of X|Y|Z") column in the schema uses a real
 * Kotlin enum rather than a magic string — Room stores these natively as
 * TEXT via `.name`, no [androidx.room.TypeConverter] required.
 */
/** [Character] scopes a codex category/entry to one [RpCharacterEntity] — Roleplay's Codex tab (Phase 11). */
enum class ScopeType { Series, Book, Character }

enum class SceneStatus { Draft, Revised, Final }

enum class CodexLinkSource { Auto, Manual }

enum class SelectiveLogic { AndAny, AndAll, NotAny, NotAll }

enum class LorePosition { BeforeChar, AfterChar, AuthorsNoteTop, AuthorsNoteBottom, AtDepth }

enum class PromptType { SceneBeat, Summarization, TextReplacement, WorkshopChat, Component, Custom }

enum class ChatRole { User, Assistant, System }

enum class RpMessageRole { User, Char, System, Narrator }

/** Spec Revision 02 §9: a per-chat toggle (plus a global default) between bubble-style chat and
 * full-width book-like prose — presentation and prompt-template only, same underlying messages. */
enum class RpDisplayMode { Messenger, DungeonMaster }

enum class ActivationStrategy { Natural, List, Manual }

enum class MediaType { Image, Video, Audio }

enum class MediaOwnerType {
    Scene, CodexEntry, ChatMessage, RpMessage, RpCharacterAvatar, RpPersonaAvatar,
    RpChatBackground, RpExpression, BookCover,
}

/** [OpenRouter] is its own type rather than reusing [OpenAICompatible] pointed at OpenRouter's
 * base URL — Revision 02 §5 wants it "first-class": extra HTTP-Referer/X-Title headers, a richer
 * /models response with pricing, and a /auth/key credits read none of the other OpenAI-wire-format
 * targets (OpenAI itself, DeepSeek, Ollama, ...) need. */
enum class AIProviderType { Anthropic, OpenAICompatible, Gemini, OpenRouter }
