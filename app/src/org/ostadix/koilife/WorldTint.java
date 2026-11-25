/*
 * Koiros shared-world colour. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

/**
 * Colour carried by the shared Life world.
 *
 * Every cell holds a hue vector whose length is its strength. Bodies paint
 * their hue into the water they pass through. Once per Life generation the
 * colour spreads along living cells, so it travels with gliders and growing
 * patterns, and fades slowly where nothing lives. Hue is circular, so all
 * mixing happens on vectors. Coordinates wrap exactly like {@link LifeField}.
 * No allocations after construction.
 */
public final class WorldTint {
    private final int columns, rows, cellCount;
    private float[] tintX, tintY, nextX, nextY;
    private final boolean[] alive;

    public WorldTint(int columns, int rows) {
        if (columns <= 0 || rows <= 0 || (long) columns * rows > 262144L) {
            throw new IllegalArgumentException("Tint dimensions must be positive and total at most 262144 cells");
        }
        this.columns = columns;
        this.rows = rows;
        cellCount = columns * rows;
        tintX = new float[cellCount];
        tintY = new float[cellCount];
        nextX = new float[cellCount];
        nextY = new float[cellCount];
        alive = new boolean[cellCount];
    }

    public int getColumns() { return columns; }
    public int getRows() { return rows; }

    /**
     * Adds a hue (degrees) to a wrapped disc around (u, v). The contribution
     * falls linearly to zero at the radius; amount is clamped to [0, 1], and no
     * cell's strength exceeds one.
     */
    public void paint(double u, double v, double radiusCells, double hueDegrees, double amount) {
        if (!(radiusCells > 0.0) || Double.isInfinite(radiusCells) || !finite(hueDegrees)
                || !(amount > 0.0)) return;
        double weight = Math.min(1.0, amount);
        double limit = Math.min(radiusCells, Math.max(columns, rows));
        int reach = (int) Math.ceil(limit);
        int centerX = (int) Math.floor(wrap(u) * columns) % columns;
        int centerY = (int) Math.floor(wrap(v) * rows) % rows;
        double radians = Math.toRadians(hueDegrees);
        double hx = Math.cos(radians), hy = Math.sin(radians);
        for (int dy = -reach; dy <= reach; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                double distance = Math.sqrt(dx * dx + dy * dy);
                if (distance > limit) continue;
                double add = weight * (1.0 - distance / (limit + 1e-9));
                int index = Math.floorMod(centerY + dy, rows) * columns + Math.floorMod(centerX + dx, columns);
                double x = tintX[index] + hx * add, y = tintY[index] + hy * add;
                double length = Math.sqrt(x * x + y * y);
                if (length > 1.0) { x /= length; y /= length; }
                tintX[index] = (float) x;
                tintY[index] = (float) y;
            }
        }
    }

    /**
     * One spreading pass for one Life generation lasting {@code seconds}.
     * Each cell mixes its own colour (weight two) with each living neighbour's
     * (weight one). Colour then fades with time constant {@code fadeSeconds}
     * on empty cells, and three times more slowly on living ones.
     */
    public void spread(LifeField field, double seconds, double fadeSeconds) {
        if (field == null || field.getColumns() != columns || field.getRows() != rows) {
            throw new IllegalArgumentException("Tint and Life dimensions must match");
        }
        if (!(seconds > 0.0) || Double.isInfinite(seconds) || !(fadeSeconds > 0.0)) return;
        field.copyCells(alive);
        float emptyFade = (float) Math.exp(-seconds / fadeSeconds);
        float livingFade = (float) Math.exp(-seconds / (3.0 * fadeSeconds));
        for (int y = 0; y < rows; y++) {
            int up = (y == 0 ? rows - 1 : y - 1) * columns;
            int row = y * columns;
            int down = (y + 1 == rows ? 0 : y + 1) * columns;
            for (int x = 0; x < columns; x++) {
                int left = x == 0 ? columns - 1 : x - 1;
                int right = x + 1 == columns ? 0 : x + 1;
                int index = row + x;
                float sumX = 2f * tintX[index], sumY = 2f * tintY[index], weight = 2f;
                int n;
                n = up + left;      if (alive[n]) { sumX += tintX[n]; sumY += tintY[n]; weight++; }
                n = up + x;         if (alive[n]) { sumX += tintX[n]; sumY += tintY[n]; weight++; }
                n = up + right;     if (alive[n]) { sumX += tintX[n]; sumY += tintY[n]; weight++; }
                n = row + left;     if (alive[n]) { sumX += tintX[n]; sumY += tintY[n]; weight++; }
                n = row + right;    if (alive[n]) { sumX += tintX[n]; sumY += tintY[n]; weight++; }
                n = down + left;    if (alive[n]) { sumX += tintX[n]; sumY += tintY[n]; weight++; }
                n = down + x;       if (alive[n]) { sumX += tintX[n]; sumY += tintY[n]; weight++; }
                n = down + right;   if (alive[n]) { sumX += tintX[n]; sumY += tintY[n]; weight++; }
                float fade = alive[index] ? livingFade : emptyFade;
                nextX[index] = sumX / weight * fade;
                nextY[index] = sumY / weight * fade;
            }
        }
        float[] swap = tintX; tintX = nextX; nextX = swap;
        swap = tintY; tintY = nextY; nextY = swap;
    }

    /** Strength in [0, 1] of one row-major cell. */
    public double strength(int index) {
        return Math.min(1.0, Math.sqrt(tintX[index] * tintX[index] + tintY[index] * tintY[index]));
    }

    /** Hue of one row-major cell in [0, 360); zero where there is no colour. */
    public double hue(int index) {
        if (tintX[index] == 0f && tintY[index] == 0f) return 0.0;
        double degrees = Math.toDegrees(Math.atan2(tintY[index], tintX[index]));
        return degrees < 0 ? degrees + 360.0 : degrees;
    }

    /** out[0] receives the hue in [0, 360) and out[1] the strength at a wrapped point. */
    public void sample(double u, double v, double[] out) {
        if (out == null || out.length < 2) throw new IllegalArgumentException("Tint sample needs two elements");
        int index = ((int) Math.floor(wrap(v) * rows) % rows) * columns
                + (int) Math.floor(wrap(u) * columns) % columns;
        out[0] = hue(index);
        out[1] = strength(index);
    }

    /** Removes all colour. */
    public void clear() {
        java.util.Arrays.fill(tintX, 0f);
        java.util.Arrays.fill(tintY, 0f);
    }

    private static double wrap(double coordinate) {
        if (Double.isNaN(coordinate) || Double.isInfinite(coordinate)) return 0.0;
        return coordinate - Math.floor(coordinate);
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
