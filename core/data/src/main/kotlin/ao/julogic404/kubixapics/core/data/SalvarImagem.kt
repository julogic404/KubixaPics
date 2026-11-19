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

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import ao.julogic404.kubixapics.core.common.model.ImagemEditada
import java.io.File

/**
 * Guarda e partilha o resultado de uma edicao, em qualquer formato suportado.
 *
 * - Android 10+: MediaStore com IS_PENDING (o ficheiro so aparece completo) e
 *   pasta Pictures/KubixaPics;
 * - Android <= 9: escrita direta em Pictures/KubixaPics com registo no MediaStore;
 * - Partilha via FileProvider pela cache da app.
 */
object SalvarImagem {

    const val ALBUM = "KubixaPics"

    /** @return Uri do ficheiro guardado, ou null em caso de falha. */
    fun save(context: Context, image: ImagemEditada): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, image)
        } else {
            saveLegacy(context, image)
        }
    } catch (t: Throwable) {
        null
    }

    private fun saveWithMediaStore(context: Context, image: ImagemEditada): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, image.displayName)
            put(MediaStore.Images.Media.MIME_TYPE, image.format.mimeType)
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$ALBUM"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        try {
            resolver.openOutputStream(uri)?.use { it.write(image.bytes) }
                ?: run {
                    resolver.delete(uri, null, null)
                    return null
                }
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            return null
        }

        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, image: ImagemEditada): Uri? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM
        )
        if (!dir.exists() && !dir.mkdirs()) return null

        val file = uniqueFile(dir, image.displayName)
        file.writeBytes(image.bytes)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, image.format.mimeType)
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        return context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "png")
        var index = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base-$index.$ext")
            index++
        }
        return candidate
    }

    /** Escreve o resultado na cache e devolve um Uri partilhavel via FileProvider. */
    fun shareUri(context: Context, image: ImagemEditada): Uri? = try {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, image.displayName)
        file.writeBytes(image.bytes)
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    } catch (t: Throwable) {
        null
    }
}
