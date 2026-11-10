package org.siloserver.silo.android.ui.screens.player

import org.siloserver.silo.common.ui.LanguageNames
import android.content.res.Resources
import org.siloserver.silo.common.R as CommonR
import org.siloserver.silo.common.i18n.LocaleFormat
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
internal fun quotaLineText(quota: SubtitleAiQuota, resources: Resources): String {
    val window = when (quota.period) {
        "day" -> resources.getQuantityString(CommonR.plurals.player_aiTranslate_window_day, 24, 24)
        "week" -> resources.getQuantityString(CommonR.plurals.player_aiTranslate_window_week, 7, 7)
        "month" -> resources.getQuantityString(CommonR.plurals.player_aiTranslate_window_month, 30, 30)
        else -> quota.period
    }
    return if (quota.remaining <= 0) {
        resources.getQuantityString(
            CommonR.plurals.player_aiTranslate_quotaUsedUp,
            quota.limit,
            quota.limit,
            window,
            LocaleFormat.integer(quota.limit),
        )
    } else {
        resources.getQuantityString(
            CommonR.plurals.player_aiTranslate_quotaLeftWindow,
            quota.limit,
            LocaleFormat.integer(quota.remaining),
            quota.limit,
            window,
            LocaleFormat.integer(quota.limit),
        )
    }
}
