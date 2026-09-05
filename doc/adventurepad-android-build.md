# Building ScummVM-AdventurePad for Android

This document records the build layout verified for the AdventurePad fork. It
is deliberately not a claim of clean-machine reproducibility: the native
dependency bootstrap is incomplete and must be finished before a public binary
release can be reproduced from the repository alone.

## Verified target and tools

- macOS host (the current generated configuration uses the NDK's Darwin
  toolchain);
- Android SDK with command-line/build tools required by the generated Gradle
  project;
- Android NDK `23.2.8568313`;
- Java suitable for Gradle 9.6.1 (JDK 17 or newer);
- `make`, Python 3, CMake, and the normal ScummVM configure prerequisites;
- Android ABI `arm64-v8a`, API level 21 native target;
- debug application ID `org.scummvm.scummvm.debug`.

The NDK version and Android application configuration are declared in
`dists/android/build.gradle`. The generated wrapper currently uses Gradle 9.6.1.

## Native dependencies

Choose a dependency root outside this repository:

```sh
export SCUMMVM_DEPS=/path/to/scummvm-adventurepad-deps
```

The verified local configuration provides:

| Dependency | Evidence available | Linkage |
| --- | --- | --- |
| Oboe 1.10.0 | named source tree, CMake cache, headers, and `liboboe.a` | static archive supplied through an additional library path |
| libpng 1.6.43 | named source tree, CMake cache, installed headers, and `libpng16.a` | static |
| FLAC 1.5.0 (reported by installed `flac.pc`) | installed headers, pkg-config/CMake metadata, and `libFLAC.a` | static |

Oboe and libpng were configured for `arm64-v8a`, Android API 21, release mode,
and NDK 23.2.8568313. Oboe samples/tests and libpng tests/tools were disabled;
libpng shared-library output was disabled.

### Reproducibility TODO

The repository does not yet provide verified commands or checksums for fetching
and building these dependencies. In particular, the FLAC source tree and the
command that produced its installed prefix are absent. The exact origin archives
or source revisions, checksums, CMake versions/options, and a clean-machine
bootstrap script still need to be recorded. Do not infer those commands from
this document or publish a binary as reproducible until that work is complete.

The historical preview APK also contained its absolute build-source path in the
native library. Build public artifacts from a neutral workspace path and scan
the final APK/native library for usernames and host paths before release.

## Configure and native build

`configure` generates `config.h`, `config.mk`, and the Android Gradle project.
Those files contain host-specific paths and are intentionally ignored. The
current configuration was made with the following shape (the dependency trees
must already exist):

```sh
CXXFLAGS="-I$SCUMMVM_DEPS/src/oboe-1.10.0/include" \
LDFLAGS="-L$SCUMMVM_DEPS/oboe-build-arm64" \
./configure \
  --host=android-arm64-v8a \
  --with-flac-prefix="$SCUMMVM_DEPS/flac-prefix-arm64" \
  --with-png-prefix="$SCUMMVM_DEPS/libpng-prefix-arm64"

make -j8 ScummVM-debug.apk
```

The root `make` build compiles native C/C++ code, links
`lib/arm64-v8a/libscummvm.so`, prepares `android_project`, invokes Android
packaging, and copies the resulting APK to:

```text
ScummVM-debug.apk
```

The generated Gradle project's intermediate APK is:

```text
android_project/build/outputs/apk/debug/ScummVM-debug.apk
```

## Why Gradle alone is insufficient

After native configuration/build has generated `android_project`, this command
can compile Java/resources and repackage the already-present native library:

```sh
android_project/gradlew -p android_project assembleDebug
```

It does **not** compile modified ScummVM C++ sources. A successful Gradle-only
build can therefore package a stale `libscummvm.so`. Use the root `make` target
after native changes, and verify that the relevant object files were rebuilt.

## Signing and installation

The debug variant uses application ID `org.scummvm.scummvm.debug`. AdventurePad
currently addresses that exact package. Do not substitute the release variant
(`org.scummvm.scummvm`) without a coordinated protocol/package migration.

The two applications use signature-protected permissions. Their APKs must be
signed with the same certificate. This tree has no release-signing configuration;
Android's local debug keystore is machine-specific, so arbitrary debug builds
from different machines are not mutually compatible upgrades.

For a matching locally built pair, installation has been verified in this form:

```sh
adb install -r ScummVM-debug.apk
adb install -r /path/to/AdventurePad/app-debug.apk
```

The order is not significant. `adb install -r` only upgrades an installed app
when package identity and signing certificate are compatible.
