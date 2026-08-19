# FastExecution API Reference Manual

`FastExecution` is the named, idempotent timing and task execution substrate for the FastJava ecosystem.

---

## Static Facade: `fastexecution.FastExecution`

### Delay Operations (One-Shot & Debounce)

- `public static void delay(String name, double delaySeconds, Runnable task)`  
  Schedules a named one-shot task. If a task under `name` is already pending, this call is a no-op (idempotent debounce).

### Loop Operations (Configurable Hz & VSync)

- `public static void loop(String name, int hz, Runnable task)`  
  Starts a named continuous loop at integer target Hz (e.g. 60, 120, 144, 240, 1000). Uses FastDWM WinMM timer (~1ms jitter) when available.

- `public static void loop(String name, double hz, Runnable task)`  
  Starts a named continuous loop with double-precision frequency (e.g. 29.97, 59.94).

- `public static void loopVSync(String name, Runnable task)`  
  Starts a VSync-locked frame loop on a `MAX_PRIORITY` daemon thread blocking on `FastDWM.waitForVSync()`. Defaults to 60Hz software fallback.

- `public static void loopVSync(String name, int fallbackHz, Runnable task)`  
  Starts a VSync-locked frame loop with configurable software fallback Hz.

### Lifecycle & Registry Control

- `public static void stop(String name)`  
  Cancels and unregisters any active delay, loop, or VSync task by name.

- `public static void stopAll()`  
  Cancels and unregisters all active execution tasks across the process.

- `public static boolean isActive(String name)`  
  Returns `true` if a delay or loop task with the given name is currently active.

- `public static boolean isNativeAvailable()`  
  Returns `true` if FastDWM native multimedia timers are loaded and raising system resolution to 1ms.
