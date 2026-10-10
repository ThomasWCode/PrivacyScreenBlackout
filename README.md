# Privacy Screen Blackout

A personal Android app (debug APK, sideloaded) that blacks out parts of the screen over any app. Draw rectangles or freehand shapes, then either **keep** them visible and black out everything else, or **black out** just those areas.

## Install on your phone

1. Build the debug APK (see [Build](#build)). No APKs are published on GitHub.
2. Connect the phone with USB debugging on, then run `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
3. **Allow restricted settings** (Android 13+, only if you installed the APK from a file manager or browser rather than adb). Such sideloaded apps cannot turn on Accessibility until you do this:
   - Open *Settings → Apps → Privacy Screen Blackout*.
   - Tap **⋮** (top right) → **Allow restricted settings** and confirm.
   - If the menu item is missing, first try to enable the Accessibility Service (step 4) once. Android shows a "Restricted setting" dialog, after which the menu item appears.
4. Enable *Settings → Accessibility → Installed apps → Privacy Screen Blackout*.
5. Open the app and follow the setup checklist (notifications, Quick Settings tile).

## Use

- **Draw a mask:** open the app → **Edit on screen** (or the notification's **Edit**, or the floating button → **Edit**). The editor appears over whatever app is underneath.
  - **Rect / Lasso / Brush:** draw a rectangle, a closed freehand shape, or a brush stroke (adjust **Width**).
  - **Keep / Black:** *Keep* shapes stay visible and everything else goes black. *Black* shapes are blacked out. Later shapes win over earlier ones.
  - **Select:** tap a shape to select it, drag to move it, or drag a corner dot to resize a rectangle. **Delete** removes it, and Keep/Black changes it.
  - **Undo / Redo / Invert / Clear**, and **⇅ Move bar** to move the toolbar to the top or bottom.
  - **✓ Done** saves into the active layout; **Save as new** creates a new layout.
- **Orientation:** each layout has a separate portrait and landscape mask. Rotate the phone, then edit to draw the other one. Rotating while editing cancels the edit.
- **Show / hide:**
  - Quick Settings tile.
  - Notification actions.
  - Floating button (tap for the menu).
  - The app itself.
- **Peek:**
  - Press and hold the floating button.
  - Or press volume-down, then press it again within about 0.35 s and hold. Release to hide again. The first press still lowers volume one step; *Restore volume after peek* raises it back.
  - A peek ends automatically after 15 s.
- **Per-app layouts:** *Per-app layouts → Add app*. When that app comes to the front, its layout shows automatically. It hides again when you leave, and your previous layout is restored.
- **Controls on/off:** *Settings → Floating button* and *Settings → Volume-down peek* turn each control on or off. With the floating button on, *Button size* (32–96 dp) and *Button opacity* (20–100%) adjust it. Its menu always stays fully opaque so you can read it.
- **Lock:** stops Hide, Edit and Peek from the floating button, tile and volume key. Unlock in the app or from the notification.
- **Auto-off:** hides the mask after 5–60 minutes. It applies even while locked.

## Limitations

- **Touch blocking along curved edges is approximate.** Black areas swallow taps using a grid of rectangular windows. Visible areas always receive taps, but thin black slivers along curved or diagonal edges may let taps through.
- **The pull-down shade, lock screen and some system dialogs** draw above accessibility overlays, so they are not masked.
- **No blur:** that would need screen capture, which is outside this app's scope.
- **Screenshots include the mask** (checked on a Galaxy S24). Screen recordings have not been checked yet.
- **The app never reads screen content.** The accessibility service only receives window-change events (to know which app is in front) and key events (volume peek).

## On-device test checklist

- [ ] Mask covers the status bar, navigation bar and notch.
- [ ] Taps work in Keep areas and are swallowed in black areas.
- [ ] Rotation swaps the portrait and landscape layouts.
- [ ] Per-app rule shows and hides the mask and restores the previous layout.
- [ ] Tile and notification actions work.
- [ ] Floating button: drag, menu and hold-to-peek work.
- [ ] Volume: a single press changes volume normally; double-press-and-hold peeks and ends on release.
- [ ] Auto-off and lock work.
- [ ] The mask comes back after a reboot.
- [ ] A new build installs over the old one and keeps your layouts.

## Build

### CI
Every push runs `.github/workflows/ci.yml`. It lints, runs the unit tests and builds the debug APK as a check only. No APK is uploaded or released.

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

#### Signing key
Android only installs an update over an existing app if both are signed with the same key. Builds from one machine share its default debug key. To build on several machines and still update the installed app (and keep your layouts), make one keystore and set `DEBUG_KEYSTORE_PATH=/path/to/privacyblackout-debug.keystore` before building:
```bash
keytool -genkeypair -keystore privacyblackout-debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US" -storetype PKCS12
```
