package org.reekwest.http.core

import org.reekwest.http.core.Method.GET
import org.reekwest.http.core.Method.POST
import org.reekwest.http.core.Uri.Companion.uri

typealias HttpHandler = (Request) -> Response

typealias Headers = Parameters

sealed class HttpMessage {
    abstract val headers: Headers
    abstract val entity: Entity?
}

data class Request(val method: Method, val uri: Uri, override val headers: Headers = listOf(), override val entity: Entity? = null) : HttpMessage() {
    companion object {
        fun get(uri: String, headers: Headers = listOf(), entity: Entity? = null) = Request(GET, uri(uri), headers, entity)
        fun post(uri: String, headers: Headers = listOf(), entity: Entity? = null) = Request(POST, uri(uri), headers, entity)
    }
}

fun Request.query(name:String, value:String) = copy(uri = uri.query(name,value))

data class Response(val status: Status, override val headers: Headers = listOf(), override val entity: Entity? = null) : HttpMessage()

enum class Method { GET, POST, PUT, DELETE, OPTIONS, TRACE, PATCH }

