# Android compatibility

Koiros runs as an ordinary Android app and live wallpaper. Installing or running it does not require root, ADB, Termux, Ostadix, or the two original apps.

| Requirement | Release behavior |
| --- | --- |
| Android | Android 8.0 / API 26 or newer |
| Device feature | Android live wallpaper support |
| CPU architecture | Universal APK; Java/DEX code, with no bundled native libraries |
| Rendering | Android Canvas, paced by display callbacks; adaptive point count |
| Network | No Internet permission, account, server, advertisements, or analytics |
| Wallpaper permission | Standard `SET_WALLPAPER`; Android protects the wallpaper service with `BIND_WALLPAPER` |
| Application ID | `org.ostadix.koilife` |

## Install and set up

1. Download the signed `Koiros-<version>.apk` from the repository's Releases page.
2. Open the APK and, if Android asks, allow installation from the browser or file manager you are using.
3. Open **Koiros** and select **Set wallpaper**. Complete Android's wallpaper preview dialog.
4. Adjust **Drift**, **Shape evolution**, and **Color drift** independently. Fresh installs start at 0.18×, 0.18×, and 0.25× respectively.

The home screen and lock screen choices are controlled by Android and the device's wallpaper picker. Launchers also decide which touch events reach a wallpaper, so tap ripples may be available in the app preview even where the launcher does not forward taps.

The animation pauses while the app preview or wallpaper is hidden. Frame rate and visual detail depend on device and display performance; a 60 fps drawing target is not a guarantee for every device. Population and the optional frequency field can be reduced for lighter rendering.

## Reinstall and upgrade

Keep the same application ID and release signing key when issuing updates. The release APK can update the earlier **Koi / Life** installation when both use the same signing certificate; the Koiros name does not change its application ID. Installing an update preserves the app's settings. Android requires a compatible version code and signature.

A build signed with your own key cannot replace a copy signed with Lee Ostadi's release key. Android will reject that update. To use your own build, first back up any settings you need, then uninstall the differently signed copy; uninstalling ordinarily removes its local settings. A source checkout does not include Lee's private signing key.

Android may back up and restore the app's preferences according to your system backup settings because the app allows Android backup. It does not provide its own account or cloud synchronization.

## Verification scope

The repository runs six pure Java model test suites and builds twice to compare APK bytes. Those checks cover simulation invariants and build repeatability, not every manufacturer's wallpaper picker, launcher, graphics driver, or battery policy. The release notes identify the device checks performed for each published version.

The manifest currently targets API 34. This release is intended for direct APK installation from GitHub; a Play Store submission is a separate distribution process with its own current requirements.
