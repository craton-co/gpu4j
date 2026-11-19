# Security Policy

## Supported Versions

Security fixes are provided for the latest release line on the default branch.
Pre-1.0 releases receive fixes on the latest minor version.

| Version | Supported |
| ------- | --------- |
| 0.1.x   | Yes       |
| < 0.1   | No        |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Send reports privately to **contact@craton.com.ar** (or via [GitHub Private Vulnerability Reporting](https://github.com/craton-co/gpu4j/security/advisories/new) if enabled on the repository). Include:

- A description of the issue and its impact
- Steps to reproduce or a proof of concept, if available
- Affected versions (Maven coordinates: `io.github.craton-co:gpu4j-core`)
- Your contact information for follow-up

We aim to acknowledge receipt within **3 business days** and will work with you on coordinated disclosure. Please allow reasonable time for investigation and a fix before public disclosure.

### What to expect

1. Acknowledgment of your report
2. Assessment and, when needed, a fix or mitigation plan
3. A release or advisory when a fix is available
4. Credit in the release notes (unless you prefer to remain anonymous)

## Sensitive maintenance topics

For embargoed or sensitive maintenance topics (for example, compromised release credentials), contact Craton Software Company at **contact@craton.com.ar** (<https://craton.com.ar>).

## Maven Central

Published artifacts use the coordinates `io.github.craton-co:gpu4j-core`. Verify checksums and PGP `.asc` signatures in production builds, and pin explicit versions while the project is pre-1.0.
