# Privacy

Last updated: 25 November 2026.

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

## Permissions and diagnostics

- `SET_WALLPAPER` opens Android's wallpaper setup flow. The wallpaper service is
  protected by Android's `BIND_WALLPAPER` permission.
- `PACKAGE_USAGE_STATS` is used only for Know my day, and only after you grant
  usage access.
- `QUERY_ALL_PACKAGES` lets Koiros look up the icons of apps you used.

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
