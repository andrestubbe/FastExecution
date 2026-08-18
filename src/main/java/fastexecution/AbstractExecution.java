package fastexecution;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared infrastructure for named, idempotent task execution.
 *
 * <p>Maintains a single 2-thread {@link ScheduledExecutorService} daemon pool and a
 * name → handle registry. Handles are either {@link ScheduledFuture} instances (Java-mode)
 * or {@link Integer} timer IDs returned by {@code FastDWM.createPeriodicTimer} (native-mode).
 *
 * <p>All registry operations are {@code synchronized} on {@code this} for thread safety.
 */
abstract class AbstractExecution {

    /** Shared daemon thread pool — 2 threads cover delay + loop concurrently. */
    protected final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "FastExecution-" + THREAD_INDEX.incrementAndGet());
        t.setDaemon(true);
        return t;
    });

    /** Maps task name → ScheduledFuture (Java) or Integer timer-id (FastDWM native). */
    protected final Map<String, Object> handles = new HashMap<>();

    private static final AtomicInteger THREAD_INDEX = new AtomicInteger(0);

    // ------------------------------------------------------------------ registry

    /**
     * Returns {@code true} if a task with the given name is currently registered.
     */
    public synchronized boolean exists(String name) {
        return handles.containsKey(name);
    }

    /**
     * Cancels and removes the task with the given name.
     * No-op if no such task exists.
     */
    public synchronized void abort(String name) {
        Object h = handles.remove(name);
        cancelHandle(h);
    }

    /**
     * Cancels and removes all currently registered tasks.
     */
    public synchronized void abortAll() {
        for (Object h : handles.values()) {
            cancelHandle(h);
        }
        handles.clear();
    }

    // ------------------------------------------------------------------ internals

    protected synchronized void register(String name, ScheduledFuture<?> future) {
        Object prev = handles.put(name, future);
        if (prev != null) cancelHandle(prev); // replace stale handle
    }

    protected synchronized void register(String name, int nativeTimerId) {
        Object prev = handles.put(name, nativeTimerId);
        if (prev != null) cancelHandle(prev);
    }

    protected synchronized void unregister(String name) {
        handles.remove(name);
    }

    private void cancelHandle(Object h) {
        if (h instanceof ScheduledFuture<?> f) {
            f.cancel(false);
        } else if (h instanceof Integer id) {
            try {
                fastdwm.FastDWM.killTimer(id);
            } catch (UnsatisfiedLinkError | NoClassDefFoundError ignored) {
                // FastDWM native not available — timer already expired or never started
            }
        }
    }
}
