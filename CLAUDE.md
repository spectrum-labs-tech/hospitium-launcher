# hospitium-launcher: notes for AI assistants

A Google TV / Android TV home screen (Kotlin, Compose for TV) that renders guest content from a
control plane speaking `hospitium-protocol` v1. Read `README.md`. `CONTRIBUTING.md` applies to you too.

## Ground rules

- **This repo is public.** Never reference private repositories, deployments, hostnames,
  infrastructure, customers, properties or secrets paths, and never name the operator's other
  products. Examples use `.example` domains. No default server address is committed.
- **CI uses GitHub-hosted runners only.** A public repo must never reach a self-hosted runner.
- **The launcher only renders.** No TV management, no ADB, no shell, no smart-home control. Making it
  the home screen is the agent's job.
- **The protocol is the contract.** `protocol/Models.kt` mirrors `hospitium-protocol` `v1/launcher.go`.
  Additive only: unknown fields are ignored, and new fields need defaults. A protocol change lands and
  is tagged there first.
- **Guest data** is a display name and stay times, each with an expiry enforced locally (the
  conservative clock, `domain/HomeView.kt`). Never log it, a token or a response body.
- **The device token is handed out once.** It's persisted (`commit`) before anything else. It's
  encrypted under a Keystore key, and backup stays off.
- **The logic is plain Kotlin** (`domain/`, `net/`, `protocol/`): unit-tested on the JVM with virtual
  time and a loopback HTTP server. Keep Android types out of it. UI code under `ui/` stays thin.
- **Every Kotlin file and build script starts with** `// SPDX-License-Identifier: Apache-2.0`.
- **Dependencies:** AndroidX, Compose for TV, kotlinx (serialization, coroutines) only. Justify any
  other library. HTTP is the platform's `HttpURLConnection`.
- **Label evidence honestly.** Emulator behavior isn't hardware behavior.

## Commands

All recurring commands live in `Taskfile.yaml` (`task --list`). `task check` must pass with no
device and no network beyond the Gradle dependency download.
