package fastexecution;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Named, idempotent one-shot timer.
 *
 * <p>Calling {@link #delay} with a name that is already pending is a <b>no-op</b> — the running
 * task is neither cancelled nor rescheduled. This makes it safe to call from any hot path
 * without manual future tracking or synchronization in the caller.
 *
 * <p>Example — debounce auto-save to fire 1 second after the last keystroke:
 * <pre>{@code
 * textField.addKeyListener(e -> DelayedExecution.delay("autosave", 1.0, this::saveDocument));
 * }</pre>
 *
 * <p>Uses the shared {@link AbstractExecution#executor()} (2-thread daemon pool). The task
 * is removed from the registry automatically after it fires, so calling {@code delay()} again
 * after completion schedules a fresh timer.
 */
class DelayedExecution extends AbstractExecution {

    /**
     * Schedules {@code task} to run once after {@code delaySeconds}, keyed by {@code name}.
     * If a task with this name is already pending, the call is silently ignored.
     *
     * @param name         unique key for this task — used for idempotency and cancellation
     * @param delaySeconds delay before the task fires, in seconds (fractional values supported)
     * @param task         the task to execute once
     */
    static void delay(String name, double delaySeconds, Runnable task) {
        if (exists(name)) return;

        long delayMs = Math.max(0, Math.round(delaySeconds * 1_000.0));

        ScheduledFuture<?> future = executor().schedule(() -> {
            try {
                task.run();
            } finally {
                abort(name); // clean registry entry after firing
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        registerFuture(name, future);
    }
}
