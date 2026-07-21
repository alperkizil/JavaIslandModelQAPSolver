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
 * Plain main-class test harness for
 * {@link StochasticUniversalSamplingSelector}: constructor validation; the
 * bulk contract (exact count, references only, population untouched,
 * count = 0 empty); a bit-exact stream replay against an independent
 * transliteration of the documented spec (linear-ranking table, one start
 * double, evenly spaced pointer walk, Fisher–Yates result shuffle) with final
 * stream-position agreement; the SUS low-variance guarantee (every member's
 * copy count equals its expectation floored or ceiled — exact counts where
 * the expectations are integers), seed-independent; shuffle decorrelation
 * (result order not fitness-monotone); the μ = 1 edge; same-seed determinism
 * / cross-seed difference; and step-timer bookkeeping.
 *
 * Usage: StochasticUniversalSamplingSelectorTest (no arguments; synthetic
 * members, no dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class StochasticUniversalSamplingSelectorTest {

    private StochasticUniversalSamplingSelectorTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        selectionContract(failures);
        streamReplay(failures);
        lowVarianceGuarantee(failures);
        shuffleDecorrelates(failures);
        singleMember(failures);
        determinism(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        for (double bad : new double[] {0.99, 2.01, 0.0, -1.0, Double.NaN}) {
            boolean threw = false;
            try {
                new StochasticUniversalSamplingSelector(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor s=" + bad + ": expected IllegalArgumentException");
        }
        new StochasticUniversalSamplingSelector(1.0);
        new StochasticUniversalSamplingSelector(2.0);
    }

    private static void selectionContract(List<String> failures) {
        Population pop = pop(50, 10, 30, 10, 70);
        StochasticUniversalSamplingSelector selector = new StochasticUniversalSamplingSelector(1.5);
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

    /** Bit-exact replica of table + pointer walk + shuffle on a same-seed stream. */
    private static void streamReplay(List<String> failures) {
        Population pop = pop(30, 10, 20, 10, 30, 10); // tie-heavy on purpose
        double s = 1.7;
        int count = 50;
        AlgorithmContext ctx = context(7L, 2);
        List<EvaluatedCandidate> picks =
                new StochasticUniversalSamplingSelector(s).selectParents(pop, count, ctx);

        int[] byRank = rankOrder(pop);
        double[] cumulative = cumulativeWeights(pop.size(), s);
        double total = cumulative[cumulative.length - 1];
        Randomizer replay = new RandomSource(7L).derive(2);
        double step = total / count;
        double start = replay.nextDouble() * step;
        EvaluatedCandidate[] expected = new EvaluatedCandidate[count];
        int rank = 0;
        for (int k = 0; k < count; k++) {
            double point = start + k * step;
            while (rank < cumulative.length - 1 && cumulative[rank] <= point) {
                rank++;
            }
            expected[k] = pop.get(byRank[rank]);
        }
        for (int i = count - 1; i > 0; i--) {
            int j = replay.nextInt(i + 1);
            EvaluatedCandidate tmp = expected[i];
            expected[i] = expected[j];
            expected[j] = tmp;
        }
        for (int k = 0; k < count; k++) {
            check(failures, picks.get(k) == expected[k],
                    "replay: pick " + k + " diverges from the documented spec");
        }
        check(failures, ctx.getRandomizer().nextLong() == replay.nextLong(),
                "replay: stream positions diverged — selector drew extra randomness");
    }

    /**
     * The property SUS exists for: actual copy counts are the expectations
     * floored or ceiled, for every seed. At s = 2 on 5 distinct members the
     * weights best→worst are {2, 1.5, 1, 0.5, 0}: count = 10 makes every
     * expectation an integer — counts must be exactly {4, 3, 2, 1, 0}.
     */
    private static void lowVarianceGuarantee(List<String> failures) {
        Population pop = pop(10, 20, 30, 40, 50); // best = index 0
        StochasticUniversalSamplingSelector selector = new StochasticUniversalSamplingSelector(2.0);

        int[][] exactExpected = {{4, 3, 2, 1, 0}};
        for (long seed : new long[] {1L, 2L, 3L}) {
            int[] counts = pickCounts(selector, pop, 10, context(seed, 0));
            for (int i = 0; i < 5; i++) {
                check(failures, counts[i] == exactExpected[0][i],
                        "SUS exact: seed " + seed + " member " + i + " count " + counts[i]
                                + " != " + exactExpected[0][i]);
            }
        }

        // count = 7: expectations best→worst {2.8, 2.1, 1.4, 0.7, 0} — each
        // count must be its floor or ceil and the total must be 7.
        long[][] bounds = {{2, 3}, {2, 3}, {1, 2}, {0, 1}, {0, 0}};
        for (long seed : new long[] {1L, 2L, 3L}) {
            int[] counts = pickCounts(selector, pop, 7, context(seed, 1));
            int sum = 0;
            for (int i = 0; i < 5; i++) {
                sum += counts[i];
                check(failures, counts[i] >= bounds[i][0] && counts[i] <= bounds[i][1],
                        "SUS floor/ceil: seed " + seed + " member " + i + " count " + counts[i]
                                + " outside [" + bounds[i][0] + ", " + bounds[i][1] + "]");
            }
            check(failures, sum == 7, "SUS floor/ceil: counts sum to " + sum + " != 7");
        }
    }

    /** The pointer walk emits rank order; the shuffle must break it up. */
    private static void shuffleDecorrelates(List<String> failures) {
        Population pop = pop(10, 20, 30, 40, 50);
        List<EvaluatedCandidate> picks = new StochasticUniversalSamplingSelector(1.5)
                .selectParents(pop, 20, context(5L, 0));
        boolean nonDecreasing = true;
        boolean nonIncreasing = true;
        for (int k = 1; k < picks.size(); k++) {
            long prev = picks.get(k - 1).getFitness();
            long cur = picks.get(k).getFitness();
            if (cur < prev) {
                nonDecreasing = false;
            }
            if (cur > prev) {
                nonIncreasing = false;
            }
        }
        check(failures, !nonDecreasing && !nonIncreasing,
                "shuffle: 20 picks came out fitness-monotone — result not shuffled");
    }

    private static void singleMember(List<String> failures) {
        Population pop = pop(77);
        List<EvaluatedCandidate> picks = new StochasticUniversalSamplingSelector(2.0)
                .selectParents(pop, 6, context(3L, 0));
        for (EvaluatedCandidate p : picks) {
            check(failures, p == pop.get(0), "μ=1: pick is not the single member");
        }
    }

    private static void determinism(List<String> failures) {
        Population pop = pop(30, 10, 20, 10, 30, 10);
        List<EvaluatedCandidate> a = new StochasticUniversalSamplingSelector(1.5)
                .selectParents(pop, 30, context(9001L, 1));
        List<EvaluatedCandidate> b = new StochasticUniversalSamplingSelector(1.5)
                .selectParents(pop, 30, context(9001L, 1));
        check(failures, a.equals(b), "determinism: same (seed, stream) picked differently");
        List<EvaluatedCandidate> c = new StochasticUniversalSamplingSelector(1.5)
                .selectParents(pop, 30, context(9002L, 1));
        check(failures, !a.equals(c), "determinism: different seeds picked identically");
    }

    private static void timerContract(List<String> failures) {
        StochasticUniversalSamplingSelector selector = new StochasticUniversalSamplingSelector(1.5);
        Population pop = pop(10, 20, 30);
        check(failures, selector.getInvocations() == 0, "timer: fresh step has invocations != 0");
        selector.selectParents(pop, 4, context(1L, 0));
        selector.selectParents(pop, 4, context(2L, 0));
        check(failures, selector.getInvocations() == 2,
                "timer: after two bulk calls invocations = " + selector.getInvocations());
        check(failures, selector.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // --- independent transliteration of the linear-ranking spec ---

    /** Worst-first member ranking: fitness descending, ties by index ascending. */
    private static int[] rankOrder(Population pop) {
        int[] byRank = new int[pop.size()];
        for (int i = 0; i < byRank.length; i++) {
            byRank[i] = i;
        }
        for (int i = 1; i < byRank.length; i++) {
            int key = byRank[i];
            long keyFitness = pop.get(key).getFitness();
            int j = i - 1;
            while (j >= 0 && pop.get(byRank[j]).getFitness() < keyFitness) {
                byRank[j + 1] = byRank[j];
                j--;
            }
            byRank[j + 1] = key;
        }
        return byRank;
    }

    private static double[] cumulativeWeights(int size, double s) {
        double[] cumulative = new double[size];
        double sum = 0.0;
        for (int r = 0; r < size; r++) {
            double weight = size == 1
                    ? 1.0
                    : (2.0 - s) + 2.0 * (s - 1.0) * r / (size - 1);
            sum += weight;
            cumulative[r] = sum;
        }
        return cumulative;
    }

    // --- shared plumbing ---

    private static int[] pickCounts(StochasticUniversalSamplingSelector selector, Population pop,
            int draws, AlgorithmContext ctx) {
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
