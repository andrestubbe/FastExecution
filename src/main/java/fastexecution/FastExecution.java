package fastexecution;

/**
 * FastExecution — Named, idempotent task scheduling and precision loop execution.
 *
 * <p>Static facade over {@link DelayedExecution} and {@link ContinuousExecution}.
 * All methods are thread-safe and idempotent by name.
 *
 * <p><b>Delay (debounce):</b>
 * <pre>{@code
 * FastExecution.delay("autosave", 1.0, () -> save());
 * FastExecution.delay("autosave", 1.0, () -> save()); // no-op — already pending
 * }</pre>
 *
 * <p><b>Precision loop at 120 Hz:</b>
 * <pre>{@code
 * FastExecution.loop("render", 120, () -> render());
 * // ... later:
 * FastExecution.stop("render");
 * }</pre>
 *
 * <p><b>VSync-locked loop (monitor refresh rate):</b>
 * <pre>{@code
 * FastExecution.loopVSync("vsync-render", () -> render());
 * }</pre>
 */
public final class FastExecution {

    private FastExecution() {}

    // ------------------------------------------------------------------ delay

    /**
     * Schedules a one-shot task to fire after {@code delaySeconds}.
     * No-op if a task with this {@code name} is already pending.
     *
     * @param name         unique task key
     * @param delaySeconds delay in seconds (fractional values supported, e.g. {@code 0.5})
     * @param task         task to execute once
     */
    public static void delay(String name, double delaySeconds, Runnable task) {
        DelayedExecution.delay(name, delaySeconds, task);
    }

    // ------------------------------------------------------------------ loop

    /**
     * Starts a continuous loop at the given frequency using the best available timer.
     *
     * <p>Automatically selects:
     * <ul>
     *   <li><b>FastDWM native</b> (WinMM {@code timeSetEvent}) — ~1 ms jitter when {@code fastdwm.dll} is present.</li>
     *   <li><b>Java fallback</b> ({@code ScheduledExecutorService}) — ~15 ms OS-default jitter otherwise.</li>
     * </ul>
     *
     * @param name unique loop key
     * @param hz   target frequency in Hertz (e.g. {@code 60}, {@code 120}, {@code 144})
     * @param task task to execute each tick
     */
    public static void loop(String name, int hz, Runnable task) {
        ContinuousExecution.loop(name, hz, task);
    }

    /**
     * Starts a VSync-locked loop that fires once per monitor refresh via
     * {@code FastDWM.waitForVSync()} on a dedicated daemon thread.
     *
     * @param name unique loop key
     * @param task task to execute each VSync pulse
     */
    public static void loopVSync(String name, Runnable task) {
        ContinuousExecution.loopVSync(name, task);
    }

    // ------------------------------------------------------------------ control

    /**
     * Stops and removes the task or loop identified by {@code name}.
     * Safe to call even if the name is not active (no-op).
     *
     * @param name task key to cancel
     */
    public static void stop(String name) {
        AbstractExecution.abort(name);
    }

    /**
     * Stops all active delays and loops.
     */
    public static void stopAll() {
        AbstractExecution.abortAll();
    }

    /**
     * Returns {@code true} if a task or loop with this {@code name} is currently active.
     *
     * @param name task key to query
     * @return {@code true} if active
     */
    public static boolean isActive(String name) {
        return AbstractExecution.exists(name);
    }
}
