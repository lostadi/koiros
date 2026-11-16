/*
 * All ten original Artificial Koi forms, adapted from Studio Coldstream
 * (mathewmolt), https://codeberg.org/mathewmolt/ArtificialKoi,
 * commit 8834094e2357e5482e1495b80516f12360ff72dc:
 * LiveWallpaperService.kt and lib/koi_algorithms.dart.
 * The upstream artwork credits @yuruyurau as its inspiration.
 *
 * GPL-3.0-or-later. See the accompanying LICENSE and CREDITS.
 */
package org.ostadix.koilife;

import static java.lang.Math.*;

/** Allocation-free original forms in a centered, 400-unit reference space. */
public final class KoiForms {
    public static final int COUNT = 10;
    private static final String[] NAMES = {
        "Koi", "Jellyfish", "Manta", "Coral", "Helix", "Breathe", "Bloom", "Trance", "Koi II", "Nebula"
    };
    private static final double[] SPEED_RATIOS = {
        1.0, 60.0 / 45.0, 60.0 / 45.0, 60.0 / 40.0, 1.0,
        60.0 / 360.0, 60.0 / 30.0, 60.0 / 240.0, 60.0 / 45.0, 60.0 / 30.0
    };

    private KoiForms() { }

    public static String name(int form) {
        checkForm(form);
        return NAMES[form];
    }

    /** Original time-step ratio relative to Koi's PI/60 at 60 frames/second. */
    public static double speedRatio(int form) {
        checkForm(form);
        return SPEED_RATIOS[form];
    }

    /**
     * Evaluates one original form, returning false at its guarded singularities.
     * time is that form's accumulated radians; callers may use baseTime*speedRatio.
     * index is the current renderer index, while creature is its explicit phase
     * group (normally index%3). Forms 0..8 sample the full original 20,000-point
     * domain at every quality setting. Nebula preserves the original 30,000-point
     * normalization and its integer two-population cutoff. out receives x,y
     * relative to the reference-space center (200,200), without allocation.
     */
    public static boolean evaluate(int form, int index, int pointCount, int creature,
                                   double time, double sf, double sa, double[] out) {
        checkForm(form);
        if (pointCount <= 0 || index < 0 || index >= pointCount || creature < 0) {
            throw new IllegalArgumentException("Invalid point or creature index");
        }
        if (out == null || out.length < 2) {
            throw new IllegalArgumentException("Coordinate output needs two elements");
        }
        out[0] = 0.0;
        out[1] = 0.0;
        if (!finite(time) || !finite(sf) || !finite(sa)) return false;
        double sourceIndex = index * (20000.0 / pointCount);
        double y, k, e, o, d, q, c, x, py;
        switch (form) {
            case 0: // Koi: identical to the original integration at coupling zero.
                y = sourceIndex / 500.0;
                k = cos(y * 5.3 * sf) * (y < 8.6 ? 22.0 : 10.5);
                if (abs(k) < 1e-6) return false;
                e = y / 7.6 - 13.4;
                o = sqrt(k * k + e * e) / 6.2;
                q = (k * y / 12.4 + 71 + sin(y * 1.1) / k
                        + k * cos(y / 2.1) * 1.9 * sin(o * 3.1 - e / 1.9 - time)) * sa;
                c = o * e / 18.4 + time / 8 + creature * 8.0;
                x = q * cos(c);
                py = q * sin(c) + 93 * cos(c / 2) + 6 * sin(c * 1.5);
                break;
            case 1: // Jellyfish
                y = sourceIndex / 99.0;
                k = 8.6 * cos(y * 2.1 * sf);
                e = y / 8.4 - 11.6;
                d = (k * k + e * e) / 76.0 + 1;
                q = (81 - e * sin(k * 1.05) + k / d
                        * (8.5 + 4.2 * sin(sin(d * d * 0.97 + e / 8.5 - time)))) * sa;
                c = d / 2.1 - cos(d * 2.05) / 4.6 - time / 16 + creature * 3.0;
                x = q * sin(c);
                py = (q + 38) * cos(c) + 6 * sin(c * 2);
                break;
            case 2: // Manta
                y = sourceIndex / 89.0;
                k = 9.4 * cos(y * 1.04 * sf);
                e = y / 8.3 - 11.8;
                d = (k * k + e * e) / 95.0 + 1;
                q = (77 - e * sin(k * 0.96) + k / d
                        * (8.4 + 5.2 * sin(d * 2.1 + cos(d * 4.8 - time * 2)))) * sa;
                c = d / 1.55 + e / 64 * sin(time + d * 1.05) - time / 8 + creature * 3.0;
                x = q * sin(c);
                py = (q + 42) * cos(c);
                break;
            case 3: // Coral
                y = sourceIndex / 500.0;
                k = cos(y * 97 * sf) * (y < 9.4 ? 21 + sin(y * 1.1) * 7.5 : 10.5);
                e = y / 7.8 - 13.2;
                o = sqrt(k * k + e * e) / 6.3;
                q = (k * y / 18 + 61 + k * sin(y) * sin(o * 2.1 - e / 3.2 - time)) * sa;
                c = o / 3.1 - e / 5.2 - time / 8 + creature * 8.0;
                x = q * sin(c) - 84 * cos(c / 3);
                py = (q + 66) * cos(c) + 4 * sin(c * 2);
                break;
            case 4: // Helix
                y = sourceIndex / 350.0;
                k = cos(y * 8.8 * sf) * 12.5;
                if (abs(k) < 1e-6) return false;
                e = y / 8.3;
                o = sqrt(k * k + e * e) / 7.8;
                q = (k * sin(y / 18 + 1.1) + 71 + k * sin(y * 1.03) + 1 / k
                        + k * (1.9 + sin(e + o * 3.9 - time * 2))) * sa;
                c = o / 4.1 + e / 5.3 - time / 8 + creature * 3.0;
                x = q * 0.72 * cos(c);
                py = (q + 28) * sin(c) + y * 0.95 * sin(c * 2);
                break;
            case 5: // Breathe
                y = sourceIndex / 500.0;
                k = cos(y * 8.7 * sf) * (y < 9.3 ? sin(time + y * 1.05) * 20 : 8.6);
                e = y / 8.2 - 12.8;
                o = sqrt(k * k + e * e);
                q = (y / 18 * k + 71 + k * cos(y * 0.97) * 1.9 * sin(o * 1.03 - time * 8)) * sa;
                c = o * e / 92 + time + creature * 9.0;
                x = q * sin(c) + 43 * sin(c / 2);
                py = (q + 67) * cos(c);
                break;
            case 6: // Bloom
                y = sourceIndex / 500.0;
                k = cos(y * 9.3 * sf) * (y < 5.3 ? sin(time / 8 + y * 1.08) * 33 : 11.5);
                e = y / 7.8 - 13.3;
                o = sqrt(k * k + e * e) / 6.2;
                q = (k * y / 18 + 51 + k * sin(y) * sin(o * 2.1 - e / 5.3 - time)) * sa;
                c = o / 3.1 - e / 5.2 - time / 8 + creature * 8.0;
                x = q * sin(c) - 75 * cos(c / 3);
                py = (q + 72) * cos(c);
                break;
            case 7: // Trance
                y = sourceIndex / 500.0;
                k = cos(y * 8.8 * sf) * (y < 9.2 ? sin(time + y * 0.96) * 27 : 11.5);
                e = y / 8.2 - 12.7;
                o = sqrt(k * k + e * e) / 6.1;
                q = (k * y / 14.5 + 77 + k * sin(y * 1.04)
                        * (1 + sin(o * 3.9 - e * 1.04 - time * 8))) * sa;
                c = o / 2.05 - e / 4.1 - time + creature * 15.0;
                x = q * sin(c) + 67 * sin(c / 3);
                py = q / 0.72 * cos(c);
                break;
            case 8: // Koi II
                y = sourceIndex / 500.0;
                k = cos(y * 5.15 * sf) * (y < 10.6 ? 21.5 : 10.6);
                if (abs(k) < 1e-6) return false;
                e = y / 7.9 - 13.2;
                o = sqrt(k * k + e * e) / 6.1;
                q = (k * 2.05 + 51 + cos(18 / k) + k * cos(y / 2.06)
                        * (1 + sin(o * 3.9 - e * 2.05 - time))) * sa;
                c = o / 1.53 - e / 5.1 - time / 8 + creature * 8.0;
                x = q * sin(c);
                py = q * cos(c) - 76 * sin(c / 3);
                break;
            case 9: // Nebula: upstream's second coordinate deliberately has no +200.
                double si = index * (30000.0 / pointCount);
                k = index < (pointCount * 2L) / 3L
                        ? sin(si * sf / 9.3) * 9.4
                        : 4.2 * cos(si * sf / 47) * cos(si * sf / 3600);
                e = si / 960 - 12.3;
                d = (k * k + e * e) / 96.0 + 1;
                q = (k * (4.1 + sin(d * 15.5 - time + k)) - 5.3 * sin(atan2(k, e) * 9)) * sa;
                c = d * 1.12 - time / 18 + creature * 3.0;
                x = q + 62 * sin(c);
                py = (q + 41) * sin(c - d) + d * 77 - 200;
                break;
            default:
                return false;
        }
        if (!finite(x) || !finite(py)) return false;
        out[0] = x;
        out[1] = py;
        return true;
    }

    private static void checkForm(int form) {
        if (form < 0 || form >= COUNT) throw new IllegalArgumentException("Form must be from 0 through 9");
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
