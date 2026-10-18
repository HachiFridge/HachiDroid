# senbei-jni

The JNI bridge HachiDroid uses to unpack CrackProof-protected PC:RD libraries on the device.

It is a thin seam, not a copy of Senbei: two exported functions (`nativeIsProtectedLibrary`,
`nativeRestoreLibrary`) over `senbei_engine::android`, plus the Android-specific plumbing Senbei's
CLI does not need — a caller-owned scratch root for `TMPDIR`, JSON results instead of exceptions,
and panic containment at the FFI boundary. Nothing here reimplements engine logic; if a restore
behaves differently from the Senbei CLI, the engine is where to look.

## Build

Gradle drives Cargo (`buildSenbeiJni` in `app/build.gradle.kts`), so a normal Android build is
enough when the toolchain is present:

- `cargo` on `PATH` (or `CARGO=/path/to/cargo`)
- an Android NDK, resolved from, in order: `ndk.dir` in `local.properties`, `ANDROID_NDK_ROOT`,
  `ANDROID_NDK_HOME`, then the newest NDK under the SDK
- the `aarch64-linux-android` standard library installed (`rustup target add aarch64-linux-android`)

Without those the task is skipped, no `.so` is packaged, `SenbeiNative.isAvailable` is false and
the UI simply does not offer on-device restore. That is a supported configuration: the app still
accepts libraries the user supplies.

Only `arm64-v8a` is built. PC:RD ships arm64 libraries, and restoring a game you cannot run on
the current ABI would waste several hundred megabytes.

## Licensing

Senbei is AGPL-3.0-only and this crate is a derivative work of it (`Cargo.toml` says so). An APK
containing `libsenbei_jni.so` is therefore covered by the AGPL: the corresponding source must be
offered, including this crate. Do not ship the built library in a build whose distribution terms
cannot meet that.

The engine also requires that restored binaries are never redistributed, so HachiDroid restores
only into the user's own storage and must not host or upload the output.
