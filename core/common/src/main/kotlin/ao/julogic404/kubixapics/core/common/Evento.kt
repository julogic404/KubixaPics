package ao.julogic404.kubixapics.core.common

/**
 * Envolve um valor para que seja consumido apenas uma vez.
 * Evita repetir Toasts e Snackbars depois de uma rotacao de ecra.
 */
class Evento<out T>(private val content: T) {

    private var handled = false

    fun getIfNotHandled(): T? {
        if (handled) return null
        handled = true
        return content
    }
}
