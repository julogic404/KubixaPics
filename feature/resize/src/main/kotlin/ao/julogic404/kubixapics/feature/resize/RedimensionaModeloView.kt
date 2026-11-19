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

package ao.julogic404.kubixapics.feature.resize

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import ao.julogic404.kubixapics.core.common.Evento
import ao.julogic404.kubixapics.core.common.model.ImagemEditada
import ao.julogic404.kubixapics.core.common.model.ImagemFormato
import ao.julogic404.kubixapics.core.data.ImagemDecodificadorEx
import ao.julogic404.kubixapics.core.data.ImagemRedimensionador
import ao.julogic404.kubixapics.core.data.SalvarImagem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ViewModel de redimensionamento (uma imagem de cada vez, com pre-visualizacao).
 */
class RedimensionaModeloView(app: Application) : AndroidViewModel(app) {

    /** Mensagens entregues uma unica vez (nao repetem apos rotacao do ecra). */
    sealed interface MensagemUi {
        data object Guardada : MensagemUi
        data object FalhaAoGuardar : MensagemUi
        data object RedimensaoFalhou : MensagemUi
    }

    private val _original = MutableLiveData<Bitmap?>(null)
    val original: LiveData<Bitmap?> = _original

    /** Bitmap completo carregado (null enquanto nao ha imagem). */
    val originalBitmap: Bitmap? get() = _original.value

    private val _origWidth = MutableLiveData(0)
    private val _origHeight = MutableLiveData(0)

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _preview = MutableLiveData<Bitmap?>(null)
    val preview: LiveData<Bitmap?> = _preview

    private val _events = MutableLiveData<Evento<MensagemUi>>()
    val events: LiveData<Evento<MensagemUi>> = _events

    /** Versao reduzida do original: base barata para a pre-visualizacao. */
    private var previewBase: Bitmap? = null
    private var previewJob: Job? = null

    // Parametros atuais
    var mode: ImagemRedimensionador.Modo = ImagemRedimensionador.Modo.Percentagem(100)

    val origWidth: Int get() = _origWidth.value ?: 0
    val origHeight: Int get() = _origHeight.value ?: 0

    fun load(uri: Uri) {
        if (_isLoading.value == true) return
        val app = getApplication<Application>()
        _isLoading.value = true
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.Default) {
                ImagemDecodificadorEx.decode(app, uri, 0)?.let { decoded ->
                    decoded.bitmap to ImagemDecodificadorEx.scaleToMax(decoded.bitmap, PREVIEW_BASE_MAX)
                }
            }
            _isLoading.value = false
            if (loaded != null) {
                val (bitmap, base) = loaded
                previewBase = base
                _origWidth.value = bitmap.width
                _origHeight.value = bitmap.height
                _original.value = bitmap
                // Nao reciclar: o bitmap original e usado como base de edicao.
            }
        }
    }

    /** Tamanho alvo calculado com o modo atual. */
    fun targetSize(): Pair<Int, Int> =
        ImagemRedimensionador.computeTarget(origWidth, origHeight, mode)

    /**
     * Renderiza a pre-visualizacao (max 512px) fora da thread principal.
     * Um pedido novo cancela o anterior, por isso escrever nos campos nao bloqueia a UI.
     */
    fun renderPreview() {
        val base = previewBase ?: return
        val (tw, th) = targetSize()
        if (tw <= 0 || th <= 0) return
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            val scale = minOf(1f, PREVIEW_MAX / maxOf(tw, th).toFloat())
            val pw = (tw * scale).toInt().coerceAtLeast(1)
            val ph = (th * scale).toInt().coerceAtLeast(1)
            val bmp = try {
                Bitmap.createScaledBitmap(base, pw, ph, true)
            } catch (t: Throwable) {
                null
            }
            ensureActive()
            if (bmp != null) _preview.postValue(bmp)
        }
    }

    /** Aplica o redimensionamento e guarda o resultado (tudo fora da thread principal). */
    fun applyAndSave(outputName: String) {
        val original = _original.value ?: return
        if (_isLoading.value == true) return
        val app = getApplication<Application>()
        val currentMode = mode
        val (tw, th) = targetSize()
        _isLoading.value = true
        viewModelScope.launch {
            val message = withContext<MensagemUi>(Dispatchers.Default) {
                try {
                    val resized = ImagemRedimensionador.resize(original, currentMode)
                    // ImagemRedimensionador devolve o original se falhar (ex.: sem memoria).
                    // Nesse caso nao se pode guardar com um nome que promete outro tamanho.
                    if (resized.width != tw.coerceAtLeast(1) || resized.height != th.coerceAtLeast(1)) {
                        if (resized !== original && !resized.isRecycled) resized.recycle()
                        return@withContext MensagemUi.RedimensaoFalhou
                    }
                    val outW = resized.width
                    val outH = resized.height
                    val bytes = encodePng(resized)
                    if (resized !== original && !resized.isRecycled) resized.recycle()
                    val edited = ImagemEditada(
                        id = UUID.randomUUID().toString(),
                        displayName = outputName,
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
                    MensagemUi.RedimensaoFalhou
                }
            }
            _isLoading.value = false
            _events.value = Evento(message)
        }
    }

    private fun encodePng(bitmap: Bitmap): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    companion object {
        /** Maior lado aceite nos campos (evita pedidos que rebentam a memoria). */
        const val MAX_SIDE = 16384
        private const val PREVIEW_MAX = 512f
        private const val PREVIEW_BASE_MAX = 1024
    }
}
