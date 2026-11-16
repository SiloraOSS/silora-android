package org.siloserver.silo.common.player

import kotlin.test.Test
import kotlin.test.assertEquals

class AiTranscribePlanTest {
    private fun plan(audio: String?, target: String, canTranslate: Boolean = true) = aiTranscribePlan(audio, target, canTranslate)

    @Test
    fun taggedAudioTranscribesOrTranslatesByLanguage() {
        assertEquals(AiTranscribePlan("transcribe", "en", ""), plan("en", "en"))
        assertEquals(AiTranscribePlan("transcribe", "eng", ""), plan("eng", "en"))
        assertEquals(AiTranscribePlan("transcribe_translate", "ja", "en"), plan("ja", "en"))
        assertEquals(AiTranscribePlan("transcribe_translate", "eng", "sl"), plan("eng", "sl"))
    }

    @Test
    fun taggedLanguagesMissingFromTheNameTableStillTranslate() {
        assertEquals(AiTranscribePlan("transcribe_translate", "nob", "en"), plan("nob", "en"))
        assertEquals(AiTranscribePlan("transcribe_translate", "pt-BR", "en"), plan("pt-BR", "en"))
        assertEquals(AiTranscribePlan("transcribe_translate", "yue", "en"), plan("yue", "en"))
        assertEquals(AiTranscribePlan("transcribe_translate", "zh-Hans", "en"), plan("zh-Hans", "en"))
    }

    @Test
    fun regionTagsMatchTheirBaseLanguage() {
        assertEquals(AiTranscribePlan("transcribe", "pt-BR", ""), plan("pt-BR", "pt"))
        assertEquals(AiTranscribePlan("transcribe", "en-US", ""), plan("en-US", "en"))
    }

    @Test
    fun untaggedAudioTranscribesWithTheTargetAsAHint() {
        for (untagged in listOf(null, "", "  ", "und", "UND", "unk", "mis", "zxx", "mul", "english", "e1", "x")) {
            assertEquals(AiTranscribePlan("transcribe", null, "en"), plan(untagged, "en"), "audio=$untagged")
        }
    }

    @Test
    fun withoutTranslationItAlwaysTranscribesWithoutAHint() {
        assertEquals(AiTranscribePlan("transcribe", "ja", ""), plan("ja", "en", canTranslate = false))
        assertEquals(AiTranscribePlan("transcribe", null, ""), plan("und", "en", canTranslate = false))
        assertEquals(AiTranscribePlan("transcribe", "nob", ""), plan("nob", "en", canTranslate = false))
    }
}
