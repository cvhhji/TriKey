package io.github.cvhhji.trikey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class ShellCommandReceiver extends BroadcastReceiver {
    public static final String ACTION_RUN = "io.github.cvhhji.trikey.action.RUN_SHELL_COMMAND";
    public static final String EXTRA_COMMAND = "io.github.cvhhji.trikey.extra.SHELL_COMMAND";
    private static final String TAG = "TriKey";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_RUN.equals(intent.getAction())) return;
        String command = intent.getStringExtra(EXTRA_COMMAND);
        PendingResult pendingResult = goAsync();
        boolean accepted = ShellCommandRunner.submit(command, result -> {
            logResult(result);
            pendingResult.finish();
        });
        if (!accepted) {
            Log.w(TAG, "Custom Root command rejected because it is invalid or another command is running");
            pendingResult.finish();
        }
    }

    static void logResult(ShellCommandRunner.Result result) {
        if (result.error != null) {
            Log.e(TAG, "Custom Root command could not start", result.error);
        } else if (result.timedOut) {
            Log.w(TAG, "Custom Root command exceeded its 7 second limit");
        } else if (result.exitCode == 0) {
            Log.i(TAG, "Custom Root command completed");
        } else {
            Log.w(TAG, "Custom Root command exited with code " + result.exitCode);
        }
    }
}
