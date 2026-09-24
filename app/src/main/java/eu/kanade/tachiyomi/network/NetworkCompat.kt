package eu.kanade.tachiyomi.network

import android.content.Context
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import rx.Observable
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

fun interface ProgressListener { fun update(bytesRead: Long, contentLength: Long, done: Boolean) }

class AndroidCookieJar : CookieJar {
    private val cookies = ConcurrentHashMap<String, List<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { this.cookies[url.host] = cookies }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies[url.host].orEmpty().filter { it.matches(url) }
    fun removeAll() = cookies.clear()
}

class NetworkHelper(context: Context) {
    val cookieJar = AndroidCookieJar()
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .cache(Cache(File(context.cacheDir, "manga_extension_network"), 10L * 1024 * 1024))
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(2, TimeUnit.MINUTES)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                if (chain.request().header("User-Agent") == null) header("User-Agent", defaultUserAgentProvider())
            }.build()
            chain.proceed(request)
        }.build()
    @Deprecated("The regular client is used") val cloudflareClient: OkHttpClient = client
    fun defaultUserAgentProvider() = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/149 Mobile Safari/537.36 Weaverse/1.4"
}

class HttpException(val code: Int) : IOException("HTTP $code")

fun GET(url: String, headers: Headers = Headers.headersOf()): Request = Request.Builder().url(url).headers(headers).build()
fun POST(url: String, headers: Headers = Headers.headersOf(), body: okhttp3.RequestBody = FormBody.Builder().build()): Request =
    Request.Builder().url(url).headers(headers).post(body).build()

fun Call.asObservable(): Observable<Response> = Observable.create { subscriber ->
    val call = clone()
    subscriber.add(rx.subscriptions.Subscriptions.create { call.cancel() })
    try {
        val response = call.execute()
        if (!subscriber.isUnsubscribed) { subscriber.onNext(response); subscriber.onCompleted() }
    } catch (t: Throwable) { if (!subscriber.isUnsubscribed) subscriber.onError(t) }
}

fun Call.asObservableSuccess(): Observable<Response> = asObservable().doOnNext { response ->
    if (!response.isSuccessful) { val code = response.code; response.close(); throw HttpException(code) }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
        override fun onResponse(call: Call, response: Response) { continuation.resume(response) { _, value, _ -> value.close() } }
    })
}

suspend fun Call.awaitSuccess(): Response = await().also { if (!it.isSuccessful) { val code=it.code; it.close(); throw HttpException(code) } }

private class ProgressBody(private val delegate: ResponseBody, private val listener: ProgressListener, private val offset: Long) : ResponseBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun source(): BufferedSource {
        val total = delegate.contentLength()
        return object : ForwardingSource(delegate.source()) {
        var read = 0L
        override fun read(sink: Buffer, byteCount: Long): Long {
            val count = super.read(sink, byteCount)
            if (count > 0) read += count
            listener.update(offset + read, offset + total, count == -1L)
            return count
        }
        }.buffer()
    }
}

fun OkHttpClient.newCachelessCallWithProgress(request: Request, listener: ProgressListener, existingSize: Long = 0L): Call =
    newBuilder().cache(null).addNetworkInterceptor { chain ->
        val req = chain.request().newBuilder().apply { if (existingSize > 0 && chain.request().header("Range") == null) header("Range", "bytes=$existingSize-") }.build()
        val response = chain.proceed(req)
        val offset = if (response.code == 206) existingSize else 0L
        response.newBuilder().body(ProgressBody(requireNotNull(response.body), listener, offset)).build()
    }.build().newCall(request)
