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

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

/**
 * Ecra de abertura (splash) do Kubixa Pics: mostra a imagem de marca por instantes
 * e segue para o ecra principal.
 */
class AberturaAtiv : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val irParaPrincipal = Runnable {
        startActivity(Intent(this, PrincipalAtiv::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ativ_abertura)
        handler.postDelayed(irParaPrincipal, DURACAO_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(irParaPrincipal)
        super.onDestroy()
    }

    private companion object {
        const val DURACAO_MS = 1800L
    }
}
