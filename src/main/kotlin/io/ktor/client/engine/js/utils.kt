package io.ktor.client.engine.js

import org.khronos.webgl.Uint8Array

internal external object Object {
    operator fun set(key: String, value: Any?)
}

internal inline fun <T : Any> jsObject(builder: T.() -> Unit): T {
    val obj = js("({})") as T
    return obj.apply {
        builder()
    }
}

@Suppress("UnsafeCastFromDynamic")
internal fun Any.getOwnPropertyNames(): Array<String> {
    @Suppress("UNUSED_VARIABLE")
    val me = this
    return js("Object.getOwnPropertyNames(me)")
}

@Suppress("UnsafeCastFromDynamic")
internal fun Uint8Array.asByteArray(): ByteArray {
    return Uint8Array(buffer, byteOffset, length).asDynamic()
}
