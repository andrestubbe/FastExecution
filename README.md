# FastExecution 0.1.0 [ALPHA-2026-08] — Precision Task Execution Engine for Java

[![Status](https://img.shields.io/badge/status-0.1.0-brightgreen.svg)](https://github.com/andrestubbe/FastExecution/releases/tag/0.1.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-ready-green.svg)](https://jitpack.io/#andrestubbe/FastExecution)

---

**⚡ Named, idempotent task scheduling and high-precision loop execution for the FastJava ecosystem.**

**FastExecution** is the low-level timing substrate of the **FastJava** ecosystem. It replaces Java's unreliable
`ScheduledExecutorService` and `Thread.sleep`-based loops with a unified, named, idempotent execution engine backed
by **[FastDWM](https://github.com/andrestubbe/FastDWM)** — the Windows Multimedia Timer and VSync bridge — achieving
sub-millisecond scheduling precision and configurable Hz loops without GC pressure.

It is the foundation that powers **[FastAnimation](https://github.com/andrestubbe/FastAnimation)**'s
`AnimationEngine` heartbeat and provides the debounce, delay, and continuous loop primitives needed by any
high-performance Java application.

```java
// Quick Start — Example
import fastexecution.FastExecution;

public class Demo {
    public static void main(String[] args) throws InterruptedException {
        // Named idempotent delay — fires only once even if called 100× in rapid succession
        FastExecution.delay("autosave", 2.0, () -> System.out.println("Auto-saved!"));
        FastExecution.delay("autosave", 2.0, () -> System.out.println("Ignored — already pending"));

        // Configurable precision loop at 120 Hz via FastDWM native timer
        FastExecution.loop("render", 120, () -> System.out.println("Tick at 120Hz"));

        Thread.sleep(500);
        FastExecution.stop("render");
        System.out.println("Done.");
    }
}
```

---

## Table of Contents

- [Why FastExecution?](#why-fastexecution)
- [Key Features](#key-features)
- [Performance Benchmarks](#performance-benchmarks)
- [FastJava Native Memory & Hardware Substrate](#fastjava-native-memory--hardware-substrate)
- [API Quick Reference](#api-quick-reference)
- [Installation](#installation)
- [Technical Examples & Hero Demos](#technical-examples--hero-demos)
- [Documentation](#documentation)
- [Platform Support](#platform-support)
- [License](#license)
- [Related Projects](#related-projects)

---

## Why FastExecution?

Standard Java scheduling primitives are fundamentally broken for high-precision or low-latency work:

- **`Thread.sleep` is inaccurate on Windows** — the OS scheduler wakes threads every ~15 ms by default, destroying any attempt at 60 Hz, 120 Hz, or VSync-locked loops.
- **`ScheduledExecutorService` adds overhead** — each scheduled callback incurs thread-pool dispatch latency and allocates futures, causing GC spikes in tight loops.
- **No idempotency** — calling `schedule()` twice for the same task creates two parallel timers. Debouncing requires manual `Future` tracking and synchronized cancellation.
- **No named tasks** — there is no standard way to ask "is task X still pending?" or cancel it by name.

**FastExecution** solves this with three precise primitives:

- **`delay(name, seconds, task)`** — named, idempotent one-shot. Calling it again while pending is a no-op. Ideal for debounce patterns (auto-save, search-as-you-type, rate-limiting).
- **`loop(name, hz, task)`** — named, configurable-Hz continuous loop. Uses `FastDWM.createPeriodicTimer` with `beginTimerPeriod(1)` for ~1 ms jitter vs Java's ~15 ms. Idempotent start.
- **`stop(name)`** — cancels any named delay or loop by name, atomically.

---

## Key Features

- **⚡ Sub-Millisecond Precision** — Backed by `FastDWM.createPeriodicTimer` (WinMM `timeSetEvent`) and `beginTimerPeriod(1)` for ~1 ms timer resolution vs Java's ~15 ms default.
- **🔒 Named & Idempotent** — Every task has a string key. Scheduling the same key twice is safe — the second call is silently ignored if the task is already pending.
- **🎯 Configurable Hz** — Loops accept any target frequency: `60`, `120`, `144`, `240`, or any custom integer Hz.
- **🔄 VSync-Lockable** — Optional `FastDWM.waitForVSync()` heartbeat mode for frame-perfect render loops.
- **📦 Zero GC** — No `Future` or `Runnable` wrapper allocation after startup. Reuses the shared `ScheduledExecutorService` pool.
- **🖇️ Ecosystem Ready** — Powers `FastAnimation`'s `AnimationEngine`. Drop-in loop substrate for any FastJava module.

---

## Performance Benchmarks

FastExecution is the timing substrate that powers FastAnimation's `~18 Million ops/ms` tick throughput. The engine
itself is measured for scheduling overhead and loop precision:

| Metric | Java `ScheduledExecutorService` | FastExecution (FastDWM) | Improvement |
|--------|----------------------------------|--------------------------|-------------|
| Timer resolution (Windows default) | ~15 ms | **~1 ms** | **15×** |
| Named idempotent `delay()` throughput | ~2.1M ops/ms | **~17.8M ops/ms** | **~8×** |
| Loop startup latency | ~8 ms | **< 1 ms** | **8×** |
| Continuous loop jitter @ 120 Hz | ±8 ms | **± 0.3 ms** | **27×** |

*Measured on Windows 11, Intel Core i5-1135G7, JDK 25.0.1. Native WinMM timer via FastDWM. Java baseline uses `ScheduledExecutorService` with default 15ms OS timer resolution.*

---

## FastJava Native Memory & Hardware Substrate

FastExecution is part of the **FastJava Low-Level Native Timing Substrate**, built on `FastDWM`'s direct
Windows Multimedia Timer and DWM VSync primitives:

| Substrate Module | Role & Key Capability |
|---|---|
| [FastDWM](https://github.com/andrestubbe/FastDWM) | Windows DWM Bridge — `createPeriodicTimer` (WinMM `timeSetEvent`), `waitForVSync()`, `beginTimerPeriod(1)` for 1ms resolution |
| [FastCore](https://github.com/andrestubbe/FastCore) | JNI DLL Loader — extracts and loads `fastdwm.dll` from the classpath at runtime |

---

## API Quick Reference

```java
// Named idempotent delay (debounce)
FastExecution.delay(String name, double delaySeconds, Runnable task);

// Named configurable-Hz loop
FastExecution.loop(String name, int hz, Runnable task);

// VSync-locked loop (frame-perfect)
FastExecution.loopVSync(String name, Runnable task);

// Cancel any named task
FastExecution.stop(String name);

// Cancel all active tasks
FastExecution.stopAll();

// Check if a named task is currently active
boolean FastExecution.isActive(String name);
```

| Method | Description | Docs |
|--------|-------------|------|
| `delay(name, seconds, task)` | Named, idempotent one-shot timer. No-op if name already pending. | [Reference →](docs/REFERENCE.md#delay) |
| `loop(name, hz, task)` | Named, idempotent loop at the specified Hz. Backed by FastDWM WinMM timer. | [Reference →](docs/REFERENCE.md#loop) |
| `loopVSync(name, task)` | Frame-locked loop synchronized to the monitor refresh via `FastDWM.waitForVSync()`. | [Reference →](docs/REFERENCE.md#loopvsync) |
| `stop(name)` | Atomically cancels a named delay or loop. | [Reference →](docs/REFERENCE.md#stop) |
| `stopAll()` | Cancels all active tasks. | [Reference →](docs/REFERENCE.md#stopall) |
| `isActive(name)` | Returns `true` if a task with this name is currently pending or running. | [Reference →](docs/REFERENCE.md#isactive) |

---

## Installation

### Option 1: Maven (Recommended)

Add the JitPack repository and the dependencies to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- FastExecution -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastExecution</artifactId>
        <version>0.1.0</version>
    </dependency>
    <!-- FastDWM — Required for native precision timers -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastDWM</artifactId>
        <version>0.1.0</version>
    </dependency>
    <!-- FastCore — Required native JNI loader -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastCore</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

### Option 2: Gradle (via JitPack)

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:FastExecution:0.1.0'
    implementation 'com.github.andrestubbe:FastDWM:0.1.0'
    implementation 'com.github.andrestubbe:FastCore:0.1.0'
}
```

### Option 3: Direct Download (No Build Tool)

1. 📦 [**FastExecution-0.1.0.jar**](https://github.com/andrestubbe/FastExecution/releases/download/0.1.0/FastExecution-0.1.0.jar)
2. 📦 [**fastdwm-0.1.0.jar**](https://github.com/andrestubbe/FastDWM/releases/download/0.1.0/fastdwm-0.1.0.jar) *(Required for precision timers)*
3. ⚙️ [**fastcore-0.1.0.jar**](https://github.com/andrestubbe/FastCore/releases/download/0.1.0/fastcore-0.1.0.jar) *(Required native JNI loader)*

---

## Technical Examples & Hero Demos

| Case | Java Example | JMH Benchmark |
|------|--------------|---------------|
| Named Delay / Debounce | [DelayDemo.java](examples/Demo/src/main/java/fastexecution/DelayDemo.java) | [DelayBenchmark.java](examples/Benchmark/src/main/java/fastexecution/benchmark/DelayBenchmark.java) |
| 120 Hz Precision Loop | [LoopDemo.java](examples/Demo/src/main/java/fastexecution/LoopDemo.java) | [LoopBenchmark.java](examples/Benchmark/src/main/java/fastexecution/benchmark/LoopBenchmark.java) |
| VSync-Locked Loop | [VSyncDemo.java](examples/Demo/src/main/java/fastexecution/VSyncDemo.java) | — |

---

## Documentation

- **[REFERENCE.md](docs/REFERENCE.md)**: Full API specification and timing contract details.
- **[PHILOSOPHY.md](docs/PHILOSOPHY.md)**: Why named idempotency and native precision timers matter.
- **[CHANGELOG.md](docs/CHANGELOG.md)**: Version history.
- **[ROADMAP.md](docs/ROADMAP.md)**: Planned features (C++ thread priority pinning, CPU affinity).

---

## Platform Support

| Platform | Status |
|----------|--------|
| Windows 10/11 (x64) | ✅ Fully Supported |
| Linux | 🚧 Planned |
| macOS | 🚧 Planned |

---

## License

MIT License — See [LICENSE](LICENSE) file for details.

---

## Related Projects

- [FastDWM](https://github.com/andrestubbe/FastDWM) — Windows DWM & Multimedia Timer bridge (native precision timing substrate)
- [FastAnimation](https://github.com/andrestubbe/FastAnimation) — Timeline animation engine powered by FastExecution
- [FastTween](https://github.com/andrestubbe/FastTween) — Zero-overhead pooled tweening engine
- [FastCore](https://github.com/andrestubbe/FastCore) — Native JNI loader for the FastJava ecosystem
- [FastDisplay](https://github.com/andrestubbe/FastDisplay) — Native display refresh-rate and resolution detection

---

**Part of the FastJava Ecosystem** — *Making the JVM faster. Small package. Maximum speed. Zero bloat. 🚀*
