package it.vfsfitvnm.vimusic.ui.screens.search

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.vfsfitvnm.innertube.Innertube
import it.vfsfitvnm.innertube.models.bodies.SearchBody
import it.vfsfitvnm.innertube.requests.searchPage
import it.vfsfitvnm.innertube.utils.from
import it.vfsfitvnm.vimusic.Database
import it.vfsfitvnm.vimusic.LocalPlayerServiceBinder
import it.vfsfitvnm.vimusic.ui.components.themed.SecondaryTextButton
import it.vfsfitvnm.vimusic.ui.screens.artistRoute
import it.vfsfitvnm.vimusic.ui.screens.localPlaylistRoute
import it.vfsfitvnm.vimusic.ui.styling.LocalAppearance
import it.vfsfitvnm.vimusic.utils.asMediaItem
import it.vfsfitvnm.vimusic.utils.forcePlay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private object SearchPages {
    private val pages = android.util.LruCache<String, List<Innertube.SongItem>>(20)
    fun get(query: String) = pages.get(query)
    fun put(query: String, rows: List<Innertube.SongItem>) { pages.put(query,rows) }
}

@Composable
private fun QueryField(label: String, value: String, change: (String)->Unit) {
    val (colors, type) = LocalAppearance.current
    BasicTextField(value,change, singleLine=true, textStyle=type.s.copy(color=colors.text),
        cursorBrush=SolidColor(colors.accent), modifier=Modifier.fillMaxWidth().padding(12.dp),
        decorationBox={ field -> Box { if(value.isEmpty()) BasicText(label,style=type.s.copy(color=colors.textSecondary)); field() } })
}

@ExperimentalAnimationApi
@Composable
internal fun IntelligentSearch(onSearch: (String)->Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    var filters by rememberSaveable { mutableStateOf(false) }
    var artist by rememberSaveable { mutableStateOf("") }
    var excluded by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("All") }
    var limit by rememberSaveable { mutableStateOf(0) }
    var offline by rememberSaveable { mutableStateOf(false) }
    var preview by rememberSaveable(input) { mutableStateOf<String?>(null) }
    val request = remember(input,artist,excluded) { discoveryRequest(input,artist,excluded) }
    val local by remember { Database.discoveryRows() }.collectAsState(emptyList())
    val binder = LocalPlayerServiceBinder.current
    val scope = rememberCoroutineScope()
    val typography=LocalAppearance.current.typography
    var retry by remember { mutableStateOf(0) }
    val online = !offline && !request.offline && !request.lyricsOnly && kind in listOf("Songs","All") && request.remoteQuery.length>=2
    var remote by remember(request.remoteQuery,online) { mutableStateOf(if(online) SearchPages.get(request.remoteQuery).orEmpty() else emptyList()) }
    var status by remember(request.remoteQuery,online) { mutableStateOf("") }
    LaunchedEffect(request.remoteQuery,online,retry) {
        if(!online || SearchPages.get(request.remoteQuery)!=null) return@LaunchedEffect
        delay(350)
        status="Searching online…"
        val response=withContext(Dispatchers.IO) {
            Innertube.searchPage(SearchBody(query=request.remoteQuery,params=Innertube.SearchFilter.Song.value),Innertube.SongItem.Companion::from)
        }
        if(response==null || response.isFailure) status="Online search unavailable" else {
            remote=response.getOrNull()?.items.orEmpty().distinctBy { it.key }
            SearchPages.put(request.remoteQuery,remote)
            status=""
        }
    }
    val remoteById=remember(remote) { remote.associateBy { it.key } }
    val suggested=input.isBlank() && artist.isBlank() && excluded.isBlank() && kind=="All" && limit==0 && !offline
    val groups by produceState(initialValue=emptyList<List<DiscoveryRow>>(),local,remote,request,kind,limit,offline,binder,suggested) {
        value=withContext(Dispatchers.Default) {
            val web=remote.map { DiscoveryRow(it.key,it.info?.name.orEmpty(),it.authors.orEmpty().joinToString("") { a->a.name.orEmpty() },"Songs",it.durationText.orEmpty(),0,null,"") }
            val rows=(local+web).distinctBy { it.kind to it.id }
            rows.asSequence().filter { kind=="All" || kind==it.kind }
                .filter { !suggested || it.kind=="Songs" && it.listened>0 }
                .filter { limit==0 || (secondsOf(it.duration) ?: Int.MAX_VALUE)<limit }
                .filter { !(offline||request.offline) || (it.length?.let { n-> n>0 && binder?.cache?.isCached(it.id,0,n)==true }==true) }
                .map { it to discoveryScore(it,request,it.kind=="Songs" && remoteById.containsKey(it.id)) }
                .filter { it.second>=0 }.sortedWith(compareByDescending<Pair<DiscoveryRow,Int>> { it.second }.thenByDescending { it.first.listened }.thenBy { it.first.title })
                .map { it.first }.take(if(suggested) 10 else 250).groupBy(::groupOf).values.toList()
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal=8.dp)) {
        QueryField("Search songs, artists, playlists or lyrics",input){input=it}
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            SecondaryTextButton(text=if(filters) "Hide filters" else "Filters",onClick={filters=!filters})
            SecondaryTextButton(text=if(request.lyricsOnly) "Exit lyrics search" else "Lyrics search",onClick={
                input=if(request.lyricsOnly) input.substringAfter(':').trim() else "lyrics: "+input
            })
        }
        if(filters) {
            QueryField("Artist",artist){artist=it}
            QueryField("Exclude artists or versions (comma-separated)",excluded){excluded=it}
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                SecondaryTextButton(text="Type · "+kind,onClick={val k=listOf("All","Songs","Artists","Playlists","History");kind=k[(k.indexOf(kind)+1)%k.size]})
                SecondaryTextButton(text=if(limit==0) "Any duration" else "Under "+limit/60+" min",onClick={limit=when(limit){0->180;180->300;else->0}})
                SecondaryTextButton(text=if(offline)"Downloaded only" else "Any availability",onClick={offline=!offline})
            }
        }
        if(status.isNotEmpty()) Row {
            BasicText(status,Modifier.padding(8.dp),style=typography.xs)
            if(status=="Online search unavailable") SecondaryTextButton(text="Retry",onClick={retry++})
        }
        if(suggested && groups.isNotEmpty()) BasicText("From your listening history",Modifier.padding(8.dp),style=typography.s)
        LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
            if(groups.isEmpty()) item { BasicText(if(suggested) "Search your library or try a phrase like “relaxing Hindi songs”." else "No matches yet",Modifier.padding(12.dp),style=typography.s) }
            items(groups,key={groupOf(it.first())}) { variants ->
                val first=variants.first()
                val group=groupOf(first)
                val renditions=variants.distinctBy { row ->
                    val duration=secondsOf(row.duration)
                    if(duration==null || row.artist.isBlank()) row.id else foldSearch(row.title)+"|"+duration
                }
                Column(Modifier.fillMaxWidth().clickable { preview=if(preview==group)null else group }.padding(10.dp)) {
                    BasicText(first.title,style=typography.s,maxLines=2,overflow=TextOverflow.Ellipsis)
                    BasicText(listOf(first.kind,first.artist,first.duration).filter { it.isNotBlank() }.joinToString(" · ")+
                        (if(renditions.size>1) " · "+renditions.size+" versions" else ""),style=typography.xs)
                    if(preview==group) {
                        renditions.forEach { row ->
                            if(row.kind=="Songs") BasicText(versionOf(row.title)+" · "+row.title+" "+row.duration,style=typography.xs)
                            if(request.lyricsOnly && row.lyrics.isNotBlank()) {
                                val pos=row.lyrics.indexOf(request.words.joinToString(" "),ignoreCase=true).coerceAtLeast(0)
                                BasicText(row.lyrics.substring(pos).take(120),style=typography.xs,maxLines=2)
                            }
                            SecondaryTextButton(text=when(row.kind){"Songs"->"Play";"History"->"Search again";else->"Open"},onClick={
                                when(row.kind) {
                                    "History"->onSearch(row.title)
                                    "Artists"->artistRoute.global(row.id)
                                    "Playlists"->row.id.toLongOrNull()?.let { localPlaylistRoute.global(it) }
                                    else->scope.launch {
                                        val player=binder ?: return@launch
                                        val media=Database.song(row.id).first()?.asMediaItem ?: remoteById[row.id]?.asMediaItem ?: return@launch
                                        val alternatives = withContext(Dispatchers.IO) {
                                            val found = mutableListOf<androidx.media3.common.MediaItem>()
                                            for (candidate in groups.flatten().asSequence()
                                                .filter { it.kind == "Songs" }.distinctBy { it.id }.take(12)) {
                                                val item = remoteById[candidate.id]?.asMediaItem
                                                    ?: Database.song(candidate.id).first()?.asMediaItem
                                                if (item != null) found += item
                                            }
                                            found
                                        }
                                        player.playSmartQueue(media, alternatives)
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}
