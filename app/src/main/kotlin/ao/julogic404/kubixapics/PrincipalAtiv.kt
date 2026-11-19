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

package ao.julogic404.kubixapics

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import ao.julogic404.kubixapics.core.data.InfoAtualizacao
import ao.julogic404.kubixapics.core.data.Prefs
import ao.julogic404.kubixapics.core.data.ResultadoVerificacao
import ao.julogic404.kubixapics.core.data.VerificadorAtualizacao
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.LocaleListCompat
import ao.julogic404.kubixapics.core.ui.aplicarFundoKubixa
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ao.julogic404.kubixapics.core.common.util.Formatadores
import ao.julogic404.kubixapics.databinding.AtivPrincipalBinding
import ao.julogic404.kubixapics.feature.compress.ComprimirAtiv
import ao.julogic404.kubixapics.feature.convert.ConverterAtiv
import ao.julogic404.kubixapics.feature.crop.CortarAtiv
import ao.julogic404.kubixapics.feature.resize.RedimensionarAtiv

/**
 * Ecra inicial: hub das ferramentas (converter, redimensionar, recortar,
 * comprimir), estatisticas acumuladas e acesso ao "Sobre".
 */
class PrincipalAtiv : AppCompatActivity() {

    private lateinit var binding: AtivPrincipalBinding

    // Android 9 e anteriores gravam diretamente em Pictures/ e precisam de permissao
    // de runtime (a declaracao no manifest nao basta). Q+ usa MediaStore sem permissao.
    private val requestLegacyWrite = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* sem permissao, guardar mostra a mensagem de falha */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aplicarFundoKubixa()
        binding = AtivPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupClickListeners()
        requestLegacyStoragePermission()
        verificarAtualizacaoAoAbrir(savedInstanceState)
    }

    override fun onDestroy() {
        dialogoAtualizacao?.dismiss()
        dialogoAtualizacao = null
        super.onDestroy()
    }

    private fun requestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestLegacyWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    override fun onResume() {
        super.onResume()
        renderStats()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    private fun setupClickListeners() {
        binding.cartaoConverter.setOnClickListener {
            startActivity(Intent(this, ConverterAtiv::class.java))
        }
        binding.cartaoRedimensionar.setOnClickListener {
            startActivity(Intent(this, RedimensionarAtiv::class.java))
        }
        binding.cartaoCortar.setOnClickListener {
            startActivity(Intent(this, CortarAtiv::class.java))
        }
        binding.cartaoComprimir.setOnClickListener {
            startActivity(Intent(this, ComprimirAtiv::class.java))
        }
        binding.btnMenu.setOnClickListener { mostrarMenuIdioma(it) }
        binding.btnSobre.setOnClickListener {
            startActivity(Intent(this, SobreAtiv::class.java))
        }
    }

    private fun renderStats() {
        val prefs = Prefs(this)
        val converted = prefs.totalConverted
        val saved = (prefs.totalOriginalBytes - prefs.totalConvertedBytes).coerceAtLeast(0L)
        val average = if (prefs.totalOriginalBytes > 0L) {
            ((saved * 100.0) / prefs.totalOriginalBytes).toInt()
        } else 0

        binding.tvEstatConvertidas.text = converted.toString()
        binding.tvEstatPoupado.text = Formatadores.formatBytes(saved)
        binding.tvEstatMedia.text = getString(R.string.valor_percentagem, average)
    }

    /** Menu hamburguer: escolhe o idioma da app (sistema, portugues ou ingles). */
    private fun mostrarMenuIdioma(ancora: android.view.View) {
        val menu = PopupMenu(this, ancora)
        menu.menuInflater.inflate(R.menu.menu_principal, menu.menu)

        // Marca o idioma atual
        val atual = AppCompatDelegate.getApplicationLocales()
        val idAtual = when (if (atual.isEmpty) null else atual[0]?.language) {
            "pt" -> R.id.idiomaPortugues
            "en" -> R.id.idiomaIngles
            else -> R.id.idiomaSistema
        }
        menu.menu.findItem(idAtual)?.isChecked = true

        menu.setOnMenuItemClickListener { item ->
            val etiquetas = when (item.itemId) {
                R.id.idiomaPortugues -> "pt"
                R.id.idiomaIngles -> "en"
                R.id.idiomaSistema -> ""
                R.id.menuProcurarAtualizacoes -> {
                    verificarAtualizacao(manual = true)
                    return@setOnMenuItemClickListener true
                }
                else -> return@setOnMenuItemClickListener false
            }
            // A AppCompat guarda a escolha e recria os ecras com o novo idioma.
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(etiquetas))
            true
        }
        menu.show()
    }

    // ----------------------------------------------------------------------
    // Atualizacoes (versao.json remoto)
    // ----------------------------------------------------------------------

    private var dialogoAtualizacao: AlertDialog? = null

    /**
     * Verificacao automatica ao abrir a app. Se o ecra for recriado (rotacao, mudanca de
     * idioma) so se repete o aviso de uma atualizacao OBRIGATORIA ja conhecida: assim ela
     * nao desaparece, e as opcionais nao voltam a incomodar.
     */
    private fun verificarAtualizacaoAoAbrir(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            verificarAtualizacao(manual = false)
        } else {
            obrigatoriaPendente?.let { mostrarDialogoAtualizacao(it, obrigatoria = true) }
        }
    }

    private fun verificarAtualizacao(manual: Boolean) {
        lifecycleScope.launch {
            when (val resultado = VerificadorAtualizacao.verificar(BuildConfig.VERSION_NAME)) {
                is ResultadoVerificacao.Disponivel -> {
                    val info = resultado.info
                    if (resultado.obrigatoria) {
                        obrigatoriaPendente = info
                        mostrarDialogoAtualizacao(info, obrigatoria = true)
                    } else {
                        obrigatoriaPendente = null
                        // Automatico: respeita o "Mais tarde" para esta versao. Manual: mostra sempre.
                        if (manual || Prefs(this@PrincipalAtiv).versaoIgnorada != info.versao) {
                            mostrarDialogoAtualizacao(info, obrigatoria = false)
                        }
                    }
                }
                ResultadoVerificacao.Atualizado -> {
                    obrigatoriaPendente = null
                    if (manual) {
                        Toast.makeText(
                            this@PrincipalAtiv,
                            getString(R.string.msg_sem_atualizacoes, BuildConfig.VERSION_NAME.substringBefore('-')),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                ResultadoVerificacao.Falhou -> {
                    if (manual) {
                        Toast.makeText(this@PrincipalAtiv, R.string.msg_verificacao_falhou, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun mostrarDialogoAtualizacao(info: InfoAtualizacao, obrigatoria: Boolean) {
        if (isFinishing || isDestroyed) return
        dialogoAtualizacao?.dismiss()

        val mensagem = if (obrigatoria) {
            getString(R.string.atualizacao_msg_obrigatoria, info.versao, info.novidades)
        } else {
            getString(
                R.string.atualizacao_msg,
                info.versao,
                BuildConfig.VERSION_NAME.substringBefore('-'),
                info.novidades
            )
        }

        val construtor = MaterialAlertDialogBuilder(this)
            .setTitle(if (obrigatoria) R.string.atualizacao_titulo_obrigatoria else R.string.atualizacao_titulo)
            .setMessage(mensagem.trim())
            .setCancelable(!obrigatoria)
            // Listener nulo: o clique e tratado abaixo para a obrigatoria nao fechar o dialogo.
            .setPositiveButton(R.string.acao_atualizar, null)
        if (!obrigatoria) {
            construtor.setNegativeButton(R.string.acao_mais_tarde) { _, _ ->
                Prefs(this).versaoIgnorada = info.versao
            }
        }

        val dialogo = construtor.create()
        dialogo.show()
        dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            abrirDownload(info.urlApk)
            if (!obrigatoria) dialogo.dismiss()
        }
        dialogoAtualizacao = dialogo
    }

    /** Abre o APK/pagina de download no navegador (so https; senao, a pagina de Releases). */
    private fun abrirDownload(urlApk: String) {
        val destino = urlApk.takeIf { it.startsWith("https://") } ?: VerificadorAtualizacao.URL_RELEASES
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destino)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.msg_sem_navegador, Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        /** Atualizacao obrigatoria ja conhecida neste processo (sobrevive a recriacoes do ecra). */
        var obrigatoriaPendente: InfoAtualizacao? = null
    }
}
