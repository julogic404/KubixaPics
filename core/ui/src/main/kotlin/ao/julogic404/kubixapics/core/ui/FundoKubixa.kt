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

package ao.julogic404.kubixapics.core.ui

import android.app.Activity
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.max

/**
 * Fundo de toda a app: a imagem preenche o ecra inteiro (corte centrado, sem
 * deformar) e leva um escurecimento leve para o texto continuar legivel.
 */
class FundoCortadoDrawable(
    private val bitmap: Bitmap,
    private val escurecimento: Int = 0x4D000000
) : Drawable() {

    private val pintura = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val area = bounds
        if (area.isEmpty) return
        val escala = max(
            area.width().toFloat() / bitmap.width,
            area.height().toFloat() / bitmap.height
        )
        val dx = area.left + (area.width() - bitmap.width * escala) / 2f
        val dy = area.top + (area.height() - bitmap.height * escala) / 2f
        canvas.save()
        canvas.clipRect(area)
        canvas.translate(dx, dy)
        canvas.scale(escala, escala)
        canvas.drawBitmap(bitmap, 0f, 0f, pintura)
        canvas.restore()
        canvas.drawColor(escurecimento)
    }

    override fun setAlpha(alpha: Int) {
        pintura.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        pintura.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}

/** Decodifica o fundo uma unica vez e partilha-o entre todos os ecras. */
object FundoKubixa {

    @Volatile
    private var bitmap: Bitmap? = null

    private fun obter(recursos: Resources): Bitmap? {
        bitmap?.let { return it }
        return synchronized(this) {
            bitmap ?: try {
                // drawable-nodpi: sem reescalar por densidade.
                BitmapFactory.decodeResource(recursos, R.drawable.fundo_kubixa)
            } catch (t: Throwable) {
                null
            }.also { bitmap = it }
        }
    }

    fun criar(recursos: Resources): Drawable? =
        obter(recursos)?.let { FundoCortadoDrawable(it) }
}

/** Aplica o fundo Kubixa a janela inteira (inclusive atras das barras do sistema). */
fun Activity.aplicarFundoKubixa() {
    FundoKubixa.criar(resources)?.let { window.setBackgroundDrawable(it) }
}
