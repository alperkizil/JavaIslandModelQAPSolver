package qapSolver.GA.Selection;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link SigmaScalingSelector}: the bulk
 * contract (exact count, references only, population untouched, count = 0
 * empty); a bit-exact stream replay against an independent transliteration of
 * the documented spec (population mean and σ, minimization weights
 * 1 + (mean − f)/2σ with the {@code MIN_WEIGHT} floor, σ = 0 → uniform,
 * member-indexed cumulative table, one double per spin) with final
 * stream-position agreement; distribution behavior — all-tied population
 * samples uniformly, a > 2σ outlier is floored (rare but never extinct), and
 * the compressed-spread case that motivates sigma scaling on QAP (a 0.9%
 * relative fitness spread still yields ~8× best-vs-worst pressure where a raw
 * wheel would flatten to uniform); the μ = 1 edge; same-seed determinism /
 * cross-seed difference; and step-timer bookkeeping.
 *
 * Usage: SigmaScalingSelectorTest (no arguments; synthetic members, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class SigmaScalingSelectorTest {

    private SigmaScalingSelectorTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        selectionContract(failures);
        streamReplay(failures);
        allTiedUniform(failures);
        floorClamp(failures);
        compressedSpreadPressure(failures);
        singleMember(failures);
        determinism(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void selectionContract(List<String> failures) {
        Population pop = pop(50, 10, 30, 10, 70);
        SigmaScalingSelector selector = new SigmaScalingSelector();
        List<EvaluatedCandidate> before = snapshot(pop);

        for (int count : new int[] {1, 7}) {
            List<EvaluatedCandidate> parents = selector.selectParents(pop, count, context(1L, 0));
            check(failures, parents.size() == count,
                    "contract: count=" + count + " returned " + parents.size());
            for (EvaluatedCandidate p : parents) {
                check(failures, indexOf(pop, p) >= 0,
                        "contract: pick is not a population member reference");
            }
        }
        check(failures, selector.selectParents(pop, 0, context(1L, 0)).isEmpty(),
                "contract: count=0 expected empty list");
        check(failures, sameMembers(before, pop), "contract: population was modified");
    }

    /** Bit-exact replica of stats + weights + spins on a same-seed stream. */
    private static void streamReplay(List<String> failures) {
        Population pop = pop(100, 90, 80, 120, 100, 110);
        int count = 50;
        AlgorithmContext ctx = context(11L, 4);
        List<EvaluatedCandidate> picks = new SigmaScalingSelector().selectParents(pop, count, ctx);

        double[] cumulative = sigmaCumulative(pop);
        double total = cumulative[cumulative.length - 1];
        Randomizer replay = new RandomSource(11L).derive(4);
        for (int k = 0; k < count; k++) {
            int member = searchMember(cumulative, replay.nextDouble() * total);
            check(failures, picks.get(k) == pop.get(member),
                    "replay: pick " + k + " diverges from the documented spec");
        }
        check(failures, ctx.getRandomizer().nextLong() == replay.nextLong(),
                "replay: stream positions diverged — selector drew extra randomness");
    }

    /** σ = 0: every weight is 1, sampling must be uniform. */
    private static void allTiedUniform(List<String> failures) {
        Population pop = pop(500, 500, 500, 500);
        int[] counts = pickCounts(new SigmaScalingSelector(), pop, 8000, context(5L, 0));
        for (int i = 0; i < counts.length; i++) {
            check(failures, Math.abs(counts[i] - 2000) <= 300,
                    "all-tied: bucket " + i + " count " + counts[i] + " far from uniform 2000");
        }
    }

    /**
     * {10,10,10,10,10,1000}: the outlier sits 2.24σ below the mean (its raw
     * weight would be −0.118), so it is floored to MIN_WEIGHT = 0.1 — share
     * ≈ 1.6% (expected ≈ 80 of 5000): rare, but never extinct.
     */
    private static void floorClamp(List<String> failures) {
        Population pop = pop(10, 10, 10, 10, 10, 1000);
        int[] counts = pickCounts(new SigmaScalingSelector(), pop, 5000, context(6L, 0));
        check(failures, counts[5] > 0, "floor: clamped outlier was never drawn (extinct)");
        check(failures, counts[5] < 300,
                "floor: clamped outlier drawn " + counts[5] + " of 5000, expected ~80");
        for (int i = 0; i < 5; i++) {
            check(failures, counts[i] > 700, // expected ≈ 984 each
                    "floor: good member " + i + " drawn " + counts[i] + " of 5000, expected ~984");
        }
    }

    /**
     * The motivating case: fitnesses 1000..1009 differ by under 1%, which a
     * raw proportional wheel would flatten to uniform (ratio 1.009). Sigma
     * scaling re-amplifies to weights 1.78 vs 0.22 — best-vs-worst pressure
     * ≈ 8×.
     */
    private static void compressedSpreadPressure(List<String> failures) {
        Population pop = pop(1000, 1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009);
        int[] counts = pickCounts(new SigmaScalingSelector(), pop, 20_000, context(7L, 0));
        check(failures, counts[0] > 5 * counts[9], // expected ≈ 3566 vs ≈ 433
                "compressed spread: best " + counts[0] + " not ≫ worst " + counts[9]);
        check(failures, counts[9] > 200,
                "compressed spread: worst drawn " + counts[9] + " of 20000, expected ~433");
    }

    private static void singleMember(List<String> failures) {
        Population pop = pop(42);
        List<EvaluatedCandidate> picks = new SigmaScalingSelector().selectParents(pop, 6, context(3L, 0));
        for (EvaluatedCandidate p : picks) {
            check(failures, p == pop.get(0), "μ=1: pick is not the single member");
        }
    }

    private static void determinism(List<String> failures) {
        Population pop = pop(100, 90, 80, 120, 100, 110);
        List<EvaluatedCandidate> a = new SigmaScalingSelector().selectParents(pop, 30, context(9001L, 1));
        List<EvaluatedCandidate> b = new SigmaScalingSelector().selectParents(pop, 30, context(9001L, 1));
        check(failures, a.equals(b), "determinism: same (seed, stream) picked differently");
        List<EvaluatedCandidate> c = new SigmaScalingSelector().selectParents(pop, 30, context(9002L, 1));
        check(failures, !a.equals(c), "determinism: different seeds picked identically");
    }

    private static void timerContract(List<String> failures) {
        SigmaScalingSelector selector = new SigmaScalingSelector();
        Population pop = pop(10, 20, 30);
        check(failures, selector.getInvocations() == 0, "timer: fresh step has invocations != 0");
        selector.selectParents(pop, 4, context(1L, 0));
        selector.selectParents(pop, 4, context(2L, 0));
        check(failures, selector.getInvocations() == 2,
                "timer: after two bulk calls invocations = " + selector.getInvocations());
        check(failures, selector.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // --- independent transliteration of the sigma-scaling spec ---

    private static double[] sigmaCumulative(Population pop) {
        int size = pop.size();
        double mean = 0.0;
        for (int i = 0; i < size; i++) {
            mean += pop.get(i).getFitness();
        }
        mean /= size;
        double variance = 0.0;
        for (int i = 0; i < size; i++) {
            double deviation = pop.get(i).getFitness() - mean;
            variance += deviation * deviation;
        }
        double sigma = Math.sqrt(variance / size);
        double[] cumulative = new double[size];
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            double weight;
            if (sigma == 0.0) {
                weight = 1.0;
            } else {
                weight = 1.0 + (mean - pop.get(i).getFitness()) / (2.0 * sigma);
                if (weight <= 0.0) {
                    weight = SigmaScalingSelector.MIN_WEIGHT;
                }
            }
            sum += weight;
            cumulative[i] = sum;
        }
        return cumulative;
    }

    /** First member index whose cumulative weight strictly exceeds the point. */
    private static int searchMember(double[] cumulative, double point) {
        int lo = 0;
        int hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] > point) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    // --- shared plumbing ---

    private static int[] pickCounts(SigmaScalingSelector selector, Population pop, int draws,
            AlgorithmContext ctx) {
        List<EvaluatedCandidate> picks = selector.selectParents(pop, draws, ctx);
        int[] counts = new int[pop.size()];
        for (EvaluatedCandidate pick : picks) {
            counts[indexOf(pop, pick)]++;
        }
        return counts;
    }

    private static int indexOf(Population pop, EvaluatedCandidate candidate) {
        for (int i = 0; i < pop.size(); i++) {
            if (pop.get(i) == candidate) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameMembers(List<EvaluatedCandidate> before, Population pop) {
        if (before.size() != pop.size()) {
            return false;
        }
        for (int i = 0; i < before.size(); i++) {
            if (before.get(i) != pop.get(i)) {
                return false;
            }
        }
        return true;
    }

    private static Population pop(long... fitnesses) {
        List<EvaluatedCandidate> members = new ArrayList<>(fitnesses.length);
        for (long f : fitnesses) {
            members.add(new EvaluatedCandidate(new int[] {0, 1, 2, 3}, f));
        }
        return new Population(members);
    }

    private static List<EvaluatedCandidate> snapshot(Population pop) {
        List<EvaluatedCandidate> out = new ArrayList<>(pop.size());
        for (int i = 0; i < pop.size(); i++) {
            out.add(pop.get(i));
        }
        return out;
    }

    private static AlgorithmContext context(long masterSeed, int streamId) {
        QAPInstance instance = new QAPInstance("synthetic4", new int[4][4], new int[4][4]);
        return new AlgorithmContext(instance, new RandomSource(masterSeed).derive(streamId));
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
