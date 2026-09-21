package io.github.cvhhji.trikey;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

public final class Config {
    public static final String PREFS = "trikey";
    public static final int DEFAULT_KEY_CODE = 219;
    public static final int DEFAULT_DOUBLE_MS = 320;
    public static final int DEFAULT_LONG_MS = 650;

    private Config() {}

    public static Bundle read(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Bundle b = new Bundle();
        b.putBoolean("enabled", p.getBoolean("enabled", true));
        b.putInt("keyCode", p.getInt("keyCode", DEFAULT_KEY_CODE));
        b.putInt("doubleMs", p.getInt("doubleMs", DEFAULT_DOUBLE_MS));
        b.putInt("longMs", p.getInt("longMs", DEFAULT_LONG_MS));
        for (String gesture : new String[]{"single", "double", "long"}) {
            b.putString(gesture + "Type", p.getString(gesture + "Type", defaultType(gesture)));
            b.putString(gesture + "Value", p.getString(gesture + "Value", ""));
        }
        return b;
    }

    private static String defaultType(String gesture) {
        if ("single".equals(gesture)) return "wechat_pay";
        if ("double".equals(gesture)) return "wechat_scan";
        return "ocr";
    }
}

