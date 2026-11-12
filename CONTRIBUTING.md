# Contributing

Thanks for helping. Issues and pull requests are welcome.

## Before you open a pull request

```bash
task check   # Android lint (warnings are errors), unit tests, debug and release builds
```

CI runs the same checks. Tests must pass with no device and no network.

## Rules

- **Logic goes in plain Kotlin** (`domain/`, `net/`, `protocol/`) with unit tests. Compose code stays thin.
- **Guest data and tokens are never logged.**
- **Every Kotlin file and build script starts with** `// SPDX-License-Identifier: Apache-2.0`.
- **Dependencies:** AndroidX, Compose for TV and kotlinx only. Justify anything else in the pull request.
- Sign off your commits under the [Developer Certificate of Origin](https://developercertificate.org/) (`git commit -s`).

## License

By contributing you agree that your contributions are licensed under the Apache License 2.0.
