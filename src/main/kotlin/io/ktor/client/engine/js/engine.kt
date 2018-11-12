package io.ktor.client.engine.js

import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.HttpEngineCall
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.js.node.http.IncommingMessage
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.request.DefaultHttpRequest
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.response.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.date.GMTDate

import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.io.ByteReadChannel
import kotlinx.coroutines.io.readRemaining
import kotlinx.coroutines.io.writer

import kotlinx.io.core.readBytes

import org.khronos.webgl.Uint8Array

object Node : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): HttpClientEngine {
        return NodeHttpEngine(HttpClientEngineConfig().apply(block))
    }
}

class NodeHttpEngine(override val config: HttpClientEngineConfig) : HttpClientEngine {
    override val dispatcher = Dispatchers.Default

    override val coroutineContext = dispatcher + SupervisorJob()

    @io.ktor.util.InternalAPI
    override suspend fun execute(
        call: HttpClientCall,
        data: HttpRequestData
    ): HttpEngineCall = withContext(dispatcher) {
        val callContext = CompletableDeferred<Unit>(
            this@NodeHttpEngine.coroutineContext[Job]
        ) + dispatcher

        val requestTime = GMTDate()
        val request = DefaultHttpRequest(call, data)

        val rawResponse = awaitResponse(
            request.url.toString(), request.toOptions(), request.bodyBytes()
        )
        val responseReader = ResponseReader(rawResponse)

        val response = NodeHttpResponse(
            call, requestTime, rawResponse, responseReader.toByteChannel(callContext), callContext
        )

        HttpEngineCall(request, response)
    }

    override fun close() {}
}

class ResponseReader(response: IncommingMessage) {
    private val chunksChannel = Channel<ByteArray>(UNLIMITED)

    init {
        response.on("data") { chunk: Uint8Array ->
            chunksChannel.offer(chunk.asByteArray())
        }
        response.on("end") { ->
            chunksChannel.close()
        }
    }

    suspend fun readChunk(): ByteArray? {
        // FIXME How we can cancel reading data?
        return try {
            chunksChannel.receive()
        } catch (exc: ClosedReceiveChannelException) {
            null
        }
    }
}

internal fun ResponseReader.toByteChannel(
    callContext: CoroutineContext
): ByteReadChannel = GlobalScope.writer(callContext) {
    while (true) {
        val chunk = readChunk() ?: break
        channel.writeFully(chunk, 0, chunk.size)
    }
}.channel

@io.ktor.util.InternalAPI
internal fun HttpRequest.toOptions(): Any {
    val jsHeaders = jsObject<dynamic> {  }
    mergeHeaders(headers, content) { key, value ->
        jsHeaders[key] = value
    }

    return jsObject<Options> {
        method = this@toOptions.method.value
        headers = jsHeaders
    }
}

internal suspend fun HttpRequest.bodyBytes(): Uint8Array? {
    val content = content
    val bodyBytes = when (content) {
        is OutgoingContent.ByteArrayContent -> content.bytes()
        is OutgoingContent.ReadChannelContent -> content.readFrom().readRemaining().readBytes()
        is OutgoingContent.WriteChannelContent -> writer(coroutineContext) { content.writeTo(channel) }
            .channel.readRemaining().readBytes()
        else -> null
    }
    return bodyBytes?.let { Uint8Array(it.toTypedArray()) }
}

class Options(var method: String, var headers: dynamic)

class NodeHttpResponse(
    override val call: HttpClientCall,
    override val requestTime: GMTDate,
    private val response: IncommingMessage,
    override val content: ByteReadChannel,
    override val coroutineContext: CoroutineContext
) : HttpResponse {
    override val status: HttpStatusCode = HttpStatusCode.fromValue(response.statusCode)

    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1

    override val responseTime: GMTDate = GMTDate()

    override val headers: Headers = Headers.build {
        val rawHeaders = response.headers.asDynamic()
        response.headers.getOwnPropertyNames().forEach { key ->
            append(key, rawHeaders[key].toString())
        }
        Unit
    }

    override fun close() {
        @Suppress("UNCHECKED_CAST")
        (coroutineContext[Job] as CompletableDeferred<Unit>).complete(Unit)
    }
}

internal suspend fun awaitResponse(url: String, options: Any, body: Uint8Array?): IncommingMessage = suspendCancellableCoroutine { cont ->
    fun callback(response: IncommingMessage) {
        cont.resume(response)
    }

    val req = if (url.startsWith("https://")) {
        io.ktor.client.engine.js.node.https.request(url, options, ::callback)
    } else {
        io.ktor.client.engine.js.node.http.request(url, options, ::callback)
    }

    req.on("error") { error: Throwable ->
        cont.resumeWithException(error)
    }

    cont.invokeOnCancellation(req::destroy)

    body?.let(req::write)
    req.end()
}
