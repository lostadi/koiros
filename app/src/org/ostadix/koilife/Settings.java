package org.ostadix.koilife;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.SecureRandom;

final class Settings {
    static final String STORE = "pond";
    static final int MIN_SPEED_PERCENT = 2, MAX_SPEED_PERCENT = 200;
    final SharedPreferences prefs;
    Settings(Context context) {
        prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        if (!prefs.contains("seed")) prefs.edit().putLong("seed", new SecureRandom().nextLong()).apply();
    }
    long seed() { return prefs.getLong("seed", 1L); }
    int rate() { return prefs.getInt("rate", 4) == 1 ? 1 : 4; }
    double strength() { return Math.max(0, Math.min(100, prefs.getInt("strength", 85))) / 100.0; }
    double motionSpeed() { return speed("motion_speed", 18); }
    double formSpeed() { return speed("form_speed", 18); }
    double colorSpeed() { return speed("color_speed", 25); }
    private double speed(String key, int defaultPercent) {
        return Math.max(MIN_SPEED_PERCENT, Math.min(MAX_SPEED_PERCENT,
            prefs.getInt(key, defaultPercent))) / 100.0;
    }
    int count() { return Math.max(1, Math.min(9, prefs.getInt("count", 3))); }
    boolean varyCount() { return prefs.getBoolean("vary_count", true); }
    boolean collisions() { return prefs.getBoolean("collisions", true); }
    boolean field() { return prefs.getBoolean("field", false); }
    boolean touchRipples() { return prefs.getBoolean("touch_ripples", true); }
    boolean autoReseed() { return prefs.getBoolean("auto_reseed", true); }
    void newSeed() { prefs.edit().putLong("seed", new SecureRandom().nextLong()).apply(); }
    void configure(KoiRenderer renderer) {
        renderer.configure(rate(), strength(), 14000, field(), autoReseed());
        renderer.setAnimationSpeeds(motionSpeed(), formSpeed(), colorSpeed());
        renderer.configurePopulation(count(), varyCount(), collisions());
        renderer.setTouchRipplesEnabled(touchRipples());
    }
}
