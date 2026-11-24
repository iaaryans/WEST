package it.vfsfitvnm.vimusic.voice

/** Conservative, source-agnostic music search normalization. Does not execute voice commands. */
internal object VoiceMusicQuery {
    private val whitespace = Regex("\\s+")
    private val prefix = listOf(
        Regex("^(?:please\\s+)?(?:search\\s+(?:for\\s+)?|find\\s+|play\\s+|show\\s+me\\s+)(?=\\S)", RegexOption.IGNORE_CASE),
        Regex("^(?:mujhe\\s+|zara\\s+)?(?:search\\s+karo\\s+|dhundo\\s+)(?=\\S)", RegexOption.IGNORE_CASE)
    )
    private val suffix = Regex("\\s+(?:ke\\s+gaane|ka\\s+gaana|song\\s+chalao|gaane\\s+chalao)$", RegexOption.IGNORE_CASE)

    fun normalize(utterance: String): String {
        var query = whitespace.replace(utterance.trim(), " ")
        if (query.isBlank()) return ""
        prefix.forEach { pattern -> query = pattern.replace(query, "") }
        query = suffix.replace(query, "").trim()
        return if (query.isNotBlank()) query else utterance.trim()
    }
}
