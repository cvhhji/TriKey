package io.github.cvhhji.trikey.hook;

final class KeyGestureDetector<T> {
    interface Scheduler {
        void postDelayed(Runnable task, long delayMs);

        void removeCallbacks(Runnable task);
    }

    interface Listener<T> {
        void onGesture(String gesture, T snapshot);
    }

    private final Scheduler scheduler;
    private final Listener<T> listener;
    private long downAt;
    private long pendingSingleAt;
    private boolean secondPress;
    private boolean longFired;
    private Runnable longTask;
    private Runnable singleTask;

    KeyGestureDetector(Scheduler scheduler, Listener<T> listener) {
        this.scheduler = scheduler;
        this.listener = listener;
    }

    synchronized void onDown(long now, int doubleMs, int longMs, T snapshot) {
        secondPress = pendingSingleAt != 0 && now - pendingSingleAt <= doubleMs;
        if (secondPress && singleTask != null) {
            scheduler.removeCallbacks(singleTask);
            singleTask = null;
        }

        downAt = now;
        longFired = false;
        if (longTask != null) scheduler.removeCallbacks(longTask);
        longTask = () -> fireLong(snapshot);
        scheduler.postDelayed(longTask, longMs);
    }

    synchronized void onUp(long now, long heldMs, int doubleMs, int longMs, T snapshot) {
        if (longTask != null) scheduler.removeCallbacks(longTask);
        longTask = null;
        if (longFired) return;

        if (heldMs >= longMs) {
            clearPendingSingle();
            longFired = true;
            listener.onGesture("long", snapshot);
            return;
        }

        if (secondPress) {
            clearPendingSingle();
            listener.onGesture("double", snapshot);
            return;
        }

        pendingSingleAt = now;
        long token = pendingSingleAt;
        singleTask = () -> fireSingle(token, snapshot);
        scheduler.postDelayed(singleTask, doubleMs);
    }

    synchronized void cancel() {
        if (longTask != null) scheduler.removeCallbacks(longTask);
        if (singleTask != null) scheduler.removeCallbacks(singleTask);
        longTask = null;
        singleTask = null;
        pendingSingleAt = 0;
        secondPress = false;
        longFired = false;
    }

    private synchronized void fireLong(T snapshot) {
        longTask = null;
        clearPendingSingle();
        longFired = true;
        listener.onGesture("long", snapshot);
    }

    private synchronized void fireSingle(long token, T snapshot) {
        if (pendingSingleAt != token || secondPress) return;
        singleTask = null;
        pendingSingleAt = 0;
        listener.onGesture("single", snapshot);
    }

    private void clearPendingSingle() {
        if (singleTask != null) scheduler.removeCallbacks(singleTask);
        singleTask = null;
        pendingSingleAt = 0;
        secondPress = false;
    }
}
