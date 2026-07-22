package qapSolver.Engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain main-class test harness for {@link PopulationStatistics}:
 * hand-computed values on a small population (best/worst/mean exact, σ
 * within round-off of √125); the single-member and all-tied populations
 * (σ = 0); order independence (shuffled multiset ⇒ identical statistics);
 * numerical stability at QAP magnitudes (~2·10⁹ fitnesses whose naive
 * sum-of-squares would overflow long); and loud misuse (null population
 * IllegalArgumentException, empty population IllegalStateException).
 *
 * Usage: PopulationStatisticsTest (no arguments; synthetic members, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class PopulationStatisticsTest {

    private PopulationStatisticsTest() {
    }

    private static final double EPS = 1e-9;

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        handComputed(failures);
        degenerateCases(failures);
        orderIndependence(failures);
        magnitudeStability(failures);
        misuse(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void handComputed(List<String> failures) {
        PopulationStatistics stats = PopulationStatistics.of(population(10, 20, 30, 40));
        check(failures, stats.getSize() == 4, "hand: size " + stats.getSize());
        check(failures, stats.getBestFitness() == 10, "hand: best " + stats.getBestFitness());
        check(failures, stats.getWorstFitness() == 40, "hand: worst " + stats.getWorstFitness());
        check(failures, Math.abs(stats.getMeanFitness() - 25.0) < EPS,
                "hand: mean " + stats.getMeanFitness() + " != 25");
        check(failures, Math.abs(stats.getStandardDeviation() - Math.sqrt(125.0)) < EPS,
                "hand: sd " + stats.getStandardDeviation() + " != sqrt(125)");
    }

    private static void degenerateCases(List<String> failures) {
        PopulationStatistics single = PopulationStatistics.of(population(42));
        check(failures, single.getBestFitness() == 42 && single.getWorstFitness() == 42
                        && Math.abs(single.getMeanFitness() - 42.0) < EPS
                        && single.getStandardDeviation() < EPS,
                "single: expected best=worst=mean=42, sd=0");

        PopulationStatistics tied = PopulationStatistics.of(population(7, 7, 7, 7, 7));
        check(failures, tied.getBestFitness() == 7 && tied.getWorstFitness() == 7
                        && Math.abs(tied.getMeanFitness() - 7.0) < EPS
                        && tied.getStandardDeviation() < EPS,
                "tied: expected best=worst=mean=7, sd=0");
    }

    private static void orderIndependence(List<String> failures) {
        PopulationStatistics a = PopulationStatistics.of(population(10, 20, 30, 40, 50));
        PopulationStatistics b = PopulationStatistics.of(population(50, 30, 10, 40, 20));
        check(failures, a.getBestFitness() == b.getBestFitness()
                        && a.getWorstFitness() == b.getWorstFitness()
                        && Math.abs(a.getMeanFitness() - b.getMeanFitness()) < EPS
                        && Math.abs(a.getStandardDeviation() - b.getStandardDeviation()) < EPS,
                "order: shuffled multiset gave different statistics");
    }

    private static void magnitudeStability(List<String> failures) {
        // Naive Σf² here is ~1.2e19 > Long.MAX_VALUE — the Welford pass must not care.
        long base = 2_000_000_000L;
        PopulationStatistics stats = PopulationStatistics.of(population(base, base + 10, base + 20));
        check(failures, stats.getBestFitness() == base && stats.getWorstFitness() == base + 20,
                "magnitude: best/worst wrong");
        check(failures, Math.abs(stats.getMeanFitness() - (base + 10)) < 1e-6,
                "magnitude: mean " + stats.getMeanFitness());
        double expectedSd = Math.sqrt(200.0 / 3.0); // {−10, 0, +10} around the mean
        check(failures, Math.abs(stats.getStandardDeviation() - expectedSd) < 1e-6,
                "magnitude: sd " + stats.getStandardDeviation() + " != " + expectedSd);
    }

    private static void misuse(List<String> failures) {
        boolean threw = false;
        try {
            PopulationStatistics.of(null);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(failures, threw, "misuse: null population must throw IllegalArgumentException");

        threw = false;
        try {
            PopulationStatistics.of(new Population(0));
        } catch (IllegalStateException e) {
            threw = true;
        }
        check(failures, threw, "misuse: empty population must throw IllegalStateException");
    }

    // ---- helpers ----

    private static Population population(long... fitnesses) {
        List<EvaluatedCandidate> members = new ArrayList<>(fitnesses.length);
        for (long f : fitnesses) {
            members.add(new EvaluatedCandidate(new int[] {0, 1, 2, 3}, f));
        }
        return new Population(members);
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
