/*
 * Koiros: hands the pond's colours to the phone's theme and to Termux. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Applies a theme drawn from the pond's colours.
 *
 * <p>System colours: Android's Material You engine takes its seed from a secure
 * setting, the same one the system's own colour picker writes. Writing it needs
 * WRITE_SECURE_SETTINGS, which Android grants only from a computer or with root
 * ({@code pm grant org.ostadix.koilife android.permission.WRITE_SECURE_SETTINGS}).
 * Every app that uses Material You colours follows, including the keyboard
 * and the system bars. The theme that was set before is kept, and put back
 * when following is turned off.
 *
 * <p>Termux: Termux has its own colours, so Koiros asks it to run
 * {@code ~/.termux/koiros-colors.sh} with the three colours. This needs the
 * RUN_COMMAND permission and {@code allow-external-apps = true} in
 * termux.properties. Both are optional; without them nothing is sent.
 */
final class ThemeWriter {
    static final String KEY = "theme_customization_overlay_packages";
    static final String PERMISSION = "android.permission.WRITE_SECURE_SETTINGS";
    static final String TERMUX = "com.termux", TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND";
    static final String TERMUX_SCRIPT = "/data/data/com.termux/files/home/.termux/koiros-colors.sh";
    private static final String ORIGINAL = "theme_original", WRITTEN = "theme_written";

    private ThemeWriter() { }

    /** Kept apart from the settings, so remembering the theme never reconfigures the pond. */
    private static SharedPreferences memory(Context context) {
        return context.getSharedPreferences("theme", Context.MODE_PRIVATE);
    }

    static boolean canWrite(Context context) {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    static boolean canTellTermux(Context context) {
        return context.checkSelfPermission(TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Sets the system theme from the seed and recolours Termux. Call off the main thread. */
    static void apply(Context context, int seed, int second, int third) {
        SharedPreferences prefs = memory(context);
        if (canWrite(context)) {
            try {
                String now = Settings.Secure.getString(context.getContentResolver(), KEY);
                // Keep the person's own theme once, the first time Koiros takes over.
                if (!prefs.contains(ORIGINAL) && (now == null || !now.equals(prefs.getString(WRITTEN, null)))) {
                    prefs.edit().putString(ORIGINAL, now == null ? "" : now).apply();
                }
                String json = ThemeFollower.themeJson(seed, System.currentTimeMillis());
                Settings.Secure.putString(context.getContentResolver(), KEY, json);
                prefs.edit().putString(WRITTEN, json).apply();
                Log.i("KoiLife", "Theme seed #" + ThemeFollower.hex(seed));
            } catch (RuntimeException e) {
                Log.w("KoiLife", "Theme unavailable: " + e.getClass().getSimpleName());
            }
        }
        termux(context, ThemeFollower.hex(seed), ThemeFollower.hex(second), ThemeFollower.hex(third));
    }

    /** Puts back the theme that was set before Koiros followed the wallpaper. */
    static void restore(Context context) {
        SharedPreferences prefs = memory(context);
        if (canWrite(context) && prefs.contains(ORIGINAL)) {
            try {
                String original = prefs.getString(ORIGINAL, "");
                Settings.Secure.putString(context.getContentResolver(), KEY, original.isEmpty() ? null : original);
                prefs.edit().remove(ORIGINAL).remove(WRITTEN).apply();
            } catch (RuntimeException e) {
                Log.w("KoiLife", "Theme restore unavailable: " + e.getClass().getSimpleName());
            }
        }
        termux(context, "--restore");
    }

    private static void termux(Context context, String... arguments) {
        if (!canTellTermux(context)) return;
        Intent intent = new Intent("com.termux.RUN_COMMAND")
            .setClassName(TERMUX, "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_SCRIPT)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arguments)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException e) {
            Log.w("KoiLife", "Termux colours unavailable: " + e.getClass().getSimpleName());
        }
    }
}
