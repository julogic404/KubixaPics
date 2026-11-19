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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Testes do calculo de dimensoes alvo.
 *
 * `ImagemRedimensionador.computeTarget` e logica pura (sem Bitmap), por isso corre em
 * JVM com JUnit normal — e o que o job `checks` do CI executa.
 */
class ImagemRedimensionadorTest {

    @Test
    fun `exato sem proporcao devolve as dimensoes pedidas`() {
        val (w, h) = ImagemRedimensionador.computeTarget(
            4000, 3000,
            ImagemRedimensionador.Modo.Exato(1080, 1080, keepRatio = false)
        )
        assertEquals(1080, w)
        assertEquals(1080, h)
    }

    @Test
    fun `exato com proporcao cabe dentro da caixa`() {
        // 4000x3000 (4:3) dentro de 1080x1080 -> limitado pela largura
        val (w, h) = ImagemRedimensionador.computeTarget(
            4000, 3000,
            ImagemRedimensionador.Modo.Exato(1080, 1080, keepRatio = true)
        )
        assertEquals(1080, w)
        assertEquals(810, h)
    }

    @Test
    fun `exato com proporcao em imagem vertical`() {
        // 3000x4000 dentro de 1080x1080 -> limitado pela altura
        val (w, h) = ImagemRedimensionador.computeTarget(
            3000, 4000,
            ImagemRedimensionador.Modo.Exato(1080, 1080, keepRatio = true)
        )
        assertEquals(810, w)
        assertEquals(1080, h)
    }

    @Test
    fun `percentagem reduz proporcionalmente`() {
        val (w, h) = ImagemRedimensionador.computeTarget(2000, 1000, ImagemRedimensionador.Modo.Percentagem(50))
        assertEquals(1000, w)
        assertEquals(500, h)
    }

    @Test
    fun `percentagem nunca devolve zero`() {
        val (w, h) = ImagemRedimensionador.computeTarget(10, 10, ImagemRedimensionador.Modo.Percentagem(1))
        assertEquals(1, w)
        assertEquals(1, h)
    }

    @Test
    fun `limite maximo nao amplia imagens pequenas`() {
        val (w, h) = ImagemRedimensionador.computeTarget(
            800, 600,
            ImagemRedimensionador.Modo.Maximo(1920, 1080)
        )
        assertEquals(800, w)
        assertEquals(600, h)
    }

    @Test
    fun `limite maximo reduz mantendo proporcao`() {
        val (w, h) = ImagemRedimensionador.computeTarget(
            4000, 2000,
            ImagemRedimensionador.Modo.Maximo(1920, 1080)
        )
        assertEquals(1920, w)
        assertEquals(960, h)
    }

    @Test
    fun `preset instagram post e quadrado`() {
        val (w, h) = ImagemRedimensionador.computeTarget(
            4000, 3000,
            ImagemRedimensionador.Modo.Exato(1080, 1080, keepRatio = false)
        )
        assertEquals(w, h)
    }
}
