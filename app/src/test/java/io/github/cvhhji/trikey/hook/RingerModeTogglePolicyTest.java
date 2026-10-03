package io.github.cvhhji.trikey.hook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class RingerModeTogglePolicyTest {
    @Test
    public void normalModeUsesTheSystemVibrateWhenSilentSetting() {
        assertEquals(RingerModeTogglePolicy.RINGER_MODE_VIBRATE,
                RingerModeTogglePolicy.nextMode(
                        RingerModeTogglePolicy.RINGER_MODE_NORMAL, true));
        assertEquals(RingerModeTogglePolicy.RINGER_MODE_SILENT,
                RingerModeTogglePolicy.nextMode(
                        RingerModeTogglePolicy.RINGER_MODE_NORMAL, false));
    }

    @Test
    public void mutedAndUnknownModesReturnToRing() {
        assertEquals(RingerModeTogglePolicy.RINGER_MODE_NORMAL,
                RingerModeTogglePolicy.nextMode(
                        RingerModeTogglePolicy.RINGER_MODE_VIBRATE, true));
        assertEquals(RingerModeTogglePolicy.RINGER_MODE_NORMAL,
                RingerModeTogglePolicy.nextMode(
                        RingerModeTogglePolicy.RINGER_MODE_SILENT, false));
        assertEquals(RingerModeTogglePolicy.RINGER_MODE_NORMAL,
                RingerModeTogglePolicy.nextMode(-1, false));
    }
}
