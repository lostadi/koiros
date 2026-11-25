package org.ostadix.koilife;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public final class MainActivity extends Activity {
    private Settings settings;
    private PondPreview preview;
    private static final int INK = Color.rgb(8, 13, 18), PANEL = Color.rgb(17, 26, 33);
    private static final int TEXT = Color.rgb(225, 236, 237), MUTED = Color.rgb(149, 169, 178);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new Settings(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(INK);
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(24), dp(18), dp(24), dp(8));
        TextView title = text("Koiros", 27, TEXT);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        heading.addView(title);
        heading.addView(text("Slowly evolving shapes, color, and currents", 13, MUTED));
        root.addView(heading);
        preview = new PondPreview();
        preview.setContentDescription("Live preview of Koi and Game of Life frequency projections");
        root.addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(20), dp(12), dp(20), dp(12));
        controls.setBackgroundColor(PANEL);
        controls.addView(text("ANIMATION SPEED", 11, MUTED));
        addSpeedControl(controls, "Drift", "motion_speed", settings.motionSpeed());
        addSpeedControl(controls, "Shape evolution", "form_speed", settings.formSpeed());
        addSpeedControl(controls, "Color drift", "color_speed", settings.colorSpeed());
        addPopulationControls(controls);
        controls.addView(text("LIFE EVOLUTION SPEED", 11, MUTED));
        RadioGroup rates = new RadioGroup(this);
        rates.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton slow = new RadioButton(this); slow.setId(101); slow.setText("1 Hz · gentle"); slow.setTextColor(TEXT);
        RadioButton fast = new RadioButton(this); fast.setId(104); fast.setText("4 Hz · lively"); fast.setTextColor(TEXT);
        rates.addView(slow, new RadioGroup.LayoutParams(0, dp(44), 1));
        rates.addView(fast, new RadioGroup.LayoutParams(0, dp(44), 1));
        rates.check(settings.rate() == 1 ? 101 : 104);
        rates.setOnCheckedChangeListener((group, id) -> settings.prefs.edit().putInt("rate", id == 101 ? 1 : 4).apply());
        controls.addView(rates);
        TextView strength = text("Pattern influence · " + Math.round(settings.strength() * 100) + "%", 14, TEXT);
        controls.addView(strength);
        SeekBar gain = new SeekBar(this);
        gain.setMax(100); gain.setProgress((int)Math.round(settings.strength() * 100));
        gain.setContentDescription("Pattern influence");
        gain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar view, int value, boolean fromUser) {
                strength.setText("Pattern influence · " + value + "%");
                if (fromUser) settings.prefs.edit().putInt("strength", value).apply();
            }
            public void onStartTrackingTouch(SeekBar view) { }
            public void onStopTrackingTouch(SeekBar view) { }
        });
        controls.addView(gain, new LinearLayout.LayoutParams(-1, dp(36)));
        Switch field = new Switch(this); field.setText("Show frequency field"); field.setTextColor(TEXT); field.setChecked(settings.field());
        field.setOnCheckedChangeListener((button, checked) -> settings.prefs.edit().putBoolean("field", checked).apply());
        controls.addView(field, new LinearLayout.LayoutParams(-1, dp(43)));
        Switch ripples = new Switch(this); ripples.setText("Tap ripples"); ripples.setTextColor(TEXT);
        ripples.setChecked(settings.touchRipples());
        ripples.setOnCheckedChangeListener((button, checked) ->
            settings.prefs.edit().putBoolean("touch_ripples", checked).apply());
        controls.addView(ripples, new LinearLayout.LayoutParams(-1, dp(43)));
        Switch renew = new Switch(this); renew.setText("Renew settled patterns"); renew.setTextColor(TEXT); renew.setChecked(settings.autoReseed());
        renew.setOnCheckedChangeListener((button, checked) -> settings.prefs.edit().putBoolean("auto_reseed", checked).apply());
        controls.addView(renew, new LinearLayout.LayoutParams(-1, dp(43)));
        LinearLayout actions = new LinearLayout(this);
        Button reseed = new Button(this); reseed.setText("New pond"); reseed.setAllCaps(false);
        reseed.setOnClickListener(v -> settings.newSeed());
        Button apply = new Button(this); apply.setText("Set wallpaper"); apply.setAllCaps(false);
        apply.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(126, 216, 205))); apply.setTextColor(INK);
        apply.setOnClickListener(v -> chooseWallpaper());
        actions.addView(reseed, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(apply, new LinearLayout.LayoutParams(0, dp(52), 1));
        controls.addView(actions);
        TextView credits = text("About Koiros", 12, MUTED);
        credits.setGravity(Gravity.CENTER); credits.setPadding(0, dp(8), 0, dp(5));
        credits.setOnClickListener(v -> showAbout());
        controls.addView(credits);
        scroll.addView(controls);
        int panelHeight = Math.min(dp(365), (int)(getResources().getDisplayMetrics().heightPixels * .49));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, panelHeight));
        setContentView(root);
    }
    private void addSpeedControl(LinearLayout controls, String title, String key, double speed) {
        TextView valueLabel = text(speedLabel(title, (int) Math.round(speed * 100)), 14, TEXT);
        valueLabel.setPadding(0, dp(8), 0, 0);
        controls.addView(valueLabel);
        SeekBar slider = new SeekBar(this);
        slider.setMin(0);
        slider.setMax(speedProgress(Settings.MAX_SPEED_PERCENT));
        slider.setProgress(speedProgress((int) Math.round(speed * 100)));
        slider.setKeyProgressIncrement(4);
        slider.setContentDescription(speedLabel(title, speedPercent(slider.getProgress())));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar view, int progress, boolean fromUser) {
                int percent = speedPercent(progress);
                String label = speedLabel(title, percent);
                valueLabel.setText(label);
                view.setContentDescription(label);
                if (fromUser) settings.prefs.edit().putInt(key, percent).apply();
            }
            public void onStartTrackingTouch(SeekBar view) { }
            public void onStopTrackingTouch(SeekBar view) { }
        });
        controls.addView(slider, new LinearLayout.LayoutParams(-1, dp(48)));
    }
    // Give 0.02x-0.50x more than half the track while retaining every saved percent.
    private static int speedProgress(int percent) {
        int bounded = Math.max(Settings.MIN_SPEED_PERCENT, Math.min(Settings.MAX_SPEED_PERCENT, percent));
        int fineEnd = (50 - Settings.MIN_SPEED_PERCENT) * 4;
        return bounded <= 50 ? (bounded - Settings.MIN_SPEED_PERCENT) * 4 : fineEnd + bounded - 50;
    }
    private static int speedPercent(int progress) {
        int fineEnd = (50 - Settings.MIN_SPEED_PERCENT) * 4;
        return progress <= fineEnd ? Settings.MIN_SPEED_PERCENT + (progress + 2) / 4 : 50 + progress - fineEnd;
    }
    private String speedLabel(String title, int percent) {
        return String.format(java.util.Locale.getDefault(), "%s · %.2f×", title, percent / 100.0);
    }
    private void addPopulationControls(LinearLayout controls) {
        TextView heading = text("CREATURES", 11, MUTED);
        heading.setPadding(0, dp(8), 0, dp(4));
        controls.addView(heading);
        TextView countLabel = text(populationLabel(settings.count(), settings.varyCount()), 14, TEXT);
        controls.addView(countLabel);
        Switch vary = new Switch(this);
        vary.setText("Vary count automatically");
        vary.setTextColor(TEXT);
        vary.setChecked(settings.varyCount());
        SeekBar countSlider = new SeekBar(this);
        countSlider.setMin(1);
        countSlider.setMax(9);
        countSlider.setProgress(settings.count());
        countSlider.setContentDescription(countLabel.getText());
        countSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar view, int count, boolean fromUser) {
                String label = populationLabel(count, vary.isChecked());
                countLabel.setText(label);
                view.setContentDescription(label);
                if (fromUser) settings.prefs.edit().putInt("count", count).apply();
            }
            public void onStartTrackingTouch(SeekBar view) { }
            public void onStopTrackingTouch(SeekBar view) { }
        });
        controls.addView(countSlider, new LinearLayout.LayoutParams(-1, dp(48)));
        vary.setOnCheckedChangeListener((button, checked) -> {
            String label = populationLabel(countSlider.getProgress(), checked);
            countLabel.setText(label);
            countSlider.setContentDescription(label);
            settings.prefs.edit().putBoolean("vary_count", checked).apply();
        });
        controls.addView(vary, new LinearLayout.LayoutParams(-1, dp(48)));
        Switch collisions = new Switch(this);
        collisions.setText("Collisions");
        collisions.setTextColor(TEXT);
        collisions.setChecked(settings.collisions());
        collisions.setOnCheckedChangeListener((button, checked) ->
            settings.prefs.edit().putBoolean("collisions", checked).apply());
        controls.addView(collisions, new LinearLayout.LayoutParams(-1, dp(48)));
    }
    private String populationLabel(int count, boolean varying) {
        return (varying ? "Maximum creatures" : "Creatures") + " · " + count;
    }
    private void chooseWallpaper() {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            new ComponentName(this, PondWallpaperService.class));
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException e) {
            try { startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)); }
            catch (android.content.ActivityNotFoundException unavailable) {
                new AlertDialog.Builder(this).setMessage("Open Android’s Wallpaper settings and choose Koiros.").setPositiveButton("OK", null).show();
            }
        }
    }
    private void showAbout() {
        String info = "Koiros — Lee Ostadi’s evolving computational wallpaper.\n\n"
            + "Koiros combines Artificial Koi’s original point equations with Conway’s Game of Life. "
            + "One to nine creatures morph through ten original Artificial Koi forms, with 48 neon hues forming cohesive spatial gradients that shift gradually across each body. Nine independently seeded Life runs, their measured frequencies, and coupled chaotic systems drive the pond. "
            + "Use Drift, Shape evolution, and Color drift to give the pond a slower, calmer pace. The pond pauses when the wallpaper is hidden.\n\n"
            + "Each creature has a fixed depth: nearer ones are larger, and they shift a little farther when you swipe between home pages. Two soft pools of light move slowly in the water behind them.\n\n"
            + "Artificial Koi — Mathew Molt and credited contributors, GPL-3.0-or-later.\n"
            + "GoL Screensaver — Harold Martin, MIT.\n"
            + "Created by Lee Daghlar Ostadi. Simulation integration built and checked using Ostadix MCP.\n\n"
            + "This combined app is GPL-3.0-or-later. Source and build instructions are supplied with the APK.";
        new AlertDialog.Builder(this).setTitle("Koiros").setMessage(info)
            .setPositiveButton("Close", null).setNeutralButton("Licenses", (d, w) -> showLicenses()).show();
    }
    private void showLicenses() {
        String content;
        try (InputStream in = getAssets().open("LICENSES.txt"); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            content = out.toString("UTF-8");
        } catch (Exception e) { content = "License files are included in the accompanying source archive."; }
        new AlertDialog.Builder(this).setTitle("Open-source licenses").setMessage(content).setPositiveButton("Close", null).show();
    }
    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override protected void onResume() { super.onResume(); if (preview != null) preview.start(); }
    @Override protected void onPause() { if (preview != null) preview.stop(); super.onPause(); }
    @Override protected void onDestroy() { if (preview != null) preview.dispose(); super.onDestroy(); }

    private final class PondPreview extends View implements
            SharedPreferences.OnSharedPreferenceChangeListener, android.view.Choreographer.FrameCallback {
        private final KoiRenderer renderer;
        private final FrameBudget budget = new FrameBudget();
        private final android.view.Choreographer choreographer = android.view.Choreographer.getInstance();
        private boolean running, scheduled;
        private long seed, lastNanos, lastFrameTime;
        private final int touchSlop;
        private final long tapTimeoutMs;
        private int tapPointer = -1;
        private float tapDownX, tapDownY;
        private long tapDownTime;
        private double clickU = .5, clickV = .5;
        private boolean clickFromTouch;
        PondPreview() {
            super(MainActivity.this); seed = settings.seed(); renderer = new KoiRenderer(seed);
            settings.configure(renderer); settings.prefs.registerOnSharedPreferenceChangeListener(this);
            touchSlop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
            tapTimeoutMs = Math.min(ViewConfiguration.getLongPressTimeout(),
                ViewConfiguration.getTapTimeout() + ViewConfiguration.getDoubleTapTimeout());
            setClickable(settings.touchRipples());
            setFocusable(settings.touchRipples());
        }
        private void schedule() {
            if (running && !scheduled) {
                scheduled = true; choreographer.postFrameCallback(this);
            }
        }
        void start() {
            running = true; lastNanos = 0; lastFrameTime = 0; tapPointer = -1;
            budget.resetTiming(); invalidate(); schedule();
        }
        void stop() {
            running = false; lastNanos = 0; lastFrameTime = 0; tapPointer = -1;
            choreographer.removeFrameCallback(this); scheduled = false;
            budget.resetTiming();
        }
        void dispose() { stop(); settings.prefs.unregisterOnSharedPreferenceChangeListener(this); }
        @Override public void doFrame(long frameTimeNanos) {
            scheduled = false;
            if (!running) return;
            if (lastFrameTime == 0 || frameTimeNanos - lastFrameTime >= 15000000L) {
                lastFrameTime = frameTimeNanos; invalidate();
            }
            schedule();
        }
        public void onSharedPreferenceChanged(SharedPreferences p, String key) {
            if (seed != settings.seed()) { seed = settings.seed(); renderer.reseed(seed); lastNanos = 0; }
            settings.configure(renderer);
            tapPointer = -1;
            setClickable(settings.touchRipples());
            setFocusable(settings.touchRipples());
            invalidate();
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (!running || !settings.touchRipples() || getWidth() <= 0 || getHeight() <= 0) {
                tapPointer = -1;
                return false;
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                tapPointer = -1;
                if (event.getPointerCount() != 1 || !insidePreview(event.getX(), event.getY())) return false;
                tapPointer = event.getPointerId(0);
                tapDownX = event.getX(); tapDownY = event.getY();
                tapDownTime = event.getEventTime();
                return true;
            }
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_OUTSIDE
                    || action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
                tapPointer = -1;
                return true;
            }
            if (tapPointer < 0 || event.getPointerCount() != 1 || event.getPointerId(0) != tapPointer) {
                tapPointer = -1;
                return true;
            }
            long held = event.getEventTime() - tapDownTime;
            if (held < 0 || held > tapTimeoutMs || movedBeyondSlop(event)
                    || !insidePreview(event.getX(), event.getY())) {
                tapPointer = -1;
                return true;
            }
            if (action == MotionEvent.ACTION_UP) {
                tapPointer = -1;
                clickU = event.getX() / (double) getWidth();
                clickV = event.getY() / (double) getHeight();
                clickFromTouch = true;
                try { performClick(); }
                finally { clickFromTouch = false; }
            }
            return true;
        }
        private boolean insidePreview(float x, float y) {
            return x >= 0 && y >= 0 && x <= getWidth() && y <= getHeight();
        }
        private boolean movedBeyondSlop(MotionEvent event) {
            // Coalesced movement must also cancel a drag that returns near its start.
            for (int i = -1; i < event.getHistorySize(); i++) {
                float x = i < 0 ? event.getX() : event.getHistoricalX(i);
                float y = i < 0 ? event.getY() : event.getHistoricalY(i);
                double dx = x - tapDownX, dy = y - tapDownY;
                if (dx * dx + dy * dy > (double) touchSlop * touchSlop) return true;
            }
            return false;
        }
        @Override public boolean performClick() {
            super.performClick();
            if (!running || !settings.touchRipples() || getWidth() <= 0 || getHeight() <= 0) return false;
            // Accessibility and keyboard clicks create a centered wave.
            renderer.touch(clickFromTouch ? clickU : .5, clickFromTouch ? clickV : .5);
            invalidate();
            return true;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            long now = System.nanoTime();
            double dt = running && lastNanos != 0 ? Math.min(.5, (now - lastNanos) / 1e9) : 0;
            lastNanos = running ? now : 0;
            renderer.setPointBudget(budget.getPointCount());
            renderer.render(canvas, dt);
            if (running) budget.observeFrame((System.nanoTime() - now) / 1e6, dt);
        }
    }
}
