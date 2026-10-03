package io.github.cvhhji.trikey;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public final class ShellCommandReceiver extends BroadcastReceiver {
    public static final String ACTION_RUN = "io.github.cvhhji.trikey.action.RUN_SHELL_COMMAND";
    public static final String EXTRA_COMMAND = "io.github.cvhhji.trikey.extra.SHELL_COMMAND";
    private static final String TAG = "TriKey";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_RUN.equals(intent.getAction())) return;
        String command = intent.getStringExtra(EXTRA_COMMAND);
        PendingResult pendingResult = goAsync();
        boolean accepted = ShellCommandRunner.submit(command, result -> {
            logResult(result);
            showResult(context, result, pendingResult::finish);
        });
        if (!accepted) {
            Log.w(TAG, "Custom Root command rejected because it is invalid or another command is running");
            showMessage(context, ShellCommandPolicy.isValidCommand(command)
                    ? "上一条 Shell 命令仍在运行" : "Shell 命令为空或超出长度限制",
                    pendingResult::finish);
        }
    }

    static void showResult(Context context, ShellCommandRunner.Result result) {
        showResult(context, result, null);
    }

    static void showResult(Context context, ShellCommandRunner.Result result, Runnable afterShown) {
        String message;
        if (result.error != null) {
            message = "Shell 命令启动失败，请检查 Root 管理器授权";
        } else if (result.timedOut) {
            message = "Shell 命令超时，单次最长运行 7 秒";
        } else if (result.exitCode == 0) {
            message = "Shell 命令已执行";
        } else {
            message = "Shell 命令失败，请检查 Trikey 的 Root 授权";
        }
        showMessage(context, message, afterShown);
    }

    static void showMessage(Context context, String message) {
        showMessage(context, message, null);
    }

    static void showMessage(Context context, String message, Runnable afterShown) {
        Context appContext = context.getApplicationContext();
        MAIN_HANDLER.post(() -> {
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
            if (afterShown != null) afterShown.run();
        });
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
