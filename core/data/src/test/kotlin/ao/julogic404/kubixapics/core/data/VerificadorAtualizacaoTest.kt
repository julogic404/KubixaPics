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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificadorAtualizacaoTest {

    private fun info(versao: String, minima: String = "", forcada: Boolean = false) =
        InfoAtualizacao(versao, "novidades", forcada, minima, "https://exemplo.com/app.apk")

    @Test
    fun `compara versoes numericamente e nao como texto`() {
        assertTrue(VerificadorAtualizacao.comparar("4.10.0", "4.9.9")!! > 0)
        assertTrue(VerificadorAtualizacao.comparar("4.0.2", "4.0.10")!! < 0)
        assertEquals(0, VerificadorAtualizacao.comparar("4.0", "4.0.0"))
    }

    @Test
    fun `ignora prefixo v e sufixo debug`() {
        assertEquals(0, VerificadorAtualizacao.comparar("v4.0.2", "4.0.2-debug"))
    }

    @Test
    fun `versao invalida como cardinal devolve null`() {
        assertNull(VerificadorAtualizacao.comparar("#", "4.0.2"))
        assertNull(VerificadorAtualizacao.comparar("", "4.0.2"))
    }

    @Test
    fun `sem versao nova fica atualizado`() {
        assertEquals(ResultadoVerificacao.Atualizado, VerificadorAtualizacao.decidir(info("4.0.2"), "4.0.2"))
        assertEquals(ResultadoVerificacao.Atualizado, VerificadorAtualizacao.decidir(info("4.0.1"), "4.0.2"))
        assertEquals(ResultadoVerificacao.Atualizado, VerificadorAtualizacao.decidir(info("#"), "4.0.2"))
    }

    @Test
    fun `versao nova e opcional por omissao`() {
        val r = VerificadorAtualizacao.decidir(info("4.1.0", minima = "4.0.0"), "4.0.2")
        assertTrue(r is ResultadoVerificacao.Disponivel && !r.obrigatoria)
    }

    @Test
    fun `force torna a atualizacao obrigatoria`() {
        val r = VerificadorAtualizacao.decidir(info("4.1.0", forcada = true), "4.0.2")
        assertTrue(r is ResultadoVerificacao.Disponivel && r.obrigatoria)
    }

    @Test
    fun `versao abaixo do minimo suportado e obrigatoria`() {
        val r = VerificadorAtualizacao.decidir(info("4.1.0", minima = "4.0.5"), "4.0.2")
        assertTrue(r is ResultadoVerificacao.Disponivel && r.obrigatoria)
    }
}
