# Contributing to gpu4j

Thanks for your interest in contributing to `gpu4j`, maintained by [Craton Software Company](https://craton.com.ar)! This document describes how to build, test, and submit changes.

## Quick build

JDK 17 or newer is required.

```bash
mvn -q package
```

## Tests & Standalone Demo

```bash
mvn test
java -cp gpu4j-core/target/gpu4j-core-0.1.0.jar examples/Demo.java
```

## Code coverage & Javadoc verification

`mvn verify` runs the JaCoCo coverage gate (`check` execution in `gpu4j-core/pom.xml`). The build **fails** if instruction or line coverage drops below **80%** or branch coverage drops below **70%** for the module bundle. Run `mvn -Prelease verify` before opening a PR to verify source and Javadoc JAR generation as well.

## Code style

- Keep changes minimal and surgical. Avoid unrelated refactors in the same PR.
- Include complete Javadoc (`@param`, `@return`, `@throws`) on all public types and methods in `craton.gpu` and `craton.gpu.testing`.
- Include the Apache-2.0 SPDX and Craton Software Company copyright header on new `.java` files.

## Licensing

By submitting a contribution to this repository, you agree that your contribution is licensed under the [Apache License, Version 2.0](LICENSE) in accordance with Section 5 (*Submission of Contributions*).

## Branch & Pull request process

1. Work on a focused branch off `main` (e.g. `feature/my-change`, `fix/issue-123`).
2. Open your PR against `main` and link the relevant issue (`Fixes #123`).
3. Fill out the PR template (summary, test plan, checklist) and update `CHANGELOG.md` under `## [Unreleased]`.

## Releasing to Maven Central

Maintainers: see [docs/MAVEN_CENTRAL.md](docs/MAVEN_CENTRAL.md) for namespace setup, GPG signing, GitHub secrets, and tag-based releases.

## Code of conduct

This project adheres to the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md). Questions or conduct reports may be sent to **contact@craton.com.ar**.
