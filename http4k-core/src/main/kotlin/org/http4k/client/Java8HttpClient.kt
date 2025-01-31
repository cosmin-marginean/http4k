package org.http4k.client

import org.http4k.core.Body
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.CLIENT_TIMEOUT
import org.http4k.core.Status.Companion.CONNECTION_REFUSED
import org.http4k.core.Status.Companion.UNKNOWN_HOST
import java.io.ByteArrayInputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.time.Duration
import java.time.Duration.ZERO

/**
 * Use this legacy Java client when you're not yet on Java 11.
 */
object Java8HttpClient {
    @JvmStatic
    @JvmName("create")
    operator fun invoke(): HttpHandler = invoke(ZERO)

    @JvmStatic
    @JvmName("createWithTimeouts")
    operator fun invoke(
        readTimeout: Duration = ZERO,
        connectionTimeout: Duration = ZERO
    ): HttpHandler = { request: Request ->
        try {
            val connection = (URI(request.uri.toString()).toURL().openConnection() as HttpURLConnection).apply {
                this.readTimeout = readTimeout.toMillis().toInt()
                this.connectTimeout = connectionTimeout.toMillis().toInt()
                instanceFollowRedirects = false
                requestMethod = request.method.name
                doOutput = true
                doInput = true
                request.headers.forEach {
                    addRequestProperty(it.first, it.second)
                }
                request.body.apply {
                    if (this != Body.EMPTY) {
                        val content = if (stream.available() == 0) payload.array().inputStream() else stream
                        content.copyTo(outputStream)
                    }
                }
            }

            val status = Status(connection.responseCode, connection.responseMessage.orEmpty())
            val baseResponse = Response(status).body(connection.body(status))
            connection.headerFields
                .filterKeys { it != null } // because response status line comes as a header with null key (*facepalm*)
                .map { header -> header.value.map { header.key to it } }
                .flatten()
                .fold(baseResponse) { acc, next -> acc.header(next.first, next.second) }
        } catch (e: UnknownHostException) {
            Response(UNKNOWN_HOST.toClientStatus(e))
        } catch (e: ConnectException) {
            Response(CONNECTION_REFUSED.toClientStatus(e))
        } catch (e: SocketTimeoutException) {
            Response(CLIENT_TIMEOUT.toClientStatus(e))
        }
    }

    // Because HttpURLConnection closes the stream if a new request is made, we are forced to consume it straight away
    private fun HttpURLConnection.body(status: Status) =
        Body(resolveStream(status).readBytes().let { ByteBuffer.wrap(it) })

    private fun HttpURLConnection.resolveStream(status: Status) =
        when {
            status.serverError || status.clientError -> errorStream
            else -> inputStream
        } ?: EMPTY_STREAM

    private val EMPTY_STREAM = ByteArrayInputStream(ByteArray(0))
}
