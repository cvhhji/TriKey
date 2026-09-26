package io.github.cvhhji.trikey.hook;

import android.annotation.SuppressLint;
import android.app.ActivityOptions;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;

import io.github.cvhhji.trikey.BuildConfig;
import io.github.cvhhji.trikey.KeyguardLaunchActivity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

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
    private static final String[] KEYGUARD_GOING_AWAY_CLASSES = {
            "com.android.server.wm.ActivityTaskManagerService",
            "com.android.server.am.ActivityManagerService"
    };
    private Handler handler;
    private KeyGestureDetector<Bundle> gestureDetector;
    private Context systemContext;
    private SharedPreferences preferences;
    private PendingWakeLaunch pendingWakeLaunch;
    private boolean pendingScreenOffGesture;
    private boolean currentPressWasSecond;
    private boolean currentPressScreenOff;
    private Runnable clearScreenOffGestureTask;
    private volatile PendingKeyguardLaunch pendingKeyguardLaunch;
    private boolean keyguardHandoffHookInstalled;
    private boolean keyguardHandoffReceiverRegistered;
    private final BroadcastReceiver keyguardHandoffCancelReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            cancelPendingKeyguardLaunch(intent.getStringExtra(
                    KeyguardLaunchActivity.EXTRA_HANDOFF_ID));
        }
    };

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
            keyguardHandoffHookInstalled = installKeyguardHandoffHook(param.getClassLoader());
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
                            clearScreenOffGestureState();
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
            ensureKeyguardHandoffCancelReceiver(context);
            currentPressWasSecond = pendingScreenOffGesture;
            if (clearScreenOffGestureTask != null) {
                handler.removeCallbacks(clearScreenOffGestureTask);
                clearScreenOffGestureTask = null;
            }
            pendingScreenOffGesture = false;
            currentPressScreenOff = currentPressWasSecond || isScreenOff(context);
            Bundle snapshot = new Bundle(config);
            snapshot.putBoolean("screenOffAtGesture", currentPressScreenOff);
            detector.onDown(SystemClock.uptimeMillis(), doubleMs, longMs, snapshot);
            return;
        }
        if (event.getAction() != KeyEvent.ACTION_UP) return;
        long heldMs = Math.max(
                SystemClock.uptimeMillis() - event.getDownTime(),
                event.getEventTime() - event.getDownTime());
        systemContext = context;
        Bundle snapshot = new Bundle(config);
        snapshot.putBoolean("screenOffAtGesture", currentPressScreenOff);
        detector.onUp(SystemClock.uptimeMillis(), heldMs, doubleMs, longMs, snapshot);
        if (currentPressWasSecond || heldMs >= longMs || !currentPressScreenOff) {
            clearScreenOffGestureState();
        } else {
            pendingScreenOffGesture = true;
            clearScreenOffGestureTask = () -> {
                pendingScreenOffGesture = false;
                clearScreenOffGestureTask = null;
            };
            handler.postDelayed(clearScreenOffGestureTask, doubleMs + 50L);
        }
    }

    private void execute(Context context, Bundle config, String gesture) {
        String type = config.getString(gesture + "Type", "none");
        log(Log.INFO, TAG, "Gesture fired: gesture=" + gesture + ", type=" + type);
        if (context == null) return;
        boolean screenOff = config.getBoolean("screenOffAtGesture", false) || isScreenOff(context);
        if (!WakeLaunchPolicy.shouldWake(screenOff, type)) {
            performAction(context, config, gesture, false);
            return;
        }
        try {
            wakeDevice(context);
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Unable to wake display", error);
        }
        boolean deviceSecure = isDeviceSecure(context);
        log(Log.INFO, TAG, "Screen-off action requires device authentication: " + deviceSecure);
        queueAfterWake(context, config, gesture,
                WakeLaunchPolicy.shouldWaitForAuthentication(deviceSecure));
    }

    private void performAction(Context context, Bundle config, String gesture,
                               boolean dismissKeyguardIfInsecure) {
        String type = config.getString(gesture + "Type", "none");
        String value = config.getString(gesture + "Value", "");
        try {
            switch (type) {
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
                    startResolvedForegroundService(context, createTargetIntent(context, type, value));
                    return;
            }
            Intent intent = createTargetIntent(context, type, value);
            if (intent == null) return;
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(context, intent, dismissKeyguardIfInsecure);
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Action failed for " + gesture + ": " + type, error);
        }
    }

    private Intent createTargetIntent(Context context, String type, String value) throws Exception {
        switch (type) {
            case "wechat":
                return component("com.tencent.mm", "com.tencent.mm.ui.LauncherUI");
            case "global_search":
                return component("com.heytap.quicksearchbox", "com.heytap.quicksearchbox.ui.activity.SearchHomeActivity");
            case "settings":
                return new Intent(android.provider.Settings.ACTION_SETTINGS);
            case "app_search":
                return component("com.heytap.quicksearchbox", "com.heytap.quicksearchbox.ui.activity.AppCategoryActivity");
            case "translate":
                return component("com.coloros.translate", "com.coloros.translate.ui.MainActivity");
            case "game_center":
                return component("com.oplus.games", "business.module.desktop.JumpSpaceActivity");
            case "camera":
                return component("com.oplus.camera", "com.oplus.camera.Camera");
            case "video_capture":
                return new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
            case "app":
                Intent app = context.getPackageManager().getLaunchIntentForPackage(value);
                if (app == null) throw new IllegalArgumentException("Package has no launcher activity: " + value);
                return app;
            case "intent":
                return Intent.parseUri(value, Intent.URI_INTENT_SCHEME);
            case "wechat_pay":
                return new Intent("com.tencent.mm.ui.ShortCutDispatchAction")
                        .setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                        .putExtra("LauncherUI.Shortcut.LaunchType", "launch_type_offline_wallet");
            case "wechat_scan":
                return new Intent("com.tencent.mm.ui.ShortCutDispatchAction")
                        .setClassName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
                        .putExtra("LauncherUI.Shortcut.LaunchType", "launch_type_scan_qrcode");
            case "alipay_pay":
                return alipay("20000056", "alipays://platformapi/startapp?appId=20000056");
            case "alipay_scan":
                return alipay("10000007", "alipays://platformapi/startapp?appId=10000007&sourceId=scan3dtouch");
            case "recorder":
                return new Intent("com.oplus.soundrecorder.LAUNCH_FROM_BRACKET_SPACE")
                        .setClassName("com.coloros.soundrecorder", "oplus.multimedia.soundrecorder.slidebar.TransparentActivity")
                        .putExtra("extra_enter_type", 3);
            case "ocr":
                return new Intent("oplus.intent.action.DIRECT_SIDEBAR_SERVICE")
                        .putExtra("extra_entrance_function", "full_screen_ocr")
                        .putExtra("triggered_app", "com.coloros.smartsidebar");
            default:
                return null;
        }
    }

    private boolean isScreenOff(Context context) {
        try {
            PowerManager powerManager = context.getSystemService(PowerManager.class);
            return powerManager != null && !powerManager.isInteractive();
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Unable to read interactive state", error);
            return false;
        }
    }

    private boolean isDeviceSecure(Context context) {
        try {
            KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
            return keyguard == null || keyguard.isDeviceSecure();
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Unable to verify device credentials; treating device as secure", error);
            return true;
        }
    }

    private void wakeDevice(Context context) throws ReflectiveOperationException {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        if (powerManager == null) throw new IllegalStateException("PowerManager is unavailable");
        Method method = WakeUpApiCompat.findWakeUpMethod(powerManager.getClass());
        Object[] arguments = WakeUpApiCompat.arguments(
                method, SystemClock.uptimeMillis(), 4, "TriKey", 0);
        method.setAccessible(true);
        method.invoke(powerManager, arguments);
        log(Log.INFO, TAG, "Woke display using PowerManager.wakeUp/"
                + method.getParameterTypes().length);
    }

    private void startActivity(Context context, Intent intent, boolean dismissIfInsecure)
            throws ReflectiveOperationException {
        if (!dismissIfInsecure || !WakeLaunchPolicy.shouldDismissKeyguard(isDeviceSecure(context))) {
            context.startActivity(intent);
            return;
        }
        ActivityOptions options = ActivityOptions.makeBasic();
        try {
            Method dismiss = ActivityOptions.class.getDeclaredMethod("setDismissKeyguardIfInsecure");
            dismiss.setAccessible(true);
            dismiss.invoke(options);
            context.startActivity(intent, options.toBundle());
        } catch (NoSuchMethodException unavailable) {
            log(Log.WARN, TAG, "Insecure keyguard dismissal option is unavailable; launching normally");
            context.startActivity(intent);
        }
    }

    private void queueAfterWake(Context context, Bundle config, String gesture,
                                boolean deviceSecure) {
        clearPendingWakeLaunch("replaced by a newer key action");
        PendingWakeLaunch queued = new PendingWakeLaunch(
                context, new Bundle(config), gesture, deviceSecure);
        pendingWakeLaunch = queued;
        queued.screenReadyTask = () -> {
            if (pendingWakeLaunch != queued) return;
            if (isScreenOff(queued.context)) {
                if (++queued.waitChecks < 60) {
                    handler.postDelayed(queued.screenReadyTask, 50L);
                } else {
                    clearPendingWakeLaunch("display did not finish waking");
                }
                return;
            }
            queued.screenReadyTask = () -> dispatchAfterWake(queued);
            handler.post(queued.screenReadyTask);
        };
        handler.post(queued.screenReadyTask);
    }

    private void dispatchAfterWake(PendingWakeLaunch queued) {
        if (pendingWakeLaunch != queued) return;
        pendingWakeLaunch = null;
        if (isScreenOff(queued.context)) {
            log(Log.INFO, TAG, "Screen-off action canceled before keyguard launch");
            return;
        }
        if (!queued.deviceSecure) {
            log(Log.INFO, TAG, "Dispatching screen-off action without device credentials");
            performAction(queued.context, queued.config, queued.gesture, true);
            return;
        }
        String type = queued.config.getString(queued.gesture + "Type", "none");
        String value = queued.config.getString(queued.gesture + "Value", "");
        try {
            Intent target = createTargetIntent(queued.context, type, value);
            if (target == null) return;
            if ("ocr".equals(type)) {
                target = resolveForegroundServiceIntent(queued.context, target);
            }
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            String handoffId = keyguardHandoffHookInstalled ? UUID.randomUUID().toString() : null;
            if (handoffId != null) {
                pendingKeyguardLaunch = new PendingKeyguardLaunch(
                        handoffId, queued.context, target, "ocr".equals(type),
                        SystemClock.elapsedRealtime());
            }
            ComponentName challengeComponent = new ComponentName(
                    BuildConfig.APPLICATION_ID,
                    KeyguardLaunchActivity.class.getName());
            Intent challenge = new Intent().setComponent(challengeComponent)
                    .putExtra(KeyguardLaunchActivity.EXTRA_TARGET_INTENT, target)
                    .putExtra(KeyguardLaunchActivity.EXTRA_TARGET_IS_SERVICE, "ocr".equals(type))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (handoffId != null) {
                challenge.putExtra(KeyguardLaunchActivity.EXTRA_SYSTEM_HANDOFF, true)
                        .putExtra(KeyguardLaunchActivity.EXTRA_HANDOFF_ID, handoffId);
            }
            queued.context.startActivity(challenge);
            log(Log.INFO, TAG, handoffId == null
                    ? "Started compatibility keyguard challenge for screen-off action: " + type
                    : "Queued screen-off action for the native keyguard exit transition: " + type);
        } catch (Throwable error) {
            PendingKeyguardLaunch pending = pendingKeyguardLaunch;
            if (pending != null) cancelPendingKeyguardLaunch(pending.id);
            log(Log.ERROR, TAG, "Unable to start keyguard challenge for " + queued.gesture, error);
        }
    }

    private boolean installKeyguardHandoffHook(ClassLoader classLoader) {
        for (String className : KEYGUARD_GOING_AWAY_CLASSES) {
            try {
                Class<?> target = Class.forName(className, false, classLoader);
                for (Method method : target.getDeclaredMethods()) {
                    if (!"keyguardGoingAway".equals(method.getName())
                            || method.getParameterCount() != 1
                            || method.getParameterTypes()[0] != int.class) continue;
                    method.setAccessible(true);
                    hook(method)
                            .setId("trikey:keyguard-going-away:" + method.toGenericString())
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                if (isKeyguardControlCaller()) dispatchPendingKeyguardLaunch();
                                return chain.proceed();
                            });
                    log(Log.INFO, TAG, "Installed native keyguard transition handoff: "
                            + method.toGenericString());
                    return true;
                }
            } catch (ClassNotFoundException unavailable) {
                log(Log.DEBUG, TAG, "Keyguard transition class unavailable: " + className);
            } catch (Throwable error) {
                log(Log.WARN, TAG, "Unable to hook keyguard transition in " + className, error);
            }
        }
        log(Log.WARN, TAG, "Native keyguard transition handoff unavailable; using activity callback");
        return false;
    }

    private boolean isKeyguardControlCaller() {
        if (Binder.getCallingUid() == Process.SYSTEM_UID) return true;
        Context context = systemContext;
        return context != null && context.checkCallingPermission(
                "android.permission.CONTROL_KEYGUARD") == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void ensureKeyguardHandoffCancelReceiver(Context context) {
        if (keyguardHandoffReceiverRegistered) return;
        try {
            IntentFilter filter = new IntentFilter(KeyguardLaunchActivity.ACTION_CANCEL_HANDOFF);
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(keyguardHandoffCancelReceiver, filter,
                        Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(keyguardHandoffCancelReceiver, filter);
            }
            keyguardHandoffReceiverRegistered = true;
        } catch (Throwable error) {
            log(Log.WARN, TAG, "Unable to register keyguard handoff cancellation receiver", error);
        }
    }

    private void dispatchPendingKeyguardLaunch() {
        PendingKeyguardLaunch pending = pendingKeyguardLaunch;
        if (pending == null) return;
        synchronized (this) {
            if (pendingKeyguardLaunch != pending) return;
            pendingKeyguardLaunch = null;
        }
        if (SystemClock.elapsedRealtime() - pending.createdAt > 120_000L) {
            log(Log.INFO, TAG, "Discarded expired screen-off keyguard handoff");
            return;
        }
        try {
            if (pending.isService) {
                pending.context.startForegroundService(pending.target);
            } else {
                pending.context.startActivity(pending.target);
            }
            log(Log.INFO, TAG, "Started screen-off target as keyguard exit began");
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Unable to start screen-off target in keyguard transition", error);
        }
    }

    private void cancelPendingKeyguardLaunch(String id) {
        if (id == null) return;
        synchronized (this) {
            if (pendingKeyguardLaunch == null || !id.equals(pendingKeyguardLaunch.id)) return;
            pendingKeyguardLaunch = null;
        }
        log(Log.INFO, TAG, "Canceled pending screen-off keyguard handoff");
    }

    private void clearPendingWakeLaunch(String reason) {
        if (pendingWakeLaunch == null) return;
        PendingWakeLaunch queued = pendingWakeLaunch;
        pendingWakeLaunch = null;
        handler.removeCallbacks(queued.screenReadyTask);
        log(Log.INFO, TAG, "Cleared pending wake launch: " + reason);
    }

    private void clearScreenOffGestureState() {
        pendingScreenOffGesture = false;
        if (clearScreenOffGestureTask != null) {
            handler.removeCallbacks(clearScreenOffGestureTask);
            clearScreenOffGestureTask = null;
        }
    }

    private static final class PendingWakeLaunch {
        final Context context;
        final Bundle config;
        final String gesture;
        final boolean deviceSecure;
        Runnable screenReadyTask;
        int waitChecks;

        PendingWakeLaunch(Context context, Bundle config, String gesture, boolean deviceSecure) {
            this.context = context;
            this.config = config;
            this.gesture = gesture;
            this.deviceSecure = deviceSecure;
        }
    }

    private static final class PendingKeyguardLaunch {
        final String id;
        final Context context;
        final Intent target;
        final boolean isService;
        final long createdAt;

        PendingKeyguardLaunch(String id, Context context, Intent target, boolean isService,
                              long createdAt) {
            this.id = id;
            this.context = context;
            this.target = target;
            this.isService = isService;
            this.createdAt = createdAt;
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
        context.startForegroundService(resolveForegroundServiceIntent(context, intent));
    }

    private Intent resolveForegroundServiceIntent(Context context, Intent intent) {
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
        return intent;
    }

    @SuppressLint("WrongConstant")
    private static void statusBar(Context context, String methodName) throws ReflectiveOperationException {
        Object service = context.getSystemService("statusbar");
        Method method = service.getClass().getMethod(methodName);
        method.setAccessible(true);
        method.invoke(service);
    }

    private void injectKey(int keyCode) throws ReflectiveOperationException {
        Class<?> inputManager;
        Object manager;
        Method inject;
        try {
            inputManager = Class.forName("android.hardware.input.InputManagerGlobal");
            manager = inputManager.getMethod("getInstance").invoke(null);
            inject = inputManager.getMethod("injectInputEvent", InputEvent.class, int.class);
        } catch (ClassNotFoundException | NoSuchMethodException unavailable) {
            inputManager = Class.forName("android.hardware.input.InputManager");
            manager = inputManager.getMethod("getInstance").invoke(null);
            inject = inputManager.getMethod("injectInputEvent", InputEvent.class, int.class);
        }
        long now = SystemClock.uptimeMillis();
        int flags = KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0,
                0, -1, 0, flags, InputDevice.SOURCE_KEYBOARD);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0,
                0, -1, 0, flags, InputDevice.SOURCE_KEYBOARD);
        boolean downAccepted = Boolean.TRUE.equals(inject.invoke(manager, down, 0));
        boolean upAccepted = Boolean.TRUE.equals(inject.invoke(manager, up, 0));
        if (!downAccepted || !upAccepted) {
            throw new IllegalStateException("Input event injection rejected: keyCode=" + keyCode
                    + ", down=" + downAccepted + ", up=" + upAccepted);
        }
        log(Log.INFO, TAG, "Injected key event: keyCode=" + keyCode + ", down/up accepted");
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
