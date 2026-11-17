package org.siloserver.silo.common.player

import android.content.Context
import android.media.MediaCodecInfo.CodecCapabilities
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodecList
import org.siloserver.silo.common.diagnostics.AndroidDiagnosticsDeviceProbe
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class MediaCodecCapabilitiesProbeDiagnosticsTest {
    @Test
    fun diagnosticsKeepHardwareLevelsWhilePlaybackFollowsTheHevcSwitch() {
        val previous = MediaCodecCapabilitiesProbe.ignoreHevcLevelLimits
        try {
            ShadowMediaCodecList.reset()
            ShadowMediaCodecList.addCodec(
                MediaCodecInfoBuilder.newBuilder()
                    .setName("c2.vendor.hevc.decoder")
                    .setIsHardwareAccelerated(true)
                    .setIsSoftwareOnly(false)
                    .setCapabilities(
                        MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                            .setMediaFormat(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 3840, 2160))
                            .setColorFormats(intArrayOf(CodecCapabilities.COLOR_FormatYUV420Flexible))
                            .setProfileLevels(arrayOf(CodecProfileLevel().apply {
                                profile = CodecProfileLevel.HEVCProfileMain10HDR10
                                level = CodecProfileLevel.HEVCMainTierLevel51
                            }))
                            .build(),
                    )
                    .build(),
            )
            MediaCodecCapabilitiesProbe.resetCacheForTest()
            MediaCodecCapabilitiesProbe.ignoreHevcLevelLimits = false
            val hardware = MediaCodecCapabilitiesProbe.probe()
            val hardwareDecoder = hardware.videoDecodeCapabilities.single()
            assertEquals(listOf(153), hardwareDecoder.levels)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val diagnostics = AndroidDiagnosticsDeviceProbe(
                context,
                AudioCapabilityManager(context),
                PlaybackNetworkEvidenceProvider.None,
            )

            listOf(true, false, true).forEach { ignoreLevels ->
                MediaCodecCapabilitiesProbe.ignoreHevcLevelLimits = ignoreLevels
                val playback = MediaCodecCapabilitiesProbe.probe()
                val playbackDecoder = playback.videoDecodeCapabilities.single()

                assertEquals(if (ignoreLevels) emptyList() else listOf(153), playbackDecoder.levels)
                assertEquals(hardwareDecoder, playbackDecoder.copy(levels = hardwareDecoder.levels))
                assertEquals(hardware, MediaCodecCapabilitiesProbe.diagnosticsSnapshot())
                assertEquals(listOf(153), diagnostics.codecs().single().levels)
                assertEquals(playback, MediaCodecCapabilitiesProbe.probe(), "diagnostics must not change playback evidence")
            }
        } finally {
            MediaCodecCapabilitiesProbe.ignoreHevcLevelLimits = previous
            ShadowMediaCodecList.reset()
            MediaCodecCapabilitiesProbe.resetCacheForTest()
        }
    }
}
