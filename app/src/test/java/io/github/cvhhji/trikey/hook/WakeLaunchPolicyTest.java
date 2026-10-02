package io.github.cvhhji.trikey.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WakeLaunchPolicyTest {
    @Test
    public void screenOffLaunchIsOnByDefault() {
        assertTrue(WakeLaunchPolicy.shouldWakeForLaunch(true, "settings"));
        assertTrue(WakeLaunchPolicy.shouldWakeForLaunch(true, "screen_translate"));
        assertFalse(WakeLaunchPolicy.shouldWakeForLaunch(false, "settings"));
    }

    @Test
    public void nonNavigationActionsKeepTheirExistingBehavior() {
        assertFalse(WakeLaunchPolicy.shouldWakeForLaunch(true, "screenshot"));
        assertFalse(WakeLaunchPolicy.shouldWakeForLaunch(true, "back"));
        assertFalse(WakeLaunchPolicy.shouldWakeForLaunch(true, "lock_screen"));
        assertFalse(WakeLaunchPolicy.shouldWakeForLaunch(true, "statusbar_expand"));
        assertFalse(WakeLaunchPolicy.shouldWakeForLaunch(true, "dnd_toggle"));
        assertFalse(WakeLaunchPolicy.shouldWakeForLaunch(true, "none"));
    }

    @Test
    public void doNotDisturbCanRunOnLockScreenWithoutAuthentication() {
        assertTrue(WakeLaunchPolicy.canRunWhileLocked("dnd_toggle"));
        assertFalse(WakeLaunchPolicy.canRunWhileLocked("settings"));
    }

    @Test
    public void secureDevicesWaitAndNeverRequestInsecureDismissal() {
        assertTrue(WakeLaunchPolicy.shouldWaitForAuthentication(true));
        assertFalse(WakeLaunchPolicy.shouldDismissKeyguard(true));
        assertFalse(WakeLaunchPolicy.shouldWaitForAuthentication(false));
        assertTrue(WakeLaunchPolicy.shouldDismissKeyguard(false));
    }
}
