package org.ostadix.koilife;

/** Standalone checks against original Dart formulas, with no Android dependency. */
public final class KoiFormsTest {
    private static int checks;
    // Independently evaluated from upstream koi_algorithms.dart, then centered.
    // Each row: form i=1234,t=.73,sf=1,sa=1; i=17011,t=2.17,sf=1.09,sa=.93.
    // Both fixtures use n=20000, creatureCount=3, so native creature=i%3=1.
    private static final double[][] ORIGINAL_POINTS = {
        {41.1227779011449, -125.81939585925825, 18.62143717443203, -20.009034199805825},
        {-63.32406793305893, -64.9175005548907, -49.53202497232553, -63.11523515146223},
        {-57.96197030090423, -45.05636131591723, -43.719515740673046, -0.13044997340131204},
        {20.210778293833243, 61.10343033967109, 53.93481436117739, -78.7900378113642},
        {-61.40881702279478, -22.553006561573284, -29.364324822506347, -30.452605417764403},
        {34.68980476978655, -16.94211706437389, -80.83745943422467, -85.92635661060525},
        {15.253062628294742, 21.478636451748912, 35.212220288755304, -103.27737041267272},
        {-52.713744102493706, 89.235598868127, -79.69867478666977, -113.66327901117165},
        {49.80979708799177, 155.5969682520033, -30.483960371518606, 26.71668807973029},
        {-37.82458366902185, 18.551360248864995, 6.210486864091422, 35.944919837349005}
    };

    public static void main(String[] args) {
        originalFormulaFixtures();
        domainsStayCompleteAtDifferentQuality();
        animationAndMorphInputsStayFinite();
        singularitiesAndArguments();
        System.out.println("KoiFormsTest passed: " + checks + " checks across all ten original forms");
    }

    private static void originalFormulaFixtures() {
        check(KoiForms.COUNT == 10, "Exactly the original ten forms");
        String[] names = {"Koi", "Jellyfish", "Manta", "Coral", "Helix", "Breathe", "Bloom", "Trance", "Koi II", "Nebula"};
        double[] steps = {60, 45, 45, 40, 60, 360, 30, 240, 45, 30};
        double[] out = new double[2];
        for (int form = 0; form < KoiForms.COUNT; form++) {
            check(names[form].equals(KoiForms.name(form)), "Original registry order");
            close(KoiForms.speedRatio(form), 60.0 / steps[form], 0.0, "Original animation pace");
            check(KoiForms.evaluate(form, 1234, 20000, 1, .73, 1, 1, out), "First original fixture exists");
            close(out[0], ORIGINAL_POINTS[form][0], 1e-8, "Original first X, form " + form);
            close(out[1], ORIGINAL_POINTS[form][1], 1e-8, "Original first Y, form " + form);
            check(KoiForms.evaluate(form, 17011, 20000, 1, 2.17, 1.09, .93, out), "Second original fixture exists");
            close(out[0], ORIGINAL_POINTS[form][2], 1e-8, "Original second X, form " + form);
            close(out[1], ORIGINAL_POINTS[form][3], 1e-8, "Original second Y, form " + form);
        }
        check(KoiForms.evaluate(9, 23456, 30000, 2, 1.41, .87, 1.12, out), "Original Nebula density");
        close(out[0], 38.520888631292564, 1e-8, "Nebula 30000-domain X");
        close(out[1], -1.0908821986795374, 1e-8, "Nebula original Y has no center offset");
    }

    private static void domainsStayCompleteAtDifferentQuality() {
        double[] dense = new double[2];
        double[] sparse = new double[2];
        for (int form = 0; form < KoiForms.COUNT; form++) {
            for (int denseIndex : new int[] {0, 2400, 6000, 12000, 18000}) {
                boolean a = KoiForms.evaluate(form, denseIndex, 20000, 2, 1.7, .91, 1.1, dense);
                boolean b = KoiForms.evaluate(form, denseIndex / 2, 10000, 2, 1.7, .91, 1.1, sparse);
                check(a == b, "Quality preserves singularity behavior");
                if (a) {
                    close(dense[0], sparse[0], 1e-9, "Quality preserves full X domain");
                    close(dense[1], sparse[1], 1e-9, "Quality preserves full Y domain");
                }
            }
        }
    }

    private static void animationAndMorphInputsStayFinite() {
        double[] a = new double[2];
        double[] b = new double[2];
        for (int form = 0; form < KoiForms.COUNT; form++) {
            int accepted = 0;
            int visible = 0;
            int moving = 0;
            for (int index = 0; index < 14000; index += 31) {
                for (int frame = 0; frame < 4; frame++) {
                    double time = frame * .71 * KoiForms.speedRatio(form);
                    double sf = .8 + .1 * frame;
                    double sa = 1.2 - .1 * frame;
                    if (!KoiForms.evaluate(form, index, 14000, index % 3, time, sf, sa, a)) continue;
                    accepted++;
                    check(Double.isFinite(a[0]) && Double.isFinite(a[1]), "Morph input is finite");
                    if (Math.abs(a[0]) <= 220 && Math.abs(a[1]) <= 350) visible++;
                    if (KoiForms.evaluate(form, index, 14000, index % 3, time + .25, sf, sa, b)
                            && Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) > 1e-5) moving++;
                }
            }
            check(accepted > 1500, "Form produces its point cloud");
            check(visible > accepted * .8, "Most points remain in the original artwork extent");
            check(moving > accepted * .9, "Original form actually animates");
        }
    }

    private static void singularitiesAndArguments() {
        double[] out = {12, 34};
        for (int form : new int[] {0, 4, 8}) {
            double divisor = form == 4 ? 350.0 : 500.0;
            double frequency = form == 0 ? 5.3 : form == 4 ? 8.8 : 5.15;
            double sf = Math.PI / (2.0 * (1000.0 / divisor) * frequency);
            check(!KoiForms.evaluate(form, 1000, 20000, 1, .3, sf, 1, out), "Original reciprocal singularity is skipped");
            close(out[0], 0.0, 0.0, "Skipped X never retains stale coordinate");
            close(out[1], 0.0, 0.0, "Skipped Y never retains stale coordinate");
        }
        check(!KoiForms.evaluate(0, 1, 20000, 0, Double.NaN, 1, 1, out), "NaN time is rejected");
        check(!KoiForms.evaluate(0, 1, 20000, 0, 0, Double.POSITIVE_INFINITY, 1, out), "Infinite shape parameter rejected");
        expectIllegal(() -> KoiForms.name(10));
        expectIllegal(() -> KoiForms.evaluate(-1, 0, 20000, 0, 0, 1, 1, out));
        expectIllegal(() -> KoiForms.evaluate(0, 1, 0, 0, 0, 1, 1, out));
        expectIllegal(() -> KoiForms.evaluate(0, 20000, 20000, 0, 0, 1, 1, out));
        expectIllegal(() -> KoiForms.evaluate(0, 0, 20000, 0, 0, 1, 1, new double[1]));
    }

    private static void expectIllegal(Runnable runnable) {
        try { runnable.run(); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Expected invalid argument rejection");
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
