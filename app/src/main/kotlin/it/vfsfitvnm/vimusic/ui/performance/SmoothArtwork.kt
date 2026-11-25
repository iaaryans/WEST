package it.vfsfitvnm.vimusic.ui.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ColorFilter
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Size-bounded, stable Coil requests. No animation per bitmap during fast scrolling. */
@Composable
internal fun SmoothArtwork(
    url: String?,
    targetPixels: Int,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val bounded = HomeArtworkPolicy.pixels(targetPixels)
    val request = remember(context, url, bounded) {
        ImageRequest.Builder(context)
            .data(HomeArtworkPolicy.requestUrl(url, bounded))
            .size(bounded, bounded)
            .crossfade(false)
            .allowHardware(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = contentScale,
        colorFilter = colorFilter,
        modifier = modifier
    )
}
