package com.ihy2ln.weaverse.core.story

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Which start a template belongs to. */
enum class StartTemplateKind(val key: String, val label: String) {
    Novel("novel", "Novel"),
    Rpg("rpg", "RPG");

    companion object {
        fun of(key: String): StartTemplateKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One saved start, as a file of its own: [payload] is the start exactly as the novel or campaign
 * start saves it (NovelStartProgress / RpgStartupState JSON). The same file is what Export writes
 * and Import reads, so a template moves between phones and between app builds.
 */
@Serializable
data class StartTemplate(
    val format: String = FORMAT,
    val version: Int = 1,
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val name: String,
    val savedAt: Long = System.currentTimeMillis(),
    val payload: String,
) {
    companion object {
        const val FORMAT = "weaverse-start-template"
        const val FILE_SUFFIX = ".weaverse-template.json"
    }
}

private val templateJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

fun encodeStartTemplate(template: StartTemplate): String = templateJson.encodeToString(StartTemplate.serializer(), template)

/** A template file's contents, or null when it isn't one (wrong format, missing start). */
fun decodeStartTemplate(text: String): StartTemplate? = runCatching {
    templateJson.decodeFromString(StartTemplate.serializer(), text)
}.getOrNull()?.takeIf { it.format == StartTemplate.FORMAT && StartTemplateKind.of(it.kind) != null && it.payload.isNotBlank() }

/** The file name a template is saved under: readable, and unique by its id. */
fun startTemplateFileName(template: StartTemplate): String {
    val slug = template.name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifBlank { "template" }
    return "${template.kind}-$slug-${template.id.take(8)}${StartTemplate.FILE_SUFFIX}"
}

/** Templates from several places, one per id (the newest save wins), newest first. */
fun mergeStartTemplates(vararg sources: List<StartTemplate>): List<StartTemplate> =
    sources.toList().flatten().groupBy { it.id }.values.map { copies -> copies.maxBy { it.savedAt } }
        .sortedByDescending { it.savedAt }

/**
 * Every saved Novel and RPG start template. They live in the app's own storage and, once the
 * writer picks a templates folder (e.g. Documents/Weaverse/Templates), as files there too: both
 * the release and the debug build can read that folder, so a template survives reinstalling or
 * switching builds. Templates found in the folder are copied into the app on every listing.
 */
@Singleton
class StartTemplateLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val slots: StartSlotStore,
) {
    private val dir = File(context.filesDir, "start-templates")
    private val folderFile = File(dir, "folder.txt")

    private val _folder = MutableStateFlow(readFolder())
    /** The shared templates folder (a document tree), or null when none is chosen. */
    val folder: StateFlow<Uri?> = _folder

    suspend fun list(kind: StartTemplateKind): List<StartTemplate> = withContext(Dispatchers.IO) {
        migrateLegacySlots()
        val local = localTemplates()
        val shared = folderTemplates()
        // Anything only in the shared folder (saved by the other build) comes in.
        shared.filter { s -> local.none { it.id == s.id && it.savedAt >= s.savedAt } }.forEach(::writeLocal)
        mergeStartTemplates(local, shared).filter { it.kind == kind.key }
    }

    suspend fun get(id: String): StartTemplate? = withContext(Dispatchers.IO) {
        localTemplates().firstOrNull { it.id == id } ?: folderTemplates().firstOrNull { it.id == id }
    }

    suspend fun save(kind: StartTemplateKind, name: String, payload: String): StartTemplate = withContext(Dispatchers.IO) {
        val template = StartTemplate(kind = kind.key, name = name.trim().ifBlank { "${kind.label} template" }.take(80), payload = payload)
        put(template)
        template
    }

    suspend fun rename(id: String, name: String) = withContext(Dispatchers.IO) {
        val current = get(id) ?: return@withContext
        delete(id)
        put(current.copy(name = name.trim().ifBlank { current.name }.take(80), savedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        localFiles().filter { decodeStartTemplate(it.readText())?.id == id }.forEach { it.delete() }
        folderDocuments().filter { (_, template) -> template.id == id }.forEach { (uri, _) ->
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
        }
    }

    /** Reads a template file the writer picked; null when it isn't a Weaverse template. */
    suspend fun import(uri: Uri): StartTemplate? = withContext(Dispatchers.IO) {
        val text = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            ?: return@withContext null
        val template = decodeStartTemplate(text) ?: return@withContext null
        put(template.copy(savedAt = System.currentTimeMillis()))
        template
    }

    /** Writes [id] to a file the writer created with the system's Save dialog. */
    suspend fun export(id: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val template = get(id) ?: return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encodeStartTemplate(template).toByteArray()) } != null
        }.getOrDefault(false)
    }

    /** Chooses the shared folder; every template already saved is copied into it. */
    suspend fun chooseFolder(uri: Uri) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        dir.mkdirs()
        folderFile.writeText(uri.toString())
        _folder.value = uri
        val inFolder = folderTemplates().map { it.id }.toSet()
        localTemplates().filter { it.id !in inFolder }.forEach(::writeFolder)
    }

    fun folderLabel(uri: Uri?): String = uri?.let {
        runCatching { DocumentsContract.getTreeDocumentId(it) }.getOrNull()?.substringAfter(':')?.ifBlank { "Device storage" }
    } ?: "Not chosen"

    // ------------------------------------------------------------ storage

    private fun put(template: StartTemplate) {
        writeLocal(template)
        writeFolder(template)
    }

    private fun writeLocal(template: StartTemplate) {
        dir.mkdirs()
        val target = File(dir, startTemplateFileName(template))
        val temp = File(dir, target.name + ".tmp")
        temp.writeText(encodeStartTemplate(template))
        if (!temp.renameTo(target)) { target.delete(); temp.renameTo(target) }
    }

    private fun writeFolder(template: StartTemplate) {
        val tree = _folder.value ?: return
        runCatching {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val name = startTemplateFileName(template)
            // Replace an older copy of the same template.
            folderDocuments().filter { (_, t) -> t.id == template.id }.forEach { (uri, _) ->
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
            val created = DocumentsContract.createDocument(context.contentResolver, parent, "application/json", name) ?: return
            context.contentResolver.openOutputStream(created, "wt")?.use { it.write(encodeStartTemplate(template).toByteArray()) }
        }
    }

    private fun localFiles(): List<File> =
        dir.listFiles { file -> file.name.endsWith(StartTemplate.FILE_SUFFIX) }?.toList().orEmpty()

    private fun localTemplates(): List<StartTemplate> = localFiles().mapNotNull { runCatching { decodeStartTemplate(it.readText()) }.getOrNull() }

    private fun folderTemplates(): List<StartTemplate> = folderDocuments().map { it.second }

    /** The template files in the shared folder, with their document URIs. */
    private fun folderDocuments(): List<Pair<Uri, StartTemplate>> {
        val tree = _folder.value ?: return emptyList()
        return runCatching {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val found = mutableListOf<Pair<Uri, StartTemplate>>()
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1).orEmpty()
                    if (!name.endsWith(StartTemplate.FILE_SUFFIX)) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0))
                    val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: continue
                    decodeStartTemplate(text)?.let { found += uri to it }
                }
            }
            found
        }.getOrDefault(emptyList())
    }

    private fun readFolder(): Uri? = runCatching { folderFile.readText().trim().takeIf { it.isNotBlank() }?.let(Uri::parse) }.getOrNull()

    /** The one-per-mode starting templates from before 1.4.99 become named templates once. */
    private suspend fun migrateLegacySlots() {
        listOf(StartTemplateKind.Novel to StartSlotStore.NOVEL_TEMPLATE, StartTemplateKind.Rpg to StartSlotStore.RPG_TEMPLATE)
            .forEach { (kind, key) ->
                val legacy = slots.read(key) ?: return@forEach
                put(StartTemplate(kind = kind.key, name = "My ${kind.label} template", payload = legacy))
                slots.delete(key)
            }
    }
}
