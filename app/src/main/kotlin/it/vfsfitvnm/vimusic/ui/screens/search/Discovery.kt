package it.vfsfitvnm.vimusic.ui.screens.search

import java.text.Normalizer
import java.util.Locale

data class DiscoveryRow(val id: String, val title: String, val artist: String,
    val kind: String, val duration: String, val listened: Long, val length: Long?, val lyrics: String)

internal fun foldSearch(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFC)
    .replace(Regex("[^\\p{L}\\p{M}\\p{N}]+"), " ").trim()

private val versionPattern = Regex("""(?i)\b(live|acoustic|remix|instrumental|karaoke|remaster(?:ed)?|sped up|slowed)\b""")
internal fun versionOf(title: String) = versionPattern.find(title)?.value?.lowercase(Locale.ROOT) ?: "Original"
internal fun groupOf(row: DiscoveryRow): String {
    if (row.kind != "Songs" || row.artist.isBlank()) return row.kind + "/" + row.id
    val stem = row.title.replace(Regex("""(?i)\s*[\[(][^\])]*(?:live|acoustic|remix|instrumental|karaoke|remaster|sped up|slowed)[^\])]*[\])]\s*$"""), "")
        .replace(Regex("""(?i)\s+-\s+(?:live|acoustic|remix|instrumental|karaoke|remastered|sped up|slowed)\b.*$"""), "")
    return foldSearch(row.artist) + "/" + foldSearch(stem)
}
internal fun secondsOf(value: String): Int? {
    val parts = value.trim().split(':')
    if (parts.size !in 2..3) return null
    val values = parts.map { it.toIntOrNull() ?: return null }
    if (values.any { it < 0 } || values.drop(1).any { it >= 60 }) return null
    return values.fold(0) { a,b -> a*60+b }
}
private fun adjacentMatch(a: String, b: String): Boolean {
    if (a.length < 4 || kotlin.math.abs(a.length-b.length)>1) return false
    val short = if (a.length<=b.length) a else b
    val long = if (a.length<=b.length) b else a
    var i=0
    while(i<short.length && short[i]==long[i]) i++
    if(i==short.length) return true
    if(short.length!=long.length) return short.regionMatches(i,long,i+1,short.length-i)
    return short.regionMatches(i+1,long,i+1,short.length-i-1) ||
        (i+1<short.length && short[i]==long[i+1] && short[i+1]==long[i] && short.regionMatches(i+2,long,i+2,short.length-i-2))
}

internal data class DiscoveryRequest(val words: List<String>, val artist: String, val exclude: List<String>,
    val maxSeconds: Int?, val offline: Boolean, val lyricsOnly: Boolean, val remoteQuery: String)

internal fun discoveryRequest(raw: String, artist: String = "", excluded: String = ""): DiscoveryRequest {
    var text = raw.trim()
    val lyric = text.startsWith("lyrics:", true)
    if (lyric) text = text.substringAfter(':').trim()
    val length = Regex("""(?i)\bunder\s+(\d{1,3})\s*(?:minutes?|mins?)\b""")
    val max = length.find(text)?.groupValues?.get(1)?.toInt()?.times(60)
    val offline = Regex("""(?i)\boffline\b""")
    val needsOffline = !lyric && offline.containsMatchIn(text)
    var phrase = if (lyric) text else offline.replace(length.replace(text," "), " ").trim()
    val by = if (!lyric) Regex("""(?i)\s+by\s+(.+)$""").find(phrase) else null
    val byArtist = by?.groupValues?.get(1).orEmpty()
    if(by!=null) phrase=phrase.substring(0,by.range.first)
    val words=foldSearch(phrase).split(' ').filter { it.isNotBlank() && (lyric || it !in listOf("songs","song","music","play","show","me","some")) }
    return DiscoveryRequest(words,foldSearch(artist.ifBlank { byArtist }), excluded.split(',').map(::foldSearch).filter(String::isNotEmpty),
        if(lyric) null else max,needsOffline,lyric, (words+artist.ifBlank { byArtist }).filter(String::isNotBlank).joinToString(" "))
}
internal fun discoveryScore(row: DiscoveryRow, q: DiscoveryRequest, onlineMatch: Boolean): Int {
    val title=foldSearch(row.title)
    val artist=foldSearch(row.artist)
    val combined="$title $artist"
    if(q.artist.isNotEmpty() && !artist.contains(q.artist)) return -1
    if(q.exclude.any { combined.contains(it) }) return -1
    if(q.maxSeconds!=null && (secondsOf(row.duration) ?: Int.MAX_VALUE)>=q.maxSeconds) return -1
    if(q.words.isEmpty()) return if(q.lyricsOnly) -1 else 0
    val phrase=q.words.joinToString(" ")
    if(q.lyricsOnly) return if(row.lyrics.isNotBlank() && foldSearch(row.lyrics).contains(phrase)) 80 else -1
    if(title==phrase) return 100
    if(combined.contains(phrase)) return 85
    val tokens=combined.split(' ')
    if(q.words.all { word -> tokens.any { it.startsWith(word) || adjacentMatch(word,it) } }) return 65
    if(row.lyrics.isNotBlank() && phrase.length>=6 && foldSearch(row.lyrics).contains(phrase)) return 45
    return if(onlineMatch) 30 else -1
}
