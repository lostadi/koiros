package org.ostadix.koilife.tools;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import org.ostadix.koilife.KoiRenderer;
import org.ostadix.koilife.LifeField;

/**
 * Offscreen Android-runtime integration probe, intentionally separate from the APK.
 *
 * Compile against android.jar and the application's Java classes, dex this class,
 * then run app_process with CLASSPATH=<probe.dex>:<application.apk>:
 *   org.ostadix.koilife.tools.RenderProbe <output-directory> [60|180]
 *
 * Timings describe a 480x900 software Canvas, not wallpaper GPU performance.
 * No settings, installed packages, or active wallpaper are changed.
 */
public final class RenderProbe {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 900;
    private static final double DT = 1.0 / 30.0;
    private static final long SEED = 0x4b6f694c696665L;
    private static final int POINTS = 14000;

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: RenderProbe output-directory [60|180]");
        }
        final File directory = new File(args[0]);
        final int frames = args.length == 2 ? Integer.parseInt(args[1]) : 60;
        require(frames == 60 || frames == 180, "Measured frame count must be 60 or 180");
        require(directory.isDirectory() || directory.mkdirs(), "Cannot create output directory");

        final Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bitmap);
        KoiRenderer warmup = renderer(SEED, 4, 0.85);
        for (int frame = 0; frame < 10; frame++) warmup.render(canvas, DT);
        warmup = null;
        System.gc();

        final KoiRenderer main = renderer(SEED, 4, 0.85);
        main.render(canvas, 0.0);
        int[] initial = snapshot(bitmap, directory, "render-initial.jpg");
        final double initialColorPhase = main.getColorPhaseDegrees();
        double oneSecondColorPhase = initialColorPhase;
        checkNumbers(main.getLifeField());
        require(main.getLifeField().getGeneration() == 0, "Initial rendering advanced Life time");

        long[] durations = new long[frames];
        int[] oneSecond = null;
        for (int frame = 0; frame < frames; frame++) {
            final long start = System.nanoTime();
            main.render(canvas, DT);
            durations[frame] = System.nanoTime() - start;
            if (frame == 29) {
                oneSecond = snapshot(bitmap, directory, "render-1s.jpg");
                oneSecondColorPhase = main.getColorPhaseDegrees();
                require(main.getLifeField().getGeneration() == 4,
                        "4 Hz renderer did not produce four generations in one second");
                checkNumbers(main.getLifeField());
            }
        }
        int[] evolved = snapshot(bitmap, directory, "render-evolved.jpg");
        final LifeField life = main.getLifeField();
        final long expectedGenerations = frames * 4L / 30L;
        require(life.getGeneration() == expectedGenerations,
                "4 Hz visible-time generation mismatch: " + life.getGeneration());
        require(life.getReseedCount() == 0, "Short probe unexpectedly auto-reseeded");
        checkNumbers(life);

        // Same visible time and same seed isolate the effect of coupling strength.
        KoiRenderer uncoupled = renderer(SEED, 4, 0.0);
        uncoupled.render(canvas, 0.0);
        for (int frame = 0; frame < 30; frame++) uncoupled.render(canvas, DT);
        int[] zeroCoupling = snapshot(bitmap, directory, "render-coupling-zero-1s.jpg");
        require(uncoupled.getLifeField().getGeneration() == 4,
                "Zero coupling must not stop Life's clock");
        require(sameGridAtOneSecond(SEED, uncoupled.getLifeField()),
                "Coupling strength changed Conway evolution");
        checkNumbers(uncoupled.getLifeField());
        uncoupled = null;
        System.gc();

        KoiRenderer differentSeed = renderer(SEED + 7L, 4, 0.85);
        differentSeed.render(canvas, 0.0);
        for (int frame = 0; frame < 30; frame++) differentSeed.render(canvas, DT);
        int[] reseeded = snapshot(bitmap, directory, "render-different-seed-1s.jpg");
        checkNumbers(differentSeed.getLifeField());
        double differentSeedColorPhase = differentSeed.getColorPhaseDegrees();
        differentSeed = null;
        System.gc();

        KoiRenderer slow = renderer(SEED, 1, 0.85);
        slow.render(canvas, 0.0);
        for (int frame = 0; frame < 30; frame++) slow.render(canvas, DT);
        require(slow.getLifeField().getGeneration() == 1,
                "1 Hz renderer did not produce one generation in one second");
        checkNumbers(slow.getLifeField());
        long slowGeneration = slow.getLifeField().getGeneration();
        slow = null;
        System.gc();

        final double initialCoverage = coverage(initial);
        final double evolvedCoverage = coverage(evolved);
        final double evolutionChange = changedFraction(initial, evolved);
        final double couplingChange = changedFraction(oneSecond, zeroCoupling);
        final double seedChange = changedFraction(oneSecond, reseeded);
        require(initialCoverage >= 0.01, "Initial render has less than 1 percent visible content");
        require(evolvedCoverage >= 0.01, "Evolved render has less than 1 percent visible content");
        require(evolutionChange >= 0.005, "Visible evolution changed less than 0.5 percent of pixels");
        require(couplingChange >= 0.005, "Life coupling changed less than 0.5 percent of pixels");
        require(seedChange >= 0.005, "A different random seed changed less than 0.5 percent of pixels");

        long total = 0L;
        for (long duration : durations) total += duration;
        Arrays.sort(durations);
        final double averageMs = total / (double) frames / 1e6;
        final double p95Ms = durations[(int) Math.ceil(frames * 0.95) - 1] / 1e6;
        final double maximumMs = durations[frames - 1] / 1e6;
        String report = String.format(Locale.US,
                "{\n"
                + "  \"status\": \"passed\",\n"
                + "  \"canvas\": \"480x900 software ARGB8888; not GPU wallpaper timing\",\n"
                + "  \"frames\": %d,\n"
                + "  \"visible_seconds\": %.3f,\n"
                + "  \"rate_hz\": 4,\n"
                + "  \"generation\": %d,\n"
                + "  \"population\": %d,\n"
                + "  \"reseed_count\": %d,\n"
                + "  \"one_hz_generation_after_one_second\": %d,\n"
                + "  \"average_draw_ms\": %.3f,\n"
                + "  \"p95_draw_ms\": %.3f,\n"
                + "  \"maximum_draw_ms\": %.3f,\n"
                + "  \"initial_nonbackground_fraction\": %.6f,\n"
                + "  \"evolved_nonbackground_fraction\": %.6f,\n"
                + "  \"evolution_changed_fraction\": %.6f,\n"
                + "  \"coupling_changed_fraction\": %.6f,\n"
                + "  \"seed_changed_fraction\": %.6f,\n"
                + "  \"initial_pixel_hash\": \"%s\",\n"
                + "  \"evolved_pixel_hash\": \"%s\",\n"
                + "  \"coupled_one_second_pixel_hash\": \"%s\",\n"
                + "  \"zero_coupling_pixel_hash\": \"%s\",\n"
                + "  \"different_seed_pixel_hash\": \"%s\"\n"
                + "}\n",
                frames, frames / 30.0, life.getGeneration(), life.getPopulation(),
                life.getReseedCount(), slowGeneration,
                averageMs, p95Ms, maximumMs, initialCoverage, evolvedCoverage,
                evolutionChange, couplingChange, seedChange,
                hash(initial), hash(evolved), hash(oneSecond), hash(zeroCoupling), hash(reseeded));
        initial = null; evolved = null; zeroCoupling = null; reseeded = null; oneSecond = null;
        System.gc();
        System.err.println("Base 180-frame checks passed; checking neon/morph.");
        final JSONObject neonMorph = checkNeonAndMorph(main, canvas, directory,
                initialColorPhase, oneSecondColorPhase, differentSeedColorPhase);
        JSONObject completeReport = new JSONObject(report);
        completeReport.put("neon_morph", neonMorph);
        completeReport.put("controls_and_population", checkControls(directory));
        completeReport.put("visual_styles", compareVisualStyles(directory));
        completeReport.put("calm_dynamics", checkCalmDynamics(directory));
        completeReport.put("touch_ripples", checkTouchRipples(directory));
        report = completeReport.toString(2) + "\n";
        try (FileOutputStream output = new FileOutputStream(new File(directory, "render-report.json"))) {
            output.write(report.getBytes(StandardCharsets.UTF_8));
        }
        System.out.print(report);
        bitmap.recycle();
    }

    private static JSONObject checkNeonAndMorph(KoiRenderer current, Canvas canvas,
            File directory, double initialPhase, double oneSecondPhase, double otherSeedPhase)
            throws Exception {
        JSONObject report = new JSONObject();
        final double evolvedPhase = current.getColorPhaseDegrees();
        require(Math.abs(evolvedPhase - initialPhase) > 0.05,
                "Neon phase did not cycle during visible Life evolution");
        require(Math.abs(oneSecondPhase - otherSeedPhase) > 1e-7,
                "Independent Life seeds did not affect the color phase");
        JSONArray palette = new JSONArray();
        JSONArray populations = new JSONArray();
        float[] hsv = new float[3];
        final int hueCount = current.getPaletteSize();
        float[] hues = new float[hueCount];
        for (int bucket = 0; bucket < hueCount; bucket++) {
            int color = current.getPaletteColor(bucket);
            Color.colorToHSV(color, hsv);
            require(hsv[1] >= 0.70f && hsv[2] >= 0.75f,
                    "Palette is not saturated bright neon at bucket " + bucket);
            hues[bucket] = hsv[0];
            palette.put(String.format(Locale.US, "#%08x", color));
        }
        for (int a = 0; a < hueCount; a++) for (int b = a + 1; b < hueCount; b++) {
            double separation = Math.abs(hues[a] - hues[b]);
            separation = Math.min(separation, 360.0 - separation);
            require(separation >= 360.0 / hueCount - 1.0, "Palette buckets collapsed to one hue");
        }
        for (int creature = 0; creature < 3; creature++) {
            JSONArray buckets = new JSONArray();
            int populated = 0;
            for (int bucket = 0; bucket < hueCount; bucket++) {
                int population = current.getPalettePopulation(creature, bucket);
                require(population >= 0, "Negative palette population");
                if (population > 0) populated++;
                buckets.put(population);
            }
            require(populated >= 4, "One koi does not contain multiple neon colors");
            populations.put(buckets);
        }

        long frozenGeneration = current.getLifeField().getGeneration();
        double frozenPhase = current.getColorPhaseDegrees();
        double frozenChaosTime = current.getChaosTimeSeconds();
        long frozenChaosSteps = current.getChaosStepCount();
        int[] frozenForms = new int[3];
        int[] frozenTargets = new int[3];
        double[] frozenBlends = new double[3];
        for (int creature = 0; creature < 3; creature++) {
            frozenForms[creature] = current.getFormIndex(creature);
            frozenTargets[creature] = current.getNextFormIndex(creature);
            frozenBlends[creature] = current.getFormBlend(creature);
        }
        for (int i = 0; i < 3; i++) current.render(canvas, 0.0);
        require(current.getLifeField().getGeneration() == frozenGeneration,
                "Zero dt advanced Life generation");
        require(current.getColorPhaseDegrees() == frozenPhase,
                "Zero dt advanced the color phase");
        require(current.getChaosTimeSeconds() == frozenChaosTime
                && current.getChaosStepCount() == frozenChaosSteps, "Zero dt advanced chaos");
        for (int creature = 0; creature < 3; creature++) {
            require(current.getFormIndex(creature) == frozenForms[creature]
                    && current.getNextFormIndex(creature) == frozenTargets[creature]
                    && current.getFormBlend(creature) == frozenBlends[creature],
                    "Zero dt advanced a morph clock");
        }

        Bitmap small = Bitmap.createBitmap(240, 450, Bitmap.Config.ARGB_8888);
        boolean[] formShots = new boolean[10];
        Canvas fastCanvas = new Canvas(small);
        KoiRenderer tour = new KoiRenderer(SEED);
        tour.configure(4, 0.85, 2000, false, true);
        tour.render(fastCanvas, 0.0);
        recordForm(small, directory, tour.getFormIndex(0), formShots);
        int[] previousForm = new int[3];
        int[] previousNext = new int[3];
        double[] previousBlend = new double[3];
        for (int creature = 0; creature < 3; creature++) {
            previousForm[creature] = tour.getFormIndex(creature);
            previousNext[creature] = tour.getNextFormIndex(creature);
            previousBlend[creature] = tour.getFormBlend(creature);
        }
        java.lang.reflect.Method advance = KoiRenderer.class.getDeclaredMethod(
                "advanceLifeAndAppearance", double.class, double.class);
        advance.setAccessible(true);
        int tourDraws = 1;
        int tourFrames = 0;
        int intermediateMorphs = 0;
        double largestBlendStep = 0.0;
        for (; tourFrames < 1200; tourFrames++) {
            advance.invoke(tour, 0.25, 0.12);
            boolean complete = true;
            for (int creature = 0; creature < 3; creature++) {
                int form = tour.getFormIndex(creature);
                int next = tour.getNextFormIndex(creature);
                double blend = tour.getFormBlend(creature);
                require(form >= 0 && form < 10 && next >= 0 && next < 10,
                        "Morph form outside the ten original algorithms");
                require(finite(blend) && blend >= 0.0 && blend <= 1.0,
                        "Morph blend outside the normalized range");
                double step = blend - previousBlend[creature];
                if (form != previousForm[creature]) {
                    require(form == previousNext[creature], "Morph jumped to an unrelated form");
                    step += 1.0;
                }
                require(step >= -1e-8 && step <= 0.35,
                        "Morph progressed discontinuously: " + step);
                largestBlendStep = Math.max(largestBlendStep, step);
                if (blend > 0.05 && blend < 0.95) intermediateMorphs++;
                previousForm[creature] = form;
                previousNext[creature] = next;
                previousBlend[creature] = blend;
                complete &= (tour.getVisitedFormMask(creature) & 1023) == 1023;
            }
            if (!formShots[tour.getFormIndex(0)]) {
                tour.render(fastCanvas, 0.0);
                tourDraws++;
                recordForm(small, directory, tour.getFormIndex(0), formShots);
            }
            if (complete) { tourFrames++; break; }
        }
        require(intermediateMorphs > 30, "No sustained smooth intermediate forms");
        JSONArray masks = new JSONArray();
        for (int creature = 0; creature < 3; creature++) {
            int mask = tour.getVisitedFormMask(creature);
            require((mask & 1023) == 1023, "A koi did not reach every original form");
            masks.put(mask);
        }
        tour.render(fastCanvas, 0.0);
        tourDraws++;
        snapshot(small, directory, "render-morph-tour.jpg");
        for (int form = 0; form < 10; form++) require(formShots[form], "Missing reached-form image");
        tour = null;
        System.gc();
        System.err.println("All ten forms verified for all three creatures; checking settled Life.");
        KoiRenderer original = new KoiRenderer(SEED);
        original.configure(4, 0.0, 2000, false, true);
        for (int frame = 0; frame < 40; frame++) original.render(fastCanvas, 0.25);
        for (int creature = 0; creature < 3; creature++) {
            require(original.getFormIndex(creature) == 0
                    && original.getNextFormIndex(creature) == 0
                    && original.getFormBlend(creature) == 0.0,
                    "Zero Life influence did not retain the original base form");
        }
        original = null;
        System.gc();
        JSONObject settled = checkSettledLife(small, fastCanvas, directory);
        small.recycle();
        report.put("status", "passed");
        report.put("settled_life_chaos", settled);
        report.put("palette_argb", palette);
        report.put("per_koi_palette_populations", populations);
        report.put("initial_color_phase_degrees", initialPhase);
        report.put("evolved_color_phase_degrees", evolvedPhase);
        report.put("independent_seed_phase_difference", Math.abs(oneSecondPhase - otherSeedPhase));
        report.put("zero_dt_clocks_unchanged", true);
        report.put("zero_influence_original_form", true);
        report.put("all_ten_forms_reached_per_koi", masks);
        report.put("captured_form_indices", new JSONArray(new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}));
        report.put("tour_method", "Reflective advanceLifeAndAppearance(0.25,0.12); render(dt=0) for selected form snapshots only");
        report.put("tour_canvas_draws", tourDraws);
        report.put("accelerated_tour_frames", tourFrames);
        report.put("accelerated_tour_visible_seconds", tourFrames * 0.25);
        report.put("intermediate_morph_samples", intermediateMorphs);
        report.put("largest_morph_step_per_quarter_second", largestBlendStep);
        return report;
    }

    private static void recordForm(Bitmap image, File directory, int form, boolean[] shots)
            throws Exception {
        if (shots[form]) return;
        writeImage(image, directory, "render-form-" + form + ".jpg");
        shots[form] = true;
    }
    private static void writeImage(Bitmap image, File directory, String name) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            require(image.compress(Bitmap.CompressFormat.JPEG, 85, output), "JPEG encoding failed");
        }
    }

    private static JSONObject checkSettledLife(Bitmap bitmap, Canvas canvas, File directory)
            throws Exception {
        KoiRenderer renderer = new KoiRenderer(SEED + 19);
        renderer.configure(4, 0.85, 2000, false, false);
        java.lang.reflect.Method setter = LifeField.class.getDeclaredMethod("setCells", boolean[].class);
        setter.setAccessible(true);
        for (int creature = 0; creature < renderer.getCreatureLimit(); creature++) {
            LifeField life = renderer.getLifeField(creature);
            life.setAutoReseed(false);
            setter.invoke(life, (Object) new boolean[life.getColumns() * life.getRows()]);
        }
        renderer.render(canvas, 0.0);
        int[] before = snapshot(bitmap, directory, "render-settled-life-initial.jpg");
        double phase = renderer.getColorPhaseDegrees();
        int initialForm = renderer.getFormIndex(0);
        double initialBlend = renderer.getFormBlend(0);
        double[][] low = new double[3][4], high = new double[3][4];
        for (int system = 0; system < 3; system++) for (int axis = 0; axis < 4; axis++) {
            low[system][axis] = Double.POSITIVE_INFINITY;
            high[system][axis] = Double.NEGATIVE_INFINITY;
        }
        for (int frame = 0; frame < 80; frame++) {
            renderer.render(canvas, 0.25);
            for (int system = 0; system < 3; system++) for (int axis = 0; axis < 4; axis++) {
                double value = renderer.getChaosValue(system, axis);
                require(finite(value) && value >= (axis == 3 ? 0.0 : -1.0) && value <= 1.0,
                        "Invalid normalized chaos sample");
                low[system][axis] = Math.min(low[system][axis], value);
                high[system][axis] = Math.max(high[system][axis], value);
            }
        }
        int[] after = snapshot(bitmap, directory, "render-settled-life-evolved.jpg");
        for (int creature = 0; creature < renderer.getCreatureLimit(); creature++) {
            LifeField life = renderer.getLifeField(creature);
            require(life.getPopulation() == 0 && life.getActivity() == 0.0
                    && life.getReseedCount() == 0, "Settled-Life fixture did not remain settled");
            require(high[creature % 3][0] - low[creature % 3][0] > 0.03,
                    "Chaos stopped with empty Life grid");
        }
        double changed = changedFraction(before, after);
        require(changed > 0.005, "Settled Life stopped visible evolution");
        require(Math.abs(renderer.getColorPhaseDegrees() - phase) > 0.05,
                "Settled Life stopped hue cycling");
        require(renderer.getFormIndex(0) != initialForm
                || Math.abs(renderer.getFormBlend(0) - initialBlend) > 0.05,
                "Settled Life stopped morph progression");
        require(renderer.getChaosStepCount() > 0 && renderer.getChaosTimeSeconds() > 0,
                "Autonomous chaos clock did not advance");
        JSONObject report = new JSONObject();
        report.put("status", "passed");
        report.put("all_life_populations_zero", true);
        report.put("color_phase_delta_degrees", renderer.getColorPhaseDegrees() - phase);
        report.put("chaos_steps", renderer.getChaosStepCount());
        report.put("chaos_seconds", renderer.getChaosTimeSeconds());
        report.put("canvas_frames", 81);
        report.put("visible_seconds", 20);
        report.put("all_life_autoreseed_disabled", true);
        report.put("changed_pixel_fraction", changed);
        return report;
    }

    private static KoiRenderer renderer(long seed, int rate, double strength) {
        KoiRenderer renderer = new KoiRenderer(seed);
        renderer.configure(rate, strength, POINTS, true, true);
        return renderer;
    }

    private static int[] snapshot(Bitmap bitmap, File directory, String name) throws Exception {
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            require(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output), "JPEG encoding failed");
        }
        int width = bitmap.getWidth(), height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return pixels;
    }

    private static void checkNumbers(LifeField life) {
        require(life.getPopulation() >= 0
                        && life.getPopulation() <= life.getColumns() * life.getRows(),
                "Population out of range");
        require(finite(life.getActivity()) && life.getActivity() >= 0.0 && life.getActivity() <= 1.0,
                "Activity is not finite and normalized");
        for (int band = 0; band < 8; band++) {
            double value = life.getBandEnergy(band);
            require(finite(value) && value >= 0.0 && value <= 1.0, "Invalid Fourier band " + band);
        }
        double[] sample = new double[3];
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                life.sample(x / 6.0, y / 6.0, sample);
                for (double value : sample) {
                    require(finite(value) && Math.abs(value) <= 1.0 + 1e-9, "Invalid spatial field sample");
                }
            }
        }
    }

    private static boolean sameGridAtOneSecond(long seed, LifeField actual) {
        LifeField reference = new LifeField(actual.getColumns(), actual.getRows(), seed);
        reference.setRate(4);
        reference.advance(1.0);
        for (int y = 0; y < actual.getRows(); y++) {
            for (int x = 0; x < actual.getColumns(); x++) {
                if (reference.isAlive(x, y) != actual.isAlive(x, y)) return false;
            }
        }
        return true;
    }

    private static double coverage(int[] pixels) {
        int background = pixels[0];
        int count = 0;
        for (int pixel : pixels) if (different(pixel, background)) count++;
        return count / (double) pixels.length;
    }

    private static double changedFraction(int[] a, int[] b) {
        int changed = 0;
        for (int i = 0; i < a.length; i++) if (different(a[i], b[i])) changed++;
        return changed / (double) a.length;
    }

    private static boolean different(int a, int b) {
        return Math.abs(((a >>> 16) & 255) - ((b >>> 16) & 255)) > 6
                || Math.abs(((a >>> 8) & 255) - ((b >>> 8) & 255)) > 6
                || Math.abs((a & 255) - (b & 255)) > 6;
    }

    private static String hash(int[] pixels) {
        long hash = 0xcbf29ce484222325L;
        for (int pixel : pixels) {
            hash ^= pixel & 0xffffffffL;
            hash *= 0x100000001b3L;
        }
        return Long.toHexString(hash);
    }


    private static JSONObject checkControls(File directory) throws Exception {
        java.lang.reflect.Method advance = KoiRenderer.class.getDeclaredMethod(
                "advanceLifeAndAppearance", double.class, double.class);
        advance.setAccessible(true);
        KoiRenderer reference = new KoiRenderer(SEED + 41);
        reference.configure(4, .85, 2000, false, false);
        reference.setAnimationSpeeds(1, 1, 1);
        for (int frame = 0; frame < 60; frame++) advance.invoke(reference, DT, DT);
        double referenceHue = reference.getColorPhaseDegrees();
        double referenceBlend = 0;
        for (int c = 0; c < 3; c++) referenceBlend += reference.getFormBlend(c);
        require(referenceBlend > .001, "Speed fixture has no measurable form progress");
        for (int mode = 0; mode < 3; mode++) {
            KoiRenderer slowed = new KoiRenderer(SEED + 41);
            slowed.configure(4, .85, 2000, false, false);
            slowed.setAnimationSpeeds(mode == 0 ? .5 : 1, mode == 1 ? .25 : 1,
                    mode == 2 ? .25 : 1);
            for (int frame = 0; frame < 60; frame++) advance.invoke(slowed, DT, DT);
            require(slowed.getLifeField().getGeneration() == 8,
                    "Visual speed changed the selected 4 Hz Life clock");
            for (int y = 0; y < 64; y++) for (int x = 0; x < 48; x++)
                require(slowed.getLifeField().isAlive(x, y) == reference.getLifeField().isAlive(x, y),
                        "Visual speed changed the Life cells");
            if (mode == 0) require(Math.abs(slowed.getChaosTimeSeconds() - 1) < 1e-8,
                    "Motion slider did not halve the chaotic motion clock");
            if (mode == 1) {
                double blend = 0;
                for (int c = 0; c < 3; c++) blend += slowed.getFormBlend(c);
                require(blend < referenceBlend, "Form slider did not slow shape changes");
                require(Math.abs(slowed.getColorPhaseDegrees() - referenceHue) < 1e-8,
                        "Form slider changed the independent color speed");
            }
            if (mode == 2) {
                require(Math.abs(slowed.getColorPhaseDegrees() - referenceHue * .25) < 1e-7,
                        "Color slider did not quarter hue progression");
                double blend = 0;
                for (int c = 0; c < 3; c++) blend += slowed.getFormBlend(c);
                require(Math.abs(blend - referenceBlend) < 1e-8,
                        "Color slider changed the independent form speed");
            }
            slowed = null;
            System.gc();
        }
        reference = null;
        System.gc();
        Bitmap bitmap = Bitmap.createBitmap(240, 450, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        KoiRenderer population = new KoiRenderer(SEED + 43);
        population.configure(4, .85, 3000, false, true);
        population.setAnimationSpeeds(1, 1, 1);
        population.configurePopulation(1, false, false);
        for (int i = 0; i < 32; i++) population.render(canvas, .25);
        require(population.getTargetCreatureCount() == 1 && population.getActiveCreatureCount() == 1,
                "Fixed population did not settle to one creature");
        snapshot(bitmap, directory, "render-one-creature.jpg");
        population.configurePopulation(9, false, true);
        for (int i = 0; i < 32; i++) population.render(canvas, .25);
        require(population.getTargetCreatureCount() == 9 && population.getActiveCreatureCount() == 9,
                "Population did not expand to nine creatures");
        int total = 0;
        for (int c = 0; c < population.getCreatureLimit(); c++) {
            require(population.getCreatureOpacity(c) > .9, "Expanded creature did not finish appearing");
            int populated = 0;
            for (int b = 0; b < population.getPaletteSize(); b++) {
                int count = population.getPalettePopulation(c, b);
                require(count >= 0, "Negative gradient population");
                total += count;
                if (count > 0) populated++;
            }
            require(populated >= 4, "Expanded creature lost its multicolor gradient");
        }
        require(total <= 3000, "Creature count multiplied the total point workload");
        require(population.getPaletteSize() >= 18, "Gradient does not contain enough intermediate hues");
        snapshot(bitmap, directory, "render-nine-creatures.jpg");
        population.configurePopulation(9, true, true);
        int firstCount = population.getTargetCreatureCount();
        boolean varied = false;
        double previousHue = population.getColorPhaseDegrees(), largestHueStep = 0;
        for (int i = 0; i < 160; i++) {
            population.render(canvas, .25);
            double hue = population.getColorPhaseDegrees();
            double hueStep = hue - previousHue;
            require(hueStep >= 0 && hueStep < 40, "Population transition caused a hue jump");
            largestHueStep = Math.max(largestHueStep, hueStep);
            previousHue = hue;
            int count = population.getTargetCreatureCount();
            require(count >= 1 && count <= 9, "Automatic population outside selected range");
            varied |= count != firstCount;
        }
        require(varied, "Automatic creature count did not change over forty seconds");
        snapshot(bitmap, directory, "render-variable-creatures.jpg");
        bitmap.recycle();
        JSONObject report = new JSONObject();
        report.put("status", "passed");
        report.put("independent_visual_speed_controls", true);
        report.put("visual_speed_preserves_life_rate_and_cells", true);
        report.put("fixed_population_range", "1..9");
        report.put("automatic_population_changed", true);
        report.put("continuous_hue_through_population_changes", true);
        report.put("largest_hue_step_per_quarter_second", largestHueStep);
        report.put("maximum_total_points", total);
        report.put("palette_hues", population.getPaletteSize());
        report.put("collision_validation", "PondDynamicsTest deterministic contact and bounds checks in build.log");
        return report;
    }


    private static JSONObject compareVisualStyles(File directory) throws Exception {
        double[][] styles = {{1.0, 110, .8}, {1.15, 100, .65}, {1.0, 135, 1.0}};
        Bitmap sheet = Bitmap.createBitmap(720, 1350, Bitmap.Config.ARGB_8888);
        Canvas sheetCanvas = new Canvas(sheet);
        JSONArray comparisons = new JSONArray();
        for (int style = 0; style < styles.length; style++) {
            Bitmap frame = Bitmap.createBitmap(240, 450, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(frame);
            KoiRenderer renderer = new KoiRenderer(SEED + 61);
            renderer.configure(1, 1.0, 9000, false, true);
            renderer.setAnimationSpeeds(.5, .5, .5);
            renderer.configurePopulation(3, false, true);
            renderer.setVisualStyle(styles[style][0], styles[style][1], styles[style][2]);
            long total = 0;
            for (int tick = 1; tick <= 120; tick++) {
                long before = System.nanoTime();
                renderer.render(canvas, .1);
                total += System.nanoTime() - before;
                if (tick == 20 || tick == 60 || tick == 120) {
                    int column = tick == 20 ? 0 : tick == 60 ? 1 : 2;
                    sheetCanvas.drawBitmap(frame, column * 240, style * 450, null);
                    snapshot(frame, directory, "render-style-" + style + "-" + tick + ".jpg");
                }
            }
            JSONObject result = new JSONObject();
            result.put("style", style);
            result.put("core_scale", styles[style][0]);
            result.put("hue_arc", styles[style][1]);
            result.put("glow", styles[style][2]);
            result.put("average_software_draw_ms", total / 120.0 / 1e6);
            comparisons.put(result);
            frame.recycle();
            renderer = null;
            System.gc();
        }
        snapshot(sheet, directory, "render-visual-comparison.jpg");
        sheet.recycle();
        JSONObject result = new JSONObject();
        result.put("method", "Same seed and 9000 total points; rows are style 0/1/2, columns are 2/6/12 seconds; actual Android Canvas");
        result.put("comparisons", comparisons);
        return result;
    }

    private static JSONObject checkCalmDynamics(File directory) throws Exception {
        Bitmap bitmap = Bitmap.createBitmap(240, 450, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        KoiRenderer calm = new KoiRenderer(SEED + 73);
        calm.configure(1, .98, 3000, true, false);
        calm.configurePopulation(5, false, true);
        calm.setAnimationSpeeds(.18, .18, .43);
        JSONArray captures = new JSONArray();
        int maximumLinks = 0;
        for (int frame = 1; frame <= 900; frame++) {
            calm.render(canvas, .1);
            maximumLinks = Math.max(maximumLinks, calm.getLastLinkCount());
            require(calm.getLastLinkCount() <= 900, "Calm render exceeded its connection budget");
            if (frame == 60 || frame == 300 || frame == 900) {
                int[] pixels = snapshot(bitmap, directory, "render-calm-" + (frame / 10) + "s.jpg");
                require(coverage(pixels) >= .005, "Calm sequence lost visible structures");
                captures.put(hash(pixels));
            }
        }
        require(calm.getLifeField().getGeneration() == 90,
                "Slow appearance changed the independent 1 Hz Life clock");
        require(!captures.getString(0).equals(captures.getString(2)),
                "Calm structures stopped evolving");
        int[] pausedBefore = snapshot(bitmap, directory, "render-calm-paused.jpg");
        for (int i = 0; i < 3; i++) calm.render(canvas, 0);
        int[] pausedAfter = snapshot(bitmap, directory, "render-calm-paused-after.jpg");
        require(hash(pausedBefore).equals(hash(pausedAfter)), "Paused calm geometry continued changing");
        KoiRenderer lowest = new KoiRenderer(SEED + 79);
        lowest.configure(4, .98, 2000, false, false);
        lowest.setAnimationSpeeds(.02, .02, .02);
        require(lowest.getMotionSpeed() == .02 && lowest.getFormSpeed() == .02
                && lowest.getColorSpeed() == .02, "Fine slowdown controls are still clamped above 0.02x");
        for (int i = 0; i < 20; i++) lowest.render(canvas, .1);
        require(lowest.getLifeField().getGeneration() == 8,
                "Minimum appearance speed slowed Life generations");
        require(Math.abs(lowest.getChaosTimeSeconds() - .04) < .009,
                "Minimum motion speed was not applied to chaos");
        JSONObject report = new JSONObject();
        report.put("status", "passed");
        report.put("method", "Actual Android Canvas, 90 visible seconds, 3000 total points, five colliding forms; drift/form 0.18x and color 0.43x");
        report.put("snapshot_seconds", new JSONArray(new int[] {6, 30, 90}));
        report.put("snapshot_hashes", captures);
        report.put("maximum_connections", maximumLinks);
        report.put("paused_pixels_unchanged", true);
        report.put("minimum_appearance_speed", .02);
        report.put("independent_life_clocks_preserved", true);
        bitmap.recycle();
        return report;
    }

    private static JSONObject checkTouchRipples(File directory) throws Exception {
        Bitmap baselineBitmap = Bitmap.createBitmap(240, 450, Bitmap.Config.ARGB_8888);
        Bitmap touchedBitmap = Bitmap.createBitmap(240, 450, Bitmap.Config.ARGB_8888);
        Canvas baselineCanvas = new Canvas(baselineBitmap), touchedCanvas = new Canvas(touchedBitmap);
        KoiRenderer baseline = new KoiRenderer(SEED + 89), touched = new KoiRenderer(SEED + 89);
        for (KoiRenderer renderer : new KoiRenderer[] {baseline, touched}) {
            renderer.configure(4, .98, 3000, false, false);
            renderer.configurePopulation(3, false, true);
            renderer.setAnimationSpeeds(.1, .1, .15);
            renderer.setTouchRipplesEnabled(true);
        }
        baseline.render(baselineCanvas, 0);
        touched.render(touchedCanvas, 0);
        touched.touch(.5, .5);
        long touchedCost = 0;
        for (int frame = 0; frame < 5; frame++) {
            baseline.render(baselineCanvas, .1);
            long start = System.nanoTime();
            touched.render(touchedCanvas, .1);
            touchedCost += System.nanoTime() - start;
        }
        require(touched.getActiveRippleCount() == 1, "Tap did not create one active wave");
        double force = touched.getRippleDisplacementMagnitude();
        require(force > 0 && force <= .18 + 1e-12, "Touch force missing or outside its bound");
        require(touched.getChaosTimeSeconds() == baseline.getChaosTimeSeconds()
                && touched.getChaosStepCount() == baseline.getChaosStepCount(),
                "Touch advanced the autonomous chaos clock");
        for (int c = 0; c < touched.getCreatureLimit(); c++) {
            LifeField actual = touched.getLifeField(c), expected = baseline.getLifeField(c);
            require(actual.getGeneration() == expected.getGeneration(),
                    "Touch changed Life generation count");
            for (int y = 0; y < actual.getRows(); y++) for (int x = 0; x < actual.getColumns(); x++)
                require(actual.isAlive(x, y) == expected.isAlive(x, y), "Touch changed a Conway cell");
        }
        // Present the paused state once so both images include the last trail
        // captured at the end of the preceding visible frame.
        baseline.render(baselineCanvas, 0);
        touched.render(touchedCanvas, 0);
        int[] untouchedPixels = snapshot(baselineBitmap, directory, "render-touch-baseline.jpg");
        int[] touchedPixels = snapshot(touchedBitmap, directory, "render-touch-ripple.jpg");
        double changed = changedFraction(untouchedPixels, touchedPixels);
        require(changed > .00001, "Tap did not cause a visible local response");
        String pausedHash = hash(touchedPixels);
        double frozenForce = touched.getRippleDisplacementMagnitude();
        for (int frame = 0; frame < 3; frame++) touched.render(touchedCanvas, 0);
        int[] pausedPixels = snapshot(touchedBitmap, directory, "render-touch-paused.jpg");
        require(hash(pausedPixels).equals(pausedHash), "Paused touch waves continued evolving");
        require(touched.getRippleDisplacementMagnitude() == frozenForce
                && touched.getActiveRippleCount() == 1, "Paused wave state changed");
        touched.touch(.2, .2); touched.touch(.8, .8); touched.touch(.3, .7);
        require(touched.getActiveRippleCount() == 3, "Tap pool did not retain exactly three recent waves");
        long generationBeforeDisable = touched.getLifeField().getGeneration();
        double chaosBeforeDisable = touched.getChaosTimeSeconds();
        touched.setTouchRipplesEnabled(false);
        touched.touch(.5, .5);
        require(touched.getActiveRippleCount() == 0, "Disabled touch retained or accepted waves");
        touched.render(touchedCanvas, 0);
        require(touched.getRippleDisplacementMagnitude() == 0, "Disabled touch left a residual force");
        require(touched.getLifeField().getGeneration() == generationBeforeDisable
                && touched.getChaosTimeSeconds() == chaosBeforeDisable,
                "Disabling touch changed simulation clocks");
        touched.setTouchRipplesEnabled(true);
        touched.touch(.5, .5);
        require(touched.getActiveRippleCount() == 1, "Touch did not resume after re-enabling");
        JSONObject report = new JSONObject();
        report.put("status", "passed");
        report.put("method", "Same seed, 240x450 native Android Canvas, 3000 total points, fixed three creatures; drift/form 0.1x and color 0.15x; one central tap after equal initialization");
        report.put("changed_pixel_fraction", changed);
        report.put("maximum_sampled_force", force);
        report.put("active_wave_limit", 3);
        report.put("paused_pixels_unchanged", true);
        report.put("disable_clears_and_rejects_taps", true);
        report.put("reenable_accepts_taps", true);
        report.put("all_life_grids_and_generations_unchanged", true);
        report.put("chaos_clock_unchanged", true);
        report.put("touch_average_software_draw_ms", touchedCost / 5.0 / 1e6);
        report.put("baseline_pixel_hash", hash(untouchedPixels));
        report.put("touch_pixel_hash", pausedHash);
        baselineBitmap.recycle(); touchedBitmap.recycle();
        return report;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
