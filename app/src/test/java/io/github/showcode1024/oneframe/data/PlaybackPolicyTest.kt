package io.github.showcode1024.oneframe.data

import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun automaticModeRetainsCopyBackForSoftwareColorProcessing() {
        assertEquals("mediacodec-copy,auto-copy", PlaybackPolicy.decoderPreference(false))
        assertEquals("no", PlaybackPolicy.decoderPreference(true))
    }
    @Test fun p5HardwareExperimentMustBeExplicitAndSoftwareOverrideWins() {
        assertEquals("mediacodec,mediacodec-copy,auto-copy", PlaybackPolicy.decoderPreference(false, true))
        assertEquals("no", PlaybackPolicy.decoderPreference(true, true))
        assertTrue(PlaybackPolicy.shouldFallbackP5(false, false, 5, 3840, "gpu-next", "mediacodec-copy", "bt.2020-ncl"))
        assertFalse(PlaybackPolicy.shouldFallbackP5(false, true, 5, 3840, "gpu-next", "mediacodec-copy", "bt.2020-ncl"))
        assertFalse(PlaybackPolicy.shouldFallbackP5(false, false, 5, 3840, "gpu-next", "mediacodec-copy", "dolbyvision"))
        assertFalse(PlaybackPolicy.shouldFallbackP5(false, false, 8, 3840, "gpu-next", "mediacodec-copy", "bt.2020-ncl"))
        assertFalse(PlaybackPolicy.shouldFallbackP5(false, false, 5, 3840, "gpu-next", "no", "bt.2020-ncl"))
    }
    @Test fun p5ExperimentNeverClaimsUnverifiedColorIsCorrect() {
        assertTrue(PlaybackPolicy.p5ColorStatus(5, "mediacodec", "bt.2020-ncl", true).contains("可能严重偏色"))
        assertTrue(PlaybackPolicy.p5ColorStatus(5, "mediacodec", "dolbyvision", true).contains("仍需验证"))
        assertTrue(PlaybackPolicy.p5ColorStatus(5, "no", "dolbyvision", true).contains("尚未实测验证"))
    }
    @Test fun progressUsesHundredNanosecondTicks() {
        assertEquals(125_000_000L, PlaybackPolicy.secondsToTicks(12.5))
        assertEquals(0L, PlaybackPolicy.secondsToTicks(Double.NaN))
        assertEquals(0L, PlaybackPolicy.secondsToTicks(-1.0))
        assertEquals(0L, PlaybackPolicy.secondsToTicks(Double.POSITIVE_INFINITY))
    }
    @Test fun completedVideoRestartsButOrdinaryResumeIsPreserved() {
        assertEquals(0.0, PlaybackPolicy.resumePosition(599.0, 600.0), 0.0)
        assertEquals(120.5, PlaybackPolicy.resumePosition(120.5, 600.0), 0.0)
        assertEquals(0.0, PlaybackPolicy.resumePosition(Double.NaN, 600.0), 0.0)
    }
    @Test fun profileSevenDoesNotClaimFullFelReconstruction() {
        assertTrue(PlaybackPolicy.videoSummary(7, "").contains("FEL 完整重建未验证"))
    }
    @Test fun atmosTagNeverClaimsObjectRendering() {
        assertTrue(PlaybackPolicy.audioSummary("eac3").contains("未实现"))
        assertTrue(PlaybackPolicy.audioSummary("truehd").contains("未实现"))
    }
}
