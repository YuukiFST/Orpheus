package com.yuukifst.orpheus.data.youtube

import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack

// YouTube search exposes no language/region per result, so the filter reads the title and
// channel name. Words here are Portuguese-only: no Spanish/Italian/French homographs, and no
// English collisions ("um", "pro", "eu", "com" from domains), so weak words need a pair.
private val STRONG_PORTUGUESE_WORDS = setOf(
    "não", "você", "vocês", "também", "então", "nós", "né", "nao", "voce", "vc", "tbm",
)

private val WEAK_PORTUGUESE_WORDS = setOf(
    "pra", "uma", "meu", "minha", "ao", "muito", "isso", "coisa",
    "galera", "mano", "tá", "tô", "ainda", "fazer", "brasil", "brasileiro", "brasileira",
    "ele", "ela", "seu", "nem", "aí", "melhores",
)

// Vietnamese also uses ã/õ; these letters only appear in Vietnamese.
private val VIETNAMESE_ONLY_LETTERS = charArrayOf('đ', 'ư', 'ơ', 'ạ', 'ả', 'ế', 'ộ', 'ợ')

private val WORD_SPLIT = Regex("[^\\p{L}]+")

/**
 * True when [text] reads as Portuguese: a Portuguese-only nasal vowel (ã/õ), one strong
 * marker word, or two distinct weak marker words.
 *
 * Example: `looksPortuguese("Cómo hacer pan en casa")` is false (Spanish),
 * `looksPortuguese("Você não vai acreditar")` is true.
 */
internal fun looksPortuguese(text: String): Boolean {
    val lower = text.lowercase()
    if (lower.isBlank()) return false
    val isVietnamese = lower.any { it in VIETNAMESE_ONLY_LETTERS }
    if (!isVietnamese && (lower.contains('ã') || lower.contains('õ'))) return true

    val words = lower.split(WORD_SPLIT).filter { it.isNotEmpty() }.toSet()
    if (words.any { it in STRONG_PORTUGUESE_WORDS }) return true
    return words.count { it in WEAK_PORTUGUESE_WORDS } >= 2
}

internal fun filterOutPortuguese(tracks: List<YouTubeTrack>): List<YouTubeTrack> =
    tracks.filterNot { track -> looksPortuguese("${track.title} ${track.channelName}") }
