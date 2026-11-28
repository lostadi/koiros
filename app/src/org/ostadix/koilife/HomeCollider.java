/*
 * Koiros collisions with the home screen's apps. GPL-3.0-or-later; see LICENSE.
 */
package org.ostadix.koilife;

import java.util.Arrays;

/**
 * Finds whether a drawn dot overlaps a home-screen shape and how to push it out.
 *
 * <p>The shapes of a {@link HomeLayout} are binned into a coarse grid over the
 * screen, so each dot tests only the one or two shapes near it, and a dot far
 * from every app costs one array read. The grid is rebuilt only when the
 * layout, the page position or the screen size changes. No allocations after
 * the first build at a given screen size.
 */
public final class HomeCollider {
    private static final int CELL = 48;
    private HomeLayout layout;
    private double builtShift = Double.NaN, margin;
    private int width, height, columns, rows, count;
    private float[] cx = new float[HomeLayout.MAX_SHAPES], cy = new float[HomeLayout.MAX_SHAPES];
    private float[] hx = new float[HomeLayout.MAX_SHAPES], hy = new float[HomeLayout.MAX_SHAPES];
    private float[] r = new float[HomeLayout.MAX_SHAPES];
    private int[] cellStart = new int[1], cellItems = new int[16];
    private final double[] normal = new double[2];

    /** True when there are shapes to collide with. */
    public boolean active() { return count > 0; }
    public int getShapeCount() { return count; }

    /**
     * Places the layout's shapes for a canvas of this size. Paged shapes are
     * moved by shiftPx. A layout read in another orientation is ignored.
     */
    public void prepare(HomeLayout next, double shiftPx, int canvasWidth, int canvasHeight, double contactMargin) {
        if (next == layout && shiftPx == builtShift && canvasWidth == width && canvasHeight == height && contactMargin == margin) return;
        layout = next; builtShift = shiftPx; width = canvasWidth; height = canvasHeight; margin = contactMargin;
        count = 0;
        if (next == null || next.count == 0 || canvasWidth <= 0 || canvasHeight <= 0
                || next.screenWidth <= 0 || next.screenHeight <= 0
                || (next.screenWidth > next.screenHeight) != (canvasWidth > canvasHeight)) {
            return;
        }
        double sx = canvasWidth / (double) next.screenWidth, sy = canvasHeight / (double) next.screenHeight;
        for (int k = 0; k < next.count; k++) {
            int o = k * HomeLayout.STRIDE;
            double x = (next.shapes[o] + (next.paged[k] ? shiftPx : 0)) * sx, y = next.shapes[o + 1] * sy;
            double ex = next.shapes[o + 2] * sx, ey = next.shapes[o + 3] * sy;
            if (x + ex < -margin || x - ex > canvasWidth + margin) continue;
            cx[count] = (float) x; cy[count] = (float) y; hx[count] = (float) ex; hy[count] = (float) ey;
            r[count] = (float) Math.min(next.shapes[o + 4] * Math.min(sx, sy), Math.min(ex, ey));
            count++;
        }
        columns = (canvasWidth + CELL - 1) / CELL; rows = (canvasHeight + CELL - 1) / CELL;
        int cells = columns * rows;
        if (cellStart.length < cells + 1) cellStart = new int[cells + 1];
        Arrays.fill(cellStart, 0, cells + 1, 0);
        // Two passes: count the shapes touching each cell, then fill.
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < count; k++) {
                int c0 = cellOf(cx[k] - hx[k] - margin, columns), c1 = cellOf(cx[k] + hx[k] + margin, columns);
                int r0 = cellOf(cy[k] - hy[k] - margin, rows), r1 = cellOf(cy[k] + hy[k] + margin, rows);
                for (int row = r0; row <= r1; row++) {
                    for (int col = c0; col <= c1; col++) {
                        int cell = row * columns + col;
                        if (pass == 0) cellStart[cell + 1]++;
                        else cellItems[cursor[cell]++] = k;
                    }
                }
            }
            if (pass == 0) {
                for (int c = 0; c < cells; c++) cellStart[c + 1] += cellStart[c];
                if (cellItems.length < cellStart[cells]) cellItems = new int[cellStart[cells] * 2];
                if (cursor.length < cells) cursor = new int[cells];
                System.arraycopy(cellStart, 0, cursor, 0, cells);
            }
        }
    }
    private int[] cursor = new int[1];

    private static int cellOf(double v, int limit) {
        return Math.max(0, Math.min(limit - 1, (int) Math.floor(v / CELL)));
    }

    /**
     * If (x, y) lies within the contact margin of a shape, writes the outward
     * normal to out[0..1] and the distance to move along it to out[2], and returns true.
     * The deepest contact wins.
     */
    public boolean collide(double x, double y, double[] out) {
        if (count == 0 || x < 0 || y < 0 || x >= width || y >= height) return false;
        int cell = ((int) (y / CELL)) * columns + (int) (x / CELL);
        if (cell < 0 || cell >= columns * rows) return false;
        double deepest = 0;
        for (int p = cellStart[cell], end = cellStart[cell + 1]; p < end; p++) {
            int k = cellItems[p];
            double d = HomeLayout.distance(x, y, cx[k], cy[k], hx[k], hy[k], r[k], normal);
            double push = margin - d;
            if (push > deepest) { deepest = push; out[0] = normal[0]; out[1] = normal[1]; out[2] = push; }
        }
        return deepest > 0;
    }
}
