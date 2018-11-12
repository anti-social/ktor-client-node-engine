@file:JsModule("https")

package io.ktor.client.engine.js.node.https

import io.ktor.client.engine.js.node.http.ClientRequest
import io.ktor.client.engine.js.node.http.IncommingMessage

external fun request(url: String, options: Any, callback: (IncommingMessage) -> Unit): ClientRequest
