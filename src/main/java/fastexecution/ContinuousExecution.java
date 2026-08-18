package fastexecution;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Named, idempotent configurable-Hz continuous loop engine.
 *
 * <p>Wraps either a native {@code FastDWM.createPeriodicTimer} callback (Windows Multimedia
 * Timer — ~1 ms jitter) or a {@link java.util.concurrent.ScheduledExecutorService} fallback
 * (~15 ms jitter with default OS timer resolution). The native path is preferred and
 * activated automatically when the FastDWM native library is available.
 *
 * <pre>{@code
 * ContinuousExecution exec = new ContinuousExecution();
 *
 * // Start a named 120 Hz loop
 * exec.loop("render", 120, () -> scene.tick());
 *
 * // Same name — idempotent, no duplicate loop
 * exec.loop("render", 120, () -> scene.tick());
 *
 * // Stop it
 * exec.abort("render");
 * }</pre>
 *
 * <p>VSync-locked loops run on a dedicated daemon thread that blocks on
 * {@code FastDWM.waitForVSync()} each frame. If FastDWM is unavailable the thread
 * falls back to a 60 Hz software loop.
 */
public class ContinuousExecution extends AbstractExecution {

    /** Whether the FastDWM native library loaded successfully. */
    private static final boolean DWM_AVAILABLE;

    /** Daemon threads for VSync-locked loops: name → thread. */
    private final Map<String, Thread> vsyncThreads = new HashMap<>();

    static {
        boolean available = false;
        try {
            Class.forName("fastdwm.FastDWM");
            fastdwm.FastDWM.beginTimerPeriod(1); // raise Windows timer resolution to 1 ms
            available = true;
        } catch (ClassNotFoundException | UnsatisfiedLinkError | NoClassDefFoundError ignored) {
            // FastDWM not on classpath or DLL not found — use ScheduledExecutorService fallback
        }
        DWM_AVAILABLE = available;
    }

    // ------------------------------------------------------------------ loop

    /**
     * Starts a named continuous loop at {@code hz} Hz (integer precision).
     *
     * <p>If a loop with {@code name} is already running this call is a no-op.
     * Uses {@code FastDWM.createPeriodicTimer} when available, otherwise falls back
     * to {@link java.util.concurrent.ScheduledExecutorService}.
     *
     * @param name task key
     * @param hz   target frequency in Hz (e.g. 60, 120, 144, 240)
     * @param task the runnable to execute each tick
     */
    public void loop(String name, int hz, Runnable task) {
        loop(name, (double) hz, task);
    }

    /**
     * Starts a named continuous loop at {@code hz} Hz (double precision).
     *
     * <p>Supports fractional rates such as {@code 29.97}, {@code 59.94}, or {@code 23.976}.
     * Uses {@code FastDWM.createPeriodicTimer} when available (1 ms resolution), otherwise
     * falls back to {@link java.util.concurrent.ScheduledExecutorService} with microsecond
     * period via {@link TimeUnit#MICROSECONDS}.
     *
     * @param name task key
     * @param hz   target frequency in Hz (fractional values supported)
     * @param task the runnable to execute each tick
     */
    public synchronized void loop(String name, double hz, Runnable task) {
        if (hz <= 0) throw new IllegalArgumentException("hz must be > 0, got: " + hz);
        if (exists(name)) return; // idempotent

        // Convert Hz → period with full double precision
        long periodUs = Math.max(1L, Math.round(1_000_000.0 / hz)); // microseconds
        int  periodMs = (int) Math.max(1L, Math.round(1_000.0   / hz)); // milliseconds for WinMM

        if (DWM_AVAILABLE) {
            try {
                int timerId = fastdwm.FastDWM.createPeriodicTimer(periodMs, task);
                register(name, timerId);
                return;
            } catch (UnsatisfiedLinkError | NoClassDefFoundError ignored) {
                // fall through to Java fallback
            }
        }

        // Java fallback — microsecond precision via ScheduledExecutorService
        var future = executor.scheduleAtFixedRate(task, 0, periodUs, TimeUnit.MICROSECONDS);
        register(name, future);
    }

    // ------------------------------------------------------------------ vsync loop

    /**
     * Starts a VSync-locked loop under {@code name} with a configurable software fallback Hz.
     *
     * <p>Runs on a dedicated MAX_PRIORITY daemon thread that blocks on
     * {@code FastDWM.waitForVSync()} each frame (monitor refresh rate).
     * If FastDWM is unavailable, falls back to a software loop at {@code fallbackHz}.
     *
     * @param name       task key
     * @param fallbackHz Hz to use when FastDWM VSync is unavailable (e.g. 60, 120)
     * @param task       the runnable to execute each frame
     */
    public synchronized void loopVSync(String name, int fallbackHz, Runnable task) {
        if (vsyncThreads.containsKey(name)) return; // idempotent

        long fallbackMs = Math.max(1L, Math.round(1_000.0 / fallbackHz));

        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (DWM_AVAILABLE) {
                    try {
                        fastdwm.FastDWM.waitForVSync();
                    } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                        sleepMs(fallbackMs);
                    }
                } else {
                    sleepMs(fallbackMs);
                }
                task.run();
            }
        }, "FastExecution-VSync-" + name);
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();

        vsyncThreads.put(name, t);
    }

    /**
     * Starts a VSync-locked loop with a default software fallback of 60 Hz.
     *
     * @param name task key
     * @param task the runnable to execute each frame
     * @see #loopVSync(String, int, Runnable)
     */
    public void loopVSync(String name, Runnable task) {
        loopVSync(name, 60, task);
    }

    // ------------------------------------------------------------------ stop

    /**
     * Stops a running loop or VSync loop by name.
     */
    @Override
    public synchronized void abort(String name) {
        super.abort(name);
        Thread vt = vsyncThreads.remove(name);
        if (vt != null) vt.interrupt();
    }

    /**
     * Stops all running loops.
     */
    @Override
    public synchronized void abortAll() {
        super.abortAll();
        vsyncThreads.values().forEach(Thread::interrupt);
        vsyncThreads.clear();
    }

    // ------------------------------------------------------------------ util

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns {@code true} if FastDWM native precision timers are active.
     */
    public static boolean isNativeAvailable() {
        return DWM_AVAILABLE;
    }
}
