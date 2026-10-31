# Changes

## 0.9.0-beta5

- Fix physical-TV black camera pictures on the tested MStar `m7632` platform with app-scoped composition / texture blending. Android screenshots alone did not expose this panel failure.
- Owner confirmed four-camera viewing with menus closed, full-screen return, page changes and five-second page cycling on 31 October 2026. No global TV graphics settings are changed.

## 0.9.0-beta4 — public-source preparation

- Name the independent project VIGI NVR TV and document supported / untested configurations.
- Separate Android application code from the plain-Java recorder / domain core.
- Centralise connection ports, validated stream URLs, UTC playback paths and camera group serialisation.
- Extend channel identities to 96 and test small / large recorder discovery and boundary routing.
- Bound camera discovery work and reject conflicting channel identities.
- Remove private installation migration hooks; new installs always use explicit setup.
- Add compatibility, testing, architecture and release documentation, enforced core coverage minimums and public issue templates.
- Allow screenshots / sharing of camera and control screens; keep private pattern and credential entry protected.
- Use software decoding for low-resolution multi-camera grids to avoid limited TV hardware decoder instances; preserve hardware decoding for full-screen and combined streams.
- Keep recorder setup single-instance across keyboard return, dismiss setup before rendering, complete player cleanup barriers after errors, and remove full-width black camera-label backgrounds.

## 0.9 alpha line

Grouped live viewing; playback scrubbing; USB conversation / push-to-talk; digital and optical zoom; private remote pattern; 1 / 4 / 8 / 16 layouts; 5 / 15 / 30 / 60-second camera / page cycling; manual camera siren / light controls with confirmation, stop timer and persistent unconfirmed-stop recovery.
