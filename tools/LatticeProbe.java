package org.ostadix.koilife;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

/**
 * Real Android Canvas frames of either model at the person's settings: how much
 * of the screen is lit, how much changes per second, events, and cost.
 *
 * Compile it with the app sources against android.jar, dex it with d8, then run
 * on a device (root or adb shell) with the dex on the class path:
 *   CLASSPATH=probe.dex app_process /system/bin org.ostadix.koilife.LatticeProbe  *       OUT lattice|classic SECONDS FPS WIDTH HEIGHT SHOT_SECONDS[,...] [DRIFT]
 * "Lit" counts cells of a 16x36 grid holding bright pixels; change is the mean
 * absolute brightness change of that grid per second.
 */
public final class LatticeProbe {
    public static void main(String[] a) throws Exception {
        File out = new File(a[0]); out.mkdirs();
        boolean lattice = a[1].equals("lattice");
        double seconds = Double.parseDouble(a[2]), fps = Double.parseDouble(a[3]);
        int w = Integer.parseInt(a[4]), h = Integer.parseInt(a[5]);
        String[] shotList = a[6].split(",");
        double motion = a.length > 7 ? Double.parseDouble(a[7]) : 0.11;
        long seed = 821166217176535313L;
        Pond r = lattice ? new LatticeRenderer(seed) : new KoiRenderer(seed);
        r.configure(1, 1.0, 14000, false, true);
        r.setAnimationSpeeds(motion, 0.10, 0.41);
        r.configurePopulation(7, true, true, true);
        r.setTouchRipplesEnabled(false);
        r.setPointBudget(9000);
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);
        int[] px = new int[w * h];
        final int GX = 16, GY = 36;
        int[] everLit = new int[GX * GY];
        int samples = 0, shots = 0;
        double occ = 0, top = 0, mid = 0, bot = 0, changeSum = 0, changePerLit = 0; int changeN = 0;
        float[] prev = null;
        double dt = 1 / fps, t = 0, nextSample = 1;
        int frames = 0; double sim = 0, geo = 0, can = 0, worst = 0, bonded = 0;
        while (t < seconds) {
            r.render(canvas, frames == 0 ? 0 : dt);
            if (frames > 0) t += dt;
            frames++;
            sim += r.getSimulationCostMs(); geo += r.getGeometryCostMs(); can += r.getCanvasCostMs();
            worst = Math.max(worst, r.getSimulationCostMs() + r.getGeometryCostMs());
            if (t >= nextSample) {
                nextSample += 1;
                bm.getPixels(px, 0, w, 0, 0, w, h);
                int lit = 0, lt = 0, lm = 0, lb = 0;
                float[] small = new float[GX * GY];
                for (int gy = 0; gy < GY; gy++) for (int gx = 0; gx < GX; gx++) {
                    int x0 = gx * w / GX, x1 = (gx + 1) * w / GX, y0 = gy * h / GY, y1 = (gy + 1) * h / GY;
                    int bright = 0; double sum = 0; int cells = 0;
                    for (int y = y0; y < y1; y += 2) for (int x = x0; x < x1; x += 2) {
                        int c = px[y * w + x];
                        int m = Math.max((c >> 16) & 255, Math.max((c >> 8) & 255, c & 255));
                        sum += m; cells++;
                        if (m > 90) bright++;
                    }
                    small[gy * GX + gx] = (float) (sum / cells);
                    if (bright >= 3) { lit++; everLit[gy * GX + gx]++; if (gy < GY / 3) lt++; else if (gy < 2 * GY / 3) lm++; else lb++; }
                }
                double litFraction = lit / (double) (GX * GY);
                occ += litFraction; top += lt / (GX * GY / 3.0); mid += lm / (GX * GY / 3.0); bot += lb / (GX * GY / 3.0);
                if (prev != null) {
                    double d = 0; for (int i = 0; i < small.length; i++) d += Math.abs(small[i] - prev[i]);
                    d /= small.length; changeSum += d; changePerLit += d / Math.max(.02, litFraction); changeN++;
                }
                prev = small;
                if (lattice) bonded += ((LatticeRenderer) r).getLattice().bondedFraction();
                samples++;
            }
            for (String s : shotList) {
                double at = Double.parseDouble(s);
                if (t >= at && t - dt < at) {
                    try (FileOutputStream o = new FileOutputStream(new File(out, String.format(Locale.US, "%s-%04.0fs.png", a[1], at)))) {
                        bm.compress(Bitmap.CompressFormat.PNG, 100, o);
                    }
                    shots++;
                }
            }
        }
        int ever = 0; for (int v : everLit) if (v > 0) ever++;
        System.out.printf(Locale.US, "%s: seconds=%.0f occupancy=%.3f top=%.3f mid=%.3f bottom=%.3f everLit=%.3f change/s=%.2f changePerLit=%.2f merges=%d divides=%d bonded=%.3f%n",
            a[1], t, occ / samples, top / samples, mid / samples, bot / samples, ever / (double) everLit.length,
            changeSum / Math.max(1, changeN), changePerLit / Math.max(1, changeN), r.getMergeCount(), r.getDivideCount(), lattice ? bonded / samples : -1);
        System.out.printf(Locale.US, "  cost ms: simulation=%.2f geometry=%.2f canvas(software)=%.2f worstSimGeo=%.2f points=%d links=%d shots=%d%n",
            sim / frames, geo / frames, can / frames, worst, r.getLastPointCount(), r.getLastLinkCount(), shots);
    }
}
