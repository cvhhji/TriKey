package io.github.cvhhji.trikey.hook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RingerModeTogglePolicyTest {
    @Test
    public void cyclesThroughRingVibrateAndSilent() {
        assertEquals(RingerModeTogglePolicy.RINGER_MODE_VIBRATE,
                RingerModeTogglePolicy.nextMode(
                        RingerModeTogglePolicy.RINGER_MODE_NORMAL));
        assertEquals(RingerModeTogglePolicy.RINGER_MODE_SILENT,
                RingerModeTogglePolicy.nextMode(
                        RingerModeTogglePolicy.RINGER_MODE_VIBRATE));
        assertEquals(RingerModeTogglePolicy.RINGER_MODE_NORMAL,
                RingerModeTogglePolicy.nextMode(
                        RingerModeTogglePolicy.RINGER_MODE_SILENT));
    }

    @Test
    public void unknownModeReturnsToRing() {
        assertEquals(RingerModeTogglePolicy.RINGER_MODE_NORMAL,
                RingerModeTogglePolicy.nextMode(-1));
    }
}
