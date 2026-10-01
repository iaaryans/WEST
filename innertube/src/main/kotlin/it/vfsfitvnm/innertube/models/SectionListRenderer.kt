package it.vfsfitvnm.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SectionListRenderer(
    val contents: List<Content>? = null,
    val continuations: List<Continuation>? = null
) {
    @Serializable
    data class Content(
        @JsonNames("musicImmersiveCarouselShelfRenderer")
        val musicCarouselShelfRenderer: MusicCarouselShelfRenderer? = null,
        @JsonNames("musicPlaylistShelfRenderer")
        val musicShelfRenderer: MusicShelfRenderer? = null,
        val gridRenderer: GridRenderer? = null,
        val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer? = null,
        val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer? = null,
    ) {
        @Serializable
        data class MusicDescriptionShelfRenderer(
            val description: Runs? = null,
        )
    }

    @Serializable
    data class MusicResponsiveHeaderRenderer(
        val title: Runs? = null,
        val subtitle: Runs? = null,
        val secondSubtitle: Runs? = null,
        val straplineTextOne: Runs? = null,
        val thumbnail: ThumbnailRenderer? = null,
    )
}
