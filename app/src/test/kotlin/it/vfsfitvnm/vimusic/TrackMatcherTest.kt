package it.vfsfitvnm.vimusic

import it.vfsfitvnm.vimusic.spotify.TrackMatcher
import it.vfsfitvnm.vimusic.spotify.TrackMatcher.Target
import it.vfsfitvnm.vimusic.spotify.MatchableTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ported BitChord matcher, on the same cases its own suite pins down.
 *
 * The matcher is the one piece of the import whose failure is invisible: a
 * bad match neither crashes nor shows an error, it plays a different
 * recording under the right title. These tests are ported from BitChord's
 * SourcesTest so WEST's import keeps exactly the same matching rules.
 */
class TrackMatcherTest {

    private fun song(
        title: String,
        artist: String,
        duration: String? = null,
        album: String? = null,
        videoId: String = "x",
    ) = MatchableTrack(
        videoId = videoId,
        title = title,
        artist = artist,
        albumName = album,
        durationText = duration,
    )

    private fun matches(
        candidate: MatchableTrack,
        title: String,
        artist: String,
        durationSec: Int? = null,
    ) = TrackMatcher.matches(candidate, title, artist, durationSec)

    @Test
    fun `matches the same recording across differing catalogue titles`() {
        assertTrue(
            matches(
                song("Bohemian Rhapsody (Remastered 2011)", "Queen"),
                title = "Bohemian Rhapsody",
                artist = "Queen",
            ),
        )
        assertTrue(
            matches(
                song("Sunflower", "Post Malone, Swae Lee"),
                title = "Sunflower (feat. Swae Lee)",
                artist = "Post Malone",
            ),
        )
        // Punctuation and case are not identity.
        assertTrue(
            matches(
                song("Don't Stop Me Now", "QUEEN"),
                title = "Dont Stop Me Now",
                artist = "Queen",
            ),
        )
    }

    @Test
    fun `matches CJK titles across punctuation and spacing differences`() {
        assertTrue(
            matches(
                song("夜に駆ける", "YOASOBI", duration = "4:20"),
                title = "夜 に 駆ける！",
                artist = "YOASOBI",
                durationSec = 261,
            ),
        )
        assertTrue(matches(song("좋은 날", "IU"), "좋은날", "IU"))
    }

    @Test
    fun `bridges katakana titles to their romaji filing`() {
        assertTrue(
            matches(
                song("コイコガレ", "MAN WITH A MISSION, milet"),
                title = "Koi Kogare",
                artist = "MAN WITH A MISSION",
            ),
        )
    }

    @Test
    fun `keeps substring matching strict for short Unicode titles`() {
        assertFalse(matches(song("愛情", "Artist"), "愛", "Artist"))
        assertFalse(matches(song("봄날", "Artist"), "봄", "Artist"))
    }

    /**
     * The one that sent BitChord's matcher back for a rewrite. YouTube files
     * the track under the film it is from and credits the lead singer; the
     * other catalogue holds the same audio under the bare title and credits
     * the duet. Every part of that disagreement is packaging.
     */
    @Test
    fun `matches a film credit against a bare catalogue listing`() {
        assertTrue(
            matches(
                song("Paniyon Sa", "Atif Aslam, Tulsi Kumar", duration = "4:07"),
                title = "Paniyon Sa (From \"Satyameva Jayate\")",
                artist = "Atif Aslam",
                durationSec = 247,
            ),
        )
        // And the other way round.
        assertTrue(
            matches(
                song("Paniyon Sa (From \"Satyameva Jayate\")", "Atif Aslam"),
                title = "Paniyon Sa",
                artist = "Atif Aslam, Tulsi Kumar",
            ),
        )
    }

    /** The trailing labels an upload hangs on a title with no brackets to hold them. */
    @Test
    fun `strips upload labelling from either side`() {
        assertTrue(matches(song("Tum Hi Ho", "Arijit Singh"), "Tum Hi Ho Full Song", "Arijit Singh"))
        assertTrue(
            matches(
                song("Kesariya", "Arijit Singh"),
                title = "Kesariya - Brahmastra | Official Video",
                artist = "Arijit Singh",
            ),
        )
        // "Artist - Title" uploads: the head is the credit, not the song.
        assertTrue(
            matches(
                song("Believer", "Imagine Dragons"),
                title = "Imagine Dragons - Believer",
                artist = "Imagine Dragons",
            ),
        )
    }

    @Test
    fun `refuses a different song by the same artist`() {
        assertFalse(
            matches(
                song("The Show Must Go On", "Queen"),
                title = "Bohemian Rhapsody",
                artist = "Queen",
            ),
        )
    }

    /**
     * The dangerous case: same title, different artist. A cover, a tribute
     * album, or a completely unrelated song that happens to share a name — all
     * of which a loose matcher would happily play instead.
     */
    @Test
    fun `refuses a cover by a different artist`() {
        assertFalse(
            matches(
                song("Hurt", "Johnny Cash"),
                title = "Hurt",
                artist = "Nine Inch Nails",
            ),
        )
    }

    @Test
    fun `accepts a shared artist when catalogues credit differently`() {
        assertTrue(
            matches(
                song("Numb / Encore", "Jay-Z & Linkin Park"),
                title = "Numb / Encore",
                artist = "Linkin Park",
            ),
        )
    }

    /**
     * Film catalogues credit from opposite ends: YouTube Music files this
     * under the composer, every store under the singer, and the two credits
     * share nothing at all. An exact runtime is what says they are the same
     * master anyway.
     */
    @Test
    fun `accepts a composer credit against a singer credit on an exact runtime`() {
        assertTrue(
            matches(
                song("Jhak Maar Ke", "Neeraj Shridhar", duration = "3:53"),
                title = "Jhak Maar Ke",
                artist = "Pritam",
                durationSec = 233,
            ),
        )
        // The remix and the acoustic cover from the same result set are the
        // reason this is safe: neither agrees on length.
        assertFalse(
            matches(
                song("Jhak Maar Ke", "Leo Lz Mix", duration = "4:01"),
                title = "Jhak Maar Ke",
                artist = "Pritam",
                durationSec = 233,
            ),
        )
    }

    /**
     * The runtime may only stand in for a credit when there *is* a runtime.
     * Without one there is nothing corroborating anything, and an unrelated
     * song sharing a title must still lose.
     */
    @Test
    fun `will not waive the credit check without a runtime to back it`() {
        assertFalse(matches(song("Jhak Maar Ke", "Neeraj Shridhar"), "Jhak Maar Ke", "Pritam"))
        assertFalse(
            matches(
                song("Jhak Maar Ke", "Neeraj Shridhar", duration = "3:53"),
                title = "Jhak Maar Ke",
                artist = "Pritam",
                durationSec = null,
            ),
        )
    }

    /** A properly credited copy always outranks one the runtime merely vouched for. */
    @Test
    fun `prefers a credited match over a runtime-vouched one`() {
        val target = Target("Jhak Maar Ke", "Pritam, Neeraj Shridhar", durationSec = 233)
        val vouched = song("Jhak Maar Ke", "Some Uploader", duration = "3:53")
        val credited = song("Jhak Maar Ke", "Neeraj Shridhar", duration = "3:53")
        assertEquals(credited, TrackMatcher.best(listOf(vouched, credited), target))
    }

    /** A name inside another name is not a shared credit. */
    @Test
    fun `refuses an artist whose name merely contains the one asked for`() {
        assertFalse(matches(song("No One Knows", "Queens of the Stone Age"), "No One Knows", "Queen"))
    }

    /**
     * A different take is a different recording, and the direction it is asked
     * for in doesn't change that.
     */
    @Test
    fun `refuses a different take of the same song`() {
        assertFalse(matches(song("Shape of You (Acoustic)", "Ed Sheeran"), "Shape of You", "Ed Sheeran"))
        assertFalse(matches(song("Shape of You", "Ed Sheeran"), "Shape of You (Acoustic)", "Ed Sheeran"))
        assertFalse(matches(song("Creep (Live)", "Radiohead"), "Creep", "Radiohead"))
        assertFalse(matches(song("Faded", "Alan Walker"), "Faded (Slowed + Reverb)", "Alan Walker"))
        // A stem carries the right title and the right artist and is not the
        // song — this one was one candidate away from playing.
        assertFalse(
            matches(
                song("Apna Bana Le - Arijit Singh Vocals Only", "Arijit Singh, Sachin-Jigar"),
                title = "Apna Bana Le (From \"Bhediya\")",
                artist = "Arijit Singh",
            ),
        )
        assertFalse(matches(song("Kesariya (Instrumental)", "Arijit Singh"), "Kesariya", "Arijit Singh"))
        // Both sides saying the same thing is still a match.
        assertTrue(matches(song("Creep (Live)", "Radiohead"), "Creep [Live]", "Radiohead"))
    }

    /** Version-shaped words that describe the ordinary release, not a new take. */
    @Test
    fun `treats an album or radio version as the plain track`() {
        assertTrue(matches(song("Africa", "Toto"), "Africa (Album Version)", "Toto"))
        assertTrue(matches(song("Clocks", "Coldplay"), "Clocks (Radio Edit)", "Coldplay"))
    }

    /** The signal a title can't give: a loop, a snippet, or a whole album side. */
    @Test
    fun `refuses a candidate whose runtime is nowhere near`() {
        assertFalse(
            matches(
                song("Levitating", "Dua Lipa", duration = "1:00:12"),
                title = "Levitating",
                artist = "Dua Lipa",
                durationSec = 203,
            ),
        )
        // A few seconds of trimmed silence is not a different recording.
        assertTrue(
            matches(
                song("Levitating", "Dua Lipa", duration = "3:25"),
                title = "Levitating",
                artist = "Dua Lipa",
                durationSec = 203,
            ),
        )
    }

    /** With no artist to check against, the title alone has to carry it. */
    @Test
    fun `falls back to title alone when no artist is known`() {
        assertTrue(matches(song("Clair de Lune", "Debussy"), "Clair de Lune", ""))
        assertFalse(matches(song("Reverie", "Debussy"), "Clair de Lune", ""))
    }

    /**
     * Search backends rank however they like. The right copy is the one whose
     * runtime and credit agree, not the one that came back first.
     */
    @Test
    fun `picks the closest candidate rather than the first acceptable one`() {
        val target = Target("Paniyon Sa", "Atif Aslam", durationSec = 247)
        val wrongLength = song("Paniyon Sa", "Atif Aslam", duration = "4:32")
        val right = song("Paniyon Sa", "Atif Aslam, Tulsi Kumar", duration = "4:06")
        assertEquals(right, TrackMatcher.best(listOf(wrongLength, right), target))
    }

    /**
     * Two rows can share a title and a credit while holding different audio;
     * the requested release must beat the row whose runtime happens to be
     * closer.
     */
    @Test
    fun `uses the album to separate duplicate recordings`() {
        val target = Target(
            "Brown Rang",
            "Yo Yo Honey Singh",
            durationSec = 175,
            album = "International Villager",
        )
        val otherRelease = song(
            "Brown Rang",
            "Yo Yo Honey Singh",
            duration = "2:56",
            album = "Desi Kalakaar",
            videoId = "wrong",
        )
        val right = song(
            "Brown Rang",
            "Yo Yo Honey Singh",
            duration = "2:57",
            album = "International Villager",
            videoId = "right",
        )
        assertEquals("right", TrackMatcher.best(listOf(otherRelease, right), target)?.videoId)
    }

    /** A candidate that does not state its runtime cannot claim to be near one. */
    @Test
    fun `withinSeconds requires both runtimes to be known`() {
        val target = Target("São Paulo", "The Weeknd", durationSec = 302)
        val stated = song("São Paulo", "The Weeknd", duration = "5:02")
        val silent = song("São Paulo", "The Weeknd", duration = null)
        assertTrue(TrackMatcher.withinSeconds(stated, target, 2))
        assertFalse(TrackMatcher.withinSeconds(silent, target, 2))
    }

    @Test
    fun `duration text parses as whole seconds`() {
        assertEquals(225, TrackMatcher.secondsOf("3:45"))
        assertEquals(3723, TrackMatcher.secondsOf("1:02:03"))
        assertEquals(null, TrackMatcher.secondsOf("3:2a"))
        assertEquals(null, TrackMatcher.secondsOf("soon"))
        assertEquals(null, TrackMatcher.secondsOf(null))
        assertEquals(null, TrackMatcher.secondsOf("0:00"))
    }

    /** The first hit is only a last resort; a judged match always wins the tie. */
    @Test
    fun `ranking orders credited matches above the rest`() {
        val target = Target("Hurt", "Nine Inch Nails", durationSec = 246)
        val cover = song("Hurt", "Johnny Cash", duration = "3:38", videoId = "cover")
        val original = song("Hurt", "Nine Inch Nails", duration = "4:06", videoId = "original")
        assertEquals("original", TrackMatcher.best(listOf(cover, original), target)?.videoId)
    }
}
