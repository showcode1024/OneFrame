package io.github.showcode1024.oneframe.data

import org.junit.Assert.*
import org.junit.Test

class PlaybackPolicyTest {
    @Test fun automaticModeRetainsCopyBackForSoftwareColorProcessing() {
        assertEquals("mediacodec-copy,auto-copy", PlaybackPolicy.decoderPreference(false))
        assertEquals("no", PlaybackPolicy.decoderPreference(true))
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
