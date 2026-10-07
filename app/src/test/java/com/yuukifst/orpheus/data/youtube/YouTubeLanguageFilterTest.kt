package com.yuukifst.orpheus.data.youtube

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class YouTubeLanguageFilterTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        delimiter = '|',
        value = [
            "Você não vai acreditar no que aconteceu|true",
            "Pão de queijo mineiro|true",
            "Como fazer isso em casa pra iniciantes|true",
            "Mano Brown fala sobre o Brasil e a galera|true",
            "Daft Punk - Get Lucky (Official Audio)|false",
            "Cómo hacer pan en casa paso a paso|false",
            "Bad Bunny - Tití Me Preguntó|false",
            "Eros Ramazzotti - Più bella cosa|false",
            "Sơn Tùng M-TP - Chúng Ta Của Hiện Tại|false",
            "Um... what is this? Pro tips for beginners|false",
        ],
    )
    fun detectsPortugueseTitles(title: String, expected: Boolean) {
        assertEquals(expected, looksPortuguese(title))
    }
}
