package io.github.cvhhji.trikey;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ShellCommandRunner {
    public static final long COMMAND_TIMEOUT_MS = 7000L;

    private static final ThreadPoolExecutor WORKER = createWorker();

    public interface Completion {
        void onFinished(Result result);
    }

    public static final class Result {
        public final int exitCode;
        public final boolean timedOut;
        public final Throwable error;

        private Result(int exitCode, boolean timedOut, Throwable error) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.error = error;
        }
    }

    private ShellCommandRunner() {}

    public static boolean submit(String command, Completion completion) {
        if (!ShellCommandPolicy.isValidCommand(command)) return false;
        try {
            WORKER.execute(() -> {
                Result result = execute(command);
                if (completion != null) {
                    try {
                        completion.onFinished(result);
                    } catch (Throwable ignored) {
                    }
                }
            });
            return true;
        } catch (RejectedExecutionException error) {
            return false;
        }
    }

    private static ThreadPoolExecutor createWorker() {
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "TriKey-Shell");
            thread.setDaemon(true);
            return thread;
        };
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                0, 1, 30L, TimeUnit.SECONDS, new SynchronousQueue<>(), factory,
                new ThreadPoolExecutor.AbortPolicy());
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static Result execute(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder(ShellCommandPolicy.rootCommand(command))
                    .redirectErrorStream(true)
                    .redirectOutput(new File("/dev/null"))
                    .start();
            process.getOutputStream().close();
            if (!process.waitFor(COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(500L, TimeUnit.MILLISECONDS);
                return new Result(-1, true, null);
            }
            return new Result(process.exitValue(), false, null);
        } catch (InterruptedException error) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
            return new Result(-1, false, error);
        } catch (IOException | RuntimeException error) {
            if (process != null) process.destroyForcibly();
            return new Result(-1, false, error);
        }
    }
}
