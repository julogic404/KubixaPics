package ao.julogic404.kubixapics.feature.resize

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.snackbar.Snackbar
import ao.julogic404.kubixapics.core.data.ImagemRedimensionador
import ao.julogic404.kubixapics.feature.resize.databinding.AtivRedimensionarBinding
import ao.julogic404.kubixapics.core.common.model.PreRedimensoes
import kotlin.math.roundToInt

/**
 * Ecra de redimensionamento.
 *
 * Modos: dimensoes exatas (com ou sem proporcao), percentagem, limites maximos
 * e presets de redes sociais. Pre-visualizacao em tempo real do tamanho alvo.
 */
class RedimensionarAtiv : AppCompatActivity() {

    private lateinit var binding: AtivRedimensionarBinding
    private val viewModel: RedimensionaModeloView by viewModels()

    private var suppressTextWatchers = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.load(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AtivRedimensionarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupToolbar()
        setupModeChips()
        setupPresets()
        setupInputs()
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

    private fun setupModeChips() {
        val modes = listOf(
            R.string.redimensao_modo_exato to 0,
            R.string.redimensao_modo_percentagem to 1,
            R.string.redimensao_modo_max to 2
        )
        binding.grupoChipsModo.removeAllViews()
        modes.forEach { (labelRes, index) ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = getString(labelRes)
                isCheckable = true
                isChecked = index == 0
                id = View.generateViewId()
                setOnClickListener { switchMode(index) }
            }
            binding.grupoChipsModo.addView(chip)
        }
        switchMode(0)
    }

    private fun switchMode(index: Int) {
        binding.grupoExato.visibility = if (index == 0) View.VISIBLE else View.GONE
        binding.grupoPercentagem.visibility = if (index == 1) View.VISIBLE else View.GONE
        binding.grupoMaximo.visibility = if (index == 2) View.VISIBLE else View.GONE
        binding.grupoPredefinicoes.visibility = if (index == 0) View.VISIBLE else View.GONE
        updateMode()
    }

    private fun setupPresets() {
        binding.grupoChipsPredefinicoes.removeAllViews()
        PreRedimensoes.all.forEach { preset ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = "${preset.label} · ${preset.width}×${preset.height}"
                isCheckable = true
                id = View.generateViewId()
                setOnClickListener {
                    // Preset = dimensoes exatas, por isso desliga "manter proporcao".
                    suppressTextWatchers = true
                    binding.etLargura.setText(preset.width.toString())
                    binding.etAltura.setText(preset.height.toString())
                    binding.switchManterProporcao.isChecked = false
                    suppressTextWatchers = false
                    updateMode()
                }
            }
            binding.grupoChipsPredefinicoes.addView(chip)
        }
    }

    private fun setupInputs() {
        binding.etLargura.addTextChangedListener(simpleWatcher {
            binding.grupoChipsPredefinicoes.clearCheck()
            if (binding.switchManterProporcao.isChecked) syncHeightFromWidth()
            updateMode()
        })
        binding.etAltura.addTextChangedListener(simpleWatcher {
            binding.grupoChipsPredefinicoes.clearCheck()
            if (binding.switchManterProporcao.isChecked) syncWidthFromHeight()
            updateMode()
        })
        binding.etPercentagem.addTextChangedListener(simpleWatcher { updateMode() })
        binding.etLarguraMax.addTextChangedListener(simpleWatcher { updateMode() })
        binding.etAlturaMax.addTextChangedListener(simpleWatcher { updateMode() })
        binding.switchManterProporcao.setOnCheckedChangeListener { _, checked ->
            if (suppressTextWatchers) return@setOnCheckedChangeListener
            if (checked) syncHeightFromWidth()
            updateMode()
        }
    }

    /** Com "manter proporcao" ligado, escrever a largura preenche a altura (e vice-versa). */
    private fun syncHeightFromWidth() {
        val ow = viewModel.origWidth
        if (ow <= 0) return
        val w = binding.etLargura.text.toString().toIntOrNull() ?: return
        val h = (w.toDouble() * viewModel.origHeight / ow).roundToInt().coerceAtLeast(1)
        suppressTextWatchers = true
        binding.etAltura.setText(h.toString())
        suppressTextWatchers = false
    }

    private fun syncWidthFromHeight() {
        val oh = viewModel.origHeight
        if (oh <= 0) return
        val h = binding.etAltura.text.toString().toIntOrNull() ?: return
        val w = (h.toDouble() * viewModel.origWidth / oh).roundToInt().coerceAtLeast(1)
        suppressTextWatchers = true
        binding.etLargura.setText(w.toString())
        suppressTextWatchers = false
    }

    private fun simpleWatcher(onChange: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!suppressTextWatchers) onChange()
        }
    }

    private fun updateMode() {
        val exactVisible = binding.grupoExato.visibility == View.VISIBLE
        val percentVisible = binding.grupoPercentagem.visibility == View.VISIBLE
        val maxVisible = binding.grupoMaximo.visibility == View.VISIBLE

        viewModel.mode = when {
            exactVisible -> {
                val w = binding.etLargura.text.toString().toIntOrNull() ?: viewModel.origWidth
                val h = binding.etAltura.text.toString().toIntOrNull() ?: viewModel.origHeight
                ImagemRedimensionador.Modo.Exato(
                    w.coerceIn(1, MAX_SIDE),
                    h.coerceIn(1, MAX_SIDE),
                    binding.switchManterProporcao.isChecked
                )
            }
            percentVisible -> {
                val p = binding.etPercentagem.text.toString().toIntOrNull() ?: 100
                ImagemRedimensionador.Modo.Percentagem(p.coerceIn(1, 400))
            }
            maxVisible -> {
                val w = binding.etLarguraMax.text.toString().toIntOrNull() ?: viewModel.origWidth
                val h = binding.etAlturaMax.text.toString().toIntOrNull() ?: viewModel.origHeight
                ImagemRedimensionador.Modo.Maximo(w.coerceIn(1, MAX_SIDE), h.coerceIn(1, MAX_SIDE))
            }
            else -> ImagemRedimensionador.Modo.Percentagem(100)
        }
        renderTargetSize()
    }

    private fun renderTargetSize() {
        if (viewModel.origWidth <= 0) {
            binding.tvTamanhoAlvo.text = getString(R.string.redimensao_sem_imagem)
            return
        }
        val (w, h) = viewModel.targetSize()
        binding.tvTamanhoAlvo.text = getString(R.string.redimensao_alvo, w, h)
        viewModel.renderPreview()
    }

    private fun setupClickListeners() {
        binding.cartaoEscolher.setOnClickListener { launchPicker() }
        binding.btnAplicar.setOnClickListener { applyResize() }
    }

    private fun launchPicker() {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun applyResize() {
        if (viewModel.originalBitmap == null) {
            toast(getString(R.string.redimensao_sem_imagem))
            return
        }
        val (w, h) = viewModel.targetSize()
        viewModel.applyAndSave("redimensionada-${w}x${h}.png")
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { loading ->
            binding.barraProgresso.visibility = if (loading) View.VISIBLE else View.GONE
            updateApplyEnabled()
        }

        viewModel.original.observe(this) { bitmap ->
            // Nao se mostra aqui o bitmap completo: fotos muito grandes excedem o
            // limite de textura da GPU e o ImageView rebenta. A pre-visualizacao
            // (renderTargetSize -> renderPreview) usa uma versao reduzida.
            binding.tvTamanhoOriginal.text = if (bitmap != null) {
                getString(R.string.redimensao_original, bitmap.width, bitmap.height)
            } else {
                getString(R.string.redimensao_sem_imagem)
            }
            updateApplyEnabled()
            if (bitmap != null) {
                suppressTextWatchers = true
                binding.etLargura.setText(bitmap.width.toString())
                binding.etAltura.setText(bitmap.height.toString())
                binding.etPercentagem.setText("100")
                binding.etLarguraMax.setText(bitmap.width.toString())
                binding.etAlturaMax.setText(bitmap.height.toString())
                suppressTextWatchers = false
            }
            renderTargetSize()
        }

        viewModel.preview.observe(this) { bmp ->
            if (bmp != null) binding.ivPreVisualizacao.setImageBitmap(bmp)
        }

        viewModel.events.observe(this) { event ->
            when (event.getIfNotHandled() ?: return@observe) {
                RedimensionaModeloView.MensagemUi.Guardada -> toast(getString(R.string.msg_guardada))
                RedimensionaModeloView.MensagemUi.FalhaAoGuardar -> toast(getString(R.string.msg_guardar_falhou))
                RedimensionaModeloView.MensagemUi.RedimensaoFalhou -> toast(getString(R.string.msg_redimensao_falhou))
            }
        }
    }

    private fun updateApplyEnabled() {
        binding.btnAplicar.isEnabled =
            viewModel.originalBitmap != null && viewModel.isLoading.value != true
    }

    private fun toast(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT)
            .setAnchorView(binding.barraInferior)
            .show()
    }

    private companion object {
        const val MAX_SIDE = RedimensionaModeloView.MAX_SIDE
    }
}
