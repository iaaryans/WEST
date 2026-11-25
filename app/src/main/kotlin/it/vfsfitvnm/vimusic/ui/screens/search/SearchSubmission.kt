package it.vfsfitvnm.vimusic.ui.screens.search

internal fun searchTermForSubmission(input: String, formVisible: Boolean): String? =
    input.trim().takeIf { formVisible && it.isNotEmpty() }
