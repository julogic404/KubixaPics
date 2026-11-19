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

/**
 * Formatos suportados pelo Kubixa Pics.
 *
 * `supportsAlpha` indica se o formato guarda transparencia; quando e falso a
 * imagem e achatada sobre branco antes de codificar (evita fundos pretos em JPG).
 * `supportsQuality` indica se o parametro de qualidade tem efeito real.
 */
enum class ImagemFormato(
    val extension: String,
    val mimeType: String,
    val label: String,
    val supportsAlpha: Boolean,
    val supportsQuality: Boolean
) {
    WEBP("webp", "image/webp", "WebP", true, true),
    PNG("png", "image/png", "PNG", true, false),
    JPG("jpg", "image/jpeg", "JPG", false, true),
    AVIF("avif", "image/avif", "AVIF", true, true),
    BMP("bmp", "image/bmp", "BMP", true, false),
    TIFF("tiff", "image/tiff", "TIFF", true, false);

    companion object {

        /** Formatos oferecidos no seletor de saida, pela ordem em que aparecem. */
        val outputFormats: List<ImagemFormato> = listOf(WEBP, PNG, JPG, AVIF, BMP, TIFF)

        fun fromExtension(extension: String?): ImagemFormato? {
            val ext = extension?.lowercase()?.trim() ?: return null
            return when (ext) {
                "webp" -> WEBP
                "png" -> PNG
                "jpg", "jpeg", "jpe", "jfif" -> JPG
                "avif" -> AVIF
                "bmp", "dib" -> BMP
                "tif", "tiff" -> TIFF
                else -> null
            }
        }

        fun fromMimeType(mime: String?): ImagemFormato? {
            val value = mime?.lowercase()?.trim() ?: return null
            return when {
                value.contains("webp") -> WEBP
                value.contains("png") -> PNG
                value.contains("jpeg") || value.contains("jpg") -> JPG
                value.contains("avif") -> AVIF
                value.contains("bmp") -> BMP
                value.contains("tiff") -> TIFF
                else -> null
            }
        }

        /** Formatos que o Photo Picker e o seletor de ficheiros devem aceitar. */
        val inputMimeTypes: Array<String> = arrayOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif",
            "image/bmp",
            "image/x-ms-bmp",
            "image/tiff",
            "image/svg+xml",
            "image/avif",
            "image/heic",
            "image/heif"
        )
    }
}
