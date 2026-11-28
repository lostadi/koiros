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
    /** New in 2.0, so existing installs receive it on. */
    boolean merging() { return prefs.getBoolean("merging", true); }
    /** New in 2.0: forms respond to touch and to their person's return. */
    boolean companion() { return prefs.getBoolean("companion", true); }
    /** New in 2.0: colours of apps used, read on this phone with usage access. */
    boolean knowMyDay() { return prefs.getBoolean("know_my_day", true); }
    boolean field() { return prefs.getBoolean("field", false); }
    /** New in 3.0: dots that obey their own equations; "classic" keeps the original forms. */
    boolean lattice() { return !"classic".equals(prefs.getString("model", "lattice")); }
    Pond createPond(long seed) { return lattice() ? new LatticeRenderer(seed) : new KoiRenderer(seed); }
    /** True when the pond's model differs from the saved choice. */
    boolean modelChanged(Pond pond) { return (pond instanceof LatticeRenderer) != lattice(); }
    /** New in 3.2: dots collide with the apps on the home screen (needs the layout reader enabled). */
    boolean homeCollisions() { return prefs.getBoolean("home_collisions", true); }
    /** New in 3.3: the phone's theme follows the pond's colours (when Koiros may set it). */
    boolean themeFollow() { return prefs.getBoolean("theme_follow", true); }
    boolean touchRipples() { return prefs.getBoolean("touch_ripples", true); }
    boolean autoReseed() { return prefs.getBoolean("auto_reseed", true); }
    void newSeed() { prefs.edit().putLong("seed", new SecureRandom().nextLong()).apply(); }
    void configure(Pond renderer) {
        renderer.configure(rate(), strength(), 14000, field(), autoReseed());
        renderer.setAnimationSpeeds(motionSpeed(), formSpeed(), colorSpeed());
        renderer.configurePopulation(count(), varyCount(), collisions(), merging());
        renderer.setTouchRipplesEnabled(touchRipples());
    }
}
