package ao.julogic404.kubixapics.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.caverock.androidsvg.SVG
import com.caverock.androidsvg.SVGParseException
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Descodifica imagens de varios formatos (JPG, PNG, WEBP, GIF, BMP, TIFF, SVG,
 * AVIF, HEIC/HEIF) aplicando a orientacao EXIF e um limite de resolucao.
 *
 * - API 28+: ImageDecoder (aplica EXIF; suporta HEIC/HEIF nativamente onde existir);
 * - API < 28: BitmapFactory + rotacao EXIF manual;
 * - SVG (qualquer API): AndroidSVG;
 * - GIF: BitmapFactory decodifica o primeiro fotograma.
 */
object ImagemDecodificadorEx {

    private const val THUMB_MAX = 220

    data class Decodificada(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int,
        val bytes: ByteArray?,
        val mimeType: String?
    )

    fun decode(context: Context, uri: Uri, maxDimension: Int): Decodificada? {
        val mime = queryMimeType(context, uri)
        val bytes = readBytes(context, uri) ?: return null

        return try {
            if (isSvg(mime, bytes)) decodeSvg(bytes, maxDimension)
            else decodeRaster(context, uri, bytes, maxDimension)
        } catch (t: Throwable) {
            null
        }
    }

    /**
     * Descodifica uma imagem raster com varias tentativas, para que uma imagem
     * "dificil" (PNG enorme, truncado, com perfil de cor largo...) nao falhe so
     * porque o primeiro descodificador nao a aceitou:
     *  1. ImageDecoder (API 28+), que e o mais fiel;
     *  2. BitmapFactory, mais tolerante com ficheiros incompletos/estranhos;
     *  3. se faltar memoria, repete com um limite de resolucao menor.
     */
    private fun decodeRaster(context: Context, uri: Uri, bytes: ByteArray, maxDimension: Int): Decodificada? {
        val limits = listOf(maxDimension, 8192, 4096, 2048)
            .filter { it == maxDimension || (it > 0 && (maxDimension <= 0 || it < maxDimension)) }
            .distinct()
        for (limit in limits) {
            val decoded = tryDecode(context, uri, bytes, limit)
            if (decoded != null) return decoded
        }
        return null
    }

    private fun tryDecode(context: Context, uri: Uri, bytes: ByteArray, limit: Int): Decodificada? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                decodeModern(bytes, limit)?.let { return it }
            } catch (t: Throwable) {
                // segue para o BitmapFactory
            }
        }
        return try {
            decodeLegacy(context, uri, bytes, limit)
        } catch (t: Throwable) {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeModern(bytes: ByteArray, maxDimension: Int): Decodificada? {
        val source = ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // PNG com perfil de cor largo / 16 bits vinham em RGBA_F16 (o dobro da
            // memoria e nem todos os codificadores aceitam). Forcamos sRGB 8 bits.
            decoder.setTargetColorSpace(
                android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB)
            )
            // Ficheiro truncado/com erro no fim: devolve o que foi lido em vez de falhar.
            decoder.setOnPartialImageListener { true }
            val w = info.size.width
            val h = info.size.height
            val longest = max(w, h)
            if (maxDimension > 0 && longest > maxDimension) {
                val scale = maxDimension.toFloat() / longest
                decoder.setTargetSize(
                    (w * scale).roundToInt().coerceAtLeast(1),
                    (h * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }
        val result = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            if (copy != null) { bitmap.recycle(); copy } else bitmap
        }
        return Decodificada(result, result.width, result.height, bytes, null)
    }

    private fun decodeLegacy(context: Context, uri: Uri, bytes: ByteArray, maxDimension: Int): Decodificada? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val longest = max(bounds.outWidth, bounds.outHeight)
        val target = if (maxDimension > 0) minOf(maxDimension, longest) else longest
        var sample = 1
        while (longest / (sample * 2) >= target) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val rotated = applyExifRotation(context, uri, decoded)
        val scaled = scaleToMax(rotated, maxDimension)
        if (scaled !== rotated && !rotated.isRecycled) rotated.recycle()
        if (scaled !== decoded && !decoded.isRecycled) decoded.recycle()
        return Decodificada(scaled, scaled.width, scaled.height, bytes, null)
    }

    private fun decodeSvg(bytes: ByteArray, maxDimension: Int): Decodificada? {
        val svg = try {
            SVG.getFromInputStream(bytes.inputStream())
        } catch (e: SVGParseException) {
            return null
        }
        val docWidth = svg.documentWidth
        val docHeight = svg.documentHeight
        if (docWidth <= 0f || docHeight <= 0f) return null

        val scale = if (maxDimension > 0) {
            val longest = max(docWidth, docHeight)
            if (longest > maxDimension) maxDimension / longest else 1f
        } else 1f
        var w = (docWidth * scale).roundToInt().coerceAtLeast(1)
        var h = (docHeight * scale).roundToInt().coerceAtLeast(1)
        // Limite de seguranca para SVG com dimensoes extremas
        val cap = if (maxDimension > 0) maxDimension else 4096
        if (max(w, h) > cap) {
            val f = cap.toFloat() / max(w, h)
            w = (w * f).roundToInt().coerceAtLeast(1)
            h = (h * f).roundToInt().coerceAtLeast(1)
        }

        val bitmap = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (t: Throwable) {
            return null
        }
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
        svg.renderToCanvas(canvas)
        return Decodificada(bitmap, w, h, bytes, "image/svg+xml")
    }

    /** Assinaturas (magic bytes) de formatos raster: nunca sao SVG, haja o texto que houver. */
    private fun hasRasterSignature(b: ByteArray): Boolean {
        if (b.size < 12) return false
        fun at(i: Int) = b[i].toInt() and 0xFF
        return (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) || // PNG
            (at(0) == 0xFF && at(1) == 0xD8) ||                                      // JPEG
            (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46) ||                     // GIF
            (at(0) == 0x42 && at(1) == 0x4D) ||                                      // BMP
            (at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46) ||    // RIFF (WebP)
            (at(0) == 0x49 && at(1) == 0x49 && at(2) == 0x2A && at(3) == 0x00) ||    // TIFF (LE)
            (at(0) == 0x4D && at(1) == 0x4D && at(2) == 0x00 && at(3) == 0x2A) ||    // TIFF (BE)
            (at(4) == 0x66 && at(5) == 0x74 && at(6) == 0x79 && at(7) == 0x70)       // HEIC/AVIF
    }

    private fun isSvg(mime: String?, bytes: ByteArray): Boolean {
        // Um PNG exportado de Inkscape/Figma traz XML/"svg" nos metadados (XMP/tEXt)
        // dentro dos primeiros bytes: a heuristica de texto classificava-o como SVG
        // e a descodificacao falhava. Os bytes magicos decidem primeiro.
        if (hasRasterSignature(bytes)) return false
        if (mime?.contains("svg") == true) return true
        // Heuristica: XML com <svg. Le apenas os primeiros 512 bytes (sem
        // copiar o ficheiro inteiro) e descodifica-os como UTF-8.
        val head = String(bytes, 0, minOf(512, bytes.size), Charsets.UTF_8).lowercase()
        return head.contains("<svg") || (head.contains("<?xml") && head.contains("svg"))
    }

    fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (t: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return rotateByExif(bitmap, orientation)
    }

    fun rotateByExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (t: Throwable) {
            bitmap
        }
    }

    fun scaleToMax(bitmap: Bitmap, maxDimension: Int): Bitmap {
        if (maxDimension <= 0) return bitmap
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } catch (t: Throwable) {
            bitmap
        }
    }

    /** Miniatura pequena para a lista (o objeto continua a guardar o bitmap completo apenas durante o processo). */
    fun createThumbnail(bitmap: Bitmap): Bitmap? = try {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= THUMB_MAX) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            val scale = THUMB_MAX.toFloat() / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true
            )
        }
    } catch (t: Throwable) {
        null
    }

    fun queryMimeType(context: Context, uri: Uri): String? = try {
        context.contentResolver.getType(uri)
    } catch (t: Throwable) {
        null
    }

    fun readBytes(context: Context, uri: Uri): ByteArray? = try {
        val stream = context.contentResolver.openInputStream(uri) ?: return null
        val out = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
        }
        out.toByteArray()
    } catch (t: Throwable) {
        null
    }
}
