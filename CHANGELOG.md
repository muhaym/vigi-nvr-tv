# Changes

## 0.9.0-beta14

- Re-check the active lock method when Android resumes an older pattern screen or delivers a new launcher intent. Completed authenticator enrollment no longer returns to a stale pattern screen; no re-enrollment is needed.

## 0.9.0-beta13

- Add offline six-digit authenticator access with local QR enrollment and a private recovery code; no account service or hosted page is required.
- Keep existing patterns until enrollment is verified and saved, then remove the pattern fallback. Fresh installations start with authenticator setup.
- Reject consumed time windows, share escalating delays across code and recovery attempts, and preserve recovery during interrupted replacement setup.
- Protect enrollment / recovery material with Keystore encryption or keyed verification, screenshot blocking and background cleanup. Add standards-vector, recovery and QR round-trip tests.

## 0.9.0-beta12

- Extend PA to all cameras or a chosen speaker subset, with saved selections and camera-group shortcuts.
- Add Start PA / Stop all announcements with a five-minute limit, alongside silent connection checks and ten-second tests.
- Keep speaker-level results visible; any connection failure stops the selected group and unblocks microphone reads.
- Generalize fan-out to 1–96 distinct channel identities and test single, small and large synthetic selections. Hardware concurrency limits remain recorder-specific.

## 0.9.0-beta11

- Add an experimental, explicitly started ten-second USB microphone test to exactly two selected camera speakers through the NVR.
- Check both talk allocations without capturing or sending audio before the live test.
- Fan out one encoded microphone stream through independent bounded send queues; stop both on failure, cancellation or loss of foreground authorisation.
- Mute TV playback during the live test and restore it afterwards. No music or all-speaker mode is included in this prototype.

## 0.9.0-beta10

- Fix digital picture movement when a hardware decoder starts video without reporting fitted image dimensions. Use the displayed surface size until metadata is available.
- Keep pan bounds valid as layout metadata arrives or the view resizes; add regression cases for all directions and returning to 1×.

## 0.9.0-beta9

- Recover recorder identity and camera lists when an added camera has no combined-preview position, or a saved overview has duplicate / stale cells. Use individual feeds until the overview is reviewed.
- Reconcile camera discovery with existing positions and disable combined preview when the channel table changes; preserve known assignments for editing.
- Add Workspace → Refresh camera list using the saved, pinned recorder connection. Failed or cancelled refreshes preserve the saved list.
- Validate profiles before saving and add regression tests for camera additions, removals and recovery across restarts.

## 0.9.0-beta8

- Split recorder onboarding into address / identity verification and sign-in, with ports and manual fallback under Advanced.
- Accept a recorder HTTPS browser URL or host with a port; reject embedded credentials and unrelated page paths.
- Add keyboard Continue / Connect actions and Back navigation that clears the entered password.
- Document the current cloud-access boundary and prerequisites without presenting an unimplemented TP-Link account login.

## 0.9.0-beta7

- Replace raw combined-preview channel IDs with a visual grid of camera names and Empty tiles.
- Move existing assignments instead of duplicating them, show unplaced cameras, and require complete mapping before Save. Cancel discards the draft.
- Add core tests for mapping drafts, resizing and assignment moves.

## 0.9.0-beta6

- Make 8 / 16 layout rows selectable, guiding unconfigured installations through explicit combined-preview mapping before enabling the chosen layout.
- Keep clearer individual streams for 1 / 4 views; use the existing combined-preview renderer for 8 / 16.
- Add live single-camera Left / Right navigation within the selected group, with wraparound. Keep playback and focused controls separate; Up / Down still reaches controls.
- Validate composite mappings and ordered camera navigation with additional core tests.

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
