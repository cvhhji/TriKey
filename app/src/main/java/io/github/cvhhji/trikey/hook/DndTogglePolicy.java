package io.github.cvhhji.trikey.hook;

import android.app.NotificationManager;

final class DndTogglePolicy {
    static final int ZEN_MODE_OFF = 0;
    static final int ZEN_MODE_IMPORTANT_INTERRUPTIONS = 1;

    private DndTogglePolicy() {}

    static int nextZenMode(int currentInterruptionFilter) {
        switch (currentInterruptionFilter) {
            case NotificationManager.INTERRUPTION_FILTER_ALL:
                return ZEN_MODE_IMPORTANT_INTERRUPTIONS;
            case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
            case NotificationManager.INTERRUPTION_FILTER_NONE:
            case NotificationManager.INTERRUPTION_FILTER_ALARMS:
                return ZEN_MODE_OFF;
            default:
                throw new IllegalArgumentException(
                        "Unknown interruption filter: " + currentInterruptionFilter);
        }
    }
}
