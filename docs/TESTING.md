# Testing and coverage

## Automated checks

```sh
./gradlew :core:check :core:jacocoTestReport :app:assembleDebug :app:lintDebug
python3 tools/check_source.py
```

The core is a plain-Java module. Its tests run on the JVM, without production credentials, camera connections or a USB microphone. Local TLS fixtures bind loopback, create disposable test certificates and delete them afterwards. They never contact a real recorder.

The validation run on 30 October 2026 passed **42 JUnit test entries**, including suites with multiple protocol / failure cases. JaCoCo measured **90.7% line coverage and 73.6% branch coverage across the entire core module**, without class exclusions. CI fails below **85% lines or 70% branches**. Reports are in `core/build/reports/jacoco/test/` and uploaded by CI.

This percentage does **not** cover Android Views, the platform Keystore, LibVLC native code, USB hardware or actual camera outputs. Do not present it as whole-app or hardware coverage.

## Covered behaviours

- Sparse profiles, old-profile defaults, custom ports, malformed hosts / input and conflicting camera IDs.
- Discovery / routing for 4, 8, 16, 32, 64 and 96-channel recorder tables; explicit channel namespaces for live, native talk, lens and alarms.
- Credential escaping in stream URLs, composite channel 0, and UTC replay boundaries independent of the TV timezone.
- Group ordering, deduplication, removed-camera pruning and atomic malformed-data fallback.
- Credential-free certificate inspection, deliberate handshake rejection, exact-pin mismatch rejection, redirect refusal and response / path bounds.
- RSA/SHA256 nonce login, session / cookie routing, exact web alarm envelopes and unchanged one-based lens control.
- Native TLS / Digest talk framing, refused sessions, media framing / continuity and cleanup.
- Single alarm start attempt, uncertain acknowledgments, deadline including request time, cancellation / lost authorisation, stop retries and interruption cleanup.
- Player cleanup completion even when stop / release throws, so future starts cannot be permanently blocked by a failed release.
- Layout geometry, digital viewport bounds, five-second cycling, pause / user deferral, microphone signal checks, pattern derivation and permission-session expiry.

## Android and hardware validation

Android release / debug builds and lint are separate checks. Existing lint warnings include English-only UI, deprecated fullscreen APIs, programmatic-view accessibility and intentional persistent-write / trust-manager review points. Errors are not suppressed with a blanket baseline.

The alpha line has owner-confirmed physical two-way audio, optical directions, siren and light; see the compatibility matrix. Beta fresh onboarding found duplicate setup dialogs on keyboard return and a cleanup path that could leave playback preparing. The beta2 source fixes those paths and removes full-width black camera-label strips. Physical confirmation of those final UI fixes is pending until the owner completes the TV check.

## Before claiming a new compatible model

Record hardware revision, firmware, TV version and account role. Test live / recordings / listen / talk / optical zoom / siren / light separately. A successful handshake is not proof of audible speech or a physical output. Use unoccupied test areas and explicit starts for physical controls. Follow the public-binary checklist before distributing an APK.
