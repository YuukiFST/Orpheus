package com.yuukifst.orpheus.data.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.Collections
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

    private val activeCalls = Collections.newSetFromMap(
        ConcurrentHashMap<okhttp3.Call, Boolean>(),
    )

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
        activeCalls.add(call)
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

    /**
     * Coroutine cancellation does not interrupt a blocking OkHttp `execute()`,
     * so a superseded query's HTTP work has to be cancelled explicitly.
     * Cancels all in-flight NewPipe calls; only invoked when the caller knows
     * every outstanding request is stale.
     */
    fun cancelActiveRequest() {
        val snapshot = activeCalls.toList()
        activeCalls.clear()
        snapshot.forEach { runCatching { it.cancel() } }
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
