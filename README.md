# Galaxy, Android test build

Galaxy is a modified AETHER v3.0.0, upstream commit 0117c6eb0deaca03a045d4feb2fdfc1bca90d046.
Original project and author credit: https://github.com/GHOSTxASR/AETHER, GHOSTxASR / Ashmit Roy.
The GPL-3.0 license and existing third-party notices are retained. Modifications were made in November 2026.

## Changes

- App name, onboarding wordmark, startup wordmark and user-facing labels renamed to Galaxy.
- Separate application ID: app.galaxy.music.debug, so this test build does not overwrite AETHER.
- True-black base with white/neutral visual accents. The red signal accent is now light neutral gray so the playback needle remains visible against black.
- Listen's statue artwork replaced with an original depth-rendered spiral-galaxy image, slowly rotated above 135 twinkling background stars. This is an artistic visualization with a dimensional appearance, not an astronomy photograph or interactive 3D model.
- Galaxy animation capped at approximately 30 redraws per second. Paused off-screen, while scrolling, when another room covers it, when the app is not resumed, and when reduced motion is selected. No new network requests or permissions.
- Existing navigation, player, glass and transition animations retained. The requested galaxy replaces the statue-specific visual animation only.
- First-launch welcome has a true-black star field rather than water footage.
- Matching black-and-white spiral galaxy adaptive launcher icon.
- About credits the original AETHER author and GPL-3.0.
- Upstream AETHER update button removed from this custom build, to avoid offering an incompatible app package. Galaxy updates must be built and supplied separately.

## Requirements and build

Android 10 or later (minimum API 29). Tested for compilation using JDK 21, Gradle 9.8.0, Android platform 37.0, SDK build tools 36.0.0 and 37.0.0, NDK 28.2.13676358, and CMake 3.22.1. The upstream script is not executable in the checkout, so invoke it with bash or chmod it first.

Set JAVA_HOME and ANDROID_HOME to your local installations. Install the SDK packages using sdkmanager:

    sdkmanager 'platform-tools' 'platforms;android-37.0' 'build-tools;36.0.0' 'build-tools;37.0.0' 'ndk;28.2.13676358' 'cmake;3.22.1'

Normal build on a development machine:

    bash gradlew :app:assembleDebug

Build used on a 2 GB machine:

    bash gradlew :app:assembleDebug --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx850m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8' -Dorg.gradle.parallel=false -Pkotlin.compiler.execution.strategy=in-process

APK output: app/build/outputs/apk/debug/app-debug.apk

This archive excludes build outputs, Gradle caches, Git metadata, local SDK paths and signing keys. Android creates a local development signing key for debug builds. Do not treat debug signing as production release signing. For future distributable releases, create and securely retain a dedicated Galaxy signing key; a differently signed APK cannot update an installed copy in place.

## Verification and limits

Both upstream and modified builds completed successfully. APK metadata was checked, the application label is Galaxy, and the APK signature verifies. The final APK supports arm64-v8a, armeabi-v7a, x86 and x86_64.

This is a test candidate, not a phone-tested release. The environment lacks hardware acceleration and has only 2 GB RAM. A software emulator was attempted, but a complete live UI and playback/performance check could not be completed. Visual layout, galaxy animation on a physical phone, music playback, cast, sign-in and downloads remain unverified. Test these on the intended phone before relying on the app. Source and existing upstream functions are preserved, but successful compilation does not prove runtime correctness.
