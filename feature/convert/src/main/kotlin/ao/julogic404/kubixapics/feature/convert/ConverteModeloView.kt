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

package ao.julogic404.kubixapics.feature.convert

import android.app.Application
import android.content.Context
import android.net.Uri
import ao.julogic404.kubixapics.core.common.model.ImagemEditada
import ao.julogic404.kubixapics.core.common.model.ImagemFormato
import ao.julogic404.kubixapics.core.data.ModeloViewListaEdit
import ao.julogic404.kubixapics.core.data.ResultadoCodificacao
import ao.julogic404.kubixapics.core.data.ImagemDecodificadorEx
import ao.julogic404.kubixapics.core.data.ImagemCodificador
import java.util.UUID

/** Conversao de formato: entrada qualquer -> formato de saida escolhido. */
class ConverteModeloView(app: Application) : ModeloViewListaEdit(app) {

    var outputFormat: ImagemFormato
        get() = runCatching { ImagemFormato.valueOf(prefs.outputFormat) }
            .getOrDefault(ImagemFormato.WEBP)
        set(value) { prefs.outputFormat = value.name }

    var quality: Int
        get() = prefs.quality
        set(value) { prefs.quality = value }

    var maxDimension: Int
        get() = prefs.maxDimension
        set(value) { prefs.maxDimension = value }

    override fun processOne(app: Application, uri: Uri): ImagemEditada? {
        val context = app as Context
        val decoded = ImagemDecodificadorEx.decode(context, uri, prefs.maxDimension) ?: return null
        // O tamanho original vem dos bytes lidos do ficheiro (evita uma segunda leitura).
        val originalSize = decoded.bytes?.size?.toLong() ?: querySize(context, uri)
        val format = outputFormat

        return try {
            val encoded = ImagemCodificador.encode(decoded.bitmap, format, prefs.quality)
            when (encoded) {
                is ResultadoCodificacao.Erro -> null
                is ResultadoCodificacao.Sucesso -> {
                    val thumbnail = ImagemDecodificadorEx.createThumbnail(decoded.bitmap)
                    ImagemEditada(
                        id = UUID.randomUUID().toString(),
                        displayName = buildName(context, uri, format.extension),
                        originalSize = originalSize,
                        resultSize = encoded.bytes.size.toLong(),
                        width = encoded.width,
                        height = encoded.height,
                        thumbnail = thumbnail,
                        bytes = encoded.bytes,
                        format = format
                    )
                }
            }
        } finally {
            if (!decoded.bitmap.isRecycled) decoded.bitmap.recycle()
        }
    }
}
