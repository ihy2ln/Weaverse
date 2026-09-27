package com.ihy2ln.weaverse.feature.chatting.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.ihy2ln.weaverse.core.media.MediaRepository
import com.ihy2ln.weaverse.data.db.WeaverseDatabase
import com.ihy2ln.weaverse.feature.chatting.social.mediaIdsOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Reservations cover concurrent posts; historical fingerprints cover app restarts. */
@Singleton
class SocialMediaReuse @Inject constructor(
    private val db: WeaverseDatabase,
    private val repository: MediaRepository,
) {
    private val mutex = Mutex()
    private val reservedUrls = mutableSetOf<String>()
    private val reservedCreators = mutableMapOf<String, Int>()
    private val claimedChecksums = mutableSetOf<String>()
    private val claimedHashes = mutableSetOf<Long>()
    private val known = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Long?>>()

    private fun canonical(url: String): String = url.substringBefore('#').substringBefore('?').trimEnd('/').lowercase()
    private fun creator(picture: WebPicture): String = picture.credit.takeIf { credit ->
        credit.isNotBlank() && credit !in setOf("Wikimedia Commons", "Powered by GIPHY", "Via Tenor", "Civitai creator") &&
            !credit.startsWith("CC ")
    }.orEmpty()

    suspend fun reserve(picture: WebPicture): Boolean = withContext(Dispatchers.IO) {
        val posts = db.socialDao().observeAllPosts().first().filter { it.parentId == null && it.authorCharacterId != null }.take(150)
        val attached = posts.flatMap { mediaIdsOf(it.mediaId) }.distinct().mapNotNull { repository.getById(it) }
        val used = (posts.flatMap { listOf(it.sourceUrl, it.sourceMediaUrl) } + attached.flatMap { media ->
            listOf(media.sourceUrl) + media.tags.split(", ").filter { it.startsWith("origin_url=") }
                .map { it.removePrefix("origin_url=") }
        }).filter { it.isNotBlank() }.map(::canonical).toSet()
        val creator = creator(picture)
        val creatorCount = if (creator.isBlank()) 0 else attached.count { it.sourceCredit == creator }
        val urls = listOf(picture.fullUrl, picture.pageUrl).filter { it.isNotBlank() }.map(::canonical)
        mutex.withLock {
            if (urls.any { it in used || it in reservedUrls } ||
                (creator.isNotBlank() && creatorCount + (reservedCreators[creator] ?: 0) >= 3)) false
            else {
                reservedUrls.addAll(urls)
                if (creator.isNotBlank()) reservedCreators[creator] = (reservedCreators[creator] ?: 0) + 1
                true
            }
        }
    }

    suspend fun accept(bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val checksum = sha(bytes)
        val hash = imageHash(bytes)
        val recentIds = db.socialDao().observeAllPosts().first().asSequence()
            .filter { it.authorCharacterId != null }.take(150).flatMap { mediaIdsOf(it.mediaId).asSequence() }.distinct().toList()
        val previous = recentIds.mapNotNull { id ->
            known[id] ?: repository.getById(id)?.let { media ->
                val file = repository.resolveFile(media)
                if (!file.isFile) null else {
                    val oldBytes = file.readBytes()
                    val pair = (media.checksum.ifBlank { sha(oldBytes) }) to imageHash(oldBytes)
                    known[id] = pair
                    pair
                }
            }
        }
        mutex.withLock {
            if (checksum in claimedChecksums || previous.any { it.first == checksum } ||
                (hash != null && (claimedHashes.any { near(it, hash) } || previous.any { it.second?.let { old -> near(old, hash) } == true }))) false
            else {
                claimedChecksums += checksum
                if (hash != null) claimedHashes += hash
                true
            }
        }
    }

    suspend fun release(picture: WebPicture) = mutex.withLock {
        reservedUrls.remove(canonical(picture.fullUrl))
        if (picture.pageUrl.isNotBlank()) reservedUrls.remove(canonical(picture.pageUrl))
        creator(picture).takeIf { it.isNotBlank() }?.let { name ->
            reservedCreators[name]?.let { count ->
                if (count <= 1) reservedCreators.remove(name) else reservedCreators[name] = count - 1
            }
        }
    }

    suspend fun releaseBytes(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val checksum = sha(bytes)
        val hash = imageHash(bytes)
        mutex.withLock {
            claimedChecksums.remove(checksum)
            hash?.let(claimedHashes::remove)
        }
    }

    suspend fun claimExisting(media: com.ihy2ln.weaverse.data.db.entities.MediaEntity): Boolean = withContext(Dispatchers.IO) {
        val file = repository.resolveFile(media)
        file.isFile && accept(file.readBytes())
    }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    /** 64 horizontal luminance comparisons on the first frame, including animated GIFs. */
    private fun imageHash(bytes: ByteArray): Long? = runCatching {
        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = 8 }) ?: return null
        val small = Bitmap.createScaledBitmap(original, 9, 8, true)
        var hash = 0L
        for (y in 0 until 8) for (x in 0 until 8) {
            fun brightness(pixel: Int): Int = ((pixel shr 16 and 255) * 3 + (pixel shr 8 and 255) * 6 + (pixel and 255)) / 10
            hash = (hash shl 1) or if (brightness(small.getPixel(x, y)) > brightness(small.getPixel(x + 1, y))) 1L else 0L
        }
        if (small !== original) small.recycle()
        original.recycle()
        hash
    }.getOrNull()

    private fun near(a: Long, b: Long): Boolean = java.lang.Long.bitCount(a xor b) <= 6
}
