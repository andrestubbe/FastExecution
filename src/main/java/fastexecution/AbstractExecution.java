package fastexecution;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Shared infrastructure for named, idempotent task scheduling.
 *
 * <p>Maintains:
 * <ul>
 *   <li>A shared 2-thread daemon {@link ScheduledExecutorService} for Java-side timed tasks.</li>
 *   <li>A {@code Map<String, ScheduledFuture<?>>} for Java-scheduled tasks.</li>
 *   <li>A {@code Map<String, Integer>} for native FastDWM timer handles.</li>
 * </ul>
 *
 * <p>All map operations are synchronized. The maps are only accessed during
 * {@code delay()}, {@code loop()}, {@code stop()} — never inside the task hot-path.
 */
abstract class AbstractExecution {

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "FastExecution-Worker");
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            });

    /** Java ScheduledFuture registry (delays and Java-mode loops). */
    private static final Map<String, ScheduledFuture<?>> FUTURES = new HashMap<>();

    /** Native FastDWM periodic timer ID registry. */
    private static final Map<String, Integer> NATIVE_TIMERS = new HashMap<>();

    /** Daemon VSync thread registry. */
    private static final Map<String, Thread> VSYNC_THREADS = new HashMap<>();

    // ------------------------------------------------------------------ query

    static synchronized boolean exists(String name) {
        if (VSYNC_THREADS.containsKey(name)) {
            return VSYNC_THREADS.get(name).isAlive();
        }
        if (NATIVE_TIMERS.containsKey(name)) {
            return true; // native timers run until killed
        }
        ScheduledFuture<?> f = FUTURES.get(name);
        return f != null && !f.isDone() && !f.isCancelled();
    }

    // ------------------------------------------------------------------ registration

    static synchronized void registerFuture(String name, ScheduledFuture<?> future) {
        FUTURES.put(name, future);
    }

    static synchronized void registerNativeTimer(String name, int timerId) {
        NATIVE_TIMERS.put(name, timerId);
    }

    static synchronized void registerVSyncThread(String name, Thread thread) {
        VSYNC_THREADS.put(name, thread);
    }

    // ------------------------------------------------------------------ cancellation

    static synchronized void abort(String name) {
        // Cancel Java future
        ScheduledFuture<?> f = FUTURES.remove(name);
        if (f != null) f.cancel(false);

        // Kill native FastDWM timer
        Integer timerId = NATIVE_TIMERS.remove(name);
        if (timerId != null) {
            try {
                fastdwm.FastDWM.killTimer(timerId);
            } catch (UnsatisfiedLinkError ignored) {}
        }

        // Interrupt VSync thread
        Thread vsync = VSYNC_THREADS.remove(name);
        if (vsync != null) vsync.interrupt();
    }

    static synchronized void abortAll() {
        FUTURES.values().forEach(f -> f.cancel(false));
        FUTURES.clear();

        NATIVE_TIMERS.forEach((name, id) -> {
            try { fastdwm.FastDWM.killTimer(id); } catch (UnsatisfiedLinkError ignored) {}
        });
        NATIVE_TIMERS.clear();

        VSYNC_THREADS.values().forEach(Thread::interrupt);
        VSYNC_THREADS.clear();
    }

    // ------------------------------------------------------------------ accessor

    static ScheduledExecutorService executor() {
        return EXECUTOR;
    }
}
