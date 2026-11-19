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

package ao.julogic404.kubixapics.feature.compress

import ao.julogic404.kubixapics.core.ui.aplicarFundoKubixa
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import ao.julogic404.kubixapics.core.common.model.ImagemEditada
import ao.julogic404.kubixapics.core.common.util.Formatadores
import ao.julogic404.kubixapics.core.data.ModeloViewListaEdit
import ao.julogic404.kubixapics.core.data.SalvarImagem
import ao.julogic404.kubixapics.core.ui.AdaptadorImagemEditada
import ao.julogic404.kubixapics.feature.compress.databinding.AtivComprimirBinding

/**
 * Ecra de compressao.
 *
 * Comprime para WebP com qualidade ajustavel e/ou alvo de tamanho maximo.
 * Mostra a comparacao antes/depois com percentagem de reducao.
 */
class ComprimirAtiv : AppCompatActivity() {

    private lateinit var binding: AtivComprimirBinding
    private lateinit var adapter: AdaptadorImagemEditada
    private val viewModel: ComprimeModeloView by viewModels()

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.process(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aplicarFundoKubixa()
        binding = AtivComprimirBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupToolbar()
        setupRecyclerView()
        setupQualityControls()
        setupTargetControls()
        setupClickListeners()
        observeViewModel()
    }

    private var bottomInset = 0

    /** Sem a barra fixa visivel (lista vazia), o conteudo tem de respeitar a barra do sistema. */
    private fun applyContentBottomInset() {
        val barVisible = binding.layoutAcoes.visibility == View.VISIBLE
        binding.conteudo.setPadding(
            binding.conteudo.paddingLeft,
            binding.conteudo.paddingTop,
            binding.conteudo.paddingRight,
            if (barVisible) 0 else bottomInset
        )
    }

    private fun applyInsets() {
        val side = resources.getDimensionPixelSize(R.dimen.padding_ecra)
        val toolbarHeight = resources.getDimensionPixelSize(R.dimen.altura_barra_ferramentas)
        val gap = resources.getDimensionPixelSize(R.dimen.espaco_medio)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // Altura = barra de ferramentas + barra de estado; sem isto o titulo fica
            // cortado (o padding comia a altura fixa da toolbar).
            binding.barraFerramentas.layoutParams = binding.barraFerramentas.layoutParams.apply {
                height = toolbarHeight + bars.top
            }
            binding.barraFerramentas.setPadding(bars.left, bars.top, bars.right, 0)
            // A barra de acoes e fixa: ela absorve a barra de navegacao do sistema.
            binding.linhaAcoes.setPadding(bars.left + side, gap, bars.right + side, gap + bars.bottom)
            binding.conteudo.setPadding(
                bars.left + side,
                binding.conteudo.paddingTop,
                bars.right + side,
                binding.conteudo.paddingBottom
            )
            bottomInset = bars.bottom
            applyContentBottomInset()
            insets
        }
    }

    private fun setupToolbar() {
        binding.barraFerramentas.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AdaptadorImagemEditada(
            onSave = { viewModel.save(it) },
            onShare = { shareImage(it) },
            onRemove = { viewModel.remove(it) }
        )
        binding.rvImagens.layoutManager = LinearLayoutManager(this)
        binding.rvImagens.adapter = adapter
    }

    private fun setupQualityControls() {
        binding.sliderQualidade.value = viewModel.compressQuality
            .coerceIn(10, 100)
            .let { (it / 5) * 5 }
            .toFloat()
        updateQualityLabels(viewModel.compressQuality)
        binding.sliderQualidade.addOnChangeListener { _, value, fromUser ->
            val quality = value.toInt()
            updateQualityLabels(quality)
            if (fromUser) viewModel.compressQuality = quality
        }
    }

    private fun updateQualityLabels(quality: Int) {
        binding.tvValorQualidade.text = getString(R.string.qualidade_valor, quality)
        binding.tvDicaQualidade.text = when {
            quality < 55 -> getString(R.string.qualidade_dica_baixa)
            quality < 85 -> getString(R.string.qualidade_dica_media)
            else -> getString(R.string.qualidade_dica_alta)
        }
    }

    private fun setupTargetControls() {
        val options = listOf(0L, 500L * 1024, 1024L * 1024, 2L * 1024 * 1024)
        val labels = listOf(
            getString(R.string.alvo_nenhum),
            getString(R.string.alvo_500kb),
            getString(R.string.alvo_1mb),
            getString(R.string.alvo_2mb)
        )
        binding.grupoChipsAlvo.removeAllViews()
        options.forEachIndexed { index, bytes ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = labels[index]
                isCheckable = true
                isChecked = viewModel.targetBytes == bytes
                id = View.generateViewId()
                setOnClickListener { viewModel.targetBytes = bytes }
            }
            binding.grupoChipsAlvo.addView(chip)
        }
    }

    private fun setupClickListeners() {
        binding.cartaoZonaSoltar.setOnClickListener { launchPicker() }
        binding.btnAdicionarMais.setOnClickListener { launchPicker() }
        binding.btnLimparTudo.setOnClickListener { viewModel.clearAll() }
        binding.btnGuardarTodas.setOnClickListener { viewModel.saveAll() }
    }

    private fun launchPicker() {
        if (viewModel.isProcessing.value == true) return
        pickImages.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun observeViewModel() {
        viewModel.images.observe(this) { images ->
            adapter.submitList(images)
            renderState(images)
        }

        viewModel.isProcessing.observe(this) { processing ->
            binding.barraProgresso.visibility = if (processing) View.VISIBLE else View.GONE
            binding.tvTituloZonaSoltar.text = if (processing) {
                getString(R.string.a_comprimir)
            } else {
                getString(R.string.zona_soltar_titulo)
            }
            binding.cartaoZonaSoltar.isEnabled = !processing
            binding.btnAdicionarMais.isEnabled = !processing
            binding.sliderQualidade.isEnabled = !processing
        }

        viewModel.progress.observe(this) { value ->
            binding.barraProgresso.isIndeterminate = value <= 0f
            if (value > 0f) binding.barraProgresso.progress = (value * 100).toInt()
        }

        viewModel.events.observe(this) { event ->
            when (val message = event.getIfNotHandled() ?: return@observe) {
                is ModeloViewListaEdit.MensagemUi.Processadas ->
                    toast(getString(R.string.msg_comprimidas, message.count))

                is ModeloViewListaEdit.MensagemUi.ProcessadasParcial ->
                    toast(getString(R.string.msg_comprimidas_parcial, message.count, message.failed))

                ModeloViewListaEdit.MensagemUi.Removida -> Unit
                ModeloViewListaEdit.MensagemUi.Limpa -> toast(getString(R.string.msg_limpa))
                ModeloViewListaEdit.MensagemUi.Guardada -> toast(getString(R.string.msg_guardada))
                ModeloViewListaEdit.MensagemUi.FalhaAoGuardar -> toast(getString(R.string.msg_guardar_falhou))
                ModeloViewListaEdit.MensagemUi.NadaParaGuardar -> toast(getString(R.string.msg_nada_para_guardar))
                is ModeloViewListaEdit.MensagemUi.TotalGuardadas ->
                    toast(getString(R.string.msg_guardadas_contagem, message.count))
                is ModeloViewListaEdit.MensagemUi.TotalFalhasAoGuardar ->
                    toast(getString(R.string.msg_guardar_falhou_contagem, message.failed))
            }
        }
    }

    private fun renderState(images: List<ImagemEditada>) {
        val hasImages = images.isNotEmpty()
        binding.estadoVazio.visibility = if (hasImages) View.GONE else View.VISIBLE
        binding.rvImagens.visibility = if (hasImages) View.VISIBLE else View.GONE
        binding.cartaoResumo.visibility = if (hasImages) View.VISIBLE else View.GONE
        binding.layoutAcoes.visibility = if (hasImages) View.VISIBLE else View.GONE
        applyContentBottomInset()

        if (hasImages) {
            val original = images.sumOf { it.originalSize }
            val compressed = images.sumOf { it.resultSize }
            val saved = (original - compressed).coerceAtLeast(0L)
            val percent = if (original > 0L) ((saved * 100.0) / original).toInt() else 0

            binding.tvResumoContagem.text = getString(R.string.resumo_contagem, images.size)
            binding.tvResumoPoupado.text = getString(
                R.string.resumo_poupado,
                Formatadores.formatBytes(saved),
                percent
            )
        }
    }

    private fun shareImage(image: ImagemEditada) {
        val uri = SalvarImagem.shareUri(this, image)
        if (uri == null) {
            toast(getString(R.string.msg_partilha_indisponivel))
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = image.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.acao_partilhar)))
        } catch (t: Throwable) {
            toast(getString(R.string.msg_partilha_indisponivel))
        }
    }

    private fun toast(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.layoutAcoes)
            .show()
    }

    private companion object {
        const val MAX_PICK = 50
    }
}
