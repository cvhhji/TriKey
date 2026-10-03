package io.github.cvhhji.trikey.hook;

final class RingerModeTogglePolicy {
    static final int RINGER_MODE_SILENT = 0;
    static final int RINGER_MODE_VIBRATE = 1;
    static final int RINGER_MODE_NORMAL = 2;

    private RingerModeTogglePolicy() {}

    static int nextMode(int currentMode) {
        if (currentMode == RINGER_MODE_NORMAL) {
            return RINGER_MODE_VIBRATE;
        }
        if (currentMode == RINGER_MODE_VIBRATE) {
            return RINGER_MODE_SILENT;
        }
        return RINGER_MODE_NORMAL;
    }
}
