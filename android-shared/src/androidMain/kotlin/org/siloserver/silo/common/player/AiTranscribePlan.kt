package org.siloserver.silo.common.player

import org.siloserver.silo.common.ui.LanguageNames
import org.siloserver.silo.model.subtitles.SubtitleAiJobKind

/** The AI subtitle job an audio-mode request sends. */
data class AiTranscribePlan(
    val kind: String,
    /** Null when the track has no usable language tag. */
    val sourceLanguage: String?,
    /** For a plain transcription, a hint for the spoken language ("" for none). */
    val targetLanguage: String,
)

/**
 * Fork: which job a Transcribe press sends, shared by phone and TV.
 *
 * - Audio tagged with the target's language: plain transcription.
 * - Audio tagged with another language: transcribe, then translate. This
 *   includes real languages the display-name table does not list ("nob",
 *   "cat", "yue", "pt-BR"); their raw tag goes to the server as the source.
 * - Audio with no usable tag (see [hasUsableLanguageTag]): plain
 *   transcription with the target as the language hint. Before, an untagged
 *   track always ran transcribe-and-translate, which paid for an
 *   English → English pass, delayed the first live lines and read
 *   "Transcribing & translating".
 *
 * [canTranslate] false (translation off on the server) always transcribes,
 * without a hint, as before.
 */
fun aiTranscribePlan(audioLanguage: String?, targetLanguage: String, canTranslate: Boolean): AiTranscribePlan {
    val tagged = hasUsableLanguageTag(audioLanguage)
    val source = audioLanguage?.trim()?.takeIf { tagged }
    val target = targetLanguage.trim()
    return when {
        !canTranslate -> AiTranscribePlan(SubtitleAiJobKind.Transcribe, source, "")
        !tagged -> AiTranscribePlan(SubtitleAiJobKind.Transcribe, null, target)
        isSameAiLanguage(source, target) -> AiTranscribePlan(SubtitleAiJobKind.Transcribe, source, "")
        else -> AiTranscribePlan(SubtitleAiJobKind.TranscribeTranslate, source, target)
    }
}

/** ISO 639 codes that say the language is unknown, missing or not a language. */
private val NON_LANGUAGE_CODES = setOf("und", "unk", "mis", "zxx", "mul")

private val ISO_639_CODE = Regex("[a-z]{2,3}")

/** The primary language subtag of [code], lowercased ("pt-BR" → "pt", "zh_Hans" → "zh"). */
private fun primaryLanguageSubtag(code: String?): String =
    code?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_').orEmpty()

/**
 * Fork: whether [code] is a real language tag, whether or not [LanguageNames]
 * lists it. Blank, "und"/"unk"/"mis"/"zxx"/"mul" and anything that is not a
 * 2-3 letter ISO 639 code (after dropping a region or script subtag) is not.
 */
fun hasUsableLanguageTag(code: String?): Boolean {
    val primary = primaryLanguageSubtag(code)
    return primary !in NON_LANGUAGE_CODES && ISO_639_CODE.matches(primary)
}

/**
 * Compares normalized codes, so "eng" audio matches an "en" target and
 * "pt-BR" audio matches a "pt" target.
 */
fun isSameAiLanguage(source: String?, target: String): Boolean {
    if (!hasUsableLanguageTag(source)) return false
    val code = primaryLanguageSubtag(source)
    val targetCode = primaryLanguageSubtag(target)
    if (code == targetCode) return true
    // searchCode maps unlisted codes to "en", so only compare listed ones.
    if (!LanguageNames.isKnown(code) || !LanguageNames.isKnown(targetCode)) return false
    return LanguageNames.searchCode(code) == LanguageNames.searchCode(targetCode)
}
