# FastExecution Development Roadmap

## Q3 2026

- [x] Named, idempotent one-shot debounce delays (`delay`)
- [x] Sub-millisecond continuous loop scheduling (`loop`)
- [x] VSync-locked hardware refresh pacing (`loopVSync`)
- [x] FastDWM native multimedia timer bridge

## Q4 2026

- [ ] Native thread priority boosting and thread name pinning in C++
- [ ] Direct CPU Core Affinity binding (`SetThreadAffinityMask`) for low-jitter animation threads
- [ ] Cross-platform high-resolution timing substrate for Linux (`timerfd`) and macOS (`dispatch_source`)
