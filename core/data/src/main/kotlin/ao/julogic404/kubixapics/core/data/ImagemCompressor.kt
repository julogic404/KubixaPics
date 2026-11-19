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
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Compressao de imagens.
 *
 * Comprime para WEBP (perda) com qualidade ajustavel, e suporta um alvo maximo
 * de bytes: reduz a qualidade iterativamente ate caber no alvo.
 */
object ImagemCompressor {

    data class PedidoCompressao(
        val quality: Int,        // 1..100 (qualidade inicial)
        val targetBytes: Long,    // >0 ativa o alvo de tamanho
        val maxDimension: Int     // 0 = tamanho original
    )

    data class ResultadoCompressao(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val finalQuality: Int
    )

    fun compress(bitmap: Bitmap, request: PedidoCompressao): ResultadoCompressao {
        var working = bitmap
        if (request.maxDimension > 0) {
            val longest = max(bitmap.width, bitmap.height)
            if (longest > request.maxDimension) {
                working = ImagemRedimensionador.resize(
                    bitmap,
                    ImagemRedimensionador.Modo.Maximo(request.maxDimension, request.maxDimension)
                )
            }
        }

        val quality = request.quality.coerceIn(1, 100)
        var attempt = quality
        var encoded = encodeWebp(working, attempt)
        var finalW: Int? = null
        var finalH: Int? = null

        if (request.targetBytes > 0L) {
            var steps = 0
            while (encoded.size > request.targetBytes && attempt > 25 && steps < 8) {
                attempt -= 10
                encoded = encodeWebp(working, attempt)
                steps++
            }
        }

        // Se ainda nao caber, tenta reduzir a resolucao.
        if (request.targetBytes > 0L && encoded.size > request.targetBytes && working.width > 64) {
            var scaled = working
            var guard = 0
            while (encoded.size > request.targetBytes && guard < 6) {
                val factor = 0.85f
                val nw = (scaled.width * factor).roundToInt().coerceAtLeast(1)
                val nh = (scaled.height * factor).roundToInt().coerceAtLeast(1)
                val next = Bitmap.createScaledBitmap(scaled, nw, nh, true)
                // Libertar o passo anterior (nunca o bitmap de entrada do chamador).
                if (scaled !== working && scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
                scaled = next
                encoded = encodeWebp(scaled, attempt.coerceAtMost(70))
                guard++
            }
            if (scaled !== working && scaled !== bitmap && !scaled.isRecycled) {
                // O ultimo passo e o que produziu os bytes finais: guardar as
                // dimensoes antes de libertar.
                finalW = scaled.width
                finalH = scaled.height
                scaled.recycle()
            }
        }

        val w = finalW ?: working.width
        val h = finalH ?: working.height
        if (working !== bitmap && !working.isRecycled) working.recycle()
        return ResultadoCompressao(encoded, w, h, attempt)
    }

    private fun encodeWebp(bitmap: Bitmap, quality: Int): ByteArray {
        // WebP suporta transparencia: nao achatar sobre branco.
        val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(format, quality.coerceIn(1, 100), out)
        return out.toByteArray()
    }
}
