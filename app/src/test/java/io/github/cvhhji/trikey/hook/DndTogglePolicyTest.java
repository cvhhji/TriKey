package io.github.cvhhji.trikey.hook;

import static org.junit.Assert.assertEquals;

import android.app.NotificationManager;

import org.junit.Test;

public final class DndTogglePolicyTest {
    @Test
    public void disabledDndEnablesPriorityMode() {
        assertEquals(DndTogglePolicy.ZEN_MODE_IMPORTANT_INTERRUPTIONS,
                DndTogglePolicy.nextZenMode(NotificationManager.INTERRUPTION_FILTER_ALL));
    }

    @Test
    public void everyEnabledDndFilterDisablesDnd() {
        assertEquals(DndTogglePolicy.ZEN_MODE_OFF,
                DndTogglePolicy.nextZenMode(NotificationManager.INTERRUPTION_FILTER_PRIORITY));
        assertEquals(DndTogglePolicy.ZEN_MODE_OFF,
                DndTogglePolicy.nextZenMode(NotificationManager.INTERRUPTION_FILTER_ALARMS));
        assertEquals(DndTogglePolicy.ZEN_MODE_OFF,
                DndTogglePolicy.nextZenMode(NotificationManager.INTERRUPTION_FILTER_NONE));
    }

    @Test(expected = IllegalArgumentException.class)
    public void unknownFilterIsRejected() {
        DndTogglePolicy.nextZenMode(NotificationManager.INTERRUPTION_FILTER_UNKNOWN);
    }
}
