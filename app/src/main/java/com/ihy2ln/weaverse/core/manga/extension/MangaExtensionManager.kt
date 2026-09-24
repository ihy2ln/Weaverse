package com.ihy2ln.weaverse.core.manga.extension

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.ihy2ln.weaverse.core.manga.MangaBrowseMode
import com.ihy2ln.weaverse.core.manga.MangaChapter
import com.ihy2ln.weaverse.core.manga.MangaPage
import com.ihy2ln.weaverse.core.manga.MangaSearchResult
import com.ihy2ln.weaverse.core.manga.MangaSourceAdapter
import com.ihy2ln.weaverse.core.manga.MangaSourceDescriptor
import dalvik.system.PathClassLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import rikka.shizuku.Shizuku
import com.ihy2ln.weaverse.mihon.shizuku.IShellInterface
import com.ihy2ln.weaverse.mihon.shizuku.ShellInterface
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import okhttp3.Call
import javax.inject.Inject
import javax.inject.Singleton

enum class ExtensionInstallMode { PackageInstaller, Private, Shizuku }
enum class ExtensionInstallStep { Idle, Downloading, Installing, Installed, Error }

@Serializable
data class ExtensionStore(
    val indexUrl: String,
    val name: String,
    val signingKey: String = "",
    val extensionListUrl: String? = null,
    val legacy: Boolean = false,
)

data class AvailableExtension(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double,
    val language: String,
    val nsfw: Boolean,
    val apkUrl: String,
    val signatureHash: String,
    val storeName: String,
)

data class InstalledExtension(
    val name: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val libVersion: Double,
    val language: String,
    val nsfw: Boolean,
    val signatureHash: String,
    val shared: Boolean,
    val trusted: Boolean,
    val sources: List<Source>,
    val error: String? = null,
)

data class ExtensionCatalogState(
    val initialized: Boolean = false,
    val stores: List<ExtensionStore> = emptyList(),
    val installed: List<InstalledExtension> = emptyList(),
    val available: List<AvailableExtension> = emptyList(),
    val installSteps: Map<String, ExtensionInstallStep> = emptyMap(),
    val message: String = "",
)

private class HostFirstExtensionClassLoader(path: String, parent: ClassLoader) : PathClassLoader(path, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(this) {
        findLoadedClass(name) ?: if (
            name.startsWith("java.") || name.startsWith("android.") || name.startsWith("androidx.") ||
            name.startsWith("kotlin.") || name.startsWith("kotlinx.") || name.startsWith("okhttp3.") ||
            name.startsWith("okio.") || name.startsWith("rx.") || name.startsWith("org.jsoup.") ||
            name.startsWith("uy.kohesive.injekt.") || name.startsWith("eu.kanade.tachiyomi.source.") ||
            name.startsWith("eu.kanade.tachiyomi.network.") || name.startsWith("tachiyomi.core.common.")
        ) {
            super.loadClass(name, resolve)
        } else {
            runCatching { findClass(name) }.getOrElse { super.loadClass(name, resolve) }.also { if (resolve) resolveClass(it) }
        }
    }
}

@Singleton
class MangaExtensionManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = OkHttpClient()
    private val prefs = context.getSharedPreferences("manga-extensions", Context.MODE_PRIVATE)
    private val privateDir = File(context.filesDir, "manga_extensions").apply { mkdirs() }
    private val _state = MutableStateFlow(ExtensionCatalogState())
    val state: StateFlow<ExtensionCatalogState> = _state.asStateFlow()
    private val activeDownloads = ConcurrentHashMap<String, Call>()
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            val packageName = intent?.data?.schemeSpecificPart
            refreshInstalled()
            if (!packageName.isNullOrBlank()) setStep(packageName, ExtensionInstallStep.Installed)
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        refreshInstalled()
    }

    fun refreshInstalled() = scope.launch {
        val stores = readStores()
        val trusted = prefs.getStringSet(KEY_TRUSTED, emptySet()).orEmpty()
        val storeKeys = stores.map { normalizeHash(it.signingKey) }.filter(String::isNotBlank).toSet()
        val shared = installedPackages().map { it to true }
        val private = privateDir.listFiles().orEmpty().filter { it.extension == PRIVATE_EXT }.mapNotNull { file ->
            packageArchive(file)?.let { it to false }
        }
        val packages = (shared + private).filter { isExtension(it.first) }.groupBy { it.first.packageName }.values.mapNotNull { candidates ->
            candidates.maxByOrNull { PackageInfoCompat.getLongVersionCode(it.first) }
        }
        val loaded = packages.map { (pkg, sharedApk) -> load(pkg, sharedApk, trusted, storeKeys) }.sortedBy { it.name.lowercase() }
        val installedNames = loaded.mapTo(hashSetOf()) { it.packageName }
        _state.value = _state.value.copy(
            initialized = true,
            stores = stores,
            installed = loaded,
            installSteps = _state.value.installSteps.mapValues { (pkg, step) ->
                if (pkg in installedNames && step == ExtensionInstallStep.Installing) ExtensionInstallStep.Installed else step
            },
        )
    }

    fun addStore(indexUrl: String) = scope.launch {
        val url = indexUrl.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            updateMessage("Store URL must use HTTP or HTTPS")
            return@launch
        }
        runCatching { fetchStore(url) }.onSuccess { store ->
            val stores = (readStores().filterNot { it.indexUrl == store.indexUrl || (it.signingKey.isNotBlank() && it.signingKey == store.signingKey) } + store)
            persistStores(stores)
            _state.value = _state.value.copy(stores = stores, message = "Added ${store.name}")
            refreshAvailable()
            refreshInstalled()
        }.onFailure { updateMessage(it.message ?: "Could not add extension store") }
    }

    fun removeStore(indexUrl: String) {
        val stores = readStores().filterNot { it.indexUrl == indexUrl }
        persistStores(stores)
        _state.value = _state.value.copy(stores = stores, available = _state.value.available.filter { item -> stores.any { it.name == item.storeName } })
    }

    fun refreshAvailable() = scope.launch {
        val results = readStores().flatMap { store -> runCatching { fetchAvailable(store) }.getOrElse { emptyList() } }
        _state.value = _state.value.copy(available = results.distinctBy { it.packageName }.sortedBy { it.name.lowercase() })
    }

    fun trust(packageName: String) {
        val ext = _state.value.installed.firstOrNull { it.packageName == packageName } ?: return
        val trusted = prefs.getStringSet(KEY_TRUSTED, emptySet()).orEmpty() + ext.signatureHash
        prefs.edit().putStringSet(KEY_TRUSTED, trusted).apply()
        refreshInstalled()
    }

    fun install(extension: AvailableExtension, mode: ExtensionInstallMode) = scope.launch {
        setStep(extension.packageName, ExtensionInstallStep.Downloading)
        val target = File(context.cacheDir, "extension-${extension.packageName}.apk")
        runCatching {
            val call = client.newCall(Request.Builder().url(extension.apkUrl).build())
            activeDownloads[extension.packageName] = call
            call.execute().use { response ->
                check(response.isSuccessful) { "Extension download failed: HTTP ${response.code}" }
                target.outputStream().use { output -> requireNotNull(response.body).byteStream().use { it.copyTo(output) } }
            }
            val archive = requireNotNull(packageArchive(target)) { "Downloaded file is not an APK" }
            check(archive.packageName == extension.packageName) { "Extension package name does not match the store" }
            check(isExtension(archive)) { "APK does not declare tachiyomi.extension" }
            val hash = signatureHash(archive)
            check(extension.signatureHash.isBlank() || normalizeHash(extension.signatureHash) == hash) { "Extension signature does not match the store" }
            _state.value.installed.firstOrNull { it.packageName == extension.packageName }?.let { current ->
                check(PackageInfoCompat.getLongVersionCode(archive) >= current.versionCode) { "Extension downgrade is not allowed" }
                check(current.signatureHash == hash) { "Extension update signature mismatch" }
            }
            if (extension.signatureHash.isNotBlank()) {
                val trusted = prefs.getStringSet(KEY_TRUSTED, emptySet()).orEmpty() + hash
                prefs.edit().putStringSet(KEY_TRUSTED, trusted).apply()
            }
            setStep(extension.packageName, ExtensionInstallStep.Installing)
            when (mode) {
                ExtensionInstallMode.Private -> installPrivate(target, archive)
                ExtensionInstallMode.PackageInstaller -> openPackageInstaller(target)
                ExtensionInstallMode.Shizuku -> installWithShizuku(target)
            }
            if (mode == ExtensionInstallMode.Private) {
                setStep(extension.packageName, ExtensionInstallStep.Installed)
                refreshInstalled()
            }
        }.onFailure { error ->
            if (activeDownloads[extension.packageName]?.isCanceled() == true) {
                setStep(extension.packageName, ExtensionInstallStep.Idle)
                updateMessage("Extension download cancelled")
            } else {
                setStep(extension.packageName, ExtensionInstallStep.Error)
                updateMessage(error.message ?: "Extension installation failed")
            }
        }
        activeDownloads.remove(extension.packageName)
        if (mode != ExtensionInstallMode.PackageInstaller) target.delete()
    }

    fun cancel(packageName: String) {
        activeDownloads[packageName]?.cancel()
        setStep(packageName, ExtensionInstallStep.Idle)
    }

    fun uninstall(extension: InstalledExtension) {
        if (extension.shared) {
            context.startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${extension.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            File(privateDir, "${extension.packageName}.$PRIVATE_EXT").delete()
            refreshInstalled()
        }
    }

    private fun installPrivate(file: File, archive: PackageInfo) {
        val destination = File(privateDir, "${archive.packageName}.$PRIVATE_EXT")
        val current = packageArchive(destination)
        if (current != null) {
            check(PackageInfoCompat.getLongVersionCode(archive) >= PackageInfoCompat.getLongVersionCode(current)) { "Extension downgrade is not allowed" }
            check(signatureHash(current) == signatureHash(archive)) { "Extension update signature mismatch" }
        }
        file.copyTo(destination, overwrite = true)
        destination.setReadOnly()
    }

    private fun openPackageInstaller(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.extension-files", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }

    private fun installWithShizuku(file: File) {
        check(Shizuku.pingBinder()) { "Shizuku is not running" }
        check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { "Grant Weaverse permission in Shizuku first" }
        val connected = CountDownLatch(1)
        val completed = CountDownLatch(1)
        var shell: IShellInterface? = null
        var installError: String? = null
        val action = "${context.packageName}.SHIZUKU_INSTALL_RESULT.${System.nanoTime()}"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                if (status != PackageInstaller.STATUS_SUCCESS) installError = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Shizuku install failed"
                completed.countDown()
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShellInterface::class.java))
            .tag("weaverse_extension_installer").processNameSuffix("extension_installer").daemon(false).version(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: android.os.IBinder?) { shell = IShellInterface.Stub.asInterface(binder); connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        try {
            Shizuku.bindUserService(args, connection)
            check(connected.await(15, TimeUnit.SECONDS)) { "Timed out connecting to Shizuku" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.extension-files", file)
            val pending = PendingIntent.getBroadcast(context, action.hashCode(), Intent(action).setPackage(context.packageName), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            context.contentResolver.openAssetFileDescriptor(uri, "r").use { descriptor -> shell?.install(requireNotNull(descriptor), pending.intentSender) }
            check(completed.await(90, TimeUnit.SECONDS)) { "Timed out waiting for Shizuku installation" }
            check(installError == null) { installError!! }
        } finally {
            runCatching { Shizuku.unbindUserService(args, connection, true) }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun load(pkg: PackageInfo, shared: Boolean, trusted: Set<String>, storeKeys: Set<String>): InstalledExtension {
        val appInfo = requireNotNull(pkg.applicationInfo)
        val metadata = appInfo.metaData
        val label = metadata?.getString(METADATA_NAME)
            ?: context.packageManager.getApplicationLabel(appInfo).toString().substringAfter("Tachiyomi: ")
        val versionName = pkg.versionName.orEmpty()
        val libVersion = metadata?.getFloat(METADATA_LIB)?.takeIf { it > 0 }?.toDouble()
            ?: versionName.substringBeforeLast('.').toDoubleOrNull() ?: 0.0
        val signature = signatureHash(pkg)
        val isTrusted = signature in trusted || signature in storeKeys
        val nsfw = (metadata?.getInt(METADATA_WARNING) ?: 0) > 0 || metadata?.getInt(METADATA_NSFW) == 1
        val allowNsfw = prefs.getBoolean(KEY_NSFW, false)
        if (libVersion !in SUPPORTED_LIBS || (nsfw && !allowNsfw) || !isTrusted) {
            return InstalledExtension(label, pkg.packageName, versionName, PackageInfoCompat.getLongVersionCode(pkg), libVersion, "", nsfw, signature, shared, isTrusted, emptyList(), when {
                !isTrusted -> "Untrusted signature"
                nsfw && !allowNsfw -> "Content warning is disabled"
                else -> "Unsupported extension library $libVersion"
            })
        }
        return runCatching {
            val loader = HostFirstExtensionClassLoader(appInfo.sourceDir, context.classLoader)
            val classes = requireNotNull(metadata?.getString(METADATA_CLASS)) { "Missing source class metadata" }.split(';').map(String::trim)
            val sources = classes.flatMap { className ->
                val resolved = if (className.startsWith('.')) pkg.packageName + className else className
                when (val instance = Class.forName(resolved, false, loader).getDeclaredConstructor().newInstance()) {
                    is Source -> listOf(instance)
                    is SourceFactory -> instance.createSources()
                    else -> error("$resolved is not a Source or SourceFactory")
                }
            }
            val language = sources.map { it.lang }.distinct().singleOrNull() ?: "all"
            InstalledExtension(label, pkg.packageName, versionName, PackageInfoCompat.getLongVersionCode(pkg), libVersion, language, nsfw, signature, shared, true, sources)
        }.getOrElse { error ->
            InstalledExtension(label, pkg.packageName, versionName, PackageInfoCompat.getLongVersionCode(pkg), libVersion, "", nsfw, signature, shared, true, emptyList(), error.message ?: error.javaClass.simpleName)
        }
    }

    private fun installedPackages(): List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
        context.packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PACKAGE_FLAGS.toLong()))
    } else @Suppress("DEPRECATION") context.packageManager.getInstalledPackages(PACKAGE_FLAGS)

    private fun packageArchive(file: File): PackageInfo? = if (!file.isFile) null else @Suppress("DEPRECATION")
        context.packageManager.getPackageArchiveInfo(file.absolutePath, PACKAGE_FLAGS)?.apply {
            applicationInfo?.apply { sourceDir = file.absolutePath; publicSourceDir = file.absolutePath }
        }

    private fun isExtension(pkg: PackageInfo) = pkg.reqFeatures.orEmpty().any { it.name == EXTENSION_FEATURE }
    private fun signatureHash(pkg: PackageInfo): String {
        val bytes = if (Build.VERSION.SDK_INT >= 28) pkg.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        else @Suppress("DEPRECATION") pkg.signatures?.firstOrNull()?.toByteArray()
        return bytes?.let { MessageDigest.getInstance("SHA-256").digest(it).joinToString("") { b -> "%02x".format(b) } }.orEmpty()
    }

    private fun readStores(): List<ExtensionStore> = prefs.getString(KEY_STORES, null)?.let { runCatching { json.decodeFromString<List<ExtensionStore>>(it) }.getOrNull() }.orEmpty()
    private fun persistStores(stores: List<ExtensionStore>) { prefs.edit().putString(KEY_STORES, json.encodeToString(stores)).apply() }
    private fun fetchText(url: String): String = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        check(response.isSuccessful) { "Store request failed: HTTP ${response.code}" }; requireNotNull(response.body).string()
    }

    private fun fetchStore(url: String): ExtensionStore {
        val root = json.parseToJsonElement(fetchText(url))
        if (root is JsonArray) return ExtensionStore(url, url.substringBefore("/index.min.json").substringAfterLast('/').ifBlank { "Extension store" }, extensionListUrl = url, legacy = true)
        val obj = root as JsonObject
        val name = obj.string("name") ?: obj.string("repoName") ?: obj.obj("meta")?.string("name") ?: "Extension store"
        val key = obj.string("signingKey") ?: obj.string("signature") ?: obj.obj("meta")?.string("signingKey").orEmpty()
        val list = obj.string("extensionListUrl") ?: obj.string("extensionsUrl") ?: obj.obj("meta")?.string("extensionListUrl")
        return ExtensionStore(url, name, key, list?.let { resolveUrl(url, it) }, obj["extensions"] is JsonArray)
    }

    private fun fetchAvailable(store: ExtensionStore): List<AvailableExtension> {
        val sourceUrl = store.extensionListUrl ?: if (store.legacy && store.indexUrl.endsWith("repo.json")) store.indexUrl.removeSuffix("repo.json") + "index.min.json" else store.indexUrl
        val root = json.parseToJsonElement(fetchText(sourceUrl))
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["extensions"] as? JsonArray ?: root["extensionList"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull { parseAvailable(it as? JsonObject ?: return@mapNotNull null, store, sourceUrl) }
    }

    private fun parseAvailable(obj: JsonObject, store: ExtensionStore, sourceUrl: String): AvailableExtension? {
        val pkg = obj.string("pkg") ?: obj.string("packageName") ?: return null
        val apk = obj.string("apk") ?: obj.string("apkUrl") ?: obj.string("downloadUrl") ?: return null
        val versionName = obj.string("version") ?: obj.string("versionName") ?: "0"
        val versionCode = obj.long("code") ?: obj.long("versionCode") ?: 0
        val lib = obj.double("libVersion") ?: versionName.substringBeforeLast('.').toDoubleOrNull() ?: 1.4
        val langs = obj.string("lang") ?: (obj["sources"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.string("lang") }?.distinct()?.singleOrNull() ?: "all"
        return AvailableExtension(
            name = obj.string("name") ?: pkg.substringAfterLast('.'), packageName = pkg, versionName = versionName,
            versionCode = versionCode, libVersion = lib, language = langs,
            nsfw = obj.long("nsfw") == 1L || obj.long("contentWarning")?.let { it > 0 } == true,
            apkUrl = resolveUrl(sourceUrl, apk), signatureHash = normalizeHash(obj.string("signature") ?: obj.string("signingKey") ?: store.signingKey),
            storeName = store.name,
        )
    }

    private fun resolveUrl(base: String, value: String): String = runCatching { java.net.URI(base).resolve(value).toString() }.getOrDefault(value)
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.long(key: String) = string(key)?.toLongOrNull()
    private fun JsonObject.double(key: String) = string(key)?.toDoubleOrNull()
    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private val JsonPrimitive.contentOrNull get() = runCatching { content }.getOrNull()
    private fun normalizeHash(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^0-9a-f]"), "")
    private fun setStep(pkg: String, step: ExtensionInstallStep) { _state.value = _state.value.copy(installSteps = _state.value.installSteps + (pkg to step)) }
    private fun updateMessage(message: String) { _state.value = _state.value.copy(message = message) }

    companion object {
        private const val EXTENSION_FEATURE = "tachiyomi.extension"
        private const val METADATA_CLASS = "tachiyomi.extension.class"
        private const val METADATA_NSFW = "tachiyomi.extension.nsfw"
        private const val METADATA_NAME = "tachiyomix.name"
        private const val METADATA_LIB = "tachiyomix.extensionLib"
        private const val METADATA_WARNING = "tachiyomix.contentWarning"
        private const val KEY_STORES = "stores"
        private const val KEY_TRUSTED = "trusted_signatures"
        private const val KEY_NSFW = "show_content_warnings"
        private const val PRIVATE_EXT = "ext"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private val SUPPORTED_LIBS = setOf(1.4, 1.6)
        @Suppress("DEPRECATION") private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or PackageManager.GET_META_DATA or PackageManager.GET_SIGNATURES or if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
    }
}

class MihonExtensionSourceAdapter(
    private val source: Source,
    val extensionPackage: String,
) : MangaSourceAdapter {
    override val descriptor = MangaSourceDescriptor(
        id = "ext:${source.id}", name = source.name, baseUrl = (source as? HttpSource)?.baseUrl.orEmpty(),
        description = "${source.lang.uppercase()} · $extensionPackage", authorized = true,
        language = source.lang, origin = "extension", packageName = extensionPackage,
        supportsLatest = source.supportsLatest, supportsNativeFilters = source.getFilterList().isNotEmpty(),
    )
    override fun nativeFilters(): FilterList = source.getFilterList()
    override suspend fun browse(mode: MangaBrowseMode): List<MangaSearchResult> = browsePage(mode, 0)
    override suspend fun browsePage(mode: MangaBrowseMode, page: Int): List<MangaSearchResult> =
        (if (mode == MangaBrowseMode.Latest) source.getLatestUpdates(page + 1) else source.getPopularManga(page + 1)).mangas.map(::result)
    override suspend fun searchPage(query: String, page: Int): List<MangaSearchResult> = searchPage(query, page, source.getFilterList())
    override suspend fun searchPage(query: String, page: Int, filters: FilterList): List<MangaSearchResult> =
        source.getSearchManga(page + 1, query, filters).mangas.map(::result)
    override suspend fun search(query: String) = searchPage(query, 0)
    override suspend fun chapters(manga: MangaSearchResult): List<MangaChapter> {
        val seed = manga.toSManga()
        val update = source.getMangaUpdate(seed, emptyList(), fetchDetails = false, fetchChapters = true)
        return update.chapters.map { chapter ->
            MangaChapter(descriptor.id, chapter.url, manga.remoteId, manga.title, chapter.name,
                chapterNumber = chapter.chapter_number.takeIf { it >= 0 }?.toString().orEmpty(), language = source.lang,
                canonicalUrl = (source as? HttpSource)?.getChapterUrl(chapter).orEmpty(), dateUpload = chapter.date_upload,
                scanlator = chapter.scanlator.orEmpty())
        }
    }
    override suspend fun details(manga: MangaSearchResult): MangaSearchResult =
        source.getMangaUpdate(manga.toSManga(), emptyList(), fetchDetails = true, fetchChapters = false).manga.let(::result)
    override suspend fun pages(chapter: MangaChapter): List<MangaPage> {
        val sourceChapter = SChapter.create().apply { url=chapter.remoteId; name=chapter.title; chapter_number=chapter.chapterNumber.toFloatOrNull() ?: -1f; scanlator=chapter.scanlator; date_upload=chapter.dateUpload }
        return source.getPageList(sourceChapter).mapIndexed { index, page ->
            val image = page.imageUrl ?: (source as? HttpSource)?.getImageUrl(page).orEmpty()
            MangaPage(descriptor.id, chapter.remoteId, index, image, "${index + 1}.${image.substringAfterLast('.', "jpg").substringBefore('?')}")
        }
    }
    private fun result(manga: SManga) = MangaSearchResult(
        sourceId = descriptor.id, remoteId = manga.url, title = manga.title, description = manga.description.orEmpty(),
        coverUrl = manga.thumbnail_url, canonicalUrl = (source as? HttpSource)?.getMangaUrl(manga).orEmpty(),
        tags = manga.getGenres().orEmpty(), languages = listOf(source.lang), authors = manga.author?.let(::listOf).orEmpty(),
        artists = manga.artist?.let(::listOf).orEmpty(), status = when(manga.status) { SManga.ONGOING->"Ongoing"; SManga.COMPLETED->"Completed"; SManga.LICENSED->"Licensed"; SManga.CANCELLED->"Cancelled"; SManga.ON_HIATUS->"On hiatus"; else->"Unknown" },
    )
    private fun MangaSearchResult.toSManga() = SManga.create().also { it.url=remoteId; it.title=title; it.thumbnail_url=coverUrl; it.description=description; it.author=authors.joinToString().ifBlank { null }; it.artist=artists.joinToString().ifBlank { null }; it.genre=tags.joinToString().ifBlank { null } }
}
