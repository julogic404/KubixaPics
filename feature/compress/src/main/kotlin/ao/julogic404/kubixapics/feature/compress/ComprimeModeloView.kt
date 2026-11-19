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

package ao.julogic404.kubixapics.feature.compress

import android.app.Application
import android.content.Context
import android.net.Uri
import ao.julogic404.kubixapics.core.common.model.ImagemEditada
import ao.julogic404.kubixapics.core.common.model.ImagemFormato
import ao.julogic404.kubixapics.core.data.ModeloViewListaEdit
import ao.julogic404.kubixapics.core.data.ImagemCompressor
import ao.julogic404.kubixapics.core.data.ImagemDecodificadorEx
import java.util.UUID

/** Compressao com qualidade e alvo de tamanho. */
class ComprimeModeloView(app: Application) : ModeloViewListaEdit(app) {

    var compressQuality: Int
        get() = prefs.compressQuality
        set(value) { prefs.compressQuality = value }

    var targetBytes: Long
        get() = prefs.compressTargetBytes
        set(value) { prefs.compressTargetBytes = value }

    override fun processOne(app: Application, uri: Uri): ImagemEditada? {
        val context = app as Context
        val decoded = ImagemDecodificadorEx.decode(context, uri, 0) ?: return null
        val originalSize = decoded.bytes?.size?.toLong() ?: querySize(context, uri)

        return try {
            val request = ImagemCompressor.PedidoCompressao(
                quality = prefs.compressQuality,
                targetBytes = prefs.compressTargetBytes,
                maxDimension = 0
            )
            val result = ImagemCompressor.compress(decoded.bitmap, request)
            val thumbnail = ImagemDecodificadorEx.createThumbnail(decoded.bitmap)
            ImagemEditada(
                id = UUID.randomUUID().toString(),
                displayName = buildName(context, uri, "webp"),
                originalSize = originalSize,
                resultSize = result.bytes.size.toLong(),
                width = result.width,
                height = result.height,
                thumbnail = thumbnail,
                bytes = result.bytes,
                format = ImagemFormato.WEBP
            )
        } finally {
            if (!decoded.bitmap.isRecycled) decoded.bitmap.recycle()
        }
    }
}
