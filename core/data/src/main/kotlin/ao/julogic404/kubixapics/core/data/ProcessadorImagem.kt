package ao.julogic404.kubixapics.core.data

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Redimensionamento de imagens.
 *
 * Suporta:
 *  - dimensoes exatas (livres) ou com proporcao mantida;
 *  - porcentagem;
 *  - limites maximos (largura/altura, mantendo proporcao);
 *  - presets (Instagram, Facebook, YouTube, WhatsApp, HD, Full HD, Cover).
 */
object ImagemRedimensionador {

    sealed class Modo {
        data class Exato(val width: Int, val height: Int, val keepRatio: Boolean) : Modo()
        data class Percentagem(val percent: Int) : Modo()
        data class Maximo(val maxWidth: Int, val maxHeight: Int) : Modo()
    }

    /**
     * @return o bitmap redimensionado (novo), ou o original se nao ha mudanca.
     * O bitmap de entrada nunca e reciclado aqui.
     */
    fun resize(bitmap: Bitmap, mode: Modo): Bitmap {
        if (bitmap.isRecycled) return bitmap
        val (targetW, targetH) = computeTarget(bitmap.width, bitmap.height, mode)
        val w = targetW.coerceAtLeast(1)
        val h = targetH.coerceAtLeast(1)
        if (w == bitmap.width && h == bitmap.height) return bitmap
        return try {
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        } catch (t: Throwable) {
            bitmap
        }
    }

    fun computeTarget(srcW: Int, srcH: Int, mode: Modo): Pair<Int, Int> {
        return when (mode) {
            is Modo.Exato -> {
                if (mode.keepRatio) {
                    val scale = minOf(
                        mode.width.toFloat() / srcW,
                        mode.height.toFloat() / srcH
                    )
                    ((srcW * scale).roundToInt().coerceAtLeast(1)) to
                        ((srcH * scale).roundToInt().coerceAtLeast(1))
                } else {
                    mode.width to mode.height
                }
            }
            is Modo.Percentagem -> {
                val scale = mode.percent / 100f
                ((srcW * scale).roundToInt().coerceAtLeast(1)) to
                    ((srcH * scale).roundToInt().coerceAtLeast(1))
            }
            is Modo.Maximo -> {
                val scale = minOf(
                    mode.maxWidth.toFloat() / srcW,
                    mode.maxHeight.toFloat() / srcH
                ).coerceAtMost(1f)
                ((srcW * scale).roundToInt().coerceAtLeast(1)) to
                    ((srcH * scale).roundToInt().coerceAtLeast(1))
            }
        }
    }
}
