package com.melox.player.data.library

import java.text.Normalizer

private val JapaneseKanaDigraphs = mapOf(
    "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
    "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
    "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
    "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
    "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
    "ぢゃ" to "dya", "ぢゅ" to "dyu", "ぢょ" to "dyo",
    "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
    "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
    "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
    "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
    "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
    "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
    "ふぁ" to "fa", "ふぃ" to "fi", "ふぇ" to "fe", "ふぉ" to "fo",
    "うぃ" to "wi", "うぇ" to "we", "うぉ" to "wo",
    "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo",
    "しぇ" to "she", "じぇ" to "je", "ちぇ" to "che",
    "てぃ" to "ti", "でぃ" to "di", "とぅ" to "tu", "どぅ" to "du",
    "いぇ" to "ye",
)

private val JapaneseKana = mapOf(
    'ぁ' to "a", 'あ' to "a", 'ぃ' to "i", 'い' to "i", 'ぅ' to "u", 'う' to "u",
    'ぇ' to "e", 'え' to "e", 'ぉ' to "o", 'お' to "o",
    'か' to "ka", 'が' to "ga", 'き' to "ki", 'ぎ' to "gi", 'く' to "ku", 'ぐ' to "gu",
    'け' to "ke", 'げ' to "ge", 'こ' to "ko", 'ご' to "go",
    'さ' to "sa", 'ざ' to "za", 'し' to "shi", 'じ' to "ji", 'す' to "su", 'ず' to "zu",
    'せ' to "se", 'ぜ' to "ze", 'そ' to "so", 'ぞ' to "zo",
    'た' to "ta", 'だ' to "da", 'ち' to "chi", 'ぢ' to "ji", 'つ' to "tsu", 'づ' to "zu",
    'て' to "te", 'で' to "de", 'と' to "to", 'ど' to "do",
    'な' to "na", 'に' to "ni", 'ぬ' to "nu", 'ね' to "ne", 'の' to "no",
    'は' to "ha", 'ば' to "ba", 'ぱ' to "pa", 'ひ' to "hi", 'び' to "bi", 'ぴ' to "pi",
    'ふ' to "fu", 'ぶ' to "bu", 'ぷ' to "pu", 'へ' to "he", 'べ' to "be", 'ぺ' to "pe",
    'ほ' to "ho", 'ぼ' to "bo", 'ぽ' to "po",
    'ま' to "ma", 'み' to "mi", 'む' to "mu", 'め' to "me", 'も' to "mo",
    'ゃ' to "ya", 'や' to "ya", 'ゅ' to "yu", 'ゆ' to "yu", 'ょ' to "yo", 'よ' to "yo",
    'ら' to "ra", 'り' to "ri", 'る' to "ru", 'れ' to "re", 'ろ' to "ro",
    'ゎ' to "wa", 'わ' to "wa", 'ゐ' to "wi", 'ゑ' to "we", 'を' to "wo", 'ん' to "n",
    'ゔ' to "vu",
)

internal fun japaneseKanaToRomaji(text: String): String? {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).map { character ->
        if (character in '\u30A1'..'\u30F6') {
            (character.code - 0x60).toChar()
        } else {
            character
        }
    }.joinToString("")
    if (!normalized.any(::isJapaneseKana)) return null

    val result = StringBuilder(normalized.length * 2)
    var index = 0
    var geminate = false
    while (index < normalized.length) {
        val character = normalized[index]
        if (character == 'っ') {
            geminate = true
            index += 1
            continue
        }
        if (character == 'ー' || character == 'ゝ' || character == 'ゞ') {
            index += 1
            continue
        }
        val pair = normalized.substring(index, (index + 2).coerceAtMost(normalized.length))
        val syllable = JapaneseKanaDigraphs[pair]
            ?: JapaneseKana[character]
        if (syllable == null) {
            if (!character.isWhitespace() && character.isLetterOrDigit()) {
                result.append(character)
            }
            geminate = false
            index += 1
            continue
        }
        if (geminate) {
            syllable.firstOrNull { it in 'a'..'z' && it !in "aeiou" }?.let(result::append)
            geminate = false
        }
        result.append(syllable)
        index += if (JapaneseKanaDigraphs.containsKey(pair)) 2 else 1
    }
    return result.toString().takeIf(String::isNotEmpty)
}

private fun isJapaneseKana(character: Char): Boolean =
    character in '\u3040'..'\u309F' || character in '\u30A0'..'\u30FF'
