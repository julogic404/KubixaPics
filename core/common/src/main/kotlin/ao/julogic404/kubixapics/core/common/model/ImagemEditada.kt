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

package ao.julogic404.kubixapics.core.common.model

import android.graphics.Bitmap

/**
 * Uma imagem ja processada (convertida, redimensionada ou comprimida).
 *
 * Guarda apenas uma miniatura pequena (nunca o bitmap completo) e os bytes do
 * resultado, para que lotes grandes nao esgotem a memoria do dispositivo.
 */
class ImagemEditada(
    val id: String,
    val displayName: String,
    val originalSize: Long,
    val resultSize: Long,
    val width: Int,
    val height: Int,
    val thumbnail: Bitmap?,
    val bytes: ByteArray,
    val format: ImagemFormato
) {
    /** Percentagem poupada/reduzida (pode ser negativa se o ficheiro cresceu). */
    val savingsPercent: Int
        get() = if (originalSize <= 0L) 0
        else (((originalSize - resultSize) * 100.0) / originalSize).toInt()

    val hasSavings: Boolean get() = savingsPercent > 0

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ImagemEditada) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
