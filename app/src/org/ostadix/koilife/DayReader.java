/*
 * Koiros: the colours of a person's day. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads, once per return to the home screen, which apps a person spent time
 * in since they last saw the pond, and the colour of each app's icon. Runs on
 * a background thread only when called. Nothing is sent anywhere: Koiros has
 * no internet permission. Only icon hues are remembered, keyed by package.
 */
final class DayReader {
    static final int MAX_APPS = 4;
    private static final long MIN_FOREGROUND_MS = 15_000L;
    private static final long LONGEST_WINDOW_MS = 12L * 3600 * 1000;

    private DayReader() { }

    /** True when Android's usage access has been granted to Koiros. */
    static boolean hasAccess(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode;
        if (Build.VERSION.SDK_INT >= 29) {
            mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        } else {
            mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * Fills hues and weights for up to {@link #MAX_APPS} apps used in
     * [from, to], ordered by foreground time. Weight is relative to the
     * longest. Returns how many were written; zero without usage access.
     */
    static int read(Context context, SharedPreferences cache, long from, long to,
                    double[] hues, double[] weights) {
        if (!hasAccess(context) || to <= from) return 0;
        UsageStatsManager usage = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (usage == null) return 0;
        long start = Math.max(from, to - LONGEST_WINDOW_MS);
        Map<String, Long> resumed = new HashMap<>(), total = new HashMap<>();
        UsageEvents events = usage.queryEvents(start, to);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(event);
            String name = event.getPackageName();
            int type = event.getEventType();
            if (type == 1) {            // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                resumed.put(name, event.getTimeStamp());
            } else if (type == 2 || type == 23) {  // ACTIVITY_PAUSED / ACTIVITY_STOPPED
                Long began = resumed.remove(name);
                if (began != null) {
                    Long sum = total.get(name);
                    total.put(name, (sum == null ? 0L : sum) + Math.max(0L, event.getTimeStamp() - began));
                }
            }
        }
        String own = context.getPackageName(), home = homePackage(context);
        String[] names = new String[MAX_APPS];
        long[] times = new long[MAX_APPS];
        for (Map.Entry<String, Long> entry : total.entrySet()) {
            String name = entry.getKey();
            long time = entry.getValue();
            if (time < MIN_FOREGROUND_MS || name.equals(own) || name.equals(home)
                    || name.equals("android") || name.equals("com.android.systemui")) continue;
            for (int i = 0; i < MAX_APPS; i++) {
                if (names[i] == null || time > times[i]) {
                    for (int j = MAX_APPS - 1; j > i; j--) { names[j] = names[j - 1]; times[j] = times[j - 1]; }
                    names[i] = name; times[i] = time;
                    break;
                }
            }
        }
        int count = 0;
        for (int i = 0; i < MAX_APPS && names[i] != null; i++) {
            double hue = iconHue(context, cache, names[i]);
            if (Double.isNaN(hue)) continue;
            hues[count] = hue;
            weights[count] = times[i] / (double) times[0];
            count++;
        }
        return count;
    }

    /** The dominant saturated hue of an app's icon, cached; NaN for a grey icon. */
    static double iconHue(Context context, SharedPreferences cache, String name) {
        String key = "hue:" + name;
        if (cache.contains(key)) {
            float stored = cache.getFloat(key, -1f);
            return stored < 0 ? Double.NaN : stored;
        }
        double hue = Double.NaN;
        try {
            PackageManager packages = context.getPackageManager();
            Drawable icon = packages.getApplicationIcon(name);
            Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            icon.setBounds(0, 0, 24, 24);
            icon.draw(canvas);
            float[] hsv = new float[3];
            double x = 0, y = 0, weight = 0;
            for (int py = 0; py < 24; py++) {
                for (int px = 0; px < 24; px++) {
                    int pixel = bitmap.getPixel(px, py);
                    if (Color.alpha(pixel) < 128) continue;
                    Color.colorToHSV(pixel, hsv);
                    // Favour vivid pixels: an icon's brand colour, not its outline.
                    double w = hsv[1] * hsv[1] * hsv[2];
                    double radians = Math.toRadians(hsv[0]);
                    x += Math.cos(radians) * w; y += Math.sin(radians) * w; weight += w;
                }
            }
            bitmap.recycle();
            if (weight > 3 && Math.hypot(x, y) > weight * .2) {
                double degrees = Math.toDegrees(Math.atan2(y, x));
                hue = degrees < 0 ? degrees + 360 : degrees;
            }
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Missing or unreadable icons simply contribute no colour.
        }
        cache.edit().putFloat(key, Double.isNaN(hue) ? -1f : (float) hue).apply();
        return hue;
    }

    private static String homePackage(Context context) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null ? info.activityInfo.packageName : "";
    }
}
