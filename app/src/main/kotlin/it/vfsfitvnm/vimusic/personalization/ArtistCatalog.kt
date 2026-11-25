package it.vfsfitvnm.vimusic.personalization

import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from

/** Live artist lookup: never ships a finite "all artists" list in the APK. */
internal object ArtistCatalog {
    // A small initial display list; catalog searches cover other artists and newly added musicians.
    val popular = listOf(
        "Arijit Singh", "Shreya Ghoshal", "Diljit Dosanjh", "Atif Aslam",
        "A. R. Rahman", "Pritam", "Sonu Nigam", "Karan Aujla",
        "The Weeknd", "Taylor Swift", "Billie Eilish", "Ed Sheeran",
        "Bruno Mars", "BTS", "BLACKPINK", "Anuv Jain"
    )

    suspend fun search(term: String): List<ArtistChoice> {
        if (term.isBlank()) return emptyList()
        return Innertube.searchPage(
            SearchBody(query = term, params = Innertube.SearchFilter.Artist.value),
            Innertube.ArtistItem.Companion::from
        )?.getOrNull()?.items.orEmpty()
            .mapNotNull { result ->
                val name = result.info?.name?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val id = result.info?.endpoint?.browseId ?: return@mapNotNull null
                ArtistChoice(name = name, id = id, photoUrl = result.thumbnail?.url)
            }
            .distinctBy { it.id }
            .take(24)
    }
}
