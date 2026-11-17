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
