package org.ostadix.koilife;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Offscreen review of the shared world: renders real Android Canvas frames
 * with a given configuration, saves frames around fusions and divisions, and
 * reports how often bodies merge and what each frame costs.
 *
 * Run with the APK and this probe's dex on the classpath:
 *   app_process /system/bin org.ostadix.koilife.MergeProbe OUT SEED SECONDS FPS
 *       MOTION FORM COLOR COUNT RATE STRENGTH [WIDTH HEIGHT]
 */
public final class MergeProbe {
    public static void main(String[] args) throws Exception {
        File out = new File(args[0]);
        long seed = Long.parseLong(args[1]);
        double seconds = Double.parseDouble(args[2]), fps = Double.parseDouble(args[3]);
        double motion = Double.parseDouble(args[4]), form = Double.parseDouble(args[5]);
        double color = Double.parseDouble(args[6]);
        int count = Integer.parseInt(args[7]), rate = Integer.parseInt(args[8]);
        double strength = Double.parseDouble(args[9]);
        int width = args.length > 10 ? Integer.parseInt(args[10]) : 640;
        int height = args.length > 11 ? Integer.parseInt(args[11]) : 1428;
        out.mkdirs();
        KoiRenderer renderer = new KoiRenderer(seed);
        renderer.configure(rate, strength, 14000, false, true);
        renderer.setAnimationSpeeds(motion, form, color);
        renderer.configurePopulation(count, true, true, true);
        renderer.setTouchRipplesEnabled(false);
        renderer.setPointBudget(12018);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        double dt = 1.0 / fps, time = 0;
        int frames = 0, shots = 0, lastMerges = 0, lastDivides = 0;
        double geometry = 0, simulation = 0, worstGeometry = 0, pendingShotAt = -1, bridgeQuietUntil = 0;
        String pendingLabel = null;
        int maxMerged = 0;
        long mergedSamples = 0, unifiedSamples = 0;
        long failHue = 0, failPhase = 0, failGroup = 0, failForm = 0;
        double longestApart = 0;
        int[][] apartRun = new int[9][9];
        double worstPhase = 0;
        StringBuilder timeline = new StringBuilder();
        double[] hueAtDay = new double[9];
        boolean toldDay = false;
        String dayReport = "";
        while (time < seconds) {
            if (!toldDay && time >= 30) {
                for (int c = 0; c < 9; c++) hueAtDay[c] = renderer.getHueIdentity(c);
                renderer.tellDay(new double[] {140, 20}, new double[] {1, .6}, 2);
                toldDay = true;
            }
            if (toldDay && dayReport.isEmpty() && time >= 90) {
                StringBuilder shift = new StringBuilder();
                for (int c = 0; c < 9; c++) {
                    if (renderer.getCreatureOpacity(c) < .05) continue;
                    double d = Math.abs(((renderer.getHueIdentity(c) - hueAtDay[c]) % 360 + 540) % 360 - 180);
                    shift.append(String.format(Locale.US, "%s%.0f", shift.length() == 0 ? "" : ", ", d));
                }
                dayReport = shift.toString();
            }
            renderer.render(canvas, frames == 0 ? 0 : dt);
            time += frames == 0 ? 0 : dt;
            frames++;
            geometry += renderer.getGeometryCostMs();
            simulation += renderer.getSimulationCostMs();
            worstGeometry = Math.max(worstGeometry, renderer.getGeometryCostMs());
            maxMerged = Math.max(maxMerged, renderer.getMergedBodyCount());
            if (renderer.getMergeCount() != lastMerges) {
                lastMerges = renderer.getMergeCount();
                timeline.append(String.format(Locale.US, "    {\"t\": %.1f, \"event\": \"merged\"},\n", time));
                pendingShotAt = time + 0.25; pendingLabel = "merged";
            }
            if (renderer.getDivideCount() != lastDivides) {
                lastDivides = renderer.getDivideCount();
                timeline.append(String.format(Locale.US, "    {\"t\": %.1f, \"event\": \"divided\"},\n", time));
                pendingShotAt = time + 0.25; pendingLabel = "divided";
            }
            // Bridges are brightest midway through a fusion or division.
            if (pendingShotAt < 0 && shots < 14 && time >= bridgeQuietUntil) {
                for (int a = 0; a < 9 && pendingShotAt < 0; a++) for (int b = a + 1; b < 9; b++) {
                    double bond = renderer.getBond(a, b);
                    if (bond > .45 && bond < .55 && renderer.getCreatureOpacity(a) > .5 && renderer.getCreatureOpacity(b) > .5) {
                        pendingShotAt = time; pendingLabel = "bridge"; bridgeQuietUntil = time + 20; break;
                    }
                }
            }
            // How alike merged members are: they should converge to one body.
            for (int a = 0; a < 9; a++) for (int b = a + 1; b < 9; b++) {
                if (renderer.getBond(a, b) < .999) continue;
                mergedSamples++;
                double hue = Math.abs(((renderer.getHueIdentity(a) - renderer.getHueIdentity(b)) % 360 + 540) % 360 - 180);
                double phase = Math.abs(renderer.getPhase(a) - renderer.getPhase(b));
                double group = Math.abs(renderer.getGroupPhase(a) - renderer.getGroupPhase(b));
                boolean sameForm = renderer.getFormIndex(a) == renderer.getFormIndex(b)
                    && renderer.getNextFormIndex(a) == renderer.getNextFormIndex(b)
                    && Math.abs(renderer.getFormBlend(a) - renderer.getFormBlend(b)) < .02;
                if (hue < 2 && phase < .05 && group < .01 && sameForm) unifiedSamples++;
                else {
                    if (hue >= 2) failHue++;
                    if (phase >= .05) failPhase++;
                    if (group >= .01) failGroup++;
                    if (!sameForm) failForm++;
                    longestApart = Math.max(longestApart, ++apartRun[a][b] / fps);
                }
                if (hue < 2 && phase < .05 && group < .01 && sameForm) apartRun[a][b] = 0;
                worstPhase = Math.max(worstPhase, phase);
            }
            boolean periodic = frames == 1 || (int) (time / 60) != (int) ((time - dt) / 60);
            if ((pendingShotAt >= 0 && time >= pendingShotAt && shots < 14) || periodic) {
                String label = periodic && !(pendingShotAt >= 0 && time >= pendingShotAt) ? "t" : pendingLabel;
                File file = new File(out, String.format(Locale.US, "frame-%03d-%s-%05.1fs.jpg", shots, label, time));
                try (FileOutputStream stream = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream);
                }
                shots++;
                if (!periodic || pendingShotAt >= 0 && time >= pendingShotAt) pendingShotAt = -1;
            }
        }
        try (PrintWriter report = new PrintWriter(new File(out, "merge-report.json"), "UTF-8")) {
            report.printf(Locale.US, "{\n  \"seed\": %d,\n  \"seconds\": %.1f,\n  \"fps\": %.1f,\n", seed, time, fps);
            report.printf(Locale.US, "  \"motion\": %.2f, \"form\": %.2f, \"color\": %.2f, \"count\": %d, \"rate\": %d,\n",
                motion, form, color, count, rate);
            report.printf(Locale.US, "  \"merges\": %d,\n  \"divides\": %d,\n  \"max_merged_bodies\": %d,\n",
                renderer.getMergeCount(), renderer.getDivideCount(), maxMerged);
            report.printf(Locale.US, "  \"mean_geometry_ms\": %.2f,\n  \"worst_geometry_ms\": %.2f,\n  \"mean_simulation_ms\": %.2f,\n",
                geometry / frames, worstGeometry, simulation / frames);
            report.printf(Locale.US, "  \"merged_pair_frames\": %d,\n  \"unified_pair_frames\": %d,\n  \"worst_merged_phase_gap\": %.3f,\n",
                mergedSamples, unifiedSamples, worstPhase);
            report.printf(Locale.US, "  \"not_unified_because\": {\"hue\": %d, \"phase\": %d, \"rotation\": %d, \"form\": %d},\n  \"longest_not_unified_seconds\": %.1f,\n",
                failHue, failPhase, failGroup, failForm, longestApart);
            report.printf(Locale.US, "  \"points\": %d,\n  \"world_generation\": %d,\n  \"world_population\": %d,\n",
                renderer.getLastPointCount(), renderer.getLifeField().getGeneration(), renderer.getLifeField().getPopulation());
            report.printf("  \"day_hue_shift_after_60s_degrees\": [%s],\n", dayReport);
            report.printf("  \"timeline\": [\n%s    {}\n  ]\n}\n", timeline);
        }
        System.out.println("MergeProbe wrote " + shots + " frames; merges=" + renderer.getMergeCount()
            + " divides=" + renderer.getDivideCount() + " maxMerged=" + maxMerged
            + String.format(Locale.US, " meanGeometryMs=%.2f unified=%d/%d", geometry / frames, unifiedSamples, mergedSamples));
    }
}
