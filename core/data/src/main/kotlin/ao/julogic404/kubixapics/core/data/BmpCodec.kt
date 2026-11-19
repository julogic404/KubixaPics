/*
 * Copyright 2026 JuLogic 404
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ao.julogic404.kubixapics.core.data

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Escritor de ficheiros BMP (Windows BITMAPINFOHEADER, sem compressao).
 *
 * O Android descodifica BMP nativamente, mas nao o codifica. Este codec cobre
 * o formato de saida, suportando 24 bit (RGB) e 32 bit (RGBA com transparencia).
 */
object BmpCodec {

    fun encode(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val hasAlpha = bitmap.hasAlpha()
        val bytesPerPixel = if (hasAlpha) 4 else 3
        val rowSize = ((bytesPerPixel * w + 3) / 4) * 4
        val pixelArraySize = rowSize * h
        val fileSize = 54 + pixelArraySize

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val out = ByteArrayOutputStream(fileSize)
        writeLeShort(out, 0x4D42)          // 'BM'
        writeLeInt(out, fileSize)
        writeLeInt(out, 0)                 // campos reservados
        writeLeInt(out, 54)                // offset dos pixeis

        writeLeInt(out, 40)                // tamanho do BITMAPINFOHEADER
        writeLeInt(out, w)
        writeLeInt(out, h)                 // positivo = ordem de baixo para cima
        writeLeShort(out, 1)               // planos
        writeLeShort(out, bytesPerPixel * 8)
        writeLeInt(out, 0)                 // BI_RGB
        writeLeInt(out, pixelArraySize)
        writeLeInt(out, 2835)              // 72 dpi (eixo X)
        writeLeInt(out, 2835)              // 72 dpi (eixo Y)
        writeLeInt(out, 0)
        writeLeInt(out, 0)

        for (y in h - 1 downTo 0) {
            var x = 0
            while (x < w) {
                val c = pixels[y * w + x]
                out.write(c and 0xFF)
                out.write((c shr 8) and 0xFF)
                out.write((c shr 16) and 0xFF)
                if (hasAlpha) out.write((c ushr 24) and 0xFF)
                x++
            }
            val padding = rowSize - bytesPerPixel * w
            repeat(padding) { out.write(0) }
        }
        return out.toByteArray()
    }

    private fun writeLeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    private fun writeLeInt(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }
}
