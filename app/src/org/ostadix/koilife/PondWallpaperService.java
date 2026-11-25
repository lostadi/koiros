package org.ostadix.koilife;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.Surface;
import android.view.SurfaceHolder;
import java.util.Locale;

/** Display-paced drawing; evolution advances only with elapsed visible time. */
public final class PondWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() { return new PondEngine(); }

    private final class PondEngine extends Engine implements
            SharedPreferences.OnSharedPreferenceChangeListener, Choreographer.FrameCallback {
        private final Choreographer choreographer = Choreographer.getInstance();
        private final FrameBudget budget = new FrameBudget();
        private Settings settings;
        private KoiRenderer renderer;
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
            seed = settings.seed();
            renderer = new KoiRenderer(seed);
            settings.configure(renderer);
            settings.prefs.registerOnSharedPreferenceChangeListener(this);
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
            if (Build.VERSION.SDK_INT >= 30 && holder.getSurface().isValid()) {
                try { holder.getSurface().setFrameRate(60f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT); }
                catch (IllegalStateException | IllegalArgumentException ignored) { }
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
        @Override public void onOffsetsChanged(float xOffset, float yOffset,
                float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            if (renderer == null) return;
            renderer.setPageOffset(xOffset, xOffsetStep, xPixelOffset,
                getSurfaceHolder().getSurfaceFrame().width());
        }
        @Override public void onVisibilityChanged(boolean nowVisible) {
            visible = nowVisible;
            resetTiming(); cancel();
            if (visible) { settings.configure(renderer); schedule(); }
            Log.i("KoiLife", "Visibility=" + visible + "; generation=" + renderer.getLifeField().getGeneration());
        }
        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false; resetTiming(); cancel();
            super.onSurfaceDestroyed(holder);
        }
        @Override public void onDestroy() {
            destroyed = true; tapPointer = -1; cancel();
            settings.prefs.unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }
        @Override public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (destroyed) return;
            tapPointer = -1;
            if (settings.seed() != seed) {
                seed = settings.seed(); renderer.reseed(seed); resetTiming();
            }
            settings.configure(renderer);
            schedule();
        }
        @Override public void onTouchEvent(MotionEvent event) {
            if (!visible || !surfaceReady || destroyed || renderer == null || !settings.touchRipples()) {
                tapPointer = -1;
                return;
            }
            SurfaceHolder holder = getSurfaceHolder();
            int width = holder.getSurfaceFrame().width(), height = holder.getSurfaceFrame().height();
            if (width <= 0 || height <= 0 || !holder.getSurface().isValid()) {
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
            // At 120 Hz draw every other display frame; at 60 Hz draw every frame.
            if (lastFrameTime != 0 && frameTimeNanos - lastFrameTime < 15000000L) {
                schedule(); return;
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
                if (holder.getSurface().isValid()) canvas = holder.lockHardwareCanvas();
                lockMs = (System.nanoTime() - lockStart) / 1e6;
                if (canvas != null) {
                    hardware = canvas.isHardwareAccelerated();
                    renderer.setPointBudget(budget.getPointCount());
                    renderer.render(canvas, dt);
                    frames++; drawn = true;
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
                        "frames=%d; fps=%.1f; drawMs=%.2f; worstMs=%.2f; points=%d; rate=%d; generation=%d; lockMs=%.2f; simulationMs=%.2f; geometryMs=%.2f; canvasMs=%.2f; postMs=%.2f; hwFrames=%d/%d; drawnPoints=%.0f; links=%.0f; maxCount=%d; field=%s",
                        frames, (windowFrames - 1) / seconds, windowCost / windowFrames,
                        windowWorst, renderer.getPointCount(), settings.rate(), renderer.getLifeField().getGeneration(),
                        windowLock / windowFrames, windowSimulation / windowFrames,
                        windowGeometry / windowFrames, windowCanvas / windowFrames,
                        windowPost / windowFrames, windowHardwareFrames, windowFrames,
                        windowPoints / (double) windowFrames, windowLinks / (double) windowFrames,
                        settings.count(), settings.field()));
                    resetWindow();
                }
            }
            schedule();
        }
    }
}
