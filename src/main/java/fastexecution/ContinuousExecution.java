package fastexecution;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Named, idempotent configurable-Hz continuous loop engine.
 *
 * <p>Three heartbeat modes are available, selected automatically by capability:
 * <ol>
 *   <li><b>NATIVE_MM</b> — {@code FastDWM.beginTimerPeriod(1)} + {@code createPeriodicTimer}.
 *       Achieves ~1 ms jitter. Used when {@code fastdwm.dll} is loaded.</li>
 *   <li><b>JAVA</b> — {@link java.util.concurrent.ScheduledExecutorService#scheduleAtFixedRate}.
 *       Fallback when FastDWM is unavailable. ~15 ms OS-default jitter on Windows.</li>
 *   <li><b>NATIVE_VSYNC</b> — dedicated daemon thread blocking on {@code FastDWM.waitForVSync()}.
 *       Frame-perfect heartbeat at the monitor refresh rate. See {@link #loopVSync}.</li>
 * </ol>
 *
 * <p>Calling {@link #loop} or {@link #loopVSync} with a name that is already active is a
 * <b>no-op</b>. Call {@link AbstractExecution#abort(String)} to stop a running loop.
 */
class ContinuousExecution extends AbstractExecution {

    /** Whether FastDWM native timers are available on this runtime. */
    private static final boolean NATIVE_AVAILABLE = probeNative();

    private static boolean probeNative() {
        try {
            // Attempt a zero-overhead probe: beginTimerPeriod with 0 returns false but doesn't throw
            fastdwm.FastDWM.beginTimerPeriod(1);
            fastdwm.FastDWM.endTimerPeriod(1);
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ loop

    /**
     * Starts a named continuous loop at the specified frequency.
     *
     * <p>Uses {@code FastDWM.createPeriodicTimer} (WinMM {@code timeSetEvent}) when available
     * for ~1 ms precision; falls back to {@code ScheduledExecutorService} otherwise.
     *
     * @param name unique key for this loop
     * @param hz   target frequency in Hertz (e.g. 60, 120, 144)
     * @param task task to execute each tick
     */
    static void loop(String name, int hz, Runnable task) {
        if (exists(name)) return;

        int periodMs = Math.max(1, 1_000 / hz);

        if (NATIVE_AVAILABLE) {
            loopNative(name, periodMs, task);
        } else {
            loopJava(name, periodMs, task);
        }
    }

    /**
     * Starts a VSync-locked loop: fires once per monitor refresh via
     * {@code FastDWM.waitForVSync()} on a dedicated daemon thread.
     *
     * @param name unique key for this loop
     * @param task task to execute each VSync pulse
     */
    static void loopVSync(String name, Runnable task) {
        if (exists(name)) return;

        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    fastdwm.FastDWM.waitForVSync();
                    task.run();
                } catch (UnsatisfiedLinkError e) {
                    // FastDWM not available — degrade to ~60Hz Java sleep
                    sleepMs(16);
                    task.run();
                } catch (Exception ignored) {}
            }
        }, "FastExecution-VSync-" + name);
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();

        registerVSyncThread(name, thread);
    }

    // ------------------------------------------------------------------ private

    /** Native WinMM path: FastDWM.createPeriodicTimer → ~1 ms jitter. */
    private static void loopNative(String name, int periodMs, Runnable task) {
        try {
            fastdwm.FastDWM.beginTimerPeriod(1);
            int timerId = fastdwm.FastDWM.createPeriodicTimer(periodMs, () -> {
                try { task.run(); } catch (Exception ignored) {}
            });
            registerNativeTimer(name, timerId);
        } catch (UnsatisfiedLinkError e) {
            loopJava(name, periodMs, task);
        }
    }

    /** Java fallback: ScheduledExecutorService at fixed rate. */
    private static void loopJava(String name, int periodMs, Runnable task) {
        ScheduledFuture<?> future = executor().scheduleAtFixedRate(() -> {
            try { task.run(); } catch (Exception ignored) {}
        }, 0, periodMs, TimeUnit.MILLISECONDS);
        registerFuture(name, future);
    }

    private static void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
