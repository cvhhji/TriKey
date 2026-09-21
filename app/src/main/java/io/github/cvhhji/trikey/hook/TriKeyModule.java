package io.github.cvhhji.trikey.hook;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class TriKeyModule extends XposedModule {
    private static final Uri CONFIG_URI = Uri.parse("content://io.github.cvhhji.trikey.settings");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Context systemContext;
    private long downAt;
    private long lastUpAt;
    private boolean longFired;
    private Runnable longTask;
    private Runnable singleTask;

    @Override
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {
        try {
            Class<?> pwm = Class.forName("com.android.server.policy.PhoneWindowManager", false, param.getClassLoader());
            int installed = 0;
            for (Method method : pwm.getDeclaredMethods()) {
                if (!"interceptKeyBeforeQueueing".equals(method.getName())) continue;
                method.setAccessible(true);
                hookMethod(method);
                installed++;
            }
            log("Installed " + installed + " key interception hook(s)");
        } catch (Throwable error) {
            log("Unable to install hooks", error);
        }
    }

    private void hookMethod(Executable method) {
        hook(method)
                .setId("trikey:" + method.toGenericString())
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    KeyEvent event = findEvent(chain.getArgs());
                    if (event == null) return chain.proceed();
                    Context context = contextFrom(chain.getThisObject());
                    if (context == null) return chain.proceed();
                    Bundle config = readConfig(context);
                    if (!config.getBoolean("enabled", true)
                            || event.getKeyCode() != config.getInt("keyCode", 219)) {
                        return chain.proceed();
                    }
                    handle(event, context, config);
                    return zeroFor(method.getReturnType());
                });
    }

    private synchronized void handle(KeyEvent event, Context context, Bundle config) {
        if (event.getRepeatCount() > 0) return;
        int longMs = config.getInt("longMs", 650);
        int doubleMs = config.getInt("doubleMs", 320);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            downAt = SystemClock.uptimeMillis();
            longFired = false;
            if (longTask != null) handler.removeCallbacks(longTask);
            Bundle snapshot = new Bundle(config);
            longTask = () -> {
                synchronized (TriKeyModule.this) {
                    longFired = true;
                    execute(context, snapshot, "long");
                }
            };
            handler.postDelayed(longTask, longMs);
            return;
        }
        if (event.getAction() != KeyEvent.ACTION_UP) return;
        if (longTask != null) handler.removeCallbacks(longTask);
        longTask = null;
        if (longFired || SystemClock.uptimeMillis() - downAt >= longMs) return;
        long now = SystemClock.uptimeMillis();
        if (lastUpAt != 0 && now - lastUpAt <= doubleMs) {
            if (singleTask != null) handler.removeCallbacks(singleTask);
            singleTask = null;
            lastUpAt = 0;
            execute(context, config, "double");
        } else {
            lastUpAt = now;
            Bundle snapshot = new Bundle(config);
            singleTask = () -> {
                synchronized (TriKeyModule.this) {
                    if (lastUpAt != 0) {
                        lastUpAt = 0;
                        execute(context, snapshot, "single");
                    }
                }
            };
            handler.postDelayed(singleTask, doubleMs);
        }
    }

    private void execute(Context context, Bundle config, String gesture) {
        String type = config.getString(gesture + "Type", "none");
        String value = config.getString(gesture + "Value", "");
        try {
            Intent intent;
            switch (type) {
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
                case "ocr":
                    intent = new Intent("oplus.intent.action.DIRECT_SIDEBAR_SERVICE")
                            .setPackage("com.coloros.smartsidebar")
                            .putExtra("extra_entrance_function", "full_screen_ocr")
                            .putExtra("triggered_app", "com.coloros.smartsidebar");
                    context.startForegroundService(intent);
                    return;
                default:
                    return;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
        } catch (Throwable error) {
            log("Action failed for " + gesture + ": " + type, error);
        }
    }

    private Bundle readConfig(Context context) {
        try {
            Bundle result = context.getContentResolver().call(CONFIG_URI, "getConfig", null, null);
            if (result != null) return result;
        } catch (Throwable error) {
            log("Unable to read settings", error);
        }
        Bundle fallback = new Bundle();
        fallback.putBoolean("enabled", false);
        return fallback;
    }

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
        return null;
    }

    private static KeyEvent findEvent(List<Object> args) {
        for (Object arg : args) if (arg instanceof KeyEvent) return (KeyEvent) arg;
        return null;
    }

    private static Object zeroFor(Class<?> type) {
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return (char) 0;
        if (type.isPrimitive()) return 0;
        return null;
    }
}
