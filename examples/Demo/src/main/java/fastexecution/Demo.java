package fastexecution;

/**
 * FastExecution Demo — shows all three scheduling primitives.
 */
public class Demo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("FastExecution Demo");
        System.out.println("Native FastDWM available: " + FastExecution.isNativeAvailable());
        System.out.println();

        // 1. Named delay — fires once after 1 second
        System.out.println("[delay] Scheduling 'ping' in 1.0 s...");
        FastExecution.delay("ping", 1.0, () -> System.out.println("[delay] ping fired!"));

        // 2. Idempotency — second call is silently ignored
        FastExecution.delay("ping", 1.0, () -> System.out.println("[delay] ping duplicate — should NOT appear"));

        // 3. 120 Hz loop — ticks every 8.33 ms via FastDWM or ScheduledExecutorService
        var counter = new long[]{0};
        FastExecution.loop("tick", 120, () -> counter[0]++);

        Thread.sleep(1000);
        FastExecution.stop("tick");
        System.out.printf("[loop]  120 Hz loop ticked %d times in 1 s (target: 120)%n", counter[0]);

        // 4. Wait for the delay to fire
        Thread.sleep(500);
        System.out.println();
        System.out.println("Demo complete.");
    }
}
