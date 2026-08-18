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
     * Starts a named continuous loop at {@code hz} Hz.
     *
     * <p>If a loop with {@code name} is already running this call is a no-op.
     * Uses {@code FastDWM.createPeriodicTimer} when available, otherwise falls back
     * to {@link java.util.concurrent.ScheduledExecutorService}.
     *
     * @param name task key
     * @param hz   target frequency in Hz (e.g. 60, 120, 144, 240)
     * @param task the runnable to execute each tick
     */
    public synchronized void loop(String name, int hz, Runnable task) {
        if (exists(name)) return; // idempotent

        int delayMs = Math.max(1, 1000 / hz);

        if (DWM_AVAILABLE) {
            try {
                int timerId = fastdwm.FastDWM.createPeriodicTimer(delayMs, task);
                register(name, timerId);
                return;
            } catch (UnsatisfiedLinkError | NoClassDefFoundError ignored) {
                // fall through to Java fallback
            }
        }

        // Java fallback
        var future = executor.scheduleAtFixedRate(task, 0, delayMs, TimeUnit.MILLISECONDS);
        register(name, future);
    }

    // ------------------------------------------------------------------ vsync loop

    /**
     * Starts a VSync-locked loop under {@code name}.
     *
     * <p>Runs on a dedicated MAX_PRIORITY daemon thread that blocks on
     * {@code FastDWM.waitForVSync()} each frame (monitor refresh rate).
     * Falls back to a ~60 Hz software loop if FastDWM is unavailable.
     *
     * @param name task key
     * @param task the runnable to execute each frame
     */
    public synchronized void loopVSync(String name, Runnable task) {
        if (vsyncThreads.containsKey(name)) return; // idempotent

        Thread t = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                if (DWM_AVAILABLE) {
                    try {
                        fastdwm.FastDWM.waitForVSync();
                    } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
                        sleepMs(16); // ~60 Hz fallback
                    }
                } else {
                    sleepMs(16);
                }
                task.run();
            }
        }, "FastExecution-VSync-" + name);
        t.setDaemon(true);
        t.setPriority(Thread.MAX_PRIORITY);
        t.start();

        vsyncThreads.put(name, t);
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
