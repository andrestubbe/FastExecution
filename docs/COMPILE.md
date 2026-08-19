# FastExecution Compilation Guide

## Requirements

- **Java**: JDK 17+ (Java 21 recommended).
- **Maven**: 3.8+.
- **FastDWM & FastCore**: Included via JitPack or local repository.

---

## Build Steps

```bash
mvn clean install -DskipTests
```

Produces `FastExecution-0.1.0.jar` and installs it to the local Maven cache.
