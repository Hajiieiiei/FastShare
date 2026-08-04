package com.fastshare.app.services.transfer

import android.content.Context
import android.net.Uri
import android.util.Log
import com.fastshare.app.data.network.protocol.CancelRequest
import com.fastshare.app.data.network.protocol.HelloRequest
import com.fastshare.app.data.network.protocol.HelloResponse
import com.fastshare.app.data.network.protocol.Protocol
import com.fastshare.app.data.network.protocol.TransferRequest
import com.fastshare.app.data.network.protocol.TransferResponse
import com.fastshare.app.data.network.protocol.VerifyRequest
import com.fastshare.app.data.network.protocol.VerifyResponse
import com.fastshare.app.domain.model.TransferItem
import com.fastshare.app.services.security.CryptoEngine
import com.fastshare.app.services.transfer.model.SendRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferHttpClient @Inject constructor(
    private val cryptoEngine: CryptoEngine,
    private val context: Context,
) {
    private val tag = "TransferHttpClient"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun hello(ip: String, port: Int, request: HelloRequest): HelloResponse? = withContext(Dispatchers.IO) {
        runCatching {
            val body = Protocol.json.encodeToString(HelloRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())
            val http = Request.Builder()
                .url("http://$ip:$port${Protocol.PATH_HELLO}")
                .post(body)
                .header(Protocol.HEADER_PROTOCOL, Protocol.VERSION.toString())
                .build()
            client.newCall(http).execute().use { response ->
                if (!response.isSuccessful) return@use null
                Protocol.json.decodeFromString(HelloResponse.serializer(), response.body?.string() ?: return@use null)
            }
        }.getOrNull()
    }

    suspend fun requestTransfer(
        ip: String,
        port: Int,
        sessionId: String,
        request: TransferRequest,
    ): TransferResponse = withContext(Dispatchers.IO) {
        val body = Protocol.json.encodeToString(TransferRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())
        val http = Request.Builder()
            .url("http://$ip:$port${Protocol.PATH_TRANSFER_REQUEST}")
            .post(body)
            .header(Protocol.HEADER_SESSION, sessionId)
            .build()
        client.newCall(http).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Transfer request failed: ${'$'}{response.code}")
            Protocol.json.decodeFromString(
                TransferResponse.serializer(),
                response.body?.string() ?: throw IOException("empty body"),
            )
        }
    }

    suspend fun uploadItem(
        ip: String,
        port: Int,
        sessionId: String,
        item: TransferItem,
        itemToken: String,
        offset: Long,
        speedLimitKbps: Int,
        onProgress: (Long, Long) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        if (item.isInline) return@withContext uploadInline(ip, port, sessionId, item, onProgress)
        val contentUri = item.localUri ?: return@withContext false
        val input = context.contentResolver.openInputStream(Uri.parse(contentUri)) ?: return@withContext false
        input.use { stream ->
            stream.skip(offset)
            val requestBody = object : RequestBody() {
                override fun contentType() = null
                override fun contentLength(): Long = item.size - offset
                override fun writeTo(sink: okio.BufferedSink) {
                    val buffer = ByteArray(Protocol.STREAM_CHUNK_SIZE)
                    var sent = 0L
                    var lastEmit = System.currentTimeMillis()
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        if (speedLimitKbps > 0) {
                            val limitMicros = (read / (speedLimitKbps * 128.0) * 1_000_000).toLong()
                            val start = System.nanoTime()
                            sink.write(buffer, 0, read)
                            sink.flush()
                            val remaining = limitMicros - (System.nanoTime() - start) / 1000
                            if (remaining > 0) Thread.sleep(remaining / 1000, (remaining % 1000).toInt())
                        } else {
                            sink.write(buffer, 0, read)
                        }
                        sent += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= Protocol.PROGRESS_EMIT_INTERVAL_MS || sent == item.size) {
                            onProgress(sent, item.size)
                            lastEmit = now
                        }
                    }
                }
            }
            val http = Request.Builder()
                .url("http://${'$'}ip:${'$'}port${Protocol.PATH_TRANSFER_DATA}")
                .post(requestBody)
                .header(Protocol.HEADER_SESSION, sessionId)
                .header(Protocol.HEADER_ITEM_ID, item.id)
                .header(Protocol.HEADER_OFFSET, offset.toString())
                .header(Protocol.HEADER_TOTAL_SIZE, item.size.toString())
                .header(Protocol.HEADER_CHECKSUM, item.sha256 ?: "")
                .header(Protocol.HEADER_TOKEN, itemToken)
                .build()
            client.newCall(http).execute().use { response -> response.isSuccessful }
        }
    }

    private fun uploadInline(
        ip: String,
        port: Int,
        sessionId: String,
        item: TransferItem,
        onProgress: (Long, Long) -> Unit,
    ): Boolean {
        val bytes = item.inlineContent?.toByteArray(Charsets.UTF_8) ?: return false
        val body = bytes.toRequestBody("text/plain".toMediaType())
        val http = Request.Builder()
            .url("http://${'$'}ip:${'$'}port${Protocol.PATH_TRANSFER_DATA}")
            .post(body)
            .header(Protocol.HEADER_SESSION, sessionId)
            .header(Protocol.HEADER_ITEM_ID, item.id)
            .header(Protocol.HEADER_TOKEN, "inline")
            .build()
        client.newCall(http).execute().use { _ ->
            onProgress(bytes.size.toLong(), bytes.size.toLong())
            return true
        }
    }

    suspend fun verifyTransfer(ip: String, port: Int, request: VerifyRequest): VerifyResponse = withContext(Dispatchers.IO) {
        val body = Protocol.json.encodeToString(VerifyRequest.serializer(), request)
            .toRequestBody("application/json".toMediaType())
        val http = Request.Builder()
            .url("http://${'$'}ip:${'$'}port${Protocol.PATH_TRANSFER_VERIFY}")
            .post(body)
            .header(Protocol.HEADER_SESSION, request.sessionId)
            .build()
        client.newCall(http).execute().use { response ->
            Protocol.json.decodeFromString(
                VerifyResponse.serializer(),
                response.body?.string() ?: throw IOException("empty"),
            )
        }
    }

    suspend fun cancel(ip: String, port: Int, request: CancelRequest) {
        withContext(Dispatchers.IO) {
            runCatching {
                val body = Protocol.json.encodeToString(CancelRequest.serializer(), request)
                    .toRequestBody("application/json".toMediaType())
                val http = Request.Builder()
                    .url("http://${'$'}ip:${'$'}port${Protocol.PATH_TRANSFER_CANCEL}")
                    .post(body)
                    .header(Protocol.HEADER_SESSION, request.sessionId)
                    .build()
                client.newCall(http).execute().close()
            }
        }
    }
}
