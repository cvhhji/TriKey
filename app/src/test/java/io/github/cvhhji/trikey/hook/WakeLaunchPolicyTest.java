package io.github.cvhhji.trikey.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WakeLaunchPolicyTest {
    @Test
    public void screenOffLaunchIsOnByDefault() {
        assertTrue(WakeLaunchPolicy.shouldWake(true, "settings"));
        assertFalse(WakeLaunchPolicy.shouldWake(false, "settings"));
    }

    @Test
    public void nonNavigationActionsKeepTheirExistingBehavior() {
        assertFalse(WakeLaunchPolicy.shouldWake(true, "screenshot"));
        assertFalse(WakeLaunchPolicy.shouldWake(true, "back"));
        assertFalse(WakeLaunchPolicy.shouldWake(true, "lock_screen"));
        assertFalse(WakeLaunchPolicy.shouldWake(true, "statusbar_expand"));
        assertFalse(WakeLaunchPolicy.shouldWake(true, "none"));
    }

    @Test
    public void secureDevicesWaitAndNeverRequestInsecureDismissal() {
        assertTrue(WakeLaunchPolicy.shouldWaitForAuthentication(true));
        assertFalse(WakeLaunchPolicy.shouldDismissKeyguard(true));
        assertFalse(WakeLaunchPolicy.shouldWaitForAuthentication(false));
        assertTrue(WakeLaunchPolicy.shouldDismissKeyguard(false));
    }
}
