package io.github.cvhhji.trikey.hook;

final class WakeLaunchPolicy {
    private WakeLaunchPolicy() {}

    static boolean shouldWake(boolean screenOff, String action) {
        return screenOff && isLaunchAction(action);
    }

    static boolean shouldWaitForAuthentication(boolean deviceSecure) {
        return deviceSecure;
    }

    static boolean shouldDismissKeyguard(boolean deviceSecure) {
        return !deviceSecure;
    }

    private static boolean isLaunchAction(String action) {
        if (action == null) return false;
        switch (action) {
            case "wechat":
            case "global_search":
            case "settings":
            case "app_search":
            case "translate":
            case "game_center":
            case "ocr":
            case "camera":
            case "video_capture":
            case "app":
            case "intent":
            case "wechat_pay":
            case "wechat_scan":
            case "alipay_pay":
            case "alipay_scan":
            case "recorder":
                return true;
            default:
                return false;
        }
    }
}
