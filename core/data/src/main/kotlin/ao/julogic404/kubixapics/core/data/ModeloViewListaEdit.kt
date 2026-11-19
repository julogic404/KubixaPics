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

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import ao.julogic404.kubixapics.core.common.Evento
import ao.julogic404.kubixapics.core.common.model.ImagemEditada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel partilhado pelas ferramentas que produzem uma lista de [ImagemEditada]:
 * conversao e compressao.
 *
 * O estado vive aqui (sobrevive a rotacao), o trabalho pesado corre em
 * Dispatchers.Default e as imagens nunca sao recicladas enquanto estao na lista.
 */
abstract class ModeloViewListaEdit(app: Application) : AndroidViewModel(app) {

    protected val prefs = Prefs(app)

    private val _images = MutableLiveData<List<ImagemEditada>>(emptyList())
    val images: LiveData<List<ImagemEditada>> = _images

    private val _isProcessing = MutableLiveData(false)
    val isProcessing: LiveData<Boolean> = _isProcessing

    private val _progress = MutableLiveData(0f)
    val progress: LiveData<Float> = _progress

    private val _events = MutableLiveData<Evento<MensagemUi>>()
    val events: LiveData<Evento<MensagemUi>> = _events

    val currentImages: List<ImagemEditada> get() = _images.value.orEmpty()

    fun process(uris: List<Uri>) {
        if (uris.isEmpty() || _isProcessing.value == true) return

        val app = getApplication<Application>()
        _isProcessing.value = true
        _progress.value = 0f

        viewModelScope.launch {
            val converted = ArrayList<ImagemEditada>(uris.size)
            var failed = 0

            uris.forEachIndexed { index, uri ->
                // Falhas (incl. OutOfMemoryError) nao podem rebentar a coroutine nem
                // deixar o estado preso em "a processar".
                val result = withContext(Dispatchers.Default) {
                    runCatching { processOne(app, uri) }.getOrNull()
                }
                if (result == null) {
                    failed++
                } else {
                    converted += result
                    prefs.recordConversion(result.originalSize, result.resultSize)
                }
                _progress.postValue((index + 1).toFloat() / uris.size)
            }

            _images.value = currentImages + converted
            _isProcessing.value = false
            _progress.value = 0f

            _events.value = Evento(
                if (failed == 0) MensagemUi.Processadas(converted.size)
                else MensagemUi.ProcessadasParcial(converted.size, failed)
            )
        }
    }

    /** Processa uma imagem; devolve null em caso de falha. */
    protected abstract fun processOne(app: Application, uri: Uri): ImagemEditada?

    fun remove(image: ImagemEditada) {
        _images.value = currentImages.filterNot { it.id == image.id }
        _events.value = Evento(MensagemUi.Removida)
    }

    /** Guarda uma imagem em Pictures/KubixaPics fora da thread principal. */
    fun save(image: ImagemEditada) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) { SalvarImagem.save(app, image) }
            _events.value = Evento(if (uri != null) MensagemUi.Guardada else MensagemUi.FalhaAoGuardar)
        }
    }

    /** Guarda todas as imagens da lista fora da thread principal. */
    fun saveAll() {
        val images = currentImages
        if (images.isEmpty()) {
            _events.value = Evento(MensagemUi.NadaParaGuardar)
            return
        }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                images.count { SalvarImagem.save(app, it) != null }
            }
            val failed = images.size - saved
            _events.value = Evento(
                if (failed == 0) MensagemUi.TotalGuardadas(saved) else MensagemUi.TotalFalhasAoGuardar(failed)
            )
        }
    }

    fun clearAll() {
        _images.value = emptyList()
        _events.value = Evento(MensagemUi.Limpa)
    }

    protected fun querySize(context: Context, uri: Uri): Long = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else 0L
        } ?: 0L
    } catch (t: Throwable) {
        0L
    }

    protected fun buildName(context: Context, uri: Uri, extension: String): String {
        val raw = queryDisplayName(context, uri)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "imagem"
        val base = raw.substringBeforeLast('.', raw).ifBlank { "imagem" }
        return "$base.$extension"
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (t: Throwable) {
        null
    }

    /** Mensagens de UI entregues uma unica vez (nao repetem apos rotacao). */
    sealed interface MensagemUi {
        data class Processadas(val count: Int) : MensagemUi
        data class ProcessadasParcial(val count: Int, val failed: Int) : MensagemUi
        data object Removida : MensagemUi
        data object Limpa : MensagemUi
        data object Guardada : MensagemUi
        data object FalhaAoGuardar : MensagemUi
        data object NadaParaGuardar : MensagemUi
        data class TotalGuardadas(val count: Int) : MensagemUi
        data class TotalFalhasAoGuardar(val failed: Int) : MensagemUi
    }
}
