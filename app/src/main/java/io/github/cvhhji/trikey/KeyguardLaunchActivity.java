package io.github.cvhhji.trikey;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;

@SuppressLint("CustomSplashScreen")
public final class KeyguardLaunchActivity extends Activity {
    public static final String EXTRA_TARGET_INTENT = "io.github.cvhhji.trikey.extra.TARGET_INTENT";
    public static final String EXTRA_TARGET_IS_SERVICE = "io.github.cvhhji.trikey.extra.TARGET_IS_SERVICE";
    public static final String EXTRA_SYSTEM_HANDOFF = "io.github.cvhhji.trikey.extra.SYSTEM_HANDOFF";
    public static final String EXTRA_HANDOFF_ID = "io.github.cvhhji.trikey.extra.HANDOFF_ID";
    public static final String ACTION_HANDOFF_STARTED = "io.github.cvhhji.trikey.action.HANDOFF_STARTED";
    public static final String ACTION_CANCEL_HANDOFF = "io.github.cvhhji.trikey.action.CANCEL_HANDOFF";
    private static final String TAG = "TriKey";
    private static final long HANDOFF_ACK_TIMEOUT_MS = 120L;

    private boolean dismissalRequested;
    private boolean completed;
    private boolean authenticationSucceeded;
    private boolean systemHandoffAcknowledged;
    private boolean handoffReceiverRegistered;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable handoffFallback = () -> finishRequest(true,
            "System keyguard transition did not start target; using authenticated fallback");
    private final BroadcastReceiver handoffReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_HANDOFF_STARTED.equals(intent.getAction())) return;
            String expectedId = getIntent().getStringExtra(EXTRA_HANDOFF_ID);
            String receivedId = intent.getStringExtra(EXTRA_HANDOFF_ID);
            if (expectedId == null || !expectedId.equals(receivedId)) return;
            systemHandoffAcknowledged = true;
            Log.i(TAG, "System acknowledged screen-off target launch");
            if (authenticationSucceeded) {
                finishRequest(true, "System started target during keyguard exit");
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Log.i(TAG, "Keyguard launch activity created");
        registerHandoffReceiver();
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "Keyguard launch activity resumed");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus || dismissalRequested) return;
        dismissalRequested = true;
        Log.i(TAG, "Keyguard launch activity window focused; requesting dismissal");
        getWindow().getDecorView().post(this::requestKeyguardDismissal);
    }

    private void requestKeyguardDismissal() {
        if (isFinishing()) return;
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard == null) {
            finishRequest(false, "Keyguard service unavailable");
            return;
        }
        if (!keyguard.isKeyguardLocked() && !keyguard.isDeviceLocked()) {
            finishRequest(true, "Device was already authenticated");
            return;
        }
        Log.i(TAG, "Requesting keyguard dismissal from visible activity");
        keyguard.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
            @Override
            public void onDismissSucceeded() {
                runOnUiThread(() -> {
                    authenticationSucceeded = true;
                    if (!getIntent().getBooleanExtra(EXTRA_SYSTEM_HANDOFF, false)
                            || systemHandoffAcknowledged) {
                        finishRequest(true, "System authentication succeeded");
                    } else {
                        Log.i(TAG, "Waiting briefly for native target-launch acknowledgement");
                        mainHandler.postDelayed(handoffFallback, HANDOFF_ACK_TIMEOUT_MS);
                    }
                });
            }

            @Override
            public void onDismissCancelled() {
                runOnUiThread(() -> finishRequest(false, "System authentication canceled"));
            }

            @Override
            public void onDismissError() {
                runOnUiThread(() -> {
                    boolean authenticated = !keyguard.isKeyguardLocked()
                            && !keyguard.isDeviceLocked();
                    if (!authenticated) cancelSystemHandoff();
                    finishRequest(authenticated, authenticated
                            ? "System keyguard had already cleared"
                            : "System keyguard challenge could not be shown");
                });
            }
        });
    }

    private void finishRequest(boolean authenticated, String reason) {
        if (completed) return;
        mainHandler.removeCallbacks(handoffFallback);
        completed = true;
        Log.i(TAG, "Keyguard dismissal result: authenticated=" + authenticated + ", reason=" + reason);
        if (authenticated) {
            if (getIntent().getBooleanExtra(EXTRA_SYSTEM_HANDOFF, false)
                    && systemHandoffAcknowledged) {
                Log.i(TAG, "Target was handed off during the system keyguard exit transition");
            } else {
                try {
                    cancelSystemHandoff();
                    Intent target = targetIntent();
                    if (target == null) throw new IllegalArgumentException("Missing target action");
                    if (getIntent().getBooleanExtra(EXTRA_TARGET_IS_SERVICE, false)) {
                        startForegroundService(target);
                    } else {
                        startActivity(target);
                    }
                    Log.i(TAG, "Screen-off target dispatched after keyguard authentication");
                } catch (Throwable error) {
                    Log.e(TAG, "Unable to dispatch screen-off target after authentication", error);
                }
            }
        } else {
            cancelSystemHandoff();
        }
        Log.i(TAG, reason);
        finish();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerHandoffReceiver() {
        if (!getIntent().getBooleanExtra(EXTRA_SYSTEM_HANDOFF, false)) return;
        IntentFilter filter = new IntentFilter(ACTION_HANDOFF_STARTED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(handoffReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(handoffReceiver, filter);
        }
        handoffReceiverRegistered = true;
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(handoffFallback);
        if (handoffReceiverRegistered) {
            unregisterReceiver(handoffReceiver);
            handoffReceiverRegistered = false;
        }
        super.onDestroy();
    }

    private Intent targetIntent() {
        Intent source = getIntent();
        if (Build.VERSION.SDK_INT >= 33) {
            return source.getParcelableExtra(EXTRA_TARGET_INTENT, Intent.class);
        }
        return source.getParcelableExtra(EXTRA_TARGET_INTENT);
    }

    private void cancelSystemHandoff() {
        Intent source = getIntent();
        if (!source.getBooleanExtra(EXTRA_SYSTEM_HANDOFF, false)) return;
        String id = source.getStringExtra(EXTRA_HANDOFF_ID);
        if (id == null) return;
        Intent cancel = new Intent(ACTION_CANCEL_HANDOFF)
                .putExtra(EXTRA_HANDOFF_ID, id);
        sendBroadcast(cancel);
    }
}
