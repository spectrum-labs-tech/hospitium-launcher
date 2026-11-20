# hospitium-launcher

A guest home screen for Google TV and Android TV, for short-term rentals and small hotels. Licensed Apache-2.0.

It shows a welcome with the guest's name, check-out time and reminder, Wi-Fi details with a QR code guests scan to join, a background photo, house rules, announcements from the host, and rows of the TV's streaming apps. It gets that content from a control plane that speaks [`hospitium-protocol`](https://github.com/spectrum-labs-tech/hospitium-protocol) (v1 launcher API), caches it, and keeps working offline.

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
- **One other host.** The only request that doesn't go to the control plane is an Unsplash
  background photo (protocol v0.8.0), loaded from `https://images.unsplash.com` as Unsplash's terms
  require. It's sent with no token and no cookies, kept in memory only, and shown with the
  photographer's credit. Any other host is refused before a request is made.
- **The device token** is encrypted with AES-GCM under a non-exportable Android Keystore key.
- **Backup is off** for the whole app. The token never leaves the TV, and everything else is a cache.
- Nothing is logged that contains a token, a server response or guest data.
- The User-Agent carries the app version and Android release only, with no device identifiers.

Report vulnerabilities as described in [SECURITY.md](SECURITY.md).

## Usage counts

The launcher counts, per hour, how often the home screen comes to the front, how often each card (Wi-Fi, house rules, amenities, each announcement) is opened, and which apps are started from it, and sends those counts with its config polls (`POST /v1/launcher/usage`, protocol v0.6.0). It records nothing else: no text, no times finer than the hour, nothing that identifies a guest. Each count is the hour's running total, so a resend after a failure never counts twice. Counts older than a week are dropped unsent, and unpairing deletes them. The home screen says so in one sentence.

## Activation

The launcher declares itself as a HOME candidate but never changes the TV's home screen itself. That's the management agent's job. See the agent's documentation for the activation strategies and their side effects on Google TV. Without the agent, the launcher still works as an ordinary app from the TV's app list.

Package: `tv.hospitium.launcher`, activity `.MainActivity`.

## Building

You need JDK 21 and the Android SDK command-line tools. Android Studio is optional.

```bash
task check     # Android lint, unit tests, debug and release APKs (what CI runs)
task install   # build, install and open on a TV or emulator connected over adb
task reset     # forget the server, pairing and cache on the connected device
task emulator  # start the Android TV emulator (see below for creating it once)
```

To create the emulator once (needs the Windows Hypervisor Platform feature on Windows, or KVM on Linux):

```bash
sdkmanager "emulator" "system-images;android-36;google-tv;x86_64"   # or: android sdk install ...
avdmanager create avd -n hospitium-tv -k "system-images;android-36;google-tv;x86_64" -d tv_1080p
```

The debug build accepts `http://10.0.2.2:<port>` as the server address, which reaches a control plane on your machine from the Android TV emulator.

### Releases

Maintainers cut releases with `task release BUMP=patch|minor|major`, never by tagging by hand. The task
tags the tip of `origin/main` after CI passed on that exact commit, and refuses a version whose minor
or patch would overflow `versionCode`. `DRY_RUN=1` runs every check without tagging. The tag
publishes an **unsigned** release APK and its SHA-256. Whoever deploys the launcher signs it with their own key: Android refuses to update an app that's signed by a different key, so the key has to belong to the operator, not this repository. `versionCode` is `major*1_000_000 + minor*1_000 + patch`.

## Status

- **Unit-tested** against the v1 protocol (38 JVM tests).
- **Emulator-verified** (Google TV system image, API 36, against a local control plane): setup, pairing, the home screen, D-pad navigation, launching an app and returning with Back, the offline cache with recovery, and a correct pairing countdown and guest view with the TV clock 28 hours behind.
- **Screen sizes:** layouts use the TV's ~960x540dp canvas, so 720p, 1080p and 4K look the same, only sharper. Card and tile sizes also follow the available width within bounds (checked on the emulator at 4:3 and ultrawide), and text follows the viewer's font-size setting (checked at 130%). Cards grow with their text; a card that still can't fit says so, and OK opens the full text.
- **Not yet tested on real Google TV hardware.**

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE). Bundled third-party licenses: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
