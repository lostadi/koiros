# Privacy

Last updated: 16 November 2026.

Koiros runs its simulations and rendering on your Android device. It does not
request Internet access and includes no advertising, analytics, account
system, or remote crash-reporting service.

## What the app stores

The app stores your selected controls and a randomly generated pond seed in
its private Android preferences. The seed initializes the simulation; it is
not an account or device identifier. Tap positions are used transiently to
animate ripples and are not saved as a history.

Android may include app settings in its own backup or device-transfer system,
depending on your Android version, manufacturer, and backup settings. This
release permits Android backup. Koiros itself does not upload those settings
or operate a backup server.

## Permissions and diagnostics

The app declares `SET_WALLPAPER` so it can open Android's wallpaper setup flow.
Its wallpaper service is protected by Android's `BIND_WALLPAPER` permission.
It does not request access to your files, contacts, calendar, microphone,
camera, location, or network.

The wallpaper writes local Android diagnostic logs about rendering
performance, lifecycle events, and simulation progress. Koiros does not
transmit these logs. If you choose to share a bug report, review the attached
logs or screenshots first.

## Removing app data

Use Android's app settings to clear Koiros storage or uninstall it to remove
its local preferences. Android's backup settings control any operating-system
backup copies. Selecting **New pond** changes the simulation seed while
retaining your other controls.

GitHub hosts the source code and downloads separately from the installed
app; visiting GitHub is governed by GitHub's own privacy policy.
