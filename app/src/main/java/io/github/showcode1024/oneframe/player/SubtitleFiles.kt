package io.github.showcode1024.oneframe.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Locale

data class SubtitleFile(val file: File, val title: String)

/** Copies selected subtitles into private temporary storage; filenames never come from the provider. */
object SubtitleFiles {
    const val MAX_BYTES = 8 * 1024 * 1024
    private val extensions = setOf("srt", "ass", "ssa", "vtt", "smi", "sami", "sub", "txt")

    suspend fun importLocal(context: Context, uri: Uri): SubtitleFile = withContext(Dispatchers.IO) {
        if (uri.scheme != "content") throw IOException("请选择设备上的字幕文件")
        val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } }.getOrNull().orEmpty()
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in extensions) throw IOException("请选择 SRT、ASS、SSA、VTT 等文本字幕文件")
        val source = context.contentResolver.openInputStream(uri) ?: throw IOException("无法读取字幕文件")
        source.use { create(context, it, extension, name) }
    }

    suspend fun importServer(context: Context, bytes: ByteArray, name: String): SubtitleFile = withContext(Dispatchers.IO) {
        bytes.inputStream().use { create(context, it, "srt", name) }
    }

    private suspend fun create(context: Context, source: InputStream, extension: String, name: String): SubtitleFile {
        val folder = File(context.cacheDir, "oneframe-subtitles")
        if (!folder.isDirectory && !folder.mkdirs() && !folder.isDirectory) throw IOException("无法创建字幕缓存")
        val file = File.createTempFile("subtitle-", ".$extension", folder)
        try {
            file.outputStream().buffered().use { target ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val size = source.read(buffer)
                    if (size < 0) break
                    total += size
                    if (total > MAX_BYTES) throw IOException("字幕文件超过 8 MB")
                    target.write(buffer, 0, size)
                }
                if (total == 0L) throw IOException("字幕文件为空")
            }
            return SubtitleFile(file, name.filterNot { it.isISOControl() }.take(100).ifBlank { "外部字幕" })
        } catch (error: Exception) { file.delete(); throw error }
    }
}
