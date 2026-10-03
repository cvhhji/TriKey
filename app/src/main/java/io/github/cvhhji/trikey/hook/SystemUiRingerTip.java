package io.github.cvhhji.trikey.hook;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

final class SystemUiRingerTip {
    private static final String TAG = "TriKey";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String SEEDLING_SERVICE_ACTION =
            "com.oplus.seedlingservice.action.SEEDLING_SERVICE";
    private static final String SEEDLING_EVENT = "ringModeEvent";
    private static final String SEEDLING_EVENT_KEY = "seedling_event";
    private static final String RINGER_MODE_KEY = "ringModeType";
    private static final int MSG_SEEDLING_EVENT = 1;

    private final Object lock = new Object();
    private Messenger serviceMessenger;
    private Messenger replyMessenger;
    private int pendingMode = -1;
    private boolean binding;
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (lock) {
                binding = false;
                bound = true;
                serviceMessenger = new Messenger(service);
                sendPendingEventLocked();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (lock) {
                serviceMessenger = null;
            }
        }
    };

    void show(Context context, int mode) {
        synchronized (lock) {
            pendingMode = mode;
            if (serviceMessenger != null) {
                sendPendingEventLocked();
                return;
            }
            if (binding || bound) return;
            bindLocked(context);
        }
    }

    private void bindLocked(Context context) {
        Intent intent = new Intent(SEEDLING_SERVICE_ACTION).setPackage(SYSTEM_UI_PACKAGE);
        try {
            replyMessenger = new Messenger(new Handler(Looper.getMainLooper()));
            binding = true;
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
            if (!bound) {
                binding = false;
                Log.w(TAG, "Unable to bind ColorOS Seedling service for ringer tip");
            }
        } catch (Throwable error) {
            binding = false;
            bound = false;
            Log.w(TAG, "Unable to bind ColorOS Seedling service for ringer tip", error);
        }
    }

    private void sendPendingEventLocked() {
        if (pendingMode < 0 || serviceMessenger == null) return;
        int mode = pendingMode;
        Bundle data = new Bundle();
        data.putString(SEEDLING_EVENT_KEY, SEEDLING_EVENT);
        data.putInt(RINGER_MODE_KEY, mode);
        Message message = Message.obtain();
        message.what = MSG_SEEDLING_EVENT;
        message.setData(data);
        message.replyTo = replyMessenger;
        try {
            serviceMessenger.send(message);
            pendingMode = -1;
            Log.i(TAG, "Sent ColorOS system ringer tip for mode " + mode);
        } catch (RemoteException error) {
            serviceMessenger = null;
            Log.w(TAG, "Unable to send ColorOS system ringer tip", error);
        }
    }
}
