# Privacy Screen Blackout

A personal Android app (debug APK, sideloaded) that blacks out parts of the screen over any app. Draw rectangles or freehand shapes, then either **keep** them visible and black out everything else, or **black out** just those areas.

## Install on your phone

1. Open the repository's **Releases** page on your phone and download `PrivacyScreenBlackout-debug.apk` from the `debug-<branch>` pre-release.
2. When prompted, allow your browser to **Install unknown apps**, then install.
3. **Allow restricted settings** (Android 13+). Sideloaded apps cannot turn on Accessibility until you do this:
   - Open *Settings → Apps → Privacy Screen Blackout*.
   - Tap **⋮** (top right) → **Allow restricted settings** and confirm.
   - If the menu item is missing, first try to enable the Accessibility Service (step 4) once. Android shows a "Restricted setting" dialog, after which the menu item appears.
4. Enable *Settings → Accessibility → Installed apps → Privacy Screen Blackout*.
5. Open the app and follow the setup checklist (notifications, Quick Settings tile).

## Build

### CI (default)
Every push runs `.github/workflows/build-debug-apk.yml`. It lints, runs the unit tests, builds the debug APK, and replaces the `debug-<branch>` pre-release with the new APK.

#### One-off: fixed signing key
Android only installs an update over an existing app if both are signed with the same key. Add the keystore as a repository secret so every CI build can update the installed app (and keep your layouts):

1. *GitHub → repository → Settings → Secrets and variables → Actions → New repository secret*.
2. Name: `DEBUG_KEYSTORE_B64`. Value: the base64 text of the debug keystore (one line).

Without the secret, CI still builds but signs with a throwaway key. You would then need to uninstall the app before installing each new build.

To make your own keystore instead:
```bash
keytool -genkeypair -keystore privacyblackout-debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US" -storetype PKCS12
base64 -w0 privacyblackout-debug.keystore   # paste output into the secret
```

### Locally
Requirements:
- JDK 17 or newer (21 recommended), e.g. `sudo apt install openjdk-21-jdk` or [Temurin](https://adoptium.net/).
- Android SDK with platform 37. Either install [Android Studio](https://developer.android.com/studio), or install only the command-line tools:
  ```bash
  # download commandlinetools-linux-*_latest.zip from https://developer.android.com/studio#command-line-tools-only
  mkdir -p ~/android-sdk/cmdline-tools && unzip commandlinetools-linux-*_latest.zip -d ~/android-sdk/cmdline-tools
  mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
  export ANDROID_HOME=~/android-sdk
  yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses
  $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-37.0" "build-tools;36.1.0" "platform-tools"
  echo "sdk.dir=$ANDROID_HOME" > local.properties
  ```

Commands:
```bash
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk   # install over USB (optional)
```

To sign a local build with the same key as CI, set `DEBUG_KEYSTORE_PATH=/path/to/privacyblackout-debug.keystore` before building.
