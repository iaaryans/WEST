@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package it.vfsfitvnm.vimusic.personalization

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.preferences
import it.vfsfitvnm.vimusic.utils.secondary
import it.vfsfitvnm.vimusic.utils.semiBold
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope

private val languages = listOf("Hindi", "English", "Punjabi", "Bengali", "Tamil", "Telugu", "Malayalam", "Marathi", "Gujarati", "Kannada", "Japanese", "Korean", "Spanish", "Arabic", "Other")
private val moods = listOf("Happy", "Sad", "Chill", "Focus", "Workout", "Relax", "Sleep", "Night Drive", "Travel", "Energetic", "Melancholy", "Custom")

/** Full-screen first-run experience. Skipping is supported and does not block playback. */
@Composable
internal fun PersonalizationOnboardingScreen(onFinished: () -> Unit) {
    val prefs = LocalContext.current.preferences
    val initial = remember(prefs) { TasteProfileStore.load(prefs) }
    val (palette, typography) = LocalAppearance.current
    var stage by rememberSaveable { mutableStateOf(0) }
    var selectedLanguages by rememberSaveable { mutableStateOf(initial.languages) }
    var selectedMoods by rememberSaveable { mutableStateOf(initial.moods) }
    // The selection is represented by stable names/IDs; no image bytes are stored in prefs.
    var selectedArtists by remember { mutableStateOf(initial.artists) }
    var selectedSongs by remember { mutableStateOf(initial.songs) }
    val stageTitle = listOf("Music that gets you", "Your music languages", "Choose your favorite artists", "Choose songs you love", "Choose your listening vibes", "Your WEST is ready")
    val stageHint = listOf(
        "Tell WEST what you enjoy. You can skip or change everything later.",
        "Choose any languages you listen to.",
        "Search any artist above or pick from these famous artists.",
        "Select tracks you already enjoy. This step is optional.",
        "Your favorite moods help shape Moodspace.",
        "WEST Intelligence will learn from your listening, favorites and feedback."
    )
    val finish: (Boolean) -> Unit = { save ->
        if (save) TasteProfileStore.save(prefs, TasteProfile(selectedLanguages, selectedArtists, selectedSongs, selectedMoods))
        else TasteProfileStore.dismiss(prefs)
        onFinished()
    }

    Column(
        Modifier.fillMaxSize().background(palette.background0)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText("WEST  ·  INTELLIGENCE", style = typography.s.semiBold)
            SecondaryTextButton("Skip", onClick = { finish(false) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(6) { i -> Spacer(Modifier.weight(1f).height(4.dp)
                .background(if (i <= stage) palette.accent else palette.background2, RoundedCornerShape(8.dp))) }
        }
        BasicText(stageTitle[stage], style = typography.xxl.semiBold)
        BasicText(stageHint[stage], style = typography.s.secondary)
        when (stage) {
            0 -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    BasicText("♫", style = typography.xxl.copy(color = palette.accent))
                    BasicText("Your music. Your taste. Your space.", style = typography.l.semiBold,
                        modifier = Modifier.padding(horizontal = 12.dp))
                    BasicText("A personalized Home, Moodspace and music discovery from day one.",
                        style = typography.s.secondary.copy(textAlign = TextAlign.Center))
                }
            }
            1 -> ChoiceGrid(languages, selectedLanguages, { item ->
                selectedLanguages = if (item in selectedLanguages) selectedLanguages - item else selectedLanguages + item
            }, Modifier.weight(1f))
            2 -> ArtistSelection(selectedArtists, { choice ->
                selectedArtists = if (selectedArtists.any { it.name.equals(choice.name, true) })
                    selectedArtists.filterNot { it.name.equals(choice.name, true) }
                else selectedArtists + choice
            }, selectedLanguages, Modifier.weight(1f))
            3 -> SongSelection(selectedSongs, { song ->
                selectedSongs = if (selectedSongs.any { it.id == song.id })
                    selectedSongs.filterNot { it.id == song.id }
                else selectedSongs + song
            }, selectedArtists, Modifier.weight(1f))
            4 -> ChoiceGrid(moods, selectedMoods, { item ->
                selectedMoods = if (item in selectedMoods) selectedMoods - item else selectedMoods + item
            }, Modifier.weight(1f))
            else -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText("✦", style = typography.xxl.copy(color = palette.accent))
                Spacer(Modifier.height(20.dp))
                BasicText("${selectedArtists.size} artists  ·  ${selectedSongs.size} songs", style = typography.l.semiBold)
                Spacer(Modifier.height(8.dp))
                BasicText("${selectedLanguages.size} languages  ·  ${selectedMoods.size} vibes", style = typography.s.secondary)
                Spacer(Modifier.height(16.dp))
                BasicText("Your choices can be edited in Settings → Privacy & history.",
                    style = typography.s.secondary.copy(textAlign = TextAlign.Center))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            if (stage > 0) SecondaryTextButton("Back", onClick = { stage-- })
            else Spacer(Modifier.width(42.dp))
            SecondaryTextButton(if (stage == 5) "Open WEST  ✓" else "Continue  →", onClick = {
                if (stage == 5) finish(true) else stage++
            })
        }
    }
}

@Composable
private fun ChoiceGrid(options: List<String>, selected: List<String>, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val (palette, typography) = LocalAppearance.current
    LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(options) { item ->
            val isSelected = item in selected
            BasicText(text = (if (isSelected) "✓  " else "○  ") + item,
                style = typography.s.semiBold.copy(color = if (isSelected) palette.accent else palette.text),
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) palette.background2 else palette.background1)
                    .clickable { onPick(item) }.padding(16.dp))
        }
    }
}

@Composable
private fun ArtistSelection(
    selected: List<ArtistChoice>, onPick: (ArtistChoice) -> Unit,
    preferredLanguages: List<String>, modifier: Modifier = Modifier
) {
    val (palette, typography) = LocalAppearance.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<ArtistChoice>()) }
    var photoLookup by remember { mutableStateOf(emptyMap<String, ArtistChoice>()) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Seed cards are visible immediately; asynchronously resolve their actual catalog photos and IDs.
    val suggested = remember(preferredLanguages) {
        val names = when {
            "Punjabi" in preferredLanguages -> listOf("Diljit Dosanjh", "Karan Aujla", "Arijit Singh", "Shreya Ghoshal")
            "English" in preferredLanguages && "Hindi" !in preferredLanguages -> listOf("The Weeknd", "Taylor Swift", "Billie Eilish", "Ed Sheeran")
            else -> listOf("Arijit Singh", "Shreya Ghoshal", "Diljit Dosanjh", "Atif Aslam")
        }
        (names + ArtistCatalog.popular).distinct().take(12)
    }
    LaunchedEffect(suggested) {
        // Resolve a bounded set of 12 popular profiles in batches of four.
        supervisorScope {
            fun normalized(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
            suggested.chunked(4).forEach { batch ->
                batch.map { name -> async {
                    try { name to ArtistCatalog.search(name).firstOrNull { normalized(it.name) == normalized(name) } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { name to null }
                } }.awaitAll().forEach { (name, found) ->
                    if (found != null) photoLookup = photoLookup + (name to found)
                }
            }
        }
    }
    LaunchedEffect(query) {
        results = emptyList()
        failed = false
        if (query.trim().length < 2) { loading = false; return@LaunchedEffect }
        delay(320)
        loading = true
        try { results = ArtistCatalog.search(query.trim()); failed = results.isEmpty() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    SearchInput(query, onChange = { query = it }, placeholder = "Search any singer or artist...")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        BasicText(if (query.length < 2) "Popular artists" else "Search results", style = typography.s.semiBold)
        BasicText("${selected.size} selected", style = typography.xs.secondary)
    }
    if (loading) BasicText("Searching artists...", style = typography.xs.secondary)
    if (failed && results.isEmpty()) BasicText("No catalog results. Check your connection or try another name.", style = typography.xs.secondary)
    val choices = if (query.trim().length >= 2) results else suggested.map { name ->
        photoLookup[name] ?: selected.firstOrNull { it.name == name } ?: ArtistChoice(name)
    }
    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(choices, key = { it.id ?: it.name }) { artist ->
            val chosen = selected.any { it.name.equals(artist.name, true) }
            Column(Modifier.fillMaxWidth().clickable { onPick(artist) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(88.dp).clip(CircleShape).background(palette.background2), contentAlignment = Alignment.Center) {
                    BasicText("♫", style = typography.xxl.secondary)
                    if (!artist.photoUrl.isNullOrBlank()) AsyncImage(model = artist.photoUrl,
                        contentDescription = "Photo of ${artist.name}", contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape))
                }
                BasicText(artist.name, style = typography.xs.semiBold.copy(textAlign = TextAlign.Center),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(if (chosen) "✓ Selected" else "Tap to select",
                    style = typography.xxs.copy(color = if (chosen) palette.accent else palette.textSecondary))
            }
        }
    }
}

@Composable
private fun SongSelection(selected: List<SongChoice>, onPick: (SongChoice) -> Unit,
    selectedArtists: List<ArtistChoice>, modifier: Modifier = Modifier
) {
    val (_, typography) = LocalAppearance.current
    var query by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf(emptyList<SongChoice>()) }
    var loading by remember { mutableStateOf(false) }
    val lookup = query.trim().ifEmpty { selectedArtists.firstOrNull()?.name.orEmpty() }
    LaunchedEffect(lookup) {
        result = emptyList()
        if (lookup.isBlank()) return@LaunchedEffect
        delay(320)
        loading = true
        try {
            result = Innertube.searchPage(SearchBody(query = lookup, params = Innertube.SearchFilter.Song.value),
                Innertube.SongItem.Companion::from)?.getOrNull()?.items.orEmpty().mapNotNull {
                val id = it.info?.endpoint?.videoId ?: return@mapNotNull null
                SongChoice(id, it.info?.name ?: "Untitled")
            }.distinctBy { it.id }.take(24)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { result = emptyList() }
        finally { loading = false }
    }
    SearchInput(query, onChange = { query = it }, placeholder = "Find favorite songs...")
    BasicText("${selected.size} songs selected", style = typography.xs.secondary)
    if (loading) BasicText("Finding songs...", style = typography.xs.secondary)
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(result, key = { it.id }) { song ->
            Row(Modifier.fillMaxWidth().clickable { onPick(song) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(song.title, modifier = Modifier.weight(1f), style = typography.s, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                BasicText(if (selected.any { it.id == song.id }) "✓" else "+", style = typography.s)
            }
        }
        if (!loading && result.isEmpty()) item { BasicText("Search a song or choose artists in the previous step.", style = typography.xs.secondary) }
    }
}

@Composable
private fun SearchInput(value: String, onChange: (String) -> Unit, placeholder: String) {
    val (palette, typography) = LocalAppearance.current
    BasicTextField(value = value, onValueChange = onChange, singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = typography.s.copy(color = palette.text),
        modifier = Modifier.fillMaxWidth().background(palette.background1, RoundedCornerShape(16.dp))
            .padding(16.dp), decorationBox = { inner ->
            Box {
                if (value.isEmpty()) BasicText("⌕  $placeholder", style = typography.s.secondary)
                inner()
            }
        })
}
