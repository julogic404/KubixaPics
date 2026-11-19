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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Conteudo do versao.json publicado no repositorio. */
data class InfoAtualizacao(
    val versao: String,
    val novidades: String,
    val forcada: Boolean,
    val versaoMinima: String,
    val urlApk: String
)

sealed interface ResultadoVerificacao {
    /** A versao instalada ja e a mais recente (ou o ficheiro remoto nao e valido). */
    data object Atualizado : ResultadoVerificacao

    /** Ha uma versao mais nova. [obrigatoria] = "force" ou versao abaixo do minimo suportado. */
    data class Disponivel(val info: InfoAtualizacao, val obrigatoria: Boolean) : ResultadoVerificacao

    /** Sem rede, timeout, resposta invalida... */
    data object Falhou : ResultadoVerificacao
}

/**
 * Verificador de atualizacoes remoto: le um pequeno JSON no GitHub e compara
 * com a versao instalada. So faz um pedido GET; nao envia nenhum dado do utilizador.
 *
 * Formato esperado:
 * { "version": "4.1.0", "changelog": "...", "force": false,
 *   "min_supported": "4.0.0", "apk_url": "https://..." }
 */
object VerificadorAtualizacao {

    const val URL_VERSAO =
        "https://raw.githubusercontent.com/julogic404/KubixaPics/refs/heads/main/versao.json"

    /** Pagina de downloads usada se o JSON nao trouxer um apk_url valido. */
    const val URL_RELEASES = "https://github.com/julogic404/KubixaPics/releases/latest"

    private const val TEMPO_LIMITE_MS = 8_000
    private const val TAMANHO_MAXIMO = 64 * 1024

    suspend fun verificar(versaoAtual: String, url: String = URL_VERSAO): ResultadoVerificacao =
        withContext(Dispatchers.IO) {
            try {
                val info = descarregar(url) ?: return@withContext ResultadoVerificacao.Falhou
                decidir(info, versaoAtual)
            } catch (t: Throwable) {
                ResultadoVerificacao.Falhou
            }
        }

    /** Logica pura (testavel sem Android): ha atualizacao? e obrigatoria? */
    fun decidir(info: InfoAtualizacao, versaoAtual: String): ResultadoVerificacao {
        val remotaVsAtual = comparar(info.versao, versaoAtual) ?: return ResultadoVerificacao.Atualizado
        if (remotaVsAtual <= 0) return ResultadoVerificacao.Atualizado

        val abaixoDoMinimo = (comparar(info.versaoMinima, versaoAtual) ?: 0) > 0
        return ResultadoVerificacao.Disponivel(info, obrigatoria = info.forcada || abaixoDoMinimo)
    }

    /**
     * Compara duas versoes "x.y.z" (ignora prefixo "v" e sufixos como "-debug"/"-beta").
     * Devolve <0, 0, >0, ou null se alguma nao for uma versao valida (ex.: "#").
     */
    fun comparar(a: String, b: String): Int? {
        val pa = partes(a) ?: return null
        val pb = partes(b) ?: return null
        val tamanho = maxOf(pa.size, pb.size)
        for (i in 0 until tamanho) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun partes(versao: String): List<Int>? {
        val limpa = versao.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
        if (limpa.isEmpty()) return null
        val numeros = limpa.split('.').map { it.toIntOrNull() ?: return null }
        return numeros.takeIf { it.all { n -> n >= 0 } }
    }

    private fun descarregar(url: String): InfoAtualizacao? {
        val ligacao = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TEMPO_LIMITE_MS
            readTimeout = TEMPO_LIMITE_MS
            requestMethod = "GET"
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (ligacao.responseCode != HttpURLConnection.HTTP_OK) return null
            val texto = ligacao.inputStream.use { entrada ->
                val buffer = ByteArray(TAMANHO_MAXIMO)
                var total = 0
                while (total < buffer.size) {
                    val lidos = entrada.read(buffer, total, buffer.size - total)
                    if (lidos < 0) break
                    total += lidos
                }
                String(buffer, 0, total, Charsets.UTF_8)
            }
            val json = JSONObject(texto)
            return InfoAtualizacao(
                versao = json.optString("version", ""),
                novidades = json.optString("changelog", ""),
                forcada = json.optBoolean("force", false),
                versaoMinima = json.optString("min_supported", ""),
                urlApk = json.optString("apk_url", "")
            )
        } finally {
            ligacao.disconnect()
        }
    }
}
