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
