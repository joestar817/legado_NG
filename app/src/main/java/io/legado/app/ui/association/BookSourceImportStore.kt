package io.legado.app.ui.association

import io.legado.app.data.entities.BookSource
import io.legado.app.help.source.isEmptyConfiguration
import io.legado.app.help.source.writeSourceJson
import io.legado.app.utils.GSON
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.RandomAccessFile

/** 仅用于内存中的列表展示，不序列化；完整规则存于该次预览的私有临时文件。 */
internal data class BookSourceImportItem(
    val bookSourceUrl: String,
    val bookSourceName: String,
    val bookSourceComment: String?,
    val lastUpdateTime: Long,
    val emptyConfiguration: Boolean,
)

/** 单条追加/读取/编辑；索引不随 URL 去重，保留同文件同标识多版本的原顺序。 */
internal class BookSourceImportStore(cacheDir: File) : Closeable {
    private data class Record(val offset: Long, val size: Long)
    private val path = File.createTempFile("book-source-import-", ".tmp", cacheDir)
    private val file = RandomAccessFile(path, "rw")
    private val records = arrayListOf<Record>()
    private var closed = false

    @Synchronized
    fun append(source: BookSource): BookSourceImportItem {
        records.add(write(source))
        return source.previewItem()
    }

    @Synchronized
    fun replace(index: Int, source: BookSource): BookSourceImportItem {
        require(index in records.indices)
        records[index] = write(source)
        return source.previewItem()
    }

    @Synchronized
    fun read(index: Int): BookSource {
        check(!closed)
        val record = records[index]
        file.seek(record.offset)
        var remaining = record.size
        val input = object : InputStream() {
            override fun read(): Int {
                if (remaining == 0L) return -1
                val value = file.read()
                if (value >= 0) remaining--
                return value
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (length == 0) return 0
                if (remaining == 0L) return -1
                val count = file.read(bytes, offset, minOf(length.toLong(), remaining).toInt())
                if (count > 0) remaining -= count
                return count
            }
        }
        return input.bufferedReader(Charsets.UTF_8).use {
            GSON.fromJson(it, BookSource::class.java)
                ?: error("导入预览配置读取失败")
        }
    }

    @Synchronized
    fun recordSize(index: Int): Long = records[index].size

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            file.close()
        } finally {
            path.delete()
            records.clear()
        }
    }

    private fun write(source: BookSource): Record {
        check(!closed)
        val start = file.length()
        file.seek(start)
        val output = object : OutputStream() {
            override fun write(value: Int) = file.write(value)
            override fun write(bytes: ByteArray, offset: Int, length: Int) = file.write(bytes, offset, length)
        }
        OutputStreamWriter(output, Charsets.UTF_8).buffered().use(source::writeSourceJson)
        return Record(start, file.filePointer - start)
    }

    private fun BookSource.previewItem() = BookSourceImportItem(
        bookSourceUrl, bookSourceName, bookSourceComment, lastUpdateTime, isEmptyConfiguration(),
    )
}
