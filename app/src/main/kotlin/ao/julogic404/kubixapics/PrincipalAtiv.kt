package ao.julogic404.kubixapics

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
import ao.julogic404.kubixapics.core.data.Prefs
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
        binding = AtivPrincipalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyInsets()
        setupClickListeners()
        requestLegacyStoragePermission()
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
}
