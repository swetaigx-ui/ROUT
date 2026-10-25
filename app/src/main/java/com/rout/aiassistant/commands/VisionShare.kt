package com.rout.aiassistant.commands

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import com.rout.aiassistant.ai.SharedImage
import java.io.ByteArrayOutputStream

/**
 * What the user intentionally shared with ROUT (a photo, or rendered PDF pages).
 * Nothing is ever read from the gallery silently - only an explicit share lands here.
 */
data class VisionDoc(
    val kind: String,           // "photo" | "pdf"
    val label: String,
    val totalPages: Int,
    val shownPages: Int,
    val images: List<SharedImage>
) {
    /** Honest disclosure: how many pages were actually processed. */
    fun disclosure(): String = when {
        kind == "pdf" && totalPages > shownPages -> "[Pehle $shownPages/$totalPages panne dekh ke]"
        kind == "pdf" -> "[Poore $totalPages panne dekh ke]"
        else -> ""
    }
}

object VisionShare {
    private const val MAX_DIM = 1280
    const val MAX_PDF_PAGES = 5

    fun loadSharedImage(context: Context, uri: Uri): SharedImage? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) > MAX_DIM) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        SharedImage(encodeJpeg(scale(bmp)), "image/jpeg")
    }.getOrNull()

    /** Renders the first [maxPages] PDF pages to images; returns total page count for honest disclosure. */
    fun loadSharedPdf(context: Context, uri: Uri, label: String? = null, maxPages: Int = MAX_PDF_PAGES): VisionDoc? = runCatching {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { fd ->
            PdfRenderer(fd).use { r ->
                val total = r.pageCount
                if (total <= 0) return null
                val shown = minOf(total, maxPages)
                val images = ArrayList<SharedImage>(shown)
                for (i in 0 until shown) {
                    val page = r.openPage(i)
                    val scale = minOf(1f, MAX_DIM.toFloat() / maxOf(page.width, page.height).toFloat())
                    val w = maxOf(1, (page.width * scale).toInt())
                    val h = maxOf(1, (page.height * scale).toInt())
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    images.add(SharedImage(encodeJpeg(bmp), "image/jpeg"))
                }
                VisionDoc("pdf", label ?: "document.pdf", total, shown, images)
            }
        }
    }.getOrNull()

    private fun scale(bmp: Bitmap): Bitmap {
        val m = maxOf(bmp.width, bmp.height)
        if (m <= MAX_DIM) return bmp
        val s = MAX_DIM.toFloat() / m.toFloat()
        return Bitmap.createScaledBitmap(bmp, maxOf(1, (bmp.width * s).toInt()), maxOf(1, (bmp.height * s).toInt()), true)
    }

    private fun encodeJpeg(bmp: Bitmap): String {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 78, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
