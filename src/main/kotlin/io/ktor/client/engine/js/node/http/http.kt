@file:JsModule("http")

package io.ktor.client.engine.js.node.http

import org.khronos.webgl.Uint8Array

abstract external class Readable {
    fun on(event: String, callback: () -> Unit)
    fun on(event: String, callback: (Uint8Array) -> Unit)
}

abstract external class Writable {
    fun on(event: String, callback: (Throwable) -> Unit)
    fun write(chunk: Uint8Array)
    fun end()
    fun destroy(error: Throwable?)
}

external class ClientRequest : Writable

external class IncommingMessage : Readable {
    var statusCode: Int
    var headers: Any
}

external fun request(url: String, options: Any, callback: (IncommingMessage) -> Unit): ClientRequest
