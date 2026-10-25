package org.siloserver.silo.common.player.audio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Night mode's compressor and limiter, as [AudioNormalizationEffect] feeds
 * them to DynamicsProcessing. The defaults are the tuning night mode shipped
 * with; a stored value only holds what the user changed away from them.
 */
@Serializable
data class NightModeParameters(
    val compressorThresholdDb: Float = -34f,
    val compressorRatio: Float = 4f,
    val compressorKneeDb: Float = 6f,
    val compressorAttackMs: Float = 30f,
    val compressorReleaseMs: Float = 300f,
    val compressorMakeupGainDb: Float = 15f,
    val limiterThresholdDb: Float = -3f,
    val limiterRatio: Float = 10f,
    val limiterAttackMs: Float = 1f,
    val limiterReleaseMs: Float = 60f,
) {
    val isDefault: Boolean get() = this == DEFAULT

    /** Every value snapped to the nearest choice its [NightModeControl] offers. */
    fun normalized(): NightModeParameters =
        NightModeControl.entries.fold(this) { params, control -> control.set(params, control.get(params)) }

    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        val DEFAULT = NightModeParameters()

        private val json = Json { ignoreUnknownKeys = true }

        /** A missing or unreadable value is the defaults; a missing field keeps its default. */
        fun decode(value: String?): NightModeParameters {
            if (value.isNullOrBlank()) return DEFAULT
            return runCatching { json.decodeFromString(serializer(), value) }
                .getOrDefault(DEFAULT)
                .normalized()
        }
    }
}

/**
 * One adjustable night mode value, with the discrete choices both settings
 * UIs offer for it (a slider's stops on phone, a picker's rows on TV).
 */
enum class NightModeControl(
    val section: Section,
    val label: String,
    val unit: ValueUnit,
    val choices: List<Float>,
) {
    CompressorThreshold(Section.Compressor, "Threshold", ValueUnit.Decibels, steps(-60f, 0f, 2f)),
    CompressorRatio(
        Section.Compressor, "Ratio", ValueUnit.Ratio,
        listOf(1f, 1.5f, 2f, 2.5f, 3f, 4f, 5f, 6f, 8f, 10f, 12f, 15f, 20f),
    ),
    CompressorKnee(Section.Compressor, "Knee", ValueUnit.Decibels, steps(0f, 12f, 1f)),
    CompressorAttack(
        Section.Compressor, "Attack", ValueUnit.Milliseconds,
        listOf(1f, 2f, 5f, 10f, 15f, 20f, 30f, 50f, 75f, 100f, 150f, 200f),
    ),
    CompressorRelease(
        Section.Compressor, "Release", ValueUnit.Milliseconds,
        listOf(50f, 100f, 150f, 200f, 300f, 400f, 500f, 750f, 1000f, 1500f, 2000f),
    ),
    CompressorMakeupGain(Section.Compressor, "Makeup Gain", ValueUnit.Decibels, steps(0f, 24f, 1f)),
    LimiterThreshold(Section.Limiter, "Threshold", ValueUnit.Decibels, steps(-12f, 0f, 0.5f)),
    LimiterRatio(Section.Limiter, "Ratio", ValueUnit.Ratio, listOf(2f, 4f, 6f, 8f, 10f, 15f, 20f, 30f, 50f)),
    LimiterAttack(Section.Limiter, "Attack", ValueUnit.Milliseconds, listOf(0.5f, 1f, 2f, 3f, 5f, 10f)),
    LimiterRelease(
        Section.Limiter, "Release", ValueUnit.Milliseconds,
        listOf(10f, 20f, 30f, 40f, 60f, 80f, 100f, 150f, 200f, 300f, 500f),
    );

    enum class Section(val title: String) { Compressor("Compressor"), Limiter("Limiter") }

    enum class ValueUnit { Decibels, Ratio, Milliseconds }

    fun get(params: NightModeParameters): Float = when (this) {
        CompressorThreshold -> params.compressorThresholdDb
        CompressorRatio -> params.compressorRatio
        CompressorKnee -> params.compressorKneeDb
        CompressorAttack -> params.compressorAttackMs
        CompressorRelease -> params.compressorReleaseMs
        CompressorMakeupGain -> params.compressorMakeupGainDb
        LimiterThreshold -> params.limiterThresholdDb
        LimiterRatio -> params.limiterRatio
        LimiterAttack -> params.limiterAttackMs
        LimiterRelease -> params.limiterReleaseMs
    }

    /** [value] snapped to the nearest of [choices]. */
    fun set(params: NightModeParameters, value: Float): NightModeParameters {
        val v = choices[indexOf(value)]
        return when (this) {
            CompressorThreshold -> params.copy(compressorThresholdDb = v)
            CompressorRatio -> params.copy(compressorRatio = v)
            CompressorKnee -> params.copy(compressorKneeDb = v)
            CompressorAttack -> params.copy(compressorAttackMs = v)
            CompressorRelease -> params.copy(compressorReleaseMs = v)
            CompressorMakeupGain -> params.copy(compressorMakeupGainDb = v)
            LimiterThreshold -> params.copy(limiterThresholdDb = v)
            LimiterRatio -> params.copy(limiterRatio = v)
            LimiterAttack -> params.copy(limiterAttackMs = v)
            LimiterRelease -> params.copy(limiterReleaseMs = v)
        }
    }

    /** The index of the choice nearest [value]. */
    fun indexOf(value: Float): Int =
        choices.indices.minBy { abs(choices[it] - value) }

    fun format(value: Float): String {
        val number = if (value % 1f == 0f) value.toInt().toString() else value.toString()
        return when (unit) {
            // A true minus sign, so negative levels read as levels.
            ValueUnit.Decibels -> "${number.replace('-', '\u2212')} dB"
            ValueUnit.Ratio -> "$number:1"
            ValueUnit.Milliseconds -> "$number ms"
        }
    }

    companion object {
        fun inSection(section: Section): List<NightModeControl> = entries.filter { it.section == section }
    }
}

private fun steps(from: Float, to: Float, step: Float): List<Float> =
    generateSequence(from) { it + step }.takeWhile { it <= to + step / 2 }.toList()
