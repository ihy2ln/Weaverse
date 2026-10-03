package com.ihy2ln.weaverse.core.manga

import com.ihy2ln.weaverse.core.manga.extension.MangaExtensionManager
import com.ihy2ln.weaverse.core.manga.extension.MihonExtensionSourceAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MangaSourceRegistry @Inject constructor(
    private val mangaDex: MangaDexSource,
    private val publicHtml: PublicHtmlMangaSources,
    private val extensions: MangaExtensionManager,
    private val galleryAccount: GalleryAccountManager,
    private val galleryHttp: okhttp3.OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val builtIns: List<MangaSourceAdapter> = listOf(mangaDex) + publicHtml.sources
    private val galleries = listOf(GallerySource(galleryAccount, galleryHttp, false), GallerySource(galleryAccount, galleryHttp, true))
    val sourcesFlow: StateFlow<List<MangaSourceAdapter>> = combine(extensions.state, galleryAccount.state) { state, account ->
        builtIns + galleries.filter { account.enabled && (it.descriptor.id != "exhentai" || (account.restricted && account.sessionSaved)) } + state.installed.filter { it.trusted && it.error == null }.flatMap { extension ->
            extension.sources.map { source -> MihonExtensionSourceAdapter(source, extension.packageName) }
        }
    }.stateIn(scope, SharingStarted.Eagerly, builtIns)
    /** Built-ins plus every currently loaded, trusted extension source. */
    val sources: List<MangaSourceAdapter> get() = sourcesFlow.value

    fun get(sourceId: String): MangaSourceAdapter? = sources.firstOrNull { it.descriptor.id == sourceId }
}
