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

package ao.julogic404.kubixapics.feature.crop

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import ao.julogic404.kubixapics.core.common.Evento
import ao.julogic404.kubixapics.core.data.ImagemDecodificadorEx
import ao.julogic404.kubixapics.core.data.SalvarImagem
import ao.julogic404.kubixapics.core.common.model.ImagemEditada
import ao.julogic404.kubixapics.core.common.model.ImagemFormato
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ViewModel do recortador de imagens (uma imagem de cada vez).
 *
 * O rect recorte e expresso em coordenadas normalizadas (0..1) relativas a
 * imagem original. A rotacao e aplicada sobre o bitmap original e o recorte
 * volta a ser calculado.
 */
class CortaModeloView(app: Application) : AndroidViewModel(app) {

    // Area de recorte normalizada (0..1): left, top, right, bottom
    var cropRect = floatArrayOf(0f, 0f, 1f, 1f)
    var rotationDegrees = 0
    /** Recorte circular (mascara redonda no resultado). */
    var circular = false

    private val _source = MutableLiveData<Uri?>(null)
    val source: LiveData<Uri?> = _source

    private val _original = MutableLiveData<Bitmap?>(null)
    val original: LiveData<Bitmap?> = _original

    private val _preview = MutableLiveData<Bitmap?>(null)
    val preview: LiveData<Bitmap?> = _preview

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    /** Mensagens entregues uma unica vez (nao repetem apos rotacao do ecra). */
    sealed interface MensagemUi {
        data object Guardada : MensagemUi
        data object FalhaAoGuardar : MensagemUi
        data object CorteFalhou : MensagemUi
    }

    private val _events = MutableLiveData<Evento<MensagemUi>>()
    val events: LiveData<Evento<MensagemUi>> = _events

    val originalBitmap: Bitmap? get() = _original.value

    fun load(uri: Uri) {
        if (_isLoading.value == true) return
        val app = getApplication<Application>()
        _isLoading.value = true
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.Default) {
                ImagemDecodificadorEx.decode(app, uri, 0)
            }
            _isLoading.value = false
            if (decoded != null) {
                // Nova imagem: recomecar sem rotacao/recorte da anterior.
                rotationDegrees = 0
                cropRect = floatArrayOf(0f, 0f, 1f, 1f)
                circular = false
                _source.value = uri
                _original.value = decoded.bitmap
                // O CortarView desenha uma versao reduzida: um bitmap enorme excede o
                // limite de textura da GPU e rebenta ao desenhar. O recorte e
                // normalizado (0..1), por isso aplica-se igual ao original completo.
                _preview.value = ImagemDecodificadorEx.scaleToMax(decoded.bitmap, DISPLAY_MAX)
            }
        }
    }

    /** Bitmap de pre-visualizacao (reduzido) com a rotacao atual aplicada. */
    fun rotatedBitmap(): Bitmap? {
        val display = _preview.value ?: return null
        if (rotationDegrees == 0) return display
        return rotate(display, rotationDegrees)
    }

    /** Bitmap recortado + rotacionado. */
    fun cropBitmap(): Bitmap? {
        val original = _original.value ?: return null
        var working: Bitmap? = null
        var result: Bitmap? = null
        try {
            working = if (rotationDegrees != 0) rotate(original, rotationDegrees) else original
            val w = working.width
            val h = working.height
            val left = (cropRect[0] * w).toInt().coerceIn(0, w - 1)
            val top = (cropRect[1] * h).toInt().coerceIn(0, h - 1)
            val right = (cropRect[2] * w).toInt().coerceIn(left + 1, w)
            val bottom = (cropRect[3] * h).toInt().coerceIn(top + 1, h)
            val cropped = Bitmap.createBitmap(working, left, top, right - left, bottom - top)
            result = if (circular) applyCircleMask(cropped) else cropped
            return result
        } catch (t: Throwable) {
            return null
        } finally {
            // Libertar a copia rodada, mas NUNCA o bitmap devolvido nem o original.
            if (working != null && working !== original && working !== result &&
                !working.isRecycled
            ) {
                working.recycle()
            }
        }
    }

    /** Recorte circular: aplica uma mascara alfa redonda sobre o bitmap. */
    private fun applyCircleMask(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        val dx = (size - source.width) / 2f
        val dy = (size - source.height) / 2f
        canvas.drawBitmap(source, dx, dy, paint)
        if (source !== _original.value && !source.isRecycled) source.recycle()
        return output
    }

    fun save() {
        val original = _original.value ?: return
        if (_isLoading.value == true) return
        val app = getApplication<Application>()
        _isLoading.value = true
        viewModelScope.launch {
            // Recorte, codificacao e escrita em disco: tudo fora da thread principal.
            val message = withContext<MensagemUi>(Dispatchers.Default) {
                try {
                    val cropped = cropBitmap() ?: return@withContext MensagemUi.CorteFalhou
                    val outW = cropped.width
                    val outH = cropped.height
                    val bytes = encodePng(cropped)
                    if (cropped !== original && !cropped.isRecycled) cropped.recycle()
                    val edited = ImagemEditada(
                        id = UUID.randomUUID().toString(),
                        displayName = "recorte-${outW}x${outH}.png",
                        originalSize = 0L,
                        resultSize = bytes.size.toLong(),
                        width = outW,
                        height = outH,
                        thumbnail = null,
                        bytes = bytes,
                        format = ImagemFormato.PNG
                    )
                    if (SalvarImagem.save(app, edited) != null) MensagemUi.Guardada else MensagemUi.FalhaAoGuardar
                } catch (t: Throwable) {
                    MensagemUi.CorteFalhou
                }
            }
            _isLoading.value = false
            _events.value = Evento(message)
        }
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
        return try {
            Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
        } catch (t: Throwable) {
            bitmap
        }
    }

    private fun encodePng(bitmap: Bitmap): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    private companion object {
        const val DISPLAY_MAX = 2048
    }
}
