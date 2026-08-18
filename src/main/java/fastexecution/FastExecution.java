package fastexecution;

/**
 * Static façade for the FastExecution scheduling engine.
 *
 * <p>Provides three named, idempotent scheduling primitives backed by
 * {@link DelayedExecution} and {@link ContinuousExecution}:
 *
 * <ul>
 *   <li>{@link #delay(String, double, Runnable)} — debounced one-shot timer</li>
 *   <li>{@link #loop(String, int, Runnable)} — configurable-Hz continuous loop</li>
 *   <li>{@link #loopVSync(String, Runnable)} — VSync-locked frame loop</li>
 * </ul>
 *
 * <p>All operations are thread-safe. Names serve as unique keys — scheduling the same
 * name twice is always a no-op for the second call.
 *
 * <pre>{@code
 * // Debounce: auto-save fires 2 s after the last keystroke
 * FastExecution.delay("autosave", 2.0, editor::save);
 *
 * // 120 Hz render loop via FastDWM native timer (1 ms precision)
 * FastExecution.loop("render", 120, scene::tick);
 *
 * // Frame-locked VSync loop
 * FastExecution.loopVSync("ui", renderer::paint);
 *
 * // Cancel by name
 * FastExecution.stop("render");
 *
 * // Cancel everything
 * FastExecution.stopAll();
 * }</pre>
 */
public final class FastExecution {

    private static final DelayedExecution    DELAYS = new DelayedExecution();
    private static final ContinuousExecution LOOPS  = new ContinuousExecution();

    private FastExecution() {}

    // ------------------------------------------------------------------ delay

    /**
     * Schedules a one-shot task after {@code delaySeconds} seconds under {@code name}.
     *
     * <p>Idempotent: if a delay task with {@code name} is already pending, this call
     * is silently ignored. The task is automatically unregistered before it fires.
     *
     * @param name         unique task key
     * @param delaySeconds delay in seconds (supports fractions)
     * @param task         the runnable to execute once
     */
    public static void delay(String name, double delaySeconds, Runnable task) {
        DELAYS.delay(name, delaySeconds, task);
    }

    // ------------------------------------------------------------------ loop

    /**
     * Starts a continuous loop at the specified Hz under {@code name} (integer precision).
     *
     * @param name task key
     * @param hz   target frequency (e.g. 60, 120, 144, 240)
     * @param task the runnable to execute each tick
     * @see #loop(String, double, Runnable)
     */
    public static void loop(String name, int hz, Runnable task) {
        LOOPS.loop(name, hz, task);
    }

    /**
     * Starts a continuous loop at the specified Hz under {@code name} (double precision).
     *
     * <p>Supports fractional rates such as {@code 29.97}, {@code 59.94}, or {@code 23.976}.
     * Uses {@code FastDWM.createPeriodicTimer} when available (1 ms resolution), otherwise
     * falls back to microsecond-precision {@link java.util.concurrent.ScheduledExecutorService}.
     * Idempotent: calling with an existing name is a no-op.
     *
     * @param name task key
     * @param hz   target frequency in Hz (fractional values supported)
     * @param task the runnable to execute each tick
     */
    public static void loop(String name, double hz, Runnable task) {
        LOOPS.loop(name, hz, task);
    }

    /**
     * Starts a VSync-locked loop under {@code name} with a default 60 Hz software fallback.
     *
     * @param name task key
     * @param task the runnable to execute each frame
     * @see #loopVSync(String, int, Runnable)
     */
    public static void loopVSync(String name, Runnable task) {
        LOOPS.loopVSync(name, task);
    }

    /**
     * Starts a VSync-locked loop under {@code name} with a configurable software fallback Hz.
     *
     * <p>Runs on a dedicated {@link Thread#MAX_PRIORITY} daemon thread that blocks on
     * {@code FastDWM.waitForVSync()} each frame. When FastDWM is unavailable, falls back
     * to a software loop at {@code fallbackHz}.
     *
     * @param name       task key
     * @param fallbackHz Hz to use when FastDWM VSync is unavailable (e.g. 60, 120)
     * @param task       the runnable to execute each frame
     */
    public static void loopVSync(String name, int fallbackHz, Runnable task) {
        LOOPS.loopVSync(name, fallbackHz, task);
    }

    // ------------------------------------------------------------------ stop

    /**
     * Cancels a delay, loop, or VSync loop by name.
     * No-op if no task with {@code name} exists.
     *
     * @param name task key to cancel
     */
    public static void stop(String name) {
        DELAYS.abort(name);
        LOOPS.abort(name);
    }

    /**
     * Cancels all active delays, loops, and VSync loops.
     */
    public static void stopAll() {
        DELAYS.abortAll();
        LOOPS.abortAll();
    }

    // ------------------------------------------------------------------ query

    /**
     * Returns {@code true} if a delay or loop task with {@code name} is currently active.
     *
     * @param name task key to query
     */
    public static boolean isActive(String name) {
        return DELAYS.exists(name) || LOOPS.exists(name);
    }

    /**
     * Returns {@code true} if FastDWM native precision timers are available on this system.
     * When {@code false}, the engine falls back to Java's {@link java.util.concurrent.ScheduledExecutorService}.
     */
    public static boolean isNativeAvailable() {
        return ContinuousExecution.isNativeAvailable();
    }
}
