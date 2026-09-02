package com.ihy2ln.weaverse.ai.prompt

/**
 * Resolves the small templating vocabulary used by Weaverse's prompt library:
 * `{! comments !}`, `{include("Weaverse/X")}`, `{#if EXPR}...{#endif}`,
 * a handful of built-in functions, and plain `{token}` substitution.
 *
 * This is a purpose-built resolver for that fixed vocabulary, not a general
 * template language — anything outside it resolves to empty string rather
 * than failing, so an unrecognized token degrades gracefully instead of
 * leaking `{like.this}` into a prompt sent to the model.
 */
object PromptTemplateEngine {

    fun render(template: String, ctx: PromptRenderContext, includeDepth: Int = 0): String {
        var text = stripComments(template)
        text = PromptAddOns.resolveBlocks(text)
        text = resolveIncludes(text, ctx, includeDepth)
        text = resolveConditionals(text, ctx)
        text = resolveFunctionCalls(text, ctx)
        text = resolveTokens(text, ctx)
        text = text.replace("{genre}", PromptAddOns.genreLabel)
        return text.trim()
    }

    private fun stripComments(text: String): String =
        Regex("\\{!.*?!\\}", RegexOption.DOT_MATCHES_ALL).replace(text, "")

    private fun resolveIncludes(text: String, ctx: PromptRenderContext, depth: Int): String {
        if (depth > 5) return text
        val pattern = Regex("""\{include\(\s*"([^"]+)"\s*\)\}""")
        return replaceAllMatches(text, pattern) { match ->
            val name = match.groupValues[1].substringAfter("/")
            val body = ctx.componentBlocks[name].orEmpty()
            render(body, ctx, depth + 1)
        }
    }

    /**
     * `{#if}/{#endif}` blocks can nest (e.g. a POV block wrapping a
     * conditional character clause), so this scans for the *matching*
     * `{#endif}` by tracking nesting depth rather than a single non-greedy
     * regex — a naive regex would close an outer `{#if}` at the nearest
     * `{#endif}`, which is wrong once any block contains another.
     */
    private fun resolveConditionals(text: String, ctx: PromptRenderContext): String {
        val openTag = Regex("""\{#if\s+([^}]*?)\}""")
        val endTag = "{#endif}"
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val open = openTag.find(text, i)
            if (open == null) {
                sb.append(text, i, text.length)
                break
            }
            sb.append(text, i, open.range.first)
            var depth = 1
            var cursor = open.range.last + 1
            var bodyEnd = -1
            while (cursor <= text.length) {
                val nextEnd = text.indexOf(endTag, cursor)
                if (nextEnd == -1) break
                val nextOpen = openTag.find(text, cursor)
                if (nextOpen != null && nextOpen.range.first < nextEnd) {
                    depth++
                    cursor = nextOpen.range.last + 1
                } else {
                    depth--
                    if (depth == 0) {
                        bodyEnd = nextEnd
                        break
                    }
                    cursor = nextEnd + endTag.length
                }
            }
            if (bodyEnd == -1) {
                // No matching {#endif} — emit the tag literally rather than eating the rest of the message.
                sb.append(open.value)
                i = open.range.last + 1
                continue
            }
            val body = text.substring(open.range.last + 1, bodyEnd)
            if (evalCondition(open.groupValues[1], ctx)) {
                sb.append(resolveConditionals(body, ctx))
            }
            i = bodyEnd + endTag.length
        }
        return sb.toString()
    }

    private fun resolveFunctionCalls(text: String, ctx: PromptRenderContext): String {
        val pattern = Regex("""\{(\w+)\(([\s\S]*?)\)\}""")
        return replaceAllMatches(text, pattern) { match ->
            evalFunction(match.groupValues[1], match.groupValues[2], ctx) ?: match.value
        }
    }

    /** Single non-overlapping-match pass — safe against unknown/unresolved patterns (no infinite loops). */
    private fun replaceAllMatches(text: String, pattern: Regex, resolve: (MatchResult) -> String): String {
        val sb = StringBuilder()
        var lastEnd = 0
        for (match in pattern.findAll(text)) {
            sb.append(text, lastEnd, match.range.first)
            sb.append(resolve(match))
            lastEnd = match.range.last + 1
        }
        sb.append(text, lastEnd, text.length)
        return sb.toString()
    }

    private fun evalFunction(name: String, argsRaw: String, ctx: PromptRenderContext): String? {
        val args = splitTopLevelArgs(argsRaw)
        return when (name) {
            "input" -> {
                val label = args.getOrNull(0)?.trim()?.removeSurrounding("\"")
                if (label == "Words") ctx.outputWords.toString() else ""
            }
            "lastWords" -> {
                val value = resolveValueToken(args.getOrNull(0)?.trim().orEmpty(), ctx)
                val n = args.getOrNull(1)?.trim()?.toIntOrNull() ?: return ""
                value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.takeLast(n).joinToString(" ")
            }
            "wordsBefore" -> {
                val n = args.getOrNull(0)?.trim()?.toIntOrNull() ?: return ""
                ctx.textBefore.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.takeLast(n).joinToString(" ")
            }
            "wordsAfter" -> {
                val n = args.getOrNull(0)?.trim()?.toIntOrNull() ?: return ""
                ctx.textAfter.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(n).joinToString(" ")
            }
            "removeWhitespace" -> {
                val value = resolveValueToken(args.getOrNull(0)?.trim().orEmpty(), ctx)
                value.replace(Regex("\\s+"), " ").trim()
            }
            "ifs" -> {
                val cond = args.getOrNull(0)?.trim().orEmpty()
                val thenText = args.getOrNull(1)?.trim()?.removeSurrounding("\"").orEmpty()
                if (evalCondition(cond, ctx)) render(thenText, ctx) else ""
            }
            else -> null
        }
    }

    private fun resolveTokens(text: String, ctx: PromptRenderContext): String {
        val tokens = mapOf(
            "novel.tense" to ctx.novelTense,
            "novel.language" to ctx.novelLanguage,
            "book.title" to ctx.novelTitle,
            "novel.title" to ctx.novelTitle,
            "series.title" to ctx.seriesTitle,
            "series.description" to ctx.seriesDescription,
            "date.today" to ctx.dateToday,
            "today" to ctx.dateToday,
            "pov.type" to ctx.povType,
            "pov.character" to ctx.povCharacter,
            "pov" to ctx.pov,
            "textBefore" to ctx.textBefore,
            "textAfter" to ctx.textAfter,
            "storySoFar" to ctx.storySoFar,
            "message" to ctx.message,
        )
        var result = text
        tokens.forEach { (key, value) -> result = result.replace("{$key}", value) }
        // Any leftover {token} we don't recognize resolves to empty rather than leaking template syntax.
        result = Regex("""\{[a-zA-Z][\w.]*\}""").replace(result, "")
        return result
    }

    /** Resolves a value-producing expression used inside `is` comparisons and function args. */
    private fun resolveValueToken(expr: String, ctx: PromptRenderContext): String = when {
        expr == "pov.character" -> ctx.povCharacter
        expr == "pov.type" -> ctx.povType
        expr == "pov" -> ctx.pov
        expr == "scene.fullText" -> ctx.sceneFullTextCurrent
        expr == "scene.previous" -> ctx.scenePreviousFullText
        expr == "pov.character(scene.previous)" -> ctx.scenePreviousPovCharacter
        expr == "scene.fullText(scene.previous)" -> ctx.scenePreviousFullText
        expr == "textBefore" -> ctx.textBefore
        expr == "textAfter" -> ctx.textAfter
        expr == "storySoFar" -> ctx.storySoFar
        expr == "message" -> ctx.message
        expr.startsWith("\"") && expr.endsWith("\"") -> expr.removeSurrounding("\"")
        else -> render(expr, ctx)
    }

    private fun evalCondition(exprRaw: String, ctx: PromptRenderContext): Boolean {
        val expr = exprRaw.trim()
        matchFunctionCall(expr, "and")?.let { args -> return args.all { evalCondition(it, ctx) } }
        matchFunctionCall(expr, "either")?.let { args -> return args.any { evalCondition(it, ctx) } }
        if (" is " in expr) {
            val idx = expr.indexOf(" is ")
            val left = resolveValueToken(expr.substring(0, idx).trim(), ctx)
            val right = resolveValueToken(expr.substring(idx + 4).trim(), ctx)
            return left.isNotBlank() && left == right
        }
        return isTruthy(expr, ctx)
    }

    private fun isTruthy(token: String, ctx: PromptRenderContext): Boolean = when (token) {
        "storySoFar" -> ctx.storySoFar.isNotBlank()
        "pov" -> ctx.pov.isNotBlank()
        "pov.type" -> ctx.povType.isNotBlank()
        "pov.character" -> ctx.povCharacter.isNotBlank()
        "textBefore" -> ctx.hasTextBefore
        "textAfter" -> ctx.hasTextAfter
        "hasTextBefore" -> ctx.hasTextBefore
        "hasTextAfter" -> ctx.hasTextAfter
        "isStartOfText" -> ctx.isStartOfText
        "message" -> ctx.message.isNotBlank()
        "true" -> true
        "false" -> false
        else -> false
    }

    /** If [expr] is `name(args)`, returns the top-level-split args; else null. */
    private fun matchFunctionCall(expr: String, name: String): List<String>? {
        if (!expr.startsWith("$name(") || !expr.endsWith(")")) return null
        val inner = expr.substring(name.length + 1, expr.length - 1)
        return splitTopLevelArgs(inner)
    }

    private fun splitTopLevelArgs(raw: String): List<String> {
        val args = mutableListOf<String>()
        var depth = 0
        var current = StringBuilder()
        for (ch in raw) {
            when (ch) {
                '(' -> { depth++; current.append(ch) }
                ')' -> { depth--; current.append(ch) }
                ',' -> if (depth == 0) {
                    args += current.toString().trim()
                    current = StringBuilder()
                } else {
                    current.append(ch)
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotBlank()) args += current.toString().trim()
        return args.filter { it.isNotBlank() }
    }
}
