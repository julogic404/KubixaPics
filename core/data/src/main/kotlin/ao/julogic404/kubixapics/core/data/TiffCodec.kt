package ao.julogic404.kubixapics.core.data

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Escritor de TIFF (Baseline, sem compressao).
 *
 * O Android descodifica TIFF nativamente, mas nao o codifica. Este codec escreve
 * um TIFF simples (little-endian) com os 9 tags obrigatorios do perfil baseline:
 * ImageWidth, ImageLength, BitsPerSample, Compression=1, PhotometricInterpretation=RGB,
 * StripOffsets, SamplesPerPixel, RowsPerStrip, StripByteCounts.
 *
 * Formato: cabeçalho (8 bytes: "II" 42 offset) + IFD (2 + N*12 + 4) + dados.
 * Cada entrada IFD tem 12 bytes: tag(2) tipo(2) contagem(4) valor/offset(4).
 * Quando os dados de uma entrada nao cabem nos 4 bytes do campo valor, o campo
 * guarda um OFFSET para os dados reais (ex.: BitsPerSample com contagem 3).
 */
object TiffCodec {

    fun encode(bitmap: Bitmap): ByteArray {
        val w = bitmap.width
        val h = bitmap.height
        val hasAlpha = bitmap.hasAlpha()
        val samplesPerPixel = if (hasAlpha) 4 else 3

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        // Pixeis por linha, cada pixel com samplesPerPixel bytes (R,G,B[,A]).
        val stripByteCounts = w * h * samplesPerPixel
        val pixelData = ByteArray(stripByteCounts)
        var p = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                pixelData[p++] = ((c shr 16) and 0xFF).toByte() // R
                pixelData[p++] = ((c shr 8) and 0xFF).toByte()  // G
                pixelData[p++] = (c and 0xFF).toByte()          // B
                if (hasAlpha) pixelData[p++] = ((c ushr 24) and 0xFF).toByte() // A
            }
        }

        val ifdOffset = 8
        val numEntries = if (hasAlpha) 10 else 9
        val ifdEnd = ifdOffset + 2 + numEntries * 12 + 4
        val bitsPerSampleOffset = ifdEnd
        val pixelDataOffset = bitsPerSampleOffset + samplesPerPixel * 2

        val out = ByteArrayOutputStream(pixelDataOffset + pixelData.size + 16)
        // Header: "II" (little-endian) + 42 + offset do IFD
        out.write('I'.code); out.write('I'.code)
        writeLeShort(out, 42)
        writeLeInt(out, ifdOffset)

        writeLeShort(out, numEntries)

        // 1. ImageWidth (256) LONG(4) count 1
        writeLeShort(out, 256); writeLeShort(out, 4); writeLeInt(out, 1); writeLeInt(out, w)
        // 2. ImageLength (257) LONG(4) count 1
        writeLeShort(out, 257); writeLeShort(out, 4); writeLeInt(out, 1); writeLeInt(out, h)
        // 3. BitsPerSample (258) SHORT(3) count = samplesPerPixel -> offset
        writeLeShort(out, 258); writeLeShort(out, 3); writeLeInt(out, samplesPerPixel)
        writeLeInt(out, bitsPerSampleOffset)
        // 4. Compression (259) SHORT(3) count 1 = 1 (none)
        writeLeShort(out, 259); writeLeShort(out, 3); writeLeInt(out, 1); writeLeShort(out, 1); writeLeShort(out, 0)
        // 5. PhotometricInterpretation (262) SHORT(3) count 1 = 2 (RGB)
        writeLeShort(out, 262); writeLeShort(out, 3); writeLeInt(out, 1); writeLeShort(out, 2); writeLeShort(out, 0)
        // 6. StripOffsets (273) LONG(4) count 1
        writeLeShort(out, 273); writeLeShort(out, 4); writeLeInt(out, 1); writeLeInt(out, pixelDataOffset)
        // 7. SamplesPerPixel (277) SHORT(3) count 1
        writeLeShort(out, 277); writeLeShort(out, 3); writeLeInt(out, 1); writeLeShort(out, samplesPerPixel); writeLeShort(out, 0)
        // 8. RowsPerStrip (278) LONG(4) count 1 = h (uma tira unica)
        writeLeShort(out, 278); writeLeShort(out, 4); writeLeInt(out, 1); writeLeInt(out, h)
        // 9. StripByteCounts (279) LONG(4) count 1
        writeLeShort(out, 279); writeLeShort(out, 4); writeLeInt(out, 1); writeLeInt(out, stripByteCounts)
        // 10. ExtraSamples (338) SHORT(3) count 1 = 2 (alfa nao associado); so com canal alfa.
        //     Sem este tag os leitores tratam o 4.o canal como lixo/ignoram a transparencia.
        if (hasAlpha) {
            writeLeShort(out, 338); writeLeShort(out, 3); writeLeInt(out, 1); writeLeShort(out, 2); writeLeShort(out, 0)
        }

        // next IFD = 0
        writeLeInt(out, 0)

        // Dados reais de BitsPerSample (todos 8)
        repeat(samplesPerPixel) { writeLeShort(out, 8) }

        // Pixeis
        out.write(pixelData)

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
