# Requirements

This document describes the build-time and runtime requirements for
`gpu4j` (`io.github.craton-co:gpu4j-core`, Maven multi-module root `gpu4j`).

## Java version

- **Java 17 or newer** is required. The Maven build sets
  `maven.compiler.release` to `17`, so the produced bytecode targets the
  Java 17 class file version.
- The library source itself uses only plain Java with no language
  features beyond Java 17, but a JDK 17+ is needed to compile it and a
  JRE 17+ to run it.
- The CI matrix builds and tests against Java 17, Java 21, and Java 25.

## Runtime: the CratonVM host

`gpu4j` (`gpu4j-core`) is a thin Java API surface. It does **not** ship a CUDA
driver, and it does **not** bundle or load a native shared library
(`.so` / `.dll` / `.dylib`) of its own.

GPU execution requires running on the **CratonVM** runtime built with
its GPU native bridge enabled (`--features gpu-driver`). The library's
`native` methods (in `craton.gpu.internal`) are *not* backed by a
library packaged with this artifact — they are installed by CratonVM's
native-builtin registry at VM startup. The host VM is responsible for
the device-side execution, kernel lowering (via `jit-cuda`), and the
CUDA toolchain integration.

### Behavior on a stock JVM

On any standard JVM (Temurin, OpenJDK, etc.) **without** CratonVM, the
GPU native bridge is not present. Core execution calls such as
`GpuExecutor.open()` fail with `java.lang.UnsatisfiedLinkError`, optional
newer runtime entry points throw `UnsupportedOperationException`, and
device discovery (`GpuDevice.queryable()` / `count()`) returns `false` / `0`.
Pure-Java utilities (`Half`, `GpuBlas.gemmReference`) and unit tests using
`GpuTestBridge` work on any JDK 17+.

## CUDA toolkit expectations

The CUDA toolchain is a requirement of the **host CratonVM runtime**,
not of this Java artifact:

- CratonVM's GPU path expects a **CUDA 12.x** toolkit and a compatible
  NVIDIA driver to be available on the host where GPU work runs.
- A CUDA-capable NVIDIA GPU is required for actual device execution.
- Exact driver and compute-capability requirements are defined by the
  CratonVM build you run; consult that project for the authoritative
  version matrix.

Building or testing the *Java side* of this library does not require
CUDA or a GPU — those steps run on a stock JVM.

## Supported operating systems

The JVM-side build and tests of this library are exercised on:

- **Linux** (Ubuntu — `ubuntu-latest` in CI)
- **Windows** (`windows-latest` in CI)
- **macOS** (`macos-latest` in CI)

These cover compiling, packaging, and running the unit test suite of
the Java library. Actual GPU offload additionally requires a host
platform supported by CratonVM and its CUDA bridge; that platform
support is governed by the CratonVM runtime, not by this artifact.

## Summary

| Concern                | Requirement                                            |
| ---------------------- | ------------------------------------------------------ |
| Build / run (Java API) | JDK 17+ (CI: 17 and 21); Linux, Windows, or macOS      |
| GPU offload            | CratonVM runtime with the GPU native bridge enabled    |
| CUDA toolkit           | CUDA 12.x — provided/required by the CratonVM host     |
| GPU hardware           | CUDA-capable NVIDIA GPU, on the CratonVM host          |
| Stock JVM              | Library loads; GPU calls throw `UnsatisfiedLinkError`  |
