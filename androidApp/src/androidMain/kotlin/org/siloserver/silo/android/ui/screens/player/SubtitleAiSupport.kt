package org.siloserver.silo.android.ui.screens.player

import org.siloserver.silo.common.ui.LanguageNames
import org.siloserver.silo.model.playback.PlayerSubtitleInfo
import org.siloserver.silo.model.subtitles.SubtitleAiJobKind
import org.siloserver.silo.model.subtitles.SubtitleAiQuota

// Mirrors web/src/player/components/SubtitleTranslateModal.tsx: external /
// downloaded sources must be a server-parseable text format, while embedded
// non-bitmap tracks can be extracted to text via ffmpeg.
private val TRANSLATABLE_TEXT_CODECS = setOf("srt", "subrip", "vtt", "webvtt")
private val BITMAP_CODECS = setOf(
    "pgs", "hdmv_pgs_subtitle", "dvd_subtitle", "dvdsub", "dvb_subtitle", "dvbsub",
)

/** Shown in place of the target language when the server cannot translate. */
internal const val TRANSCRIBE_ONLY_NOTE =
    "Subtitles are written in the spoken language. AI translation is turned off on this server."

/** True when the server can use [track] as an AI translation source. */
internal fun isTranslatableSource(track: PlayerSubtitleInfo): Boolean {
    val codec = track.codec?.lowercase().orEmpty()
    return if (track.source == "embedded") {
        codec !in BITMAP_CODECS
    } else {
        codec in TRANSLATABLE_TEXT_CODECS
    }
}

/**
 * Audio-mode job kind: same target as the audio language → plain
 * transcription; otherwise transcribe-then-translate (web parity).
 */
internal fun transcribeKindFor(audioLanguage: String?, targetLanguage: String): String =
    if (isSameLanguage(audioLanguage, targetLanguage)) {
        SubtitleAiJobKind.Transcribe
    } else {
        SubtitleAiJobKind.TranscribeTranslate
    }

/**
 * Fork: compares normalized codes, so an "eng" audio track and an "en" target
 * no longer start a paid English → English translation pass (TV parity with
 * tvIsSameLanguage).
 */
private fun isSameLanguage(source: String?, target: String): Boolean {
    val code = source?.trim()?.lowercase().orEmpty()
    if (code.isEmpty()) return false
    if (code == target.trim().lowercase()) return true
    val normalized = LanguageNames.searchCode(code)
    // searchCode maps unknown codes to "en"; don't mistake those for English.
    if (normalized == "en" && LanguageNames.displayName(code) != "English") return false
    return normalized == LanguageNames.searchCode(target)
}

/** Quota counter line, wording pinned to the web modal + its period labels. */
internal fun quotaLineText(quota: SubtitleAiQuota): String {
    val window = when (quota.period) {
        "day" -> "24 hours"
        "week" -> "7 days"
        "month" -> "30 days"
        else -> quota.period
    }
    return if (quota.remaining <= 0) {
        "You've used all ${quota.limit} transcriptions for the last $window. Try again later."
    } else {
        "${quota.remaining} of ${quota.limit} transcriptions left for the last $window."
    }
}
