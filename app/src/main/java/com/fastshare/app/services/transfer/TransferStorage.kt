package com.fastshare.app.services.transfer

import android.content.Context
import com.fastshare.app.core.util.FastShareError
import com.fastshare.app.core.util.Outcome
import com.fastshare.app.core.util.asFailure
import com.fastshare.app.core.util.asSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage layer for received payloads. Files are written to a `.partial` sibling and
 * atomically renamed on completion, so a crash mid-transfer never leaves a corrupt
 * "finished" file. Resume is supported when the receiver reports an offset > 0.
 */
@Singleton
class TransferStorage @Inject constructor(
    private val context: Context,
) {
    private val tag = "TransferStorage"

    suspend fun writeStream(
        target: File,
        stream: ByteReadChannel,
        offset: Long,
        totalSize: Long,
        onProgress: (Long) -> Unit,
    ): Outcome<Long> = withContext(Dispatchers.IO) {
        var written: Long = offset
        try {
            val partial = File(target.parentFile, target.name + ".partial")
            partial.parentFile?.mkdirs()
            when {
                offset > 0 && partial.exists() -> RandomAccessFile(partial, "rw").use { raf ->
                    raf.seek(offset)
                    written = copyTo(stream, raf.fd.outputStream() as java.io.OutputStream, written, onProgress)
                }
                else -> partial.outputStream().use { sink ->
                    written = copyTo(stream, sink, 0L, onProgress)
                }
            }
            if (totalSize > 0 && written != totalSize) {
                partial.delete()
                return@withContext FastShareError.TransferFailed("storage", "incomplete: $written/$totalSize").asFailure()
            }
            if (partial.exists() && !partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            written.asSuccess()
        } catch (e: Exception) {
            FastShareError.TransferFailed("storage", e.message ?: "write failed", e).asFailure()
        }
    }

    private suspend fun copyTo(
        source: ByteReadChannel,
        sink: java.io.OutputStream,
        initialWritten: Long,
        onProgress: (Long) -> Unit,
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var total = initialWritten
        while (true) {
            val read = source.readAvailable(buffer)
            if (read < 0) break
            sink.write(buffer, 0, read)
            total += read
            onProgress(total)
        }
        sink.flush()
        return total
    }

    /** Writes an inline text/clipboard payload to a .txt file. */
    fun saveInline(name: String, content: String): File? = runCatching {
        val dir = File(context.filesDir, "incoming").apply { mkdirs() }
        File(dir, name).apply { writeText(content) }
    }.getOrNull()

    fun sha256(file: File): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02X".format(it) }
    }.getOrElse { "" }
}
