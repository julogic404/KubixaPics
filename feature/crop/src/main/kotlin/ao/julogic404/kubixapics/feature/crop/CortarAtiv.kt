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

package ao.julogic404.kubixapics.feature.crop

import ao.julogic404.kubixapics.core.ui.aplicarFundoKubixa
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import ao.julogic404.kubixapics.feature.crop.databinding.AtivCortarBinding
import ao.julogic404.kubixapics.core.ui.widget.CortarView

/**
 * Ecra de recorte.
 *
 * Aspetos: livre, 1:1, circular, 16:9, 4:3. Rotacao em passos de 90 graus.
 * A pre-visualizacao e em tempo real (a moldura e arrastavel).
 */
class CortarAtiv : AppCompatActivity() {

    private lateinit var binding: AtivCortarBinding
    private val viewModel: CortaModeloView by viewModels()

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.load(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aplicarFundoKubixa()
        binding = AtivCortarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupToolbar()
        setupAspectChips()
        setupClickListeners()
        observeViewModel()
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
            // O botao Aplicar e guardar vive numa barra fixa que absorve a barra do sistema.
            binding.linhaAcoes.setPadding(bars.left + side, gap, bars.right + side, gap + bars.bottom)
            binding.conteudo.setPadding(
                bars.left + side,
                binding.conteudo.paddingTop,
                bars.right + side,
                0
            )
            insets
        }
    }

    private fun setupToolbar() {
        binding.barraFerramentas.setNavigationOnClickListener { finish() }
    }

    private fun setupAspectChips() {
        binding.grupoChipsProporcao.removeAllViews()
        CortarView.Proporcao.entries.forEach { aspect ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = aspect.label
                isCheckable = true
                isChecked = aspect == CortarView.Proporcao.LIVRE
                id = View.generateViewId()
                setOnClickListener { binding.cortarView.aspect = aspect }
            }
            binding.grupoChipsProporcao.addView(chip)
        }
    }

    private fun setupClickListeners() {
        binding.cartaoEscolher.setOnClickListener { launchPicker() }
        binding.btnRodar.setOnClickListener {
            viewModel.rotationDegrees = (viewModel.rotationDegrees + 90) % 360
            renderPreview()
        }
        binding.btnAplicar.setOnClickListener { applyCrop() }
    }

    private fun launchPicker() {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /**
     * A moldura e normalizada sobre a imagem JA rodada, por isso a pre-visualizacao
     * tem de mostrar a versao rodada (tambem depois de recriar o ecra).
     */
    private fun renderPreview() {
        binding.tvRotacao.text = getString(R.string.cortar_rotacao, viewModel.rotationDegrees)
        binding.cortarView.setBitmap(viewModel.rotatedBitmap())
    }

    private fun applyCrop() {
        if (viewModel.originalBitmap == null) {
            toast(getString(R.string.cortar_sem_imagem))
            return
        }
        viewModel.cropRect = binding.cortarView.getNormalizedCrop()
        viewModel.circular = binding.cortarView.isCircle()
        viewModel.save()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.barraProgresso.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnAplicar.isEnabled = !loading && viewModel.originalBitmap != null
        }

        viewModel.original.observe(this) { bitmap ->
            binding.btnAplicar.isEnabled = bitmap != null && viewModel.isLoading.value != true
            binding.btnRodar.isEnabled = bitmap != null
            binding.dicaVazio.visibility = if (bitmap == null) View.VISIBLE else View.GONE
        }

        viewModel.preview.observe(this) { bitmap ->
            if (bitmap != null) renderPreview() else binding.cortarView.setBitmap(null)
        }

        viewModel.events.observe(this) { event ->
            when (event.getIfNotHandled() ?: return@observe) {
                CortaModeloView.MensagemUi.Guardada -> toast(getString(R.string.msg_guardada))
                CortaModeloView.MensagemUi.FalhaAoGuardar -> toast(getString(R.string.msg_guardar_falhou))
                CortaModeloView.MensagemUi.CorteFalhou -> toast(getString(R.string.msg_corte_falhou))
            }
        }
    }

    private fun toast(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.barraInferior)
            .show()
    }
}
