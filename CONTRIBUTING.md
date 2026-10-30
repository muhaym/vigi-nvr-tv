# Contributing

Keep changes small and add tests for transport, channel-routing, trust and lifecycle changes. Use synthetic recorder responses and disposable test certificates. Never submit footage, real accounts, private site details or vendor client binaries. Run `./gradlew :core:check :core:jacocoTestReport :app:assembleDebug :app:lintDebug` before proposing changes.

Any UI change must be operable with Up, Down, Left, Right, OK and Back. Menu may be a shortcut, never the only entry point. Keep focus visible, preserve the viewed camera / time where practical, and reserve actual camera controls for explicit actions.

Protocol support claims need the recorder model, firmware, account role and hardware result. A successful handshake is not proof of audible speech, optical movement or safe release. Report those separately.

No external services, telemetry, network scans or recorder-setting mutations should be added to onboarding without a deliberate product decision and clear user controls.

Keep protocol / data policies in `:core`, Android / media / permission integration in `:app`. Add synthetic fixtures for new firmware variants, preserve channel identity and add compatibility evidence via the issue template. Public binary releases require the native-library checklist in docs/RELEASE.md.
