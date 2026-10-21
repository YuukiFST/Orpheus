package com.yuukifst.orpheus.data.youtube

import com.yuukifst.orpheus.data.preferences.YouTubeResultLanguage
import com.yuukifst.orpheus.data.youtube.model.YouTubeTrack
import java.text.Normalizer

// YouTube search exposes no language per result, so detection reads the title, channel name and
// description snippet. Each list holds words common in that language and rare in English titles;
// a word shared by several languages ("que", "la", "en") is listed under each, so it adds no lead.
// Words that also appear in English titles ("die", "em", "pour", "non", "per", "est", "mit", "von")
// are left out.
private val MARKER_WORDS: Map<YouTubeResultLanguage, Set<String>> = mapOf(
    YouTubeResultLanguage.ENGLISH to setOf(
        "the", "and", "of", "to", "is", "are", "you", "your", "with", "this", "that", "what", "how",
        "why", "my", "from", "about", "for", "it", "we", "they", "be", "at", "by", "or", "official",
        "lyrics", "episode", "interview", "full", "best", "new", "who", "when", "our", "all", "just",
        "can", "will", "not", "have", "has", "subscribe", "watch", "talk", "life",
    ),
    YouTubeResultLanguage.PORTUGUESE to setOf(
        "não", "você", "vocês", "também", "então", "nós", "né", "nao", "voce", "vc", "tbm", "pra",
        "uma", "meu", "minha", "ao", "muito", "isso", "coisa", "galera", "mano", "tá", "tô", "ainda",
        "fazer", "brasil", "brasileiro", "brasileira", "ele", "ela", "seu", "nem", "aí", "melhores",
        "é", "com", "da", "dos", "das", "que", "para", "como", "mais", "sobre", "episódio",
        "entrevista", "conversa", "sua", "está", "são", "aqui", "inscreva", "canal",
    ),
    YouTubeResultLanguage.SPANISH to setOf(
        "el", "los", "las", "del", "y", "en", "es", "con", "para", "una", "qué", "cómo", "más", "muy",
        "pero", "que", "como", "sobre", "su", "sus", "también", "episodio", "entrevista", "hola",
        "está", "aquí", "canal", "suscríbete", "nuestro", "cuando", "porque", "la", "dos",
    ),
    YouTubeResultLanguage.FRENCH to setOf(
        "le", "la", "les", "des", "du", "et", "une", "avec", "dans", "sur", "pas", "ce",
        "cette", "qui", "je", "vous", "nous", "mais", "très", "que", "en", "épisode", "chaîne",
        "abonnez", "aussi", "être", "pourquoi",
    ),
    YouTubeResultLanguage.GERMAN to setOf(
        "der", "das", "und", "ist", "nicht", "ein", "eine", "ich", "du", "wie", "auf", "für",
        "zu", "sie", "wir", "folge", "über", "auch", "oder", "warum", "kanal",
    ),
    YouTubeResultLanguage.ITALIAN to setOf(
        "il", "di", "che", "della", "del", "con", "sono", "una", "gli", "questo", "più",
        "anche", "perché", "cosa", "ciao", "puntata", "la", "le", "nel", "alla", "da", "intervista",
        "canale",
    ),
)

// Letters only one offered language uses; one is enough evidence on its own ("Pão de queijo").
private val STRONG_MARKER_LETTERS: Map<Char, YouTubeResultLanguage> = mapOf(
    'ã' to YouTubeResultLanguage.PORTUGUESE,
    'õ' to YouTubeResultLanguage.PORTUGUESE,
    'ñ' to YouTubeResultLanguage.SPANISH,
    '¿' to YouTubeResultLanguage.SPANISH,
    '¡' to YouTubeResultLanguage.SPANISH,
    'ß' to YouTubeResultLanguage.GERMAN,
    'œ' to YouTubeResultLanguage.FRENCH,
)

// Letters several languages share; shared ones ("ç", "à") count for each language. Umlauts are
// left out: band names ("Motörhead", "Björk") and Nordic or Turkish text use them too.
private val MARKER_LETTERS: Map<Char, List<YouTubeResultLanguage>> = mapOf(
    'û' to listOf(YouTubeResultLanguage.FRENCH),
    'î' to listOf(YouTubeResultLanguage.FRENCH),
    'ë' to listOf(YouTubeResultLanguage.FRENCH),
    'ï' to listOf(YouTubeResultLanguage.FRENCH),
    'ò' to listOf(YouTubeResultLanguage.ITALIAN),
    'ì' to listOf(YouTubeResultLanguage.ITALIAN),
    'ç' to listOf(YouTubeResultLanguage.PORTUGUESE, YouTubeResultLanguage.FRENCH),
    'ê' to listOf(YouTubeResultLanguage.PORTUGUESE, YouTubeResultLanguage.FRENCH),
    'ô' to listOf(YouTubeResultLanguage.PORTUGUESE, YouTubeResultLanguage.FRENCH),
    'â' to listOf(YouTubeResultLanguage.PORTUGUESE, YouTubeResultLanguage.FRENCH),
    'à' to listOf(YouTubeResultLanguage.PORTUGUESE, YouTubeResultLanguage.FRENCH, YouTubeResultLanguage.ITALIAN),
    'è' to listOf(YouTubeResultLanguage.FRENCH, YouTubeResultLanguage.ITALIAN),
    'ù' to listOf(YouTubeResultLanguage.FRENCH, YouTubeResultLanguage.ITALIAN),
)

// Vietnamese is Latin script and also uses ã/õ; these letters only appear in Vietnamese. Two are
// needed, so one dish name in an English title ("Best Phở in Hanoi") is not read as Vietnamese.
private val VIETNAMESE_ONLY_LETTERS = charArrayOf('đ', 'ư', 'ơ', 'ạ', 'ả', 'ế', 'ộ', 'ợ')
private const val VIETNAMESE_LETTER_MIN = 2

// A language needs this many points and an outright lead, so one stray word ("Los Lonely Boys",
// "Viva Las Vegas") does not hide an English result.
private const val MIN_LANGUAGE_SCORE = 2

// Share of letters outside the Latin script from which a text reads as another script:
// "BTS (방탄소년단) 'Dynamite' Official MV" stays Latin, a Japanese title does not.
private const val NON_LATIN_LETTER_SHARE = 0.3

private val URL_PATTERN = Regex("""https?://\S+|www\.\S+|\S+\.(com|net|org|io|tv|me|br|ly)\b\S*""")
// Auto-generated Topic uploads start their description with English boilerplate whatever the
// song's language, which would read a Brazilian song as English.
private val YOUTUBE_BOILERPLATE = Regex("provided to youtube by|auto-generated by youtube")
private val WORD_SPLIT = Regex("[^\\p{L}]+")

/** Language read from a result's text. */
internal sealed interface DetectedTextLanguage {
    data class Known(val language: YouTubeResultLanguage) : DetectedTextLanguage

    /** A non-Latin script, or a Latin-script language Settings does not offer (Vietnamese). */
    data object Other : DetectedTextLanguage

    /** Too little signal or a tie, as in an artist name or "Daft Punk - Get Lucky". */
    data object Undetermined : DetectedTextLanguage
}

/**
 * Reads [text] as one of the offered languages: each distinct marker word and each distinct
 * shared marker letter scores a point, a strong marker letter scores [MIN_LANGUAGE_SCORE], and the
 * top language needs [MIN_LANGUAGE_SCORE] points and an outright lead.
 */
internal fun detectTextLanguage(text: String): DetectedTextLanguage {
    val lower = Normalizer.normalize(text, Normalizer.Form.NFC).lowercase()
        .replace(URL_PATTERN, " ")
        .replace(YOUTUBE_BOILERPLATE, " ")
    if (lower.isBlank()) return DetectedTextLanguage.Undetermined
    if (lower.count { it in VIETNAMESE_ONLY_LETTERS } >= VIETNAMESE_LETTER_MIN || readsAsNonLatinScript(lower)) {
        return DetectedTextLanguage.Other
    }

    val scores = mutableMapOf<YouTubeResultLanguage, Int>()
    lower.split(WORD_SPLIT).filter { it.isNotEmpty() }.toSet().forEach { word ->
        MARKER_WORDS.forEach { (language, words) ->
            if (word in words) scores[language] = scores.getOrElse(language) { 0 } + 1
        }
    }
    lower.toSet().forEach { letter ->
        STRONG_MARKER_LETTERS[letter]?.let { language ->
            scores[language] = scores.getOrElse(language) { 0 } + MIN_LANGUAGE_SCORE
        }
        MARKER_LETTERS[letter]?.forEach { language -> scores[language] = scores.getOrElse(language) { 0 } + 1 }
    }

    val ranked = scores.entries.sortedByDescending { it.value }
    val top = ranked.firstOrNull() ?: return DetectedTextLanguage.Undetermined
    val runnerUpScore = ranked.getOrNull(1)?.value ?: 0
    if (top.value < MIN_LANGUAGE_SCORE || top.value == runnerUpScore) return DetectedTextLanguage.Undetermined
    return DetectedTextLanguage.Known(top.key)
}

private fun readsAsNonLatinScript(text: String): Boolean {
    var latinLetters = 0
    var otherLetters = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        index += Character.charCount(codePoint)
        if (!Character.isLetter(codePoint)) continue
        if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.LATIN) latinLetters++ else otherLetters++
    }
    val letters = latinLetters + otherLetters
    return letters > 0 && otherLetters.toDouble() / letters >= NON_LATIN_LETTER_SHARE
}

private fun YouTubeTrack.languageSampleText(): String =
    listOfNotNull(effectiveTitle, channelName, descriptionSnippet).joinToString(" ")

/**
 * Keeps results read as [language] plus those with no language signal; results read as another
 * language or script are dropped. [YouTubeResultLanguage.ANY] keeps everything.
 */
internal fun filterByResultLanguage(
    tracks: List<YouTubeTrack>,
    language: YouTubeResultLanguage,
): List<YouTubeTrack> {
    if (language == YouTubeResultLanguage.ANY) return tracks
    return tracks.filter { track ->
        when (val detected = detectTextLanguage(track.languageSampleText())) {
            DetectedTextLanguage.Undetermined -> true
            DetectedTextLanguage.Other -> false
            is DetectedTextLanguage.Known -> detected.language == language
        }
    }
}
