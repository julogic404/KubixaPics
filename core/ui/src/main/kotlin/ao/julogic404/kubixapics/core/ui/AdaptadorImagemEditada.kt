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

package ao.julogic404.kubixapics.core.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ao.julogic404.kubixapics.core.ui.databinding.ItemImagemEditadaBinding
import ao.julogic404.kubixapics.core.common.model.ImagemEditada
import ao.julogic404.kubixapics.core.common.util.Formatadores

/**
 * Lista generica de imagens editadas (conversao, redimensionamento, compressao).
 *
 * Usa ListAdapter + DiffUtil para atualizacoes corretas apos remocoes.
 */
class AdaptadorImagemEditada(
    private val onSave: (ImagemEditada) -> Unit,
    private val onShare: (ImagemEditada) -> Unit,
    private val onRemove: (ImagemEditada) -> Unit
) : ListAdapter<ImagemEditada, AdaptadorImagemEditada.ImagemHolder>(DIFF) {

    class ImagemHolder(val binding: ItemImagemEditadaBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImagemHolder {
        val binding = ItemImagemEditadaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ImagemHolder(binding)
    }

    override fun onBindViewHolder(holder: ImagemHolder, position: Int) {
        val image = getItem(position)
        val b = holder.binding
        val context = b.root.context

        b.ivPreVisualizacao.setImageBitmap(image.thumbnail)
        b.tvNomeFicheiro.text = image.displayName
        b.tvTamanhos.text = context.getString(
            R.string.seta_tamanho,
            Formatadores.formatBytes(image.originalSize),
            Formatadores.formatBytes(image.resultSize)
        )
        b.tvDimensoes.text = "${image.width} \u00d7 ${image.height}"

        if (image.hasSavings) {
            b.tvPoupanca.text = context.getString(R.string.poupanca_selo, image.savingsPercent)
            b.tvPoupanca.setBackgroundResource(R.drawable.fundo_selo_sucesso)
            b.tvPoupanca.setTextColor(ContextCompat.getColor(context, R.color.sucesso))
        } else {
            b.tvPoupanca.text = context.getString(R.string.poupanca_nenhuma)
            b.tvPoupanca.setBackgroundResource(R.drawable.fundo_selo_neutro)
            b.tvPoupanca.setTextColor(ContextCompat.getColor(context, R.color.texto_suave))
        }

        b.btnGuardar.setOnClickListener { onSave(image) }
        b.btnPartilhar.setOnClickListener { onShare(image) }
        b.btnRemover.setOnClickListener { onRemove(image) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ImagemEditada>() {
            override fun areItemsTheSame(oldItem: ImagemEditada, newItem: ImagemEditada) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ImagemEditada, newItem: ImagemEditada) =
                oldItem.id == newItem.id &&
                    oldItem.resultSize == newItem.resultSize &&
                    oldItem.displayName == newItem.displayName
        }
    }
}
