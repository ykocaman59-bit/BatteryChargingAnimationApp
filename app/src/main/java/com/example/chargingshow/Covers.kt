package com.example.chargingshow

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Kapak resimleri.
 *  - "asset:wallpapers/osmanli_001.webp" -> uygulamanın içindeki hazır resimler
 *  - "file:cover_custom_123.jpg"          -> cihazdan seçilen resim (uygulamaya kopyalanır)
 * assets/wallpapers klasörüne yeni resim atarsan otomatik listeye eklenir.
 */
object Covers {
    private const val DIR = "wallpapers"

    fun listAssets(context: Context): List<String> {
        val names = context.assets.list(DIR) ?: emptyArray()
        return names
            .filter { it.endsWith(".webp") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }
            .sorted()
            .map { "asset:$DIR/$it" }
    }

    private fun open(context: Context, ref: String): InputStream? = when {
        ref.startsWith("asset:") -> context.assets.open(ref.removePrefix("asset:"))
        ref.startsWith("file:") -> {
            val f = File(context.filesDir, ref.removePrefix("file:"))
            if (f.exists()) f.inputStream() else null
        }
        else -> null
    }

    private fun sampleSize(w: Int, h: Int, reqW: Int, reqH: Int): Int {
        var s = 1
        if (w > 0 && h > 0) {
            while (w / (s * 2) >= reqW && h / (s * 2) >= reqH) s *= 2
        }
        return s
    }

    /** Resmi, en az reqW x reqH olacak şekilde küçülterek açar. */
    fun decode(context: Context, ref: String, reqW: Int, reqH: Int): Bitmap? {
        if (ref.isEmpty()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            open(context, ref)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
            }
            open(context, ref)?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            null
        }
    }

    /** Galeriden seçilen resmi uygulamanın içine kopyalar. Başarılıysa "file:..." döner. */
    fun saveCustom(context: Context, uri: Uri): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1200, 2400)
            }
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            context.filesDir.listFiles()?.filter { it.name.startsWith("cover_custom_") }?.forEach { it.delete() }
            val name = "cover_custom_${System.currentTimeMillis()}.jpg"
            File(context.filesDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            "file:$name"
        } catch (e: Exception) {
            null
        }
    }

    private fun centerCrop(src: Bitmap, tw: Int, th: Int): Bitmap {
        val scale = max(tw / src.width.toFloat(), th / src.height.toFloat())
        val sw = min(src.width, (tw / scale).toInt())
        val sh = min(src.height, (th / scale).toInt())
        val x = (src.width - sw) / 2
        val y = (src.height - sh) / 2
        val cropped = Bitmap.createBitmap(src, x, y, sw, sh)
        return Bitmap.createScaledBitmap(cropped, tw, th, true)
    }

    /** Resmi KİLİT EKRANI duvar kağıdı olarak ayarlar. */
    fun applyToLock(context: Context, ref: String): Boolean {
        return try {
            val dm = context.resources.displayMetrics
            val bmp = decode(context, ref, dm.widthPixels, dm.heightPixels) ?: return false
            val fitted = centerCrop(bmp, dm.widthPixels, dm.heightPixels)
            WallpaperManager.getInstance(context)
                .setBitmap(fitted, null, true, WallpaperManager.FLAG_LOCK)
            true
        } catch (e: Exception) {
            false
        }
    }
}
