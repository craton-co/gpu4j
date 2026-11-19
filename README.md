# gpu4j (Java)

[![CI](https://github.com/craton-co/gpu4j/actions/workflows/ci.yml/badge.svg)](https://github.com/craton-co/gpu4j/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.craton-co/gpu4j-core.svg)](https://central.sonatype.com/artifact/io.github.craton-co/gpu4j-core)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

Java GPU programming API and native bridge bindings for [CratonVM](https://github.com/craton-co/rust-jvm), developed by [Craton Software Company](https://craton.com.ar).

Organized as a multi-module Maven build (`gpu4j` parent + `gpu4j-core` artifact, JPMS module `craton.gpu`) decoupled from the Rust JVM workspace so the Java API can evolve and be published independently.

## Installation

Maven:

```xml
<dependency>
    <groupId>io.github.craton-co</groupId>
    <artifactId>gpu4j-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.github.craton-co:gpu4j-core:0.1.0")
}
```

Gradle (Groovy DSL):

```groovy
dependencies {
    implementation 'io.github.craton-co:gpu4j-core:0.1.0'
}
```

## Use

```java
import craton.gpu.GpuExecutor;
import craton.gpu.GpuFuture;
import craton.gpu.GpuException;

public class Example {
    public static void main(String[] args) throws InterruptedException {
        try (GpuExecutor exec = GpuExecutor.open()) {
            int[] a = new int[1 << 20];
            int[] b = new int[1 << 20];
            int[] out = new int[1 << 20];
            // ... fill a, b ...

            GpuFuture<Void> f = exec.submit(
                "com/example/EligibleVectorAdd",
                "vectorAdd",
                "([I[I[I)V",
                a, b, out);

            f.get();
            // out[] is now filled by the GPU kernel.
        } catch (GpuException e) {
            throw new RuntimeException(e);
        }
    }
}
```

## Runtime requirement

GPU execution requires running on **CratonVM** compiled with `--features gpu-driver`. On a stock JVM there is no native library bound to `craton.gpu.internal.Native`:
- Core execution entry points (`GpuExecutor.open()`) throw `java.lang.UnsatisfiedLinkError`.
- Device discovery (`GpuDevice.queryable()`, `GpuDevice.count()`, `GpuDevice.all()`) degrades gracefully to `false` / `0` / empty list.
- Pure-Java helpers (`Half`, `GpuBlas.gemmReference`, validation) and mock/simulation tests via `GpuTestBridge` + `GpuBackendSpi` run on any JDK 17+.

## Surface

Exported packages in `module craton.gpu`:
- `craton.gpu`:
  - `GpuExecutor` — `open()` / `submit()` / `launch()` / `dispatchNamed()` / `dispatchNamedHandle()` / `prepare()` / `beginCapture()` / `endCapture()` / `replay(GpuGraph)` / `beginReplay(GpuGraph)` / `endReplay()` / `awaitSubmission()` / `releaseSubmission()` / `newStream()` / `close()`, plus `submitV()` and `submitScale()` overloads.
  - `GpuGraph` — `AutoCloseable` captured CUDA graph (`handle()`, `executor()`, `nodeCount()`, `replay()`, `replayAsync()`, `beginReplay()`, `endReplay()`, `close()`).
  - `GpuStream` — thread-safe async FIFO queue (`handle()`, `dispatch()`, `synchronize()`, `close()`).
  - `GpuFuture<T>` — `get()` / timed `get(long, TimeUnit)` / `isDone()` / `getNow()` / `cancel()` / `isCancelled()` / `getAsGpuArray()` (single-claim zero-copy device buffer promotion) / `thenApplyGpu(GpuFunction)` / `thenApplyGpu(className, methodName, descriptor, extraArgs...)` / `toCompletableFuture()`.
  - `GpuArray<T>` — device-resident buffers (`allocateInt/Long/Float/Double/Half`, `wrap`, `wrapHalf`, `fromNativeHandle`, `copyFromHost`, `toHost`, `isResident`).
  - `GpuBlas`, `Transpose`, `Half`, `GpuDevice`, `PreparedKernel` — cuBLAS GEMM/fp16 helpers, IEEE-754 binary16 utilities, device discovery, and pre-validated kernel handles.
  - `GpuCallable<T>`, `GpuRunnable`, `GpuFunction<T,R>` — SAM task interfaces.
  - Annotations & enums: `@EnableGpuAsync`, `@GpuExclude`, `@GpuKernel`, `AdmissionHint`, `GridShape`.
  - Exceptions: `GpuException`, `GpuCompileException`, `GpuLaunchException`, `GpuOutOfMemoryException`.
- `craton.gpu.testing`:
  - `GpuTestBridge` & `GpuBackendSpi` — public test and simulation seam (without exposing `craton.gpu.internal`).

Encapsulated (`craton.gpu.internal`, module-private):
- `Native`, `NativeBridge`, `NativeImpl` — native bridge seam CratonVM binds at startup.
- `GpuExecutorImpl`, `GpuFutureImpl`, `GpuStreamImpl`, `Submissions`, `KernelArgs`, `GpuErrors`, `ImmediateFutures`, `StreamCleaner`.

## Relationship with CratonVM

CratonVM's workspace contains the `craton-gpu4j` crate (`cratonvm-gpu`), which compiles these Java sources during `cargo build` when pointed at `gpu4j-core/src/main/java` via `$CRATON_GPU_JAVA_SRC`.

## Build & Run Demo

```bash
mvn -B -ntp verify
java -cp gpu4j-core/target/gpu4j-core-0.1.0.jar examples/Demo.java
```

Requires JDK 17 or newer. Run local CI across JDK 17, 21, and 25 in Docker with:

```powershell
pwsh scripts/local-ci.ps1
```

## Limitations

The `0.1.0` surface is pre-1.0 and tied to CratonVM's native bridge (`--features gpu-driver`):
- `GpuFuture.cancel()` returns `false` once GPU work is in flight on CUDA hardware.
- Off-CratonVM execution requires installing a `GpuBackendSpi` via `GpuTestBridge` for unit testing or simulation.

## Versioning

This project follows [Semantic Versioning](https://semver.org/). It is currently pre-1.0, so **the public API may break between minor versions** until `1.0.0` is cut. Pin the exact version you depend on and read [CHANGELOG.md](CHANGELOG.md) before upgrading.

## License

Copyright 2025-2026 [Craton Software Company](https://craton.com.ar). Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).

## Contributing / Reporting bugs

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow, coding conventions, and how to run tests. Bug reports and feature requests should be filed at <https://github.com/craton-co/gpu4j/issues>.
