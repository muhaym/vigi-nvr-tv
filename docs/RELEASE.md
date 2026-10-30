# Release checks

## Source publication

- [ ] Clean build, core tests / coverage and Android lint pass for the published revision.
- [ ] Source privacy gate and wrapper / dependency verification pass.
- [ ] Repository contains only reviewed source, docs and the official Gradle wrapper; no profiles, certificates, credentials, footage, vendor clients or signing material.
- [ ] CI checks the pushed revision; failures are investigated before a release claim.
- [ ] Fresh setup is tested separately from upgrades. Record model / firmware and actual results.

## Before a public APK release

- [ ] Package exact corresponding LibVLC native source, build configuration, notices and applicable modification / relinking materials. The source repository alone does not complete native-binary redistribution obligations.
- [ ] Set a stable signing / update policy, without committing signing keys.
- [ ] Verify first pattern, certificate approval / rejection, account setup, discovery fallback and app restart on a clean installation.
- [ ] Verify preservation of account / groups / pattern on upgrade.
- [ ] Verify directional focus, optical boundaries and playback.
- [ ] Verify microphone stop on route change, Home, focus loss and locking; check both audio directions physically.
- [ ] Verify alarm channel, explicit start / stop, timeout and recovery without disturbing occupied spaces.
- [ ] Review minimum API 26 paths, low-memory TVs and native 16 KB page-size eligibility before any Play Store claim.
- [ ] Test more recorder models / firmware / roles before expanding the compatibility matrix.

## Existing hardware evidence

The owner confirmed two-way audio, optical control directions, and both manual siren and light in the alpha line. The alpha3 layout / cycle iteration was exercised on the TV, and the owner reported alpha4 working after the five-second / alarm update. These confirmations are not a universal hardware certification. The owner uninstalled the app to verify fresh onboarding for the public-source iteration.

Current build, coverage, CI and fresh-install results are recorded in [testing](TESTING.md) as they are completed. No claim of a tested public APK release is made by this source checklist.
