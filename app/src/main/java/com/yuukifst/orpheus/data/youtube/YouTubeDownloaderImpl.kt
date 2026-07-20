package com.yuukifst.orpheus.data.youtube

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

class YouTubeDownloaderImpl private constructor(
    private val client: OkHttpClient,
) : Downloader() {

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

        val httpResponse = client.newCall(httpRequest).execute()
        if (httpResponse.code == 429) {
            httpResponse.close()
            throw ReCaptchaException("HTTP 429", request.url())
        }

        val body = httpResponse.body?.string().orEmpty()
        val responseHeaders = mutableMapOf<String, List<String>>()
        httpResponse.headers.forEach { (name, value) ->
            responseHeaders.getOrPut(name) { emptyList() } + value
            responseHeaders[name] = (responseHeaders[name] ?: emptyList()) + value
        }

        return Response(
            httpResponse.code,
            httpResponse.message,
            responseHeaders,
            body,
            httpResponse.request.url.toString(),
        )
    }

    companion object {
        @Volatile
        private var instance: YouTubeDownloaderImpl? = null

        fun getInstance(): YouTubeDownloaderImpl {
            return instance ?: synchronized(this) {
                instance ?: YouTubeDownloaderImpl(
                    OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .build(),
                ).also { instance = it }
            }
        }
    }
}
