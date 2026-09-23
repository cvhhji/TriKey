package io.github.cvhhji.trikey.hook;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.cvhhji.trikey.Config;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;

public final class TriKeyModule extends XposedModule {
    private static final String TAG = "TriKey";
    private static final String OPLUS_POLICY_CLASS =
            "com.android.server.policy.StrategyActionButtonKeyLaunchApp";
    private static final String OPLUS_POLICY_METHOD = "actionInterceptKeyBeforeQueueing";
    private static final String AOSP_POLICY_CLASS = "com.android.server.policy.PhoneWindowManager";
    private static final String AOSP_POLICY_METHOD = "interceptKeyBeforeQueueing";
    private Handler handler;
    private KeyGestureDetector<Bundle> gestureDetector;
    private Context systemContext;
    private SharedPreferences preferences;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "API 102 module loaded in " + param.getProcessName());
    }

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        try {
            handler = new Handler(Looper.getMainLooper());
            gestureDetector = new KeyGestureDetector<>(new KeyGestureDetector.Scheduler() {
                @Override
                public void postDelayed(Runnable task, long delayMs) {
                    handler.postDelayed(task, delayMs);
                }

                @Override
                public void removeCallbacks(Runnable task) {
                    handler.removeCallbacks(task);
                }
            }, (gesture, snapshot) -> execute(systemContext, snapshot, gesture));
            preferences = getRemotePreferences("trikey");
            int installed = installHooks(param.getClassLoader(), OPLUS_POLICY_CLASS, OPLUS_POLICY_METHOD);
            if (installed > 0) {
                log(Log.INFO, TAG, "Using ColorOS shortcut-key policy hook; installed "
                        + installed + " hook(s)");
            } else {
                installed = installHooks(param.getClassLoader(), AOSP_POLICY_CLASS, AOSP_POLICY_METHOD);
                log(Log.WARN, TAG, "ColorOS shortcut-key policy was unavailable; installed "
                        + installed + " PhoneWindowManager fallback hook(s)");
            }
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Unable to install hooks", error);
        }
    }

    private int installHooks(ClassLoader classLoader, String className, String methodName) {
        try {
            Class<?> target = Class.forName(className, false, classLoader);
            int installed = 0;
            for (Method method : target.getDeclaredMethods()) {
                if (!methodName.equals(method.getName()) || !hasKeyEventParameter(method)) continue;
                method.setAccessible(true);
                hookMethod(method);
                installed++;
            }
            return installed;
        } catch (ClassNotFoundException error) {
            log(Log.WARN, TAG, "Hook class not found: " + className);
            return 0;
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Unable to hook " + className + "." + methodName, error);
            return 0;
        }
    }

    private static boolean hasKeyEventParameter(Method method) {
        for (Class<?> type : method.getParameterTypes()) {
            if (KeyEvent.class.isAssignableFrom(type)) return true;
        }
        return false;
    }

    private void hookMethod(Method method) {
        hook(method)
                .setId("trikey:" + method.toGenericString())
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    try {
                        KeyEvent event = findEvent(chain.getArgs());
                        if (event == null) return chain.proceed();
                        SharedPreferences source = preferences;
                        if (source == null || !source.getBoolean("enabled", true)) {
                            KeyGestureDetector<Bundle> detector = gestureDetector;
                            if (detector != null) detector.cancel();
                            return chain.proceed();
                        }
                        int keyCode = Config.normalizeKeyCode(
                                source.getInt("keyCode", Config.DEFAULT_KEY_CODE));
                        if (event.getKeyCode() != keyCode) return chain.proceed();
                        Context context = contextFrom(chain.getThisObject());
                        if (context == null) return chain.proceed();
                        Bundle config = readConfig();
                        log(Log.INFO, TAG, "Shortcut entry received: action=" + event.getAction()
                                + ", keyCode=" + event.getKeyCode()
                                + ", repeat=" + event.getRepeatCount());
                        handle(event, context, config);
                        if (config.getBoolean("consumeOriginal", false)) {
                            return defaultValue(method.getReturnType());
                        }
                    } catch (Throwable error) {
                        log(Log.ERROR, TAG, "Shortcut observer failed", error);
                    }
                    return chain.proceed();
                });
    }

    private void handle(KeyEvent event, Context context, Bundle config) {
        KeyGestureDetector<Bundle> detector = gestureDetector;
        if (detector == null) return;
        if (event.getRepeatCount() > 0) return;
        int longMs = config.getInt("longMs", 650);
        int doubleMs = config.getInt("doubleMs", 320);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            systemContext = context;
            detector.onDown(SystemClock.uptimeMillis(), doubleMs, longMs, new Bundle(config));
            return;
        }
        if (event.getAction() != KeyEvent.ACTION_UP) return;
        long heldMs = Math.max(
                SystemClock.uptimeMillis() - event.getDownTime(),
                event.getEventTime() - event.getDownTime());
        systemContext = context;
        detector.onUp(SystemClock.uptimeMillis(), heldMs, doubleMs, longMs, new Bundle(config));
    }

    private void execute(Context context, Bundle config, String gesture) {
        String type = config.getString(gesture + "Type", "none");
        String value = config.getString(gesture + "Value", "");
        log(Log.INFO, TAG, "Gesture fired: gesture=" + gesture + ", type=" + type);
        try {
            Intent intent;
            switch (type) {
                case "wechat":
                    intent = component("com.tencent.mm", "com.tencent.mm.ui.LauncherUI");
                    break;
                case "global_search":
                    intent = component("com.heytap.quicksearchbox", "com.heytap.quicksearchbox.ui.activity.SearchHomeActivity");
                    break;
                case "settings":
                    intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
                    break;
                case "app_search":
                    intent = component("com.heytap.quicksearchbox", "com.heytap.quicksearchbox.ui.activity.AppCategoryActivity");
                    break;
                case "translate":
                    intent = component("com.coloros.translate", "com.coloros.translate.ui.MainActivity");
                    break;
                case "game_center":
                    intent = component("com.oplus.games", "business.module.desktop.JumpSpaceActivity");
                    break;
                case "camera":
                    intent = component("com.oplus.camera", "com.oplus.camera.Camera");
                    break;
                case "video_capture":
                    intent = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
                    break;
                case "app":
                    intent = context.getPackageManager().getLaunchIntentForPackage(value);
                    if (intent == null) throw new IllegalArgumentException("Package has no launcher activity: " + value);
                    break;
                case "intent":
                    intent = Intent.parseUri(value, Intent.URI_INTENT_SCHEME);
                    break;
                case "wechat_pay":
                    intent = new Intent("com.tencent.mm.ui.ShortCutDispatchAction")
                            .setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                            .putExtra("LauncherUI.Shortcut.LaunchType", "launch_type_offline_wallet");
                    break;
                case "wechat_scan":
                    intent = new Intent("com.tencent.mm.ui.ShortCutDispatchAction")
                            .setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                            .putExtra("LauncherUI.Shortcut.LaunchType", "launch_type_scan_qrcode");
                    break;
                case "alipay_pay":
                    intent = alipay("20000056", "alipays://platformapi/startapp?appId=20000056");
                    break;
                case "alipay_scan":
                    intent = alipay("10000007", "alipays://platformapi/startapp?appId=10000007&sourceId=scan3dtouch");
                    break;
                case "recorder":
                    intent = new Intent("com.oplus.soundrecorder.LAUNCH_FROM_BRACKET_SPACE")
                            .setClassName("com.coloros.soundrecorder", "oplus.multimedia.soundrecorder.slidebar.TransparentActivity")
                            .putExtra("extra_enter_type", 3);
                    break;
                case "statusbar_expand":
                    statusBar(context, "expandNotificationsPanel");
                    return;
                case "statusbar_collapse":
                    statusBar(context, "collapsePanels");
                    return;
                case "screenshot":
                    injectKey(KeyEvent.KEYCODE_SYSRQ);
                    return;
                case "back":
                    injectKey(KeyEvent.KEYCODE_BACK);
                    return;
                case "lock_screen":
                    injectKey(KeyEvent.KEYCODE_POWER);
                    return;
                case "ocr":
                    intent = new Intent("oplus.intent.action.DIRECT_SIDEBAR_SERVICE")
                            .putExtra("extra_entrance_function", "full_screen_ocr")
                            .putExtra("triggered_app", "com.coloros.smartsidebar");
                    startResolvedForegroundService(context, intent);
                    return;
                default:
                    return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Action failed for " + gesture + ": " + type, error);
        }
    }

    private static Intent component(String packageName, String className) {
        return new Intent(Intent.ACTION_MAIN).setClassName(packageName, className);
    }

    private static Intent alipay(String appId, String scheme) {
        return new Intent(Intent.ACTION_VIEW)
                .setClassName("com.eg.android.AlipayGphone", "com.alipay.android.phone.wallet.shortcuts.bridge.ShortcutsLauncherActivity")
                .putExtra("KEY_APP_ID", appId)
                .putExtra("KEY_SCHEME", scheme);
    }

    private void startResolvedForegroundService(Context context, Intent intent) {
        int flags = PackageManager.MATCH_SYSTEM_ONLY
                | PackageManager.MATCH_DIRECT_BOOT_AWARE
                | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
        ResolveInfo resolved = context.getPackageManager().resolveService(intent, flags);
        if (resolved == null || resolved.serviceInfo == null) {
            throw new IllegalStateException("No system service handles " + intent.getAction());
        }
        ComponentName component = new ComponentName(
                resolved.serviceInfo.packageName, resolved.serviceInfo.name);
        intent.setComponent(component);
        log(Log.INFO, TAG, "Resolved foreground service: " + component.flattenToShortString());
        context.startForegroundService(intent);
    }

    @SuppressLint("WrongConstant")
    private static void statusBar(Context context, String methodName) throws ReflectiveOperationException {
        Object service = context.getSystemService("statusbar");
        Method method = service.getClass().getMethod(methodName);
        method.setAccessible(true);
        method.invoke(service);
    }

    private static void injectKey(int keyCode) throws ReflectiveOperationException {
        Class<?> inputManager = Class.forName("android.hardware.input.InputManager");
        Object manager = inputManager.getMethod("getInstance").invoke(null);
        Method inject = inputManager.getMethod("injectInputEvent", android.view.InputEvent.class, int.class);
        long now = SystemClock.uptimeMillis();
        inject.invoke(manager, new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0), 0);
        inject.invoke(manager, new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0), 0);
    }

    private Bundle readConfig() {
        SharedPreferences source = preferences;
        Bundle config = new Bundle();
        if (source == null) {
            config.putBoolean("enabled", false);
            return config;
        }
        config.putBoolean("enabled", source.getBoolean("enabled", true));
        config.putBoolean("consumeOriginal", source.getBoolean("consumeOriginal", false));
        config.putInt("keyCode", Config.normalizeKeyCode(
                source.getInt("keyCode", Config.DEFAULT_KEY_CODE)));
        config.putInt("doubleMs", source.getInt("doubleMs", 320));
        config.putInt("longMs", source.getInt("longMs", 650));
        for (String gesture : new String[]{"single", "double", "long"}) {
            config.putString(gesture + "Type", source.getString(gesture + "Type", defaultType(gesture)));
            config.putString(gesture + "Value", source.getString(gesture + "Value", ""));
        }
        return config;
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        throw new AssertionError("Unknown primitive return type: " + type);
    }

    private static String defaultType(String gesture) {
        if ("single".equals(gesture)) return "wechat_pay";
        if ("double".equals(gesture)) return "wechat_scan";
        return "ocr";
    }

    @SuppressLint("PrivateApi")
    private Context contextFrom(Object object) {
        if (systemContext != null) return systemContext;
        Class<?> type = object.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("mContext");
                field.setAccessible(true);
                Object value = field.get(object);
                if (value instanceof Context) {
                    systemContext = (Context) value;
                    return systemContext;
                }
            } catch (ReflectiveOperationException ignored) {
                type = type.getSuperclass();
            }
        }
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
            if (thread != null) {
                Object context = activityThread.getMethod("getSystemContext").invoke(thread);
                if (context instanceof Context) {
                    systemContext = (Context) context;
                    return systemContext;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return null;
    }

    private static KeyEvent findEvent(List<Object> args) {
        for (Object arg : args) if (arg instanceof KeyEvent) return (KeyEvent) arg;
        return null;
    }

}
