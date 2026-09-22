package io.github.cvhhji.trikey;

public final class Config {
    public static final String PREFS = "trikey";
    public static final int DEFAULT_KEY_CODE = 780;
    public static final int LEGACY_DEFAULT_KEY_CODE = 219;
    public static final int DEFAULT_DOUBLE_MS = 320;
    public static final int DEFAULT_LONG_MS = 650;

    public static int normalizeKeyCode(int keyCode) {
        return keyCode == LEGACY_DEFAULT_KEY_CODE ? DEFAULT_KEY_CODE : keyCode;
    }

    private Config() {}
}
