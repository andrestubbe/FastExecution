# FastExecution Architecture & Engineering Philosophy

## Core Principles

1. **Named Idempotency**  
   All tasks are keyed by unique string names. Registering the same name twice avoids race conditions and duplicate timer spawns by silently ignoring the second invocation (ideal for keystroke debouncing).

2. **Native Sub-Millisecond Precision**  
   Standard `ScheduledExecutorService` is bound to the default Windows OS timer resolution (~15.6ms). FastExecution bridges with `FastDWM` to invoke `timeBeginPeriod(1)` and `timeSetEvent`, achieving hardware-level ~1ms precision without CPU spinning.

3. **Pure Java Fallback**  
   If FastDWM native DLLs are not present, FastExecution smoothly degrades to microsecond-scheduled daemon executors, remaining 100% portable.
