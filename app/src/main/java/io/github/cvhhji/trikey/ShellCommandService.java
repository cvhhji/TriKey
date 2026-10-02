package io.github.cvhhji.trikey;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public final class ShellCommandService extends Service {
    private static final String TAG = "TriKey";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String command = intent == null
                ? null : intent.getStringExtra(ShellCommandReceiver.EXTRA_COMMAND);
        boolean accepted = ShellCommandRunner.submit(command, result -> {
            ShellCommandReceiver.logResult(result);
            stopSelfResult(startId);
        });
        if (!accepted) {
            Log.w(TAG, "Authenticated Root command rejected because it is invalid or another command is running");
            stopSelfResult(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
