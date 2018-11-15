import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Node
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay

import testCompiler

suspend fun main() {
    val httpClient = HttpClient(Node)

    val compiledQuery = testCompiler()
    val result = httpClient.get<String>("http://es1.uaprom:9207/uaprom2_v1/product/_search") {
        body = """{"query": {"match_all": {}}}"""
    }
    println("Result: $result")
    println("Exiting")
}
