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
