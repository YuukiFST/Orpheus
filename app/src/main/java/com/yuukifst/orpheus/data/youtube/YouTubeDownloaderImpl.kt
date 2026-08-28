package com.yuukifst.orpheus.data.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeDownloaderImpl @Inject constructor(
    sharedClient: OkHttpClient,
) : Downloader() {

    private val client: OkHttpClient = sharedClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val requestScope = ThreadLocal.withInitial { YouTubeHttpScope.STREAM }
    private val activeCalls = ConcurrentHashMap<okhttp3.Call, YouTubeHttpScope>()

    override fun execute(request: Request): Response {
        val httpRequest = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
            .apply {
                request.headers().forEach { (key, values) ->
                    values.forEach { value -> addHeader(key, value) }
                }
            }
            .build()

        val call = client.newCall(httpRequest)
        val scope = requestScope.get() ?: YouTubeHttpScope.STREAM
        activeCalls[call] = scope
        try {
            val httpResponse = call.execute()
            if (httpResponse.code == 429) {
                httpResponse.close()
                throw ReCaptchaException("HTTP 429", request.url())
            }

            val body = httpResponse.body?.string().orEmpty()
            val responseHeaders = mutableMapOf<String, List<String>>()
            httpResponse.headers.forEach { (name, value) ->
                responseHeaders[name] = (responseHeaders[name] ?: emptyList()) + value
            }

            return Response(
                httpResponse.code,
                httpResponse.message,
                responseHeaders,
                body,
                httpResponse.request.url.toString(),
            )
        } finally {
            activeCalls.remove(call)
        }
    }

    fun <T> runAsSearch(block: () -> T): T = runAs(YouTubeHttpScope.SEARCH, block)

    fun <T> runAsStream(block: () -> T): T = runAs(YouTubeHttpScope.STREAM, block)

    private fun <T> runAs(scope: YouTubeHttpScope, block: () -> T): T {
        val previous = requestScope.get()
        requestScope.set(scope)
        return try {
            block()
        } finally {
            requestScope.set(previous)
        }
    }

    /**
     * Coroutine cancellation does not interrupt a blocking OkHttp `execute()`,
     * so a superseded query's HTTP work has to be cancelled explicitly.
     * Only search/suggestion calls are cancelled so in-flight stream extracts
     * (play/prefetch) are not torn down.
     */
    fun cancelActiveRequest() {
        cancelCalls(YouTubeHttpScope.SEARCH)
    }

    internal fun cancelCalls(scope: YouTubeHttpScope) {
        val snapshot = activeCalls.entries.filter { it.value == scope }
        snapshot.forEach { (call, _) ->
            activeCalls.remove(call)
            runCatching { call.cancel() }
        }
    }

    fun warmUpConnection() {
        runCatching {
            client.newCall(
                okhttp3.Request.Builder()
                    .url("https://www.youtube.com")
                    .head()
                    .build(),
            ).execute().close()
        }
    }

    companion object {
        internal fun createStandalone(): YouTubeDownloaderImpl {
            return YouTubeDownloaderImpl(
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }
}

internal enum class YouTubeHttpScope {
    SEARCH,
    STREAM,
}
