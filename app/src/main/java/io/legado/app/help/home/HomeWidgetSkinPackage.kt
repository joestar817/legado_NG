package io.legado.app.help.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.Keep
import androidx.annotation.StringRes
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.utils.GSON
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipFile


internal enum class HomeWidgetSkinRenderer(
    val styleId: String,
    val resourceId: String,
    @param:StringRes val titleRes: Int,
    val artwork: Set<String>,
    val optionalArtwork: Set<String> = emptySet(),
) {
    STORYBOOK("storybook", "ng.home_skin.storybook", R.string.home_style_storybook,
        setOf("calendar", "leaves", "listening-small", "listening-large", "reading", "updates")),
    NIGHT_DESK("night", "ng.home_skin.night_desk", R.string.home_style_night, setOf("lamp", "desk", "leaves", "wood")),
    BLUE_FISH("blue_fish", "ng.home_skin.blue_fish", R.string.home_style_blue_fish,
        setOf("reading", "listening", "updates", "waves"), optionalArtwork = setOf("calendar")),
}

@Keep
internal data class HomeWidgetSkinManifest(
    @SerializedName("schemaVersion") val schemaVersion: Int = 0,
    @SerializedName("renderer") val renderer: String = "",
)

internal data class InstalledHomeWidgetSkin(
    val id: String,
    val name: String,
    val renderer: HomeWidgetSkinRenderer,
    val archive: File,
    val revision: String,
    val fromResource: Boolean = false,
    val sizeBytes: Long = 0,
) {
    fun record() = HomeWidgetSkinRecord(id, name, revision, sizeBytes, fromResource)
}

/** A skin is a static archive. The host owns all widget layout and interaction. */
internal object HomeWidgetSkinPackage {
    private const val MANIFEST = "manifest.json"
    private const val PREVIEW = "preview.png"
    private const val MAX_MANIFEST_BYTES = 4096
    private const val MAX_IMAGE_BYTES = 1024 * 1024
    const val MAX_PACKAGE_BYTES = 8L * 1024 * 1024
    private const val MAX_EDGE = 1536
    private const val MAX_PIXELS = 8_000_000L

    fun read(
        file: File,
        imageSize: (ByteArray) -> Pair<Int, Int> = ::bitmapSize,
    ): HomeWidgetSkinRenderer {
        require(file.length() in 1..MAX_PACKAGE_BYTES) { "组件皮肤大小无效" }
        return ZipFile(file).use { zip ->
            require(zip.size() in 2..(2 + HomeWidgetSkinRenderer.entries.maxOf { it.artwork.size + it.optionalArtwork.size })) {
                "组件皮肤文件数量无效"
            }
            val manifest = GSON.fromJson(
                readEntry(zip, MANIFEST, MAX_MANIFEST_BYTES).toString(Charsets.UTF_8),
                HomeWidgetSkinManifest::class.java,
            )
            require(manifest?.schemaVersion == 1) { "组件皮肤版本不兼容" }
            val renderer = HomeWidgetSkinRenderer.entries.firstOrNull { it.styleId == manifest.renderer }
                ?: error("组件皮肤样式不受支持")
            val names = zip.entries().asSequence().map { it.name }.toList()
            val requiredImages = renderer.artwork.map(::artworkPath)
            val optionalImages = renderer.optionalArtwork.map(::artworkPath)
            val images = requiredImages + optionalImages.filter { it in names }
            val required = requiredImages.toSet() + MANIFEST
            val hasPreview = PREVIEW in names
            val allowed = required + optionalImages + PREVIEW
            require(names.size == names.toSet().size && names.containsAll(required) && names.all { it in allowed }) {
                "组件皮肤素材不完整或包含未知文件"
            }
            var pixels = 0L
            images.forEach { path ->
                val bytes = readEntry(zip, path, MAX_IMAGE_BYTES)
                require(bytes.size >= 16 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) &&
                    bytes.copyOfRange(8, 12).contentEquals("WEBP".toByteArray())) { "组件皮肤素材必须为 WebP" }
                val (width, height) = imageSize(bytes)
                pixels += width.toLong() * height
                require(width in 1..MAX_EDGE && height in 1..MAX_EDGE && pixels <= MAX_PIXELS) {
                    "组件皮肤素材尺寸无效"
                }
            }
            if (hasPreview) preview(zip)
            renderer
        }
    }

    fun artworkPath(role: String) = "artwork/$role.webp"

    fun previewBitmap(file: File, sampleSize: Int): Bitmap? = ZipFile(file).use { zip ->
        if (zip.getEntry(PREVIEW) == null) return@use null
        val bytes = preview(zip)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    fun preview(zip: ZipFile): ByteArray {
        val bytes = readEntry(zip, PREVIEW, MAX_IMAGE_BYTES)
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        require(bytes.size >= 33 && bytes.copyOfRange(0, 8).contentEquals(signature)) { "组件皮肤预览必须为 PNG" }
        val header = java.nio.ByteBuffer.wrap(bytes)
        require(header.getInt(8) == 13 && header.getInt(12) == 0x49484452 &&
            header.getInt(16) == 768 && header.getInt(20) == 768) { "组件皮肤预览尺寸无效" }
        return bytes
    }

    private fun bitmapSize(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outMimeType == "image/webp") { "组件皮肤素材格式无效" }
        if (options.outWidth in 1..MAX_EDGE && options.outHeight in 1..MAX_EDGE) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = 4 })
                ?: error("组件皮肤素材无法解码")
            bitmap.recycle()
        }
        return options.outWidth to options.outHeight
    }

    private fun readEntry(zip: ZipFile, name: String, maximum: Int): ByteArray {
        val entry = zip.getEntry(name) ?: error("组件皮肤缺少素材")
        require(!entry.isDirectory && entry.size in 1L..maximum.toLong()) { "组件皮肤素材大小无效" }
        val bytes = ByteArray(entry.size.toInt())
        zip.getInputStream(entry).use { input ->
            var offset = 0
            while (offset < bytes.size) {
                val count = input.read(bytes, offset, bytes.size - offset)
                require(count > 0) { "组件皮肤素材不完整" }
                offset += count
            }
            require(input.read() == -1) { "组件皮肤素材超过声明大小" }
        }
        require(CRC32().apply { update(bytes) }.value == entry.crc) { "组件皮肤素材校验失败" }
        return bytes
    }
}
