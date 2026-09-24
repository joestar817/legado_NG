package io.legado.app.ui.book.read.epub

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import androidx.core.graphics.drawable.DrawableCompat
import io.legado.app.ui.book.read.page.provider.ReadNoteMarkerStyle
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/** Projects the existing marker drawable; does not own bookmark data or actions. */
internal class EpubNoteMarker(private val context: Context) {
    private var signature: Pair<Int, Int>? = null
    private var value: JSONObject? = null

    fun style(): JSONObject? {
        val color = ReadNoteMarkerStyle.color()
        val size = (ReadNoteMarkerStyle.SIZE_DP * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        if (signature == (color to size)) return value
        val drawable = ReadNoteMarkerStyle.drawable(context) ?: return null
        DrawableCompat.setTint(drawable, color)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bytes = try {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(Canvas(bitmap))
            ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        } finally { bitmap.recycle() }
        value = JSONObject().put("size", ReadNoteMarkerStyle.SIZE_DP).put("gap", ReadNoteMarkerStyle.GAP_DP)
            .put("trailing", ReadNoteMarkerStyle.TRAILING_GAP_DP).put("touch", ReadNoteMarkerStyle.TOUCH_SIZE_DP)
            .put("image", "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
        signature = color to size
        return value
    }
}
