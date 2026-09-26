package io.github.cvhhji.trikey.hook;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Method;

public final class WakeUpApiCompatTest {
    @Test
    public void selectsAndroid15WakeUpSignature() throws Exception {
        Method method = WakeUpApiCompat.findWakeUpMethod(Android15PowerManager.class);

        assertEquals(3, method.getParameterTypes().length);
        assertArrayEquals(new Object[]{120L, 4, "TriKey"},
                WakeUpApiCompat.arguments(method, 120L, 4, "TriKey", 0));
    }

    @Test
    public void prefersDisplayAwareSignatureWhenAvailable() throws Exception {
        Method method = WakeUpApiCompat.findWakeUpMethod(ModernPowerManager.class);

        assertEquals(4, method.getParameterTypes().length);
        assertArrayEquals(new Object[]{120L, 4, "TriKey", 0},
                WakeUpApiCompat.arguments(method, 120L, 4, "TriKey", 0));
    }

    @Test
    public void supportsLegacyTwoArgumentSignature() throws Exception {
        Method method = WakeUpApiCompat.findWakeUpMethod(LegacyPowerManager.class);

        assertEquals(2, method.getParameterTypes().length);
        assertArrayEquals(new Object[]{120L, "TriKey"},
                WakeUpApiCompat.arguments(method, 120L, 4, "TriKey", 0));
    }

    @Test
    public void rejectsUnknownSignatures() throws Exception {
        try {
            WakeUpApiCompat.findWakeUpMethod(UnsupportedPowerManager.class);
            fail("Expected an unsupported wakeUp overload to be rejected");
        } catch (NoSuchMethodException expected) {
            assertEquals("No supported PowerManager.wakeUp overload", expected.getMessage());
        }
    }

    public static final class Android15PowerManager {
        public void wakeUp(long time, int reason, String details) {}
    }

    public static final class ModernPowerManager {
        public void wakeUp(long time, int reason, String details) {}
        public void wakeUp(long time, int reason, String details, int displayId) {}
    }

    public static final class LegacyPowerManager {
        public void wakeUp(long time, String details) {}
    }

    public static final class UnsupportedPowerManager {
        public void wakeUp(int time, String details) {}
    }
}
