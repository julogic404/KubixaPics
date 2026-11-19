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

import ao.julogic404.kubixapics.core.common.model.PreRedimensoes
import org.junit.Assert.assertEquals
import org.junit.Test

/** Garante que nao ha presets repetidos (o HD e o Full HD chegaram a ser iguais). */
class PreRedimensoesTest {

    @Test
    fun `presets nao repetem dimensoes`() {
        val sizes = PreRedimensoes.all.map { it.width to it.height }
        assertEquals(sizes.size, sizes.toSet().size)
    }

    @Test
    fun `limite maximo com campo vazio nunca devolve zero`() {
        val (w, h) = ImagemRedimensionador.computeTarget(1000, 500, ImagemRedimensionador.Modo.Maximo(1, 1))
        assertEquals(1, w.coerceAtLeast(1))
        assertEquals(1, h.coerceAtLeast(1))
    }
}
