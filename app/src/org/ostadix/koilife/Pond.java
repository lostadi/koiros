package org.ostadix.koilife;

import android.graphics.Canvas;

/**
 * What the wallpaper and its preview need from a pond, whichever model draws
 * it: {@link LatticeRenderer} (dots obeying their own equations) or
 * {@link KoiRenderer} (the classic forms).
 */
interface Pond {
    int TOUCH_DOWN = 0, TOUCH_MOVE = 1, TOUCH_UP = 2, TOUCH_TAP = 3;

    void configure(int rate, double strength, int requestedPoints, boolean visibleField, boolean autoReseed);
    void setAnimationSpeeds(double motion, double form, double color);
    void configurePopulation(int count, boolean varying, boolean collisions, boolean merging);
    void setTouchRipplesEnabled(boolean enabled);
    void setPageOffset(double xOffset, double xOffsetStep, double xPixelOffset, double surfaceWidth);
    /** A normalized screen tap for water ripples. */
    void touch(double u, double v);
    /** A finger moving over the wallpaper: screen fractions, and screen fractions per second. */
    void stir(double u, double v, double velocityU, double velocityV);
    /** A companion touch in normalized screen coordinates; kind is one of TOUCH_*. */
    void companionTouch(double u, double v, int kind);
    void greet(double strength);
    void setMood(double value);
    double getLiveliness();
    double getAffection();
    void setAffection(double value);
    void tellDay(double[] hues, double[] weights, int count);
    void reseed(long seed);
    void setPointBudget(int requested);
    int getPointCount();
    void render(Canvas canvas, double dtSeconds);
    double getSimulationCostMs();
    double getGeometryCostMs();
    double getCanvasCostMs();
    int getLastLinkCount();
    int getLastPointCount();
    int getMergeCount();
    int getDivideCount();
    int getMergedBodyCount();
    /** Generations of the hidden Life world. */
    long getGeneration();
}
