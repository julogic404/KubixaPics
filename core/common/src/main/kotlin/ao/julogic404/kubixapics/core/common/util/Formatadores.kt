package ao.julogic404.kubixapics.core.common.util

import java.util.Locale

/** Formatacao de tamanhos e percentagens. */
object Formatadores {

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.getDefault(), "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    }

    fun formatPercent(value: Int): String = "$value%"
}
