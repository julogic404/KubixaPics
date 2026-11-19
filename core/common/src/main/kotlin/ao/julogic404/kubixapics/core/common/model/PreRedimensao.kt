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

package ao.julogic404.kubixapics.core.common.model

/** Dimensoes predefinidas para redes sociais e ecras. */
data class PreRedimensao(val label: String, val width: Int, val height: Int)

object PreRedimensoes {

    val instagramPost = PreRedimensao("Instagram Post", 1080, 1080)
    val instagramStory = PreRedimensao("Instagram Story", 1080, 1920)
    val facebookCover = PreRedimensao("Facebook Cover", 851, 315)
    val youtubeThumbnail = PreRedimensao("YouTube Thumbnail", 1280, 720)
    val whatsappProfile = PreRedimensao("WhatsApp Profile", 500, 500)
    val hd = PreRedimensao("HD", 1600, 900)
    val fullHd = PreRedimensao("Full HD", 1920, 1080)
    val cover = PreRedimensao("Cover", 3000, 3000)

    val all: List<PreRedimensao> = listOf(
        instagramPost,
        instagramStory,
        facebookCover,
        youtubeThumbnail,
        whatsappProfile,
        fullHd,
        cover
    )
}
