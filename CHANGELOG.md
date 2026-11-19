# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-11-19

Initial open-source release of `gpu4j` (`io.github.craton-co:gpu4j` / `gpu4j-core`, JPMS `module craton.gpu`) by [Craton Software Company](https://craton.com.ar).

### Added

- Multi-module Maven build: `io.github.craton-co:gpu4j` (parent POM) + `io.github.craton-co:gpu4j-core` (`module craton.gpu`).
- Execution and stream abstractions: `GpuExecutor`, `GpuStream` (thread-safe `ReentrantReadWriteLock` dispatch and `synchronize()`), `PreparedKernel`, `GpuDevice`.
- Captured CUDA graph lifecycle: `GpuGraph` (`AutoCloseable`, `Cleaner`-backed) with `beginCapture()`, `endCapture()`, `replay(GpuGraph)`, `beginReplay(GpuGraph)`, and `endReplay()`.
- Device memory and half-precision helpers: `GpuArray` (`allocateInt/Long/Float/Double/Half`, `wrap`, `wrapHalf`, `fromNativeHandle`, `copyFromHost`, `toHost`, `isResident`) and `Half` (IEEE-754 binary16).
- BLAS acceleration: `GpuBlas` (`gemm`, `gemmHalf`, `gemmReference`) and `Transpose`.
- Asynchronous futures: `GpuFuture` (`get`, timed `get`, `getNow`, `cancel`, single-claim zero-copy `getAsGpuArray()`, `thenApplyGpu(GpuFunction)`, named `thenApplyGpu`, `toCompletableFuture`).
- Kernel annotations and SAM interfaces: `@EnableGpuAsync`, `@GpuKernel`, `@GpuExclude`, `GridShape`, `AdmissionHint`, `GpuCallable`, `GpuRunnable`, `GpuFunction`.
- Structured exception hierarchy: `GpuException`, `GpuCompileException`, `GpuLaunchException`, `GpuOutOfMemoryException`.
- Public test and simulation seam `craton.gpu.testing` (`GpuTestBridge` + `GpuBackendSpi`), keeping `craton.gpu.internal` encapsulated.
- Local Docker CI scripts (`scripts/local-ci.ps1`, `scripts/local-ci.sh`), GitHub Actions CI/Release workflows, bundled `META-INF/LICENSE` & `META-INF/NOTICE`, GPG signing, and Sonatype Central Portal deployment.

[Unreleased]: https://github.com/craton-co/gpu4j/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/craton-co/gpu4j/releases/tag/v0.1.0
