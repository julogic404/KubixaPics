package ao.julogic404.kubixapics.core.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Preferencias persistentes do app (qualidade, formato, estatisticas).
 * Usa um unico ficheiro, incluido nas regras de backup.
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    var quality: Int
        get() = sp.getInt(KEY_QUALITY, DEFAULT_QUALITY).coerceIn(MIN_QUALITY, MAX_QUALITY)
        set(value) = sp.edit { putInt(KEY_QUALITY, value.coerceIn(MIN_QUALITY, MAX_QUALITY)) }

    /** 0 = manter resolucao original. */
    var maxDimension: Int
        get() = sp.getInt(KEY_MAX_DIMENSION, 0)
        set(value) = sp.edit { putInt(KEY_MAX_DIMENSION, value) }

    /** Formato de saida da conversao (por omissao WebP). */
    var outputFormat: String
        get() = sp.getString(KEY_OUTPUT_FORMAT, "WEBP") ?: "WEBP"
        set(value) = sp.edit { putString(KEY_OUTPUT_FORMAT, value) }

    /** Nivel de compressao (1..100). */
    var compressQuality: Int
        get() = sp.getInt(KEY_COMPRESS_QUALITY, 80).coerceIn(1, 100)
        set(value) = sp.edit { putInt(KEY_COMPRESS_QUALITY, value.coerceIn(1, 100)) }

    /** Alvo de tamanho maximo em bytes para compressao (0 = desativado). */
    var compressTargetBytes: Long
        get() = sp.getLong(KEY_COMPRESS_TARGET, 0L)
        set(value) = sp.edit { putLong(KEY_COMPRESS_TARGET, value.coerceAtLeast(0L)) }

    var totalConverted: Int
        get() = sp.getInt(KEY_TOTAL_CONVERTED, 0)
        private set(value) = sp.edit { putInt(KEY_TOTAL_CONVERTED, value) }

    var totalOriginalBytes: Long
        get() = sp.getLong(KEY_TOTAL_ORIGINAL, 0L)
        private set(value) = sp.edit { putLong(KEY_TOTAL_ORIGINAL, value) }

    var totalConvertedBytes: Long
        get() = sp.getLong(KEY_TOTAL_CONVERTED_BYTES, 0L)
        private set(value) = sp.edit { putLong(KEY_TOTAL_CONVERTED_BYTES, value) }

    /** Regista uma edicao concluida para alimentar as estatisticas do ecra inicial. */
    fun recordConversion(originalBytes: Long, convertedBytes: Long) {
        totalConverted = totalConverted + 1
        totalOriginalBytes = totalOriginalBytes + originalBytes
        totalConvertedBytes = totalConvertedBytes + convertedBytes
    }

    fun resetStats() {
        sp.edit {
            putInt(KEY_TOTAL_CONVERTED, 0)
            putLong(KEY_TOTAL_ORIGINAL, 0L)
            putLong(KEY_TOTAL_CONVERTED_BYTES, 0L)
        }
    }

    companion object {
        private const val FILE_NAME = "kubixapics_prefs"
        private const val KEY_QUALITY = "quality"
        private const val KEY_MAX_DIMENSION = "max_dimension"
        private const val KEY_OUTPUT_FORMAT = "output_format"
        private const val KEY_COMPRESS_QUALITY = "compress_quality"
        private const val KEY_COMPRESS_TARGET = "compress_target_bytes"
        private const val KEY_TOTAL_CONVERTED = "total_converted"
        private const val KEY_TOTAL_ORIGINAL = "total_original_bytes"
        private const val KEY_TOTAL_CONVERTED_BYTES = "total_converted_bytes"

        const val MIN_QUALITY = 10
        const val MAX_QUALITY = 100
        const val DEFAULT_QUALITY = 80
    }
}
