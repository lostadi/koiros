package org.ostadix.koilife;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.WallpaperManager;
import android.app.KeyguardManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.graphics.Canvas;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.util.Calendar;
import java.util.Locale;

/**
 * Display-paced drawing; evolution advances only with elapsed visible time.
 * Full 60 fps follows touches, page swipes and unlocks; after eight quiet
 * seconds the pond draws at 24 fps (20 fps in Battery Saver) and asks the
 * display for a matching rate. Between drawn frames the engine sleeps
 * rather than waking on every display refresh.
 *
 * The companion reacts only to events: touches, returning to the home
 * screen, the time of day, and the battery level checked with Battery Saver.
 * Which apps were used is read once per return, on a background thread.
 */
public final class PondWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() { return new PondEngine(); }

    private final class PondEngine extends Engine implements
            SharedPreferences.OnSharedPreferenceChangeListener, Choreographer.FrameCallback {
        private final Choreographer choreographer = Choreographer.getInstance();
        private final FrameBudget budget = new FrameBudget();
        private static final long IDLE_AFTER_NANOS = 8000000000L, POWER_CHECK_NANOS = 5000000000L;
        private static final long ACTIVE_INTERVAL = 15000000L, IDLE_INTERVAL = 40000000L, SAVER_INTERVAL = 48000000L;
        private PowerManager power;
        private boolean idle, powerSave;
        private long lastInteraction, nextPowerCheck;
        private float requestedRate;
        private final IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        private SharedPreferences memory;
        private HandlerThread dayThread;
        private Handler dayHandler;
        private final Handler mainHandler = new Handler(Looper.getMainLooper());
        private boolean sleepy;
        private long lastHiddenAt, petDownTime;
        private float petDownX, petDownY;
        private boolean petTracking;
        private double stirLastU, stirLastV;
        private long stirLastTime;
        private Settings settings;
        private Pond renderer;
        private boolean visible, surfaceReady, destroyed, scheduled;
        private long lastNanos, lastFrameTime, seed, frames, windowStart;
        private int windowFrames;
        private double windowCost, windowWorst;
        private double windowLock, windowPost, windowSimulation, windowGeometry, windowCanvas;
        private long windowLinks, windowPoints;
        private int windowHardwareFrames;
        private int touchSlop, tapPointer = -1;
        private long tapTimeoutMs, tapDownTime;
        private float tapDownX, tapDownY;

        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            settings = new Settings(PondWallpaperService.this);
            power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            seed = settings.seed();
            renderer = settings.createPond(seed);
            settings.configure(renderer);
            homeCollisions = settings.homeCollisions();
            themeFollow = settings.themeFollow();
            settings.prefs.registerOnSharedPreferenceChangeListener(this);
            // Companion memory lives apart from the settings, so remembering
            // never triggers a reconfiguration.
            memory = getSharedPreferences("companion", Context.MODE_PRIVATE);
            // Early builds counted every launcher tap as play; start honestly.
            if (!memory.getBoolean("taps_v2", false)) {
                memory.edit().putFloat("affection", 0f).putBoolean("taps_v2", true).apply();
            }
            long now = System.currentTimeMillis();
            renderer.setAffection(Companion.fade(memory.getFloat("affection", 0f),
                now - memory.getLong("seen", now)));
            if (!isPreview()) {
                dayThread = new HandlerThread("KoirosDay", android.os.Process.THREAD_PRIORITY_BACKGROUND);
                dayThread.start();
                dayHandler = new Handler(dayThread.getLooper());
            }
            touchSlop = ViewConfiguration.get(PondWallpaperService.this).getScaledTouchSlop();
            tapTimeoutMs = Math.min(ViewConfiguration.getLongPressTimeout(),
                ViewConfiguration.getTapTimeout() + ViewConfiguration.getDoubleTapTimeout());
            setTouchEventsEnabled(true);
            Log.i("KoiLife", "Engine created; rate=" + settings.rate() + "; preview=" + isPreview());
        }
        @Override public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceReady = true;
            requestDisplayRate(holder);
            schedule();
        }
        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceReady = width > 0 && height > 0;
            tapPointer = -1;
            requestDisplayRate(holder);
            schedule();
        }
        private void requestDisplayRate(SurfaceHolder holder) {
            float rate = powerSave || sleepy ? 20f : idle ? 24f : 60f;
            if (Build.VERSION.SDK_INT >= 30 && holder.getSurface().isValid()) {
                try {
                    holder.getSurface().setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                    requestedRate = rate;
                }
                catch (IllegalStateException | IllegalArgumentException ignored) { }
            }
        }
        /** Any touch, page swipe or reveal restores full pace immediately. */
        private void markInteraction() {
            lastInteraction = System.nanoTime();
            if (idle) {
                idle = false;
                requestDisplayRate(getSurfaceHolder());
                Log.i("KoiLife", "Pace=active");
            }
        }
        private void resetTiming() {
            lastNanos = 0; lastFrameTime = 0; tapPointer = -1;
            resetWindow();
            budget.resetTiming();
        }
        private void resetWindow() {
            windowStart = 0; windowFrames = 0; windowCost = 0; windowWorst = 0;
            windowLock = 0; windowPost = 0; windowSimulation = 0;
            windowGeometry = 0; windowCanvas = 0;
            windowLinks = 0; windowPoints = 0; windowHardwareFrames = 0;
        }
        private void cancel() {
            choreographer.removeFrameCallback(this);
            scheduled = false;
        }
        /**
         * Hands the home screen's shapes to the pond. On the lock screen there are
         * no apps to meet, so none are passed; the lock state is asked at most once a second.
         */
        private void passHome(int width) {
            HomeLayout layout = homeCollisions ? HomeLayout.current : null;
            if (layout != null) {
                long now = SystemClock.uptimeMillis();
                if (now - keyguardCheckedAt > 1000) {
                    keyguardCheckedAt = now;
                    KeyguardManager keyguard = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                    locked = keyguard != null && keyguard.isKeyguardLocked();
                }
                if (locked) layout = null;
            }
            renderer.setHome(layout, layout == null ? 0 : layout.pageShift(HomeLayout.liveOffset, HomeLayout.liveStep, width));
        }
        /**
         * Every few seconds of drawing, reads the colours on screen; when they have
         * moved far enough, the phone's theme and Termux follow, written on the
         * background thread. Previews never touch the theme.
         */
        private void followTheme() {
            if (isPreview() || dayHandler == null || !themeFollow) return;
            // Battery Saver or a low, unplugged battery: the theme holds still.
            if (powerSave || sleepy) return;
            long now = SystemClock.uptimeMillis();
            if (now - themeCheckedAt < THEME_CHECK_MS) return;
            themeCheckedAt = now;
            if (!ThemeWriter.canWrite(PondWallpaperService.this) && !ThemeWriter.canTellTermux(PondWallpaperService.this)) return;
            follower.reset();
            renderer.showColors(follower);
            int found = follower.peaks(themeHues);
            follower.glide(themeHues, found, now, themeGlide);
            if (!follower.next(themeGlide, now, themeGlide)) return;
            final int seed = ThemeFollower.neon(themeGlide[0]);
            final int second = ThemeFollower.neon(themeGlide[1]), third = ThemeFollower.neon(themeGlide[2]);
            dayHandler.post(() -> ThemeWriter.apply(PondWallpaperService.this, seed, second, third));
        }
        private static final long THEME_CHECK_MS = 10000;
        private final ThemeFollower follower = new ThemeFollower();
        private final double[] themeHues = new double[3], themeGlide = new double[3];
        private long themeCheckedAt;
        private boolean themeFollow = true;
        private long keyguardCheckedAt;
        private boolean locked, homeCollisions = true;
        @Override public void onOffsetsChanged(float xOffset, float yOffset,
                float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            if (renderer == null) return;
            if (visible) markInteraction();
            HomeLayout.liveOffset = xOffset; HomeLayout.liveStep = xOffsetStep;
            renderer.setPageOffset(xOffset, xOffsetStep, xPixelOffset,
                getSurfaceHolder().getSurfaceFrame().width());
        }
        @Override public void onVisibilityChanged(boolean nowVisible) {
            visible = nowVisible;
            resetTiming(); cancel();
            if (visible) {
                nextPowerCheck = 0; markInteraction(); settings.configure(renderer);
                welcomeBack();
                schedule();
            } else {
                lastHiddenAt = System.currentTimeMillis();
                remember();
            }
            Log.i("KoiLife", "Visibility=" + visible + "; generation=" + renderer.getGeneration());
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false; resetTiming(); cancel();
            super.onSurfaceDestroyed(holder);
        }
        /** Greets a returning person and, if allowed, reads the colours of their time away. */
        private void welcomeBack() {
            if (!settings.companion()) return;
            long now = System.currentTimeMillis();
            updateMood();
            if (lastHiddenAt <= 0) return;
            final long since = lastHiddenAt, until = now;
            renderer.greet(Companion.welcome(until - since));
            if (dayHandler == null || !settings.knowMyDay() || until - since < Companion.WELCOME_AFTER_MS) return;
            dayHandler.post(() -> {
                final double[] hues = new double[DayReader.MAX_APPS], weights = new double[DayReader.MAX_APPS];
                final int count;
                try { count = DayReader.read(PondWallpaperService.this, memory, since, until, hues, weights); }
                catch (RuntimeException e) { Log.w("KoiLife", "Day colours unavailable: " + e.getClass().getSimpleName()); return; }
                if (count == 0) return;
                mainHandler.post(() -> {
                    if (destroyed || renderer == null) return;
                    renderer.tellDay(hues, weights, count);
                    Log.i("KoiLife", "Day colours: " + count + " apps");
                    schedule();
                });
            });
        }
        /** Time of day and battery set the pond's mood; a low battery also saves its own power. */
        private void updateMood() {
            Intent battery = registerReceiver(null, batteryFilter);
            int level = -1; boolean charging = false;
            if (battery != null) {
                int raw = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                level = raw < 0 || scale <= 0 ? -1 : raw * 100 / scale;
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
            }
            Calendar clock = Calendar.getInstance();
            double hour = clock.get(Calendar.HOUR_OF_DAY) + clock.get(Calendar.MINUTE) / 60.0;
            boolean companion = settings.companion();
            renderer.setMood(companion ? Companion.liveliness(Companion.night(hour), level, charging) : 1.0);
            sleepy = Companion.sleepy(level, charging);
        }
        private void remember() {
            if (memory == null || renderer == null) return;
            memory.edit().putFloat("affection", (float) renderer.getAffection())
                .putLong("seen", System.currentTimeMillis()).apply();
        }
        @Override public void onDestroy() {
            remember();
            if (dayThread != null) dayThread.quitSafely();
            destroyed = true; tapPointer = -1; cancel();
            settings.prefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (destroyed) return;
            tapPointer = -1;
            if (settings.modelChanged(renderer)) {
                // Switching models starts the chosen pond fresh and keeps the companion's memory.
                double kept = renderer.getAffection(), mood = renderer.getLiveliness();
                seed = settings.seed();
                renderer = settings.createPond(seed);
                renderer.setAffection(kept); renderer.setMood(mood);
                resetTiming();
                Log.i("KoiLife", "Model=" + (settings.lattice() ? "lattice" : "classic"));
            } else if (settings.seed() != seed) {
                seed = settings.seed(); renderer.reseed(seed); resetTiming();
            }
            settings.configure(renderer);
            homeCollisions = settings.homeCollisions();
            boolean follow = settings.themeFollow();
            if (follow && !themeFollow) { follower.forget(); themeCheckedAt = 0; }
            themeFollow = follow;
            schedule();
        }
        @Override public void onTouchEvent(MotionEvent event) {
            if (visible && !destroyed) markInteraction();
            if (!visible || !surfaceReady || destroyed || renderer == null) {
                tapPointer = -1;
                return;
            }
            SurfaceHolder holder = getSurfaceHolder();
            int width = holder.getSurfaceFrame().width(), height = holder.getSurfaceFrame().height();
            if (width <= 0 || height <= 0 || !holder.getSurface().isValid()) {
                tapPointer = -1;
                return;
            }
            if (settings.companion()) companionTouch(event, width, height);
            if (!settings.touchRipples()) {
                tapPointer = -1;
                return;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                tapPointer = -1;
                if (event.getPointerCount() != 1 || !insideSurface(event.getX(), event.getY(), width, height)) return;
                tapPointer = event.getPointerId(0);
                tapDownX = event.getX(); tapDownY = event.getY();
                tapDownTime = event.getEventTime();
                return;
            }
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_OUTSIDE
                    || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
                tapPointer = -1;
                return;
            }
            if (tapPointer < 0 || event.getPointerCount() != 1 || event.getPointerId(0) != tapPointer) {
                tapPointer = -1;
                return;
            }
            long held = event.getEventTime() - tapDownTime;
            if (held < 0 || held > tapTimeoutMs || movedBeyondSlop(event)
                    || !insideSurface(event.getX(), event.getY(), width, height)) {
                tapPointer = -1;
                return;
            }
            if (action == MotionEvent.ACTION_UP) {
                tapPointer = -1;
                renderer.touch(event.getX() / (double) width, event.getY() / (double) height);
                schedule();
            }
        }
        /** The launcher reports a tap on empty wallpaper: the forms come to look. */
        @Override public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action) && visible && !destroyed && renderer != null
                    && settings.companion()) {
                SurfaceHolder holder = getSurfaceHolder();
                int width = holder.getSurfaceFrame().width(), height = holder.getSurfaceFrame().height();
                if (width > 0 && height > 0) {
                    markInteraction();
                    renderer.companionTouch(x / (double) width, y / (double) height, Pond.TOUCH_TAP);
                    schedule();
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }
        /**
         * Holding still draws nearby forms to the finger; any touch earns a
         * glance. Fast drags, such as swiping between home pages, are ignored.
         */
        private void companionTouch(MotionEvent event, int width, int height) {
            int action = event.getActionMasked();
            double u = event.getX() / (double) width, v = event.getY() / (double) height;
            // Every finger movement stirs the dots, swipes included.
            long now = event.getEventTime();
            if (action == MotionEvent.ACTION_DOWN) {
                stirLastU = u; stirLastV = v; stirLastTime = now;
                renderer.stir(u, v, 0, 0);
            } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                double seconds = Math.max(.008, (now - stirLastTime) / 1000.0);
                renderer.stir(u, v, (u - stirLastU) / seconds, (v - stirLastV) / seconds);
                stirLastU = u; stirLastV = v; stirLastTime = now;
            }
            if (action == MotionEvent.ACTION_DOWN && event.getPointerCount() == 1) {
                petTracking = true; petDownX = event.getX(); petDownY = event.getY();
                petDownTime = event.getEventTime();
                renderer.companionTouch(u, v, Pond.TOUCH_DOWN);
            } else if (!petTracking) {
                return;
            } else if (action == MotionEvent.ACTION_MOVE) {
                long held = Math.max(1, event.getEventTime() - petDownTime);
                double travel = Math.hypot(event.getX() - petDownX, event.getY() - petDownY);
                if (travel / held * 1000.0 > width * 1.2) {
                    petTracking = false;  // a swipe, not a touch meant for the pond
                    renderer.companionTouch(u, v, Pond.TOUCH_UP);
                } else {
                    renderer.companionTouch(u, v, Pond.TOUCH_MOVE);
                }
            } else if (action == MotionEvent.ACTION_UP) {
                petTracking = false;
                // A raw touch only draws a glance; affection comes from taps on
                // empty wallpaper, which the launcher reports via onCommand.
                renderer.companionTouch(u, v, Pond.TOUCH_UP);
                schedule();
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                petTracking = false;
                renderer.companionTouch(u, v, Pond.TOUCH_UP);
            }
        }
        private boolean insideSurface(float x, float y, int width, int height) {
            return x >= 0 && y >= 0 && x <= width && y <= height;
        }
        private boolean movedBeyondSlop(MotionEvent event) {
            // Do not interpret a launcher swipe or a coalesced out-and-back drag as a tap.
            for (int i = -1; i < event.getHistorySize(); i++) {
                float x = i < 0 ? event.getX() : event.getHistoricalX(i);
                float y = i < 0 ? event.getY() : event.getHistoricalY(i);
                double dx = x - tapDownX, dy = y - tapDownY;
                if (dx * dx + dy * dy > (double) touchSlop * touchSlop) return true;
            }
            return false;
        }
        private void schedule() {
            if (visible && surfaceReady && !destroyed && !scheduled) {
                scheduled = true;
                choreographer.postFrameCallback(this);
            }
        }
        @Override public void doFrame(long frameTimeNanos) {
            scheduled = false;
            if (!visible || !surfaceReady || destroyed) return;
            if (frameTimeNanos >= nextPowerCheck) {
                powerSave = power != null && power.isPowerSaveMode();
                updateMood();
                nextPowerCheck = frameTimeNanos + POWER_CHECK_NANOS;
            }
            boolean saving = powerSave || sleepy;
            boolean quiet = saving || (lastInteraction != 0 && frameTimeNanos - lastInteraction > IDLE_AFTER_NANOS);
            if (quiet != idle) {
                idle = quiet;
                requestDisplayRate(getSurfaceHolder());
                Log.i("KoiLife", "Pace=" + (powerSave ? "saver" : sleepy ? "sleepy" : idle ? "idle" : "active"));
            }
            long interval = saving ? SAVER_INTERVAL : idle ? IDLE_INTERVAL : ACTIVE_INTERVAL;
            if (lastFrameTime != 0 && frameTimeNanos - lastFrameTime < interval) {
                // Sleep until shortly before the next drawn frame instead of
                // waking for every display refresh.
                long waitMs = (interval - (frameTimeNanos - lastFrameTime)) / 1000000L - 4;
                if (waitMs > 0 && !scheduled) {
                    scheduled = true;
                    choreographer.postFrameCallbackDelayed(this, waitMs);
                } else {
                    schedule();
                }
                return;
            }
            lastFrameTime = frameTimeNanos;
            final long start = System.nanoTime();
            final double dt = lastNanos == 0 ? 0 : Math.min(.5, (start - lastNanos) / 1e9);
            lastNanos = start;
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            boolean drawn = false, hardware = false;
            double lockMs = 0, postMs = 0;
            try {
                long lockStart = System.nanoTime();
                // Hardware canvases draw vertex batches from Android 10 on; before
                // that, the lattice is drawn on a software canvas.
                if (holder.getSurface().isValid()) {
                    canvas = Build.VERSION.SDK_INT >= 29 ? holder.lockHardwareCanvas() : holder.lockCanvas();
                }
                lockMs = (System.nanoTime() - lockStart) / 1e6;
                if (canvas != null) {
                    hardware = canvas.isHardwareAccelerated();
                    // A sleepy pond on a low battery also draws less.
                    renderer.setPointBudget(sleepy ? Math.min(8000, budget.getPointCount()) : budget.getPointCount());
                    passHome(canvas.getWidth());
                    renderer.render(canvas, dt);
                    frames++; drawn = true;
                    followTheme();
                }
            } catch (IllegalStateException | IllegalArgumentException e) {
                Log.w("KoiLife", "Surface unavailable: " + e.getClass().getSimpleName());
            } finally {
                if (canvas != null) {
                    long postStart = System.nanoTime();
                    try { holder.unlockCanvasAndPost(canvas); }
                    catch (IllegalStateException | IllegalArgumentException ignored) { }
                    finally { postMs = (System.nanoTime() - postStart) / 1e6; }
                }
            }
            if (drawn) {
                double cost = (System.nanoTime() - start) / 1e6;
                budget.observeFrame(cost, dt);
                if (windowStart == 0) windowStart = start;
                windowFrames++; windowCost += cost; windowWorst = Math.max(windowWorst, cost);
                windowLock += lockMs; windowPost += postMs;
                windowSimulation += renderer.getSimulationCostMs();
                windowGeometry += renderer.getGeometryCostMs();
                windowCanvas += renderer.getCanvasCostMs();
                windowLinks += renderer.getLastLinkCount();
                windowPoints += renderer.getLastPointCount();
                if (hardware) windowHardwareFrames++;
                double seconds = (start - windowStart) / 1e9;
                if (seconds >= 2) {
                    Log.i("KoiLife", String.format(Locale.US,
                        "frames=%d; fps=%.1f; drawMs=%.2f; worstMs=%.2f; points=%d; rate=%d; generation=%d; lockMs=%.2f; simulationMs=%.2f; geometryMs=%.2f; canvasMs=%.2f; postMs=%.2f; hwFrames=%d/%d; drawnPoints=%.0f; links=%.0f; maxCount=%d; field=%s; pace=%s; display=%.0f; merges=%d; divides=%d; mergedBodies=%d; mood=%.2f; affection=%.2f; model=%s",
                        frames, (windowFrames - 1) / seconds, windowCost / windowFrames,
                        windowWorst, renderer.getPointCount(), settings.rate(), renderer.getGeneration(),
                        windowLock / windowFrames, windowSimulation / windowFrames,
                        windowGeometry / windowFrames, windowCanvas / windowFrames,
                        windowPost / windowFrames, windowHardwareFrames, windowFrames,
                        windowPoints / (double) windowFrames, windowLinks / (double) windowFrames,
                        settings.count(), settings.field(), powerSave ? "saver" : sleepy ? "sleepy" : idle ? "idle" : "active",
                        requestedRate, renderer.getMergeCount(), renderer.getDivideCount(),
                        renderer.getMergedBodyCount(), renderer.getLiveliness(), renderer.getAffection(),
                        renderer instanceof LatticeRenderer ? "lattice" : "classic"));
                    resetWindow();
                }
            }
            schedule();
        }
    }
}
