/*
 * Koiros home-screen layout reader. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Reads where the apps, folders and widgets sit on the home screen, so the
 * wallpaper's dots can collide with them.
 *
 * <p>Android does not tell a wallpaper where the launcher draws its icons, so
 * this optional accessibility service reads the launcher's window, and only
 * that window: the system delivers it nothing from any other app. From each
 * item it keeps its bounds, whether it is an icon, the length of its name (to
 * size the label) and whether it scrolls with the home pages. Names and every
 * other content are discarded at once. The result stays in memory in this
 * process; Koiros has no internet permission.
 *
 * <p>The launcher is read at most once every quarter second, and only after it
 * reports a change, so a still home screen costs nothing.
 */
public final class HomeLayoutService extends AccessibilityService {
    private static final long SETTLE_MS = 250;
    private static final int MAX_NODES = 600, MAX_DEPTH = 40;
    private static final int MAX_ITEMS = HomeLayout.MAX_SHAPES / 2;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable scan = this::scan;
    private boolean pending;
    private String home;

    private final Rect bounds = new Rect();
    private final int[] left = new int[MAX_ITEMS], top = new int[MAX_ITEMS];
    private final int[] right = new int[MAX_ITEMS], bottom = new int[MAX_ITEMS];
    private final boolean[] labelled = new boolean[MAX_ITEMS], paged = new boolean[MAX_ITEMS];
    private final int[] nameChars = new int[MAX_ITEMS];
    private int items, visited, screenWidth, screenHeight;

    @Override protected void onServiceConnected() {
        home = homePackage();
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null && home != null) {
            // Ask the system for the launcher's events only.
            info.packageNames = new String[] { home };
            setServiceInfo(info);
        }
        Log.i("KoiLife", "Home layout reader connected; launcher=" + home);
        request();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence from = event.getPackageName();
        if (home != null && (from == null || !home.contentEquals(from))) return;
        request();
    }

    @Override public void onInterrupt() { }

    @Override public void onDestroy() {
        handler.removeCallbacks(scan);
        HomeLayout.current = null;
        super.onDestroy();
    }

    @Override public boolean onUnbind(Intent intent) {
        handler.removeCallbacks(scan);
        HomeLayout.current = null;
        return super.onUnbind(intent);
    }

    /** One read a quarter second after a change; changes meanwhile join it. */
    private void request() {
        if (pending) return;
        pending = true;
        handler.postDelayed(scan, SETTLE_MS);
    }

    private void scan() {
        pending = false;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        try {
            CharSequence owner = root.getPackageName();
            // Another app or the notification shade is in front: keep the last layout.
            if (home != null && (owner == null || !home.contentEquals(owner))) return;
            measureScreen();
            items = 0; visited = 0;
            collect(root, 0, false);
            HomeLayout.current = HomeLayout.fromItems(screenWidth, screenHeight, left, top, right, bottom,
                labelled, nameChars, paged, items, getResources().getDisplayMetrics().density,
                HomeLayout.liveOffset, HomeLayout.liveStep);
        } catch (RuntimeException e) {
            // The launcher's window can change under the reader; the next change reads again.
            Log.w("KoiLife", "Home layout unavailable: " + e.getClass().getSimpleName());
        } finally {
            recycle(root);
        }
    }

    /**
     * Each home-screen item is the outermost thing a person can tap or hold:
     * an icon, a folder, a widget or the search bar. Hidden views, the app
     * drawer and anything covering much of the screen are not items.
     */
    private void collect(AccessibilityNodeInfo node, int depth, boolean scrolling) {
        if (node == null || depth > MAX_DEPTH || visited++ > MAX_NODES || items >= MAX_ITEMS) return;
        if (!node.isVisibleToUser()) return;
        String id = node.getViewIdResourceName();
        if (id != null && (id.contains("apps_view") || id.contains("all_apps") || id.contains("overview")
                || id.contains("search_results") || id.contains("popup_container"))) return;
        node.getBoundsInScreen(bounds);
        boolean pagedHere = scrolling || node.isScrollable();
        long area = (long) Math.max(0, bounds.width()) * Math.max(0, bounds.height());
        boolean small = area > 0 && area < .45 * screenWidth * (double) screenHeight;
        if (small && (node.isClickable() || node.isLongClickable())) {
            CharSequence text = node.getText(), description = node.getContentDescription();
            CharSequence kind = node.getClassName();
            boolean named = (text != null && text.length() > 0) || (description != null && description.length() > 0);
            boolean iconLike = kind != null && (kind.toString().endsWith("TextView") || kind.toString().contains("Folder"));
            left[items] = bounds.left; top[items] = bounds.top; right[items] = bounds.right; bottom[items] = bounds.bottom;
            labelled[items] = named && iconLike;
            nameChars[items] = text != null && text.length() > 0 ? text.length() : description != null ? Math.min(24, description.length()) : 0;
            paged[items] = pagedHere;
            items++;
            return;
        }
        for (int k = 0, n = node.getChildCount(); k < n && items < MAX_ITEMS; k++) {
            AccessibilityNodeInfo child = node.getChild(k);
            if (child == null) continue;
            try { collect(child, depth + 1, pagedHere); }
            finally { recycle(child); }
        }
    }

    @SuppressWarnings("deprecation")
    private void measureScreen() {
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (windows != null) windows.getDefaultDisplay().getRealMetrics(metrics);
        else metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels; screenHeight = metrics.heightPixels;
    }

    private String homePackage() {
        Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info == null || info.activityInfo == null) return null;
        String name = info.activityInfo.packageName;
        // Without a chosen launcher the resolver itself answers; then read whichever home is shown.
        return "android".equals(name) ? null : name;
    }

    @SuppressWarnings("deprecation")
    private static void recycle(AccessibilityNodeInfo node) {
        if (node != null && android.os.Build.VERSION.SDK_INT < 33) node.recycle();
    }

    /** True when the person has turned this reader on in Accessibility settings. */
    static boolean isEnabled(android.content.Context context) {
        String enabled = android.provider.Settings.Secure.getString(context.getContentResolver(),
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String mine = context.getPackageName() + "/" + HomeLayoutService.class.getName();
        String shortName = context.getPackageName() + "/.HomeLayoutService";
        for (String entry : enabled.split(":")) {
            if (entry.equalsIgnoreCase(mine) || entry.equalsIgnoreCase(shortName)) return true;
        }
        return false;
    }
}
