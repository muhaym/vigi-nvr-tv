# Third-party notices

## VideoLAN LibVLC 3.7.7

Artifact: `org.videolan.android:libvlc-all:3.7.7` from Maven Central.
Upstream: https://code.videolan.org/videolan/libvlcjni
Binary / POM: https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.7.7/
License: LGPL 2.1 or later, subject to the exact native build and its dependencies. See `licenses/LGPL-2.1.txt` and the upstream source / notices. Before public binary distribution provide the exact corresponding native source, configuration and applicable relinking / modification materials. This source preparation does not claim that the binary compliance package is finished.

## Kotlin standard library

Transitive LibVLC dependency. Apache 2.0. Upstream https://github.com/JetBrains/kotlin . Preserve its notices in binary distributions.

## go2rtc authentication adaptation

`VigiNativeAuth.java` adapts the securityEncode implementation from https://github.com/AlexxIT/go2rtc/blob/master/pkg/tapo/client.go . Copyright (c) 2022 Alexey Khit. MIT. The full upstream MIT notice is in `licenses/go2rtc-MIT.txt`.

## Build and test tools

Gradle wrapper 8.11.1: Apache 2.0, https://gradle.org/ . Android Gradle plugin 8.10.1: Apache 2.0, https://developer.android.com/build . JUnit 4.13.2: EPL 1.0, https://junit.org/junit4/ . JSON-java 20240303 (core compile-only and tests; Android supplies JSON at runtime): public domain, https://github.com/stleary/JSON-java . JaCoCo 0.8.12: EPL 2.0, https://www.jacoco.org/ . These test / build dependencies are not application-authored code.

No proprietary TP-Link client binaries, captured media or private recorder certificates are distributed in this repository.
