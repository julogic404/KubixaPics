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

import ao.julogic404.kubixapics.core.ui.aplicarFundoKubixa
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ao.julogic404.kubixapics.databinding.AtivSobreBinding

/**
 * Ecra "Sobre".
 */
class SobreAtiv : AppCompatActivity() {

    private lateinit var binding: AtivSobreBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aplicarFundoKubixa()
        binding = AtivSobreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val side = resources.getDimensionPixelSize(R.dimen.padding_ecra)
        val toolbarHeight = resources.getDimensionPixelSize(R.dimen.altura_barra_ferramentas)
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
            binding.scrollConteudo.setPadding(
                bars.left + side,
                binding.scrollConteudo.paddingTop,
                bars.right + side,
                bars.bottom
            )
            insets
        }

        binding.barraFerramentas.setNavigationOnClickListener { finish() }
        // Hiperligacao para o portfolio no GitHub (sublinhada para parecer um link).
        binding.tvGithub.paintFlags = binding.tvGithub.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        binding.cartaoDesenvolvedor.setOnClickListener { openPortfolio() }
    }

    private fun openPortfolio() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.sobre_desenvolvedor_url)))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Sem navegador instalado.
            Toast.makeText(this, R.string.sobre_desenvolvedor_url, Toast.LENGTH_SHORT).show()
        }
    }
}
