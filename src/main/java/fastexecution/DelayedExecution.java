package fastexecution;

import java.util.concurrent.TimeUnit;

/**
 * Named, idempotent one-shot task scheduler.
 *
 * <p>Calling {@link #delay(String, double, Runnable)} schedules a task under a string key.
 * If a task with that key is already pending, the second call is silently ignored — the
 * existing task is not rescheduled and not cancelled. This makes {@code DelayedExecution}
 * the correct primitive for debounce patterns:
 *
 * <pre>{@code
 * // Only the first call takes effect — ideal for auto-save debouncing:
 * DelayedExecution.delay("autosave", 2.0, this::save);
 * DelayedExecution.delay("autosave", 2.0, this::save); // no-op
 * }</pre>
 *
 * <p>When the delay fires, the task name is removed from the registry before the
 * {@link Runnable} is invoked, so re-scheduling inside the callback is safe.
 */
public class DelayedExecution extends AbstractExecution {

    /**
     * Schedules {@code task} to run after {@code delaySeconds} seconds under {@code name}.
     *
     * <p>If a task with {@code name} is already pending this call is a no-op.
     * The task is automatically unregistered before it runs.
     *
     * @param name         unique task key
     * @param delaySeconds delay in seconds (fractional values supported)
     * @param task         the runnable to execute
     */
    public synchronized void delay(String name, double delaySeconds, Runnable task) {
        if (exists(name)) return; // idempotent — already pending

        long delayMs = Math.max(1L, Math.round(delaySeconds * 1000.0));

        var future = executor.schedule(() -> {
            unregister(name);
            task.run();
        }, delayMs, TimeUnit.MILLISECONDS);

        register(name, future);
    }

    /**
     * Returns {@code true} if a task with {@code name} is currently pending.
     */
    public boolean isPending(String name) {
        return exists(name);
    }
}
