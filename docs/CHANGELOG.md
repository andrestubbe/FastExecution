# FastExecution Version Changelog

## [0.1.0] — 2026-08-18

### Added
- **Core Scheduler Infrastructure**: `AbstractExecution` with thread-safe named task registry.
- **Idempotent Debounce Timer**: `DelayedExecution.delay(name, seconds, task)`.
- **Configurable Hz Loop**: `ContinuousExecution.loop(name, hz, task)` with integer and double-precision overloads.
- **VSync-Locked Heartbeat**: `ContinuousExecution.loopVSync(name, fallbackHz, task)` blocking on native DWM refresh sync.
- **FastDWM Native Timer Integration**: Windows Multimedia Timer resolution raised to 1ms.
- **Static Facade**: `FastExecution` providing top-level convenience methods.
