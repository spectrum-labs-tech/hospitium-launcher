# hospitium-launcher

A guest home screen for Google TV and Android TV, for short-term rentals and small hotels. Licensed Apache-2.0.

It shows a welcome with the guest's name, check-out time and reminder, Wi-Fi details, house rules, announcements from the host, and rows of the TV's streaming apps. It gets that content from a control plane that speaks [`hospitium-protocol`](https://github.com/spectrum-labs-tech/hospitium-protocol) (v1 launcher API), caches it, and keeps working offline.

**Its only job is the guest screen.** Installing it, making it the home screen and clearing streaming sign-ins between guests are the job of [`hospitium-agent`](https://github.com/spectrum-labs-tech/hospitium-agent). The launcher never depends on the agent being there.

## How it works

1. **Setup.** On first start it asks for the control plane's address (HTTPS only). An operator can bake a default into a build with `-Phospitium.serverUrl=https://hospitium.example`.
2. **Pairing.** It shows a six-character code. An administrator enters the code in the control plane to bind this TV to a property and unit. The launcher polls until the code is claimed, then stores its device token. The server hands out that token **exactly once**, so it's written to disk before anything else happens.
3. **Home.** It fetches `GET /v1/launcher/config` with the device token, caches it, and refreshes on the server's suggested interval (clamped to 15 s–1 h). It also refreshes when the TV wakes up. When the server is unreachable it keeps showing the cache, marked as offline, and backs off exponentially (15 s up to 10 minutes).

### Guest data

The config carries a guest **display name and stay times only**, each with an expiry. The launcher enforces expiries locally, offline included:

- It keeps two clocks: the TV's own, and the TV's clock corrected by the server time on the last config.
- A guest's name disappears at the **earliest** moment either clock says it expired. A TV clock that's slow or reset can't keep a previous guest's name on screen.
- The check-out reminder uses the corrected clock, the best estimate of the real time.

### Revocation

If an administrator removes the TV, the config call starts returning 401. After **three consecutive** refusals, the launcher deletes its token and cache and shows a new pairing code. It waits for three because one bad response from a degraded server shouldn't strand a TV until someone visits.

## Security

- **HTTPS only**, system trust store only. Debug builds also allow plain HTTP to a development machine (`10.0.2.2`, `localhost`).
- Redirects are never followed, so a bearer token can't be forwarded to another host.
- **The device token** is encrypted with AES-GCM under a non-exportable Android Keystore key.
- **Backup is off** for the whole app. The token never leaves the TV, and everything else is a cache.
- Nothing is logged that contains a token, a server response or guest data.
- The User-Agent carries the app version and Android release only, with no device identifiers.

Report vulnerabilities as described in [SECURITY.md](SECURITY.md).

## Activation

The launcher declares itself as a HOME candidate but never changes the TV's home screen itself. That's the management agent's job. See the agent's documentation for the activation strategies and their side effects on Google TV. Without the agent, the launcher still works as an ordinary app from the TV's app list.

Package: `tech.spectrumlabs.hospitium.launcher`, activity `.MainActivity`.

## Building

You need JDK 21 and the Android SDK command-line tools. Android Studio is optional.

```bash
task check     # Android lint, unit tests, debug and release APKs (what CI runs)
task install   # build, install and open on a TV or emulator connected over adb
task reset     # forget the server, pairing and cache on the connected device
```

The debug build accepts `http://10.0.2.2:<port>` as the server address, which reaches a control plane on your machine from the Android TV emulator.

### Releases

Pushing a tag `vMAJOR.MINOR.PATCH` publishes an **unsigned** release APK and its SHA-256. Whoever deploys the launcher signs it with their own key: Android refuses to update an app that's signed by a different key, so the key has to belong to the operator, not this repository. `versionCode` is `major*1_000_000 + minor*1_000 + patch`.

## Status

Built and unit-tested against the v1 protocol. Testing on real Google TV hardware hasn't started yet.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
