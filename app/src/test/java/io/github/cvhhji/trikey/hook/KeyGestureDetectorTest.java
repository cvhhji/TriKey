package io.github.cvhhji.trikey.hook;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class KeyGestureDetectorTest {
    private static final int DOUBLE_MS = 320;
    private static final int LONG_MS = 650;

    @Test
    public void singleFiresAfterDoubleClickWindow() {
        Fixture fixture = new Fixture();

        fixture.down(0);
        fixture.up(80);
        fixture.scheduler.advanceTo(399);
        assertEquals(List.of(), fixture.gestures);
        fixture.scheduler.advanceTo(400);

        assertEquals(List.of("single:first"), fixture.gestures);
    }

    @Test
    public void twoShortPressesFireOnlyDouble() {
        Fixture fixture = new Fixture();

        fixture.down(0);
        fixture.up(60);
        fixture.down(180);
        fixture.up(240);
        fixture.scheduler.advanceTo(1000);

        assertEquals(List.of("double:second"), fixture.gestures);
    }

    @Test
    public void longPressFiresOnlyLong() {
        Fixture fixture = new Fixture();

        fixture.down(0);
        fixture.scheduler.advanceTo(650);
        fixture.up(700);
        fixture.scheduler.advanceTo(1200);

        assertEquals(List.of("long:first"), fixture.gestures);
    }

    @Test
    public void clickThenLongPressDoesNotLeakSingle() {
        Fixture fixture = new Fixture();

        fixture.down(0);
        fixture.up(60);
        fixture.down(200);
        fixture.scheduler.advanceTo(850);
        fixture.up(900);
        fixture.scheduler.advanceTo(1400);

        assertEquals(List.of("long:second"), fixture.gestures);
    }

    @Test
    public void thirdPressStartsANewSingleGesture() {
        Fixture fixture = new Fixture();

        fixture.down(0);
        fixture.up(40);
        fixture.down(120);
        fixture.up(160);
        fixture.down(240);
        fixture.up(280);
        fixture.scheduler.advanceTo(600);

        assertEquals(List.of("double:second", "single:third"), fixture.gestures);
    }

    @Test
    public void cancelDropsPendingGestures() {
        Fixture fixture = new Fixture();

        fixture.down(0);
        fixture.up(40);
        fixture.detector.cancel();
        fixture.scheduler.advanceTo(1000);

        assertEquals(List.of(), fixture.gestures);
    }

    private static final class Fixture {
        final FakeScheduler scheduler = new FakeScheduler();
        final List<String> gestures = new ArrayList<>();
        final KeyGestureDetector<String> detector = new KeyGestureDetector<>(
                scheduler, (gesture, snapshot) -> gestures.add(gesture + ":" + snapshot));
        int pressCount;
        long downAt;

        void down(long now) {
            scheduler.advanceTo(now);
            downAt = now;
            pressCount++;
            detector.onDown(now, DOUBLE_MS, LONG_MS, snapshot());
        }

        void up(long now) {
            scheduler.advanceTo(now);
            detector.onUp(now, now - downAt, DOUBLE_MS, LONG_MS, snapshot());
        }

        private String snapshot() {
            if (pressCount == 1) return "first";
            if (pressCount == 2) return "second";
            return "third";
        }
    }

    private static final class FakeScheduler implements KeyGestureDetector.Scheduler {
        private final List<Task> tasks = new ArrayList<>();
        private long now;

        @Override
        public void postDelayed(Runnable task, long delayMs) {
            tasks.add(new Task(task, now + delayMs));
        }

        @Override
        public void removeCallbacks(Runnable task) {
            tasks.removeIf(candidate -> candidate.runnable == task);
        }

        void advanceTo(long target) {
            while (true) {
                Task next = tasks.stream()
                        .filter(task -> task.at <= target)
                        .min(Comparator.comparingLong(task -> task.at))
                        .orElse(null);
                if (next == null) break;
                tasks.remove(next);
                now = next.at;
                next.runnable.run();
            }
            now = target;
        }
    }

    private static final class Task {
        final Runnable runnable;
        final long at;

        Task(Runnable runnable, long at) {
            this.runnable = runnable;
            this.at = at;
        }
    }
}
