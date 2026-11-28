# Privacy

Last updated: 28 November 2026.

Koiros runs its simulations and rendering on your Android device. It does not
request Internet access and includes no advertising, analytics, account
system, or remote crash-reporting service. Without Internet permission, Android
does not let it send anything off the phone.

## What the app stores

The app stores your selected controls and a randomly generated pond seed in
its private Android preferences. The seed initializes the simulation; it is
not an account or device identifier. Tap positions are used transiently to
animate ripples and to draw the forms toward a touch; they are not saved as a
history.

The companion keeps a small separate memory: an affection value, the time the
pond was last seen, and a cache of icon hues keyed by app package name (for
example `hue:com.spotify.music`). That cache reveals which apps have appeared
in your day, so it is **excluded from Android backup and device transfer** and
never leaves the phone.

Android may include your other app settings (controls and seed) in its own
backup or device-transfer system, depending on your Android version,
manufacturer, and backup settings. Koiros itself does not upload them or
operate a backup server.

## Know my day (optional usage access)

If you turn on **Know my day** and grant Android's usage access, Koiros reads,
once each time you return to the home screen, which apps were in the
foreground since you last saw it (at most the previous twelve hours, apps used
for at least fifteen seconds). It takes the dominant vivid colour of each
app's icon and releases up to four of those colours into the pond. It does
not read notifications, messages, screen contents, or anything inside other
apps, and it keeps no record of usage times. Turn the switch off or revoke
usage access in Android's settings at any time.

## Collide with my apps (optional accessibility reader)

Android does not tell a live wallpaper where the launcher draws its icons. If
you turn on **Koiros app collisions** in Android's Accessibility settings,
Koiros reads your launcher's window, and only that window, to find where its
apps, folders, widgets, dock and search bar sit. Android delivers it nothing
from any other app. From each item it keeps the bounds on screen, whether it
is an icon, the length of its name (to size the label shape) and whether it
scrolls with the home pages. Names and all other content are discarded at once.
The shapes stay in memory only, are never written to storage, and are cleared
when you turn the reader off. Koiros reads the launcher at most four times a
second, and only after the launcher reports a change.

## Theme follows the wallpaper (optional)

With the permissions below granted, Koiros writes one system setting, `theme_customization_overlay_packages`, the same one Android's own colour picker writes, holding only a seed colour taken from the wallpaper. The theme that was there before is kept in Koiros's app storage and written back when the switch is turned off. Koiros also asks Termux to run `~/.termux/koiros-colors.sh` with three colours; nothing else is sent to Termux, and nothing leaves the phone.

- `WRITE_SECURE_SETTINGS` can be granted only from a computer (`adb shell pm grant org.ostadix.koilife android.permission.WRITE_SECURE_SETTINGS`) or with root. Revoke it with `pm revoke`.
- `com.termux.permission.RUN_COMMAND` lets Koiros run that one script. It also needs `allow-external-apps = true` in `~/.termux/termux.properties`, which lets any app holding this permission run Termux commands; only apps you grant it to can.

## Permissions and diagnostics

- `SET_WALLPAPER` opens Android's wallpaper setup flow. The wallpaper service is
  protected by Android's `BIND_WALLPAPER` permission.
- `PACKAGE_USAGE_STATS` is used only for Know my day, and only after you grant
  usage access.
- `QUERY_ALL_PACKAGES` lets Koiros look up the icons of apps you used and
  which app is your launcher.
- The **Koiros app collisions** accessibility service is bound by Android only
  after you turn it on, and is protected by `BIND_ACCESSIBILITY_SERVICE`.

Koiros does not request access to your files, contacts, calendar, microphone,
camera, location, notifications, or network.

The wallpaper writes local Android diagnostic logs about rendering
performance, lifecycle events, and simulation progress, including how many
app colours arrived (not which apps). Koiros does not transmit these logs. If
you choose to share a bug report, review the attached logs or screenshots
first.

## Removing app data

Use Android's app settings to clear Koiros storage or uninstall it to remove
its local preferences and the companion's memory. Android's backup settings
control any operating-system backup copies. Selecting **New pond** changes the
simulation seed while retaining your other controls.

GitHub hosts the source code and downloads separately from the installed
app; visiting GitHub is governed by GitHub's own privacy policy.
