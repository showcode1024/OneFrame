package io.github.showcode1024.oneframe.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGesturePolicyTest {
    @Test fun upwardMovementRaisesAndDownwardMovementLowersBrightness() {
        assertEquals(.8f, PlayerGesturePolicy.adjusted(.5f, -200f, 1000, true), .001f)
        assertEquals(.2f, PlayerGesturePolicy.adjusted(.5f, 200f, 1000, true), .001f)
    }
    @Test fun limitsAvoidBlackScreenAndAllowMute() {
        assertEquals(.03f, PlayerGesturePolicy.adjusted(.5f, 1000f, 1000, true), .001f)
        assertEquals(0f, PlayerGesturePolicy.adjusted(.5f, 1000f, 1000, false), .001f)
        assertEquals(1f, PlayerGesturePolicy.adjusted(.5f, -1000f, 1000, false), .001f)
    }
    @Test fun invalidDimensionsOrMovementLeaveCurrentValue() {
        assertEquals(.5f, PlayerGesturePolicy.adjusted(.5f, Float.NaN, 1000, true), .001f)
        assertEquals(.5f, PlayerGesturePolicy.adjusted(.5f, 20f, 0, false), .001f)
    }
}
