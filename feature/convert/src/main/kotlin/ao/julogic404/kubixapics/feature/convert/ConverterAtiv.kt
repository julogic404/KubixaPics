package ao.julogic404.kubixapics.feature.convert

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
import ao.julogic404.kubixapics.core.common.model.ImagemFormato
import ao.julogic404.kubixapics.core.common.util.Formatadores
import ao.julogic404.kubixapics.core.data.ModeloViewListaEdit
import ao.julogic404.kubixapics.core.data.SalvarImagem
import ao.julogic404.kubixapics.core.ui.AdaptadorImagemEditada
import ao.julogic404.kubixapics.feature.convert.databinding.AtivConverterBinding

/**
 * Ecra de conversao de formato.
 *
 * Entrada: JPG, PNG, WEBP, GIF, BMP, TIFF, SVG, AVIF, HEIC/HEIF.
 * Saida: WEBP, PNG, JPG, AVIF, BMP, TIFF.
 */
class ConverterAtiv : AppCompatActivity() {

    private lateinit var binding: AtivConverterBinding
    private lateinit var adapter: AdaptadorImagemEditada
    private val viewModel: ConverteModeloView by viewModels()

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.process(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AtivConverterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupToolbar()
        setupRecyclerView()
        setupFormatChips()
        setupQualityControls()
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

    private fun setupFormatChips() {
        binding.grupoChipsFormato.removeAllViews()
        ImagemFormato.outputFormats.forEach { format ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = format.label
                isCheckable = true
                isChecked = viewModel.outputFormat == format
                id = View.generateViewId()
                setOnClickListener {
                    viewModel.outputFormat = format
                    updateQualityAvailability(format)
                }
            }
            binding.grupoChipsFormato.addView(chip)
        }
        updateQualityAvailability(viewModel.outputFormat)
    }

    private fun updateQualityAvailability(format: ImagemFormato) {
        val enabled = format.supportsQuality
        binding.sliderQualidade.isEnabled = enabled
        binding.tvDicaQualidade.text = if (enabled) {
            qualityHint(viewModel.quality)
        } else {
            getString(R.string.qualidade_nao_aplicavel, format.label)
        }
    }

    private fun setupQualityControls() {
        binding.sliderQualidade.value = viewModel.quality
            .coerceIn(10, 100)
            .let { (it / 5) * 5 }
            .toFloat()
        updateQualityLabels(viewModel.quality)
        binding.sliderQualidade.addOnChangeListener { _, value, fromUser ->
            val quality = value.toInt()
            updateQualityLabels(quality)
            if (fromUser) viewModel.quality = quality
        }

        val options = listOf(0, 2048, 1280, 800)
        val labels = listOf(
            getString(R.string.tam_max_original),
            getString(R.string.tam_max_2048),
            getString(R.string.tam_max_1280),
            getString(R.string.tam_max_800)
        )
        binding.grupoChipsTamanho.removeAllViews()
        options.forEachIndexed { index, dimension ->
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = labels[index]
                isCheckable = true
                isChecked = viewModel.maxDimension == dimension
                id = View.generateViewId()
                setOnClickListener { viewModel.maxDimension = dimension }
            }
            binding.grupoChipsTamanho.addView(chip)
        }
    }

    private fun updateQualityLabels(quality: Int) {
        binding.tvValorQualidade.text = getString(R.string.qualidade_valor, quality)
        if (viewModel.outputFormat.supportsQuality) {
            binding.tvDicaQualidade.text = qualityHint(quality)
        }
    }

    private fun qualityHint(quality: Int): String = when {
        quality < 55 -> getString(R.string.qualidade_dica_baixa)
        quality < 85 -> getString(R.string.qualidade_dica_media)
        else -> getString(R.string.qualidade_dica_alta)
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
                getString(R.string.a_converter)
            } else {
                getString(R.string.zona_soltar_titulo)
            }
            binding.cartaoZonaSoltar.isEnabled = !processing
            binding.btnAdicionarMais.isEnabled = !processing
            binding.sliderQualidade.isEnabled = !processing && viewModel.outputFormat.supportsQuality
        }

        viewModel.progress.observe(this) { value ->
            binding.barraProgresso.isIndeterminate = value <= 0f
            if (value > 0f) binding.barraProgresso.progress = (value * 100).toInt()
        }

        viewModel.events.observe(this) { event ->
            when (val message = event.getIfNotHandled() ?: return@observe) {
                is ModeloViewListaEdit.MensagemUi.Processadas ->
                    toast(getString(R.string.msg_convertidas, message.count))

                is ModeloViewListaEdit.MensagemUi.ProcessadasParcial ->
                    toast(getString(R.string.msg_convertidas_parcial, message.count, message.failed))

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
            val converted = images.sumOf { it.resultSize }
            val saved = (original - converted).coerceAtLeast(0L)
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
