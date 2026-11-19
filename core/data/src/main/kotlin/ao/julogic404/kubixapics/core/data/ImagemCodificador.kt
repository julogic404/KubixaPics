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
import ao.julogic404.kubixapics.core.common.model.ImagemFormato
import java.io.ByteArrayOutputStream

/**
 * Estados de codificacao (sao retornados juntamente com os bytes para a UI
 * conseguir distinguir "converteu mas nao reduziu" de "formato nao suportado").
 */
sealed class ResultadoCodificacao {
    data class Sucesso(val bytes: ByteArray, val width: Int, val height: Int) : ResultadoCodificacao()
    data class Erro(val message: String) : ResultadoCodificacao()
}

/**
 * Motor de codificacao de imagens.
 *
 * Suporta saida em: WEBP, PNG, JPG, AVIF, BMP, TIFF.
 * - Preserva transparencia quando o formato suporta (WEBP, PNG, AVIF, BMP, TIFF);
 * - Achata sobre branco para JPG (evita fundo preto);
 * - Para AVIF usa a biblioteca avif-coder (io.github.awxkee), que funciona em
 *   APIs 24+ mesmo onde o Android nao expoe codificacao AVIF nativa.
 */
object ImagemCodificador {

    fun encode(bitmap: Bitmap, format: ImagemFormato, quality: Int): ResultadoCodificacao {
        if (bitmap.isRecycled) return ResultadoCodificacao.Erro("Bitmap reciclado")
        return try {
            when (format) {
                ImagemFormato.WEBP -> CodificadorComPerda.webp(bitmap, quality)
                ImagemFormato.PNG -> CodificadorSemPerda.png(bitmap)
                ImagemFormato.JPG -> jpg(bitmap, quality)
                ImagemFormato.AVIF -> avif(bitmap, quality)
                ImagemFormato.BMP -> ResultadoCodificacao.Sucesso(BmpCodec.encode(bitmap), bitmap.width, bitmap.height)
                ImagemFormato.TIFF -> ResultadoCodificacao.Sucesso(TiffCodec.encode(bitmap), bitmap.width, bitmap.height)
            }
        } catch (t: Throwable) {
            ResultadoCodificacao.Erro(t.message ?: "Erro ao codificar")
        }
    }

    private fun jpg(bitmap: Bitmap, quality: Int): ResultadoCodificacao {
        // Achatar sobre branco: JPG nao tem canal alfa.
        val source = flattenToOpaque(bitmap)
        try {
            val out = ByteArrayOutputStream()
            source.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), out)
            return ResultadoCodificacao.Sucesso(out.toByteArray(), source.width, source.height)
        } finally {
            if (source !== bitmap && !source.isRecycled) source.recycle()
        }
    }

    private fun avif(bitmap: Bitmap, quality: Int): ResultadoCodificacao =
        PonteAvif.encode(bitmap, quality)

    /** Flatten de transparencia sobre um fundo branco. */
    fun flattenToOpaque(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return out
    }

    private object CodificadorComPerda {
        // WebP guarda transparencia: nao se acha sobre branco (so o JPG o faz).
        fun webp(bitmap: Bitmap, quality: Int): ResultadoCodificacao {
            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(format, quality.coerceIn(1, 100), out)
            return ResultadoCodificacao.Sucesso(out.toByteArray(), bitmap.width, bitmap.height)
        }
    }

    private object CodificadorSemPerda {
        fun png(bitmap: Bitmap): ResultadoCodificacao {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return ResultadoCodificacao.Sucesso(out.toByteArray(), bitmap.width, bitmap.height)
        }
    }
}

/** Ponte para a biblioteca avif-coder; isolado para facilitar testes. */
private object PonteAvif {

    /**
     * O codificador AVIF exige largura e altura PARES. Se a imagem tiver
     * dimensoes impares, e recortada em 1 px (nunca esticada, para nao
     * distorcer) antes de codificar.
     */
    fun encode(bitmap: Bitmap, quality: Int): ResultadoCodificacao.Sucesso {
        val even = ensureEvenDimensions(bitmap)
        try {
            val bytes = com.radzivon.bartoshyk.avif.coder.HeifCoder().encodeAvif(
                bitmap = even,
                quality = quality.coerceIn(0, 100)
            )
            return ResultadoCodificacao.Sucesso(bytes, even.width, even.height)
        } finally {
            if (even !== bitmap && !even.isRecycled) even.recycle()
        }
    }

    private fun ensureEvenDimensions(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w % 2 == 0 && h % 2 == 0) return bitmap
        val evenW = if (w % 2 == 0) w else w - 1
        val evenH = if (h % 2 == 0) h else h - 1
        if (evenW < 2 || evenH < 2) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, evenW, evenH)
    }
}
