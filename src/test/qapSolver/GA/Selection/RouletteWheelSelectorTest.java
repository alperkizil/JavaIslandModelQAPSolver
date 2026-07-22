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
 * Plain main-class test harness for {@link RouletteWheelSelector}: constructor
 * validation; the bulk contract (exact count, references only, population
 * untouched, count = 0 empty); a bit-exact stream replay against an
 * independent transliteration of the documented linear-ranking spec (worst-
 * first (fitness desc, index asc) ranking, weight (2−s) + 2(s−1)r/(μ−1),
 * cumulative table, one double per spin, strict-exceed binary search) with
 * final stream-position agreement; distribution checks (s = 2: zero-weight
 * worst never drawn, best ≈ 2/μ share; s = 1: uniform); the μ = 1 edge;
 * same-seed determinism / cross-seed difference; and step-timer bookkeeping.
 *
 * Usage: RouletteWheelSelectorTest (no arguments; synthetic members, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class RouletteWheelSelectorTest {

    private RouletteWheelSelectorTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        selectionContract(failures);
        streamReplay(failures);
        distribution(failures);
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
                new RouletteWheelSelector(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor s=" + bad + ": expected IllegalArgumentException");
        }
        new RouletteWheelSelector(1.0); // uniform end is legal
        new RouletteWheelSelector(2.0); // strongest end is legal
    }

    private static void selectionContract(List<String> failures) {
        Population pop = pop(50, 10, 30, 10, 70);
        RouletteWheelSelector selector = new RouletteWheelSelector(1.5);
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

    /** Bit-exact replica of ranking + wheel spins on a same-seed stream. */
    private static void streamReplay(List<String> failures) {
        Population pop = pop(30, 10, 20, 10, 30, 10); // tie-heavy on purpose
        double s = 1.7;
        int count = 50;
        AlgorithmContext ctx = context(7L, 2);
        List<EvaluatedCandidate> picks = new RouletteWheelSelector(s).selectParents(pop, count, ctx);

        int[] byRank = rankOrder(pop);
        double[] cumulative = cumulativeWeights(pop.size(), s);
        double total = cumulative[cumulative.length - 1];
        Randomizer replay = new RandomSource(7L).derive(2);
        for (int k = 0; k < count; k++) {
            int rank = searchRank(cumulative, replay.nextDouble() * total);
            check(failures, picks.get(k) == pop.get(byRank[rank]),
                    "replay: pick " + k + " diverges from the documented spec");
        }
        check(failures, ctx.getRandomizer().nextLong() == replay.nextLong(),
                "replay: stream positions diverged — selector drew extra randomness");
    }

    private static void distribution(List<String> failures) {
        Population pop = pop(10, 20, 30, 40, 50); // distinct, best = index 0

        // s = 2: weights best→worst are {2, 1.5, 1, 0.5, 0} — the worst member
        // has weight exactly 0 and must never be drawn.
        int[] strong = pickCounts(new RouletteWheelSelector(2.0), pop, 20_000, context(5L, 0));
        check(failures, strong[4] == 0, "s=2: zero-weight worst drawn " + strong[4] + " times");
        check(failures, strong[0] >= 7400 && strong[0] <= 8600, // expectation 8000 (40%)
                "s=2: best drawn " + strong[0] + " of 20000, expected ~8000");

        int[] uniform = pickCounts(new RouletteWheelSelector(1.0), pop, 20_000, context(6L, 0));
        for (int i = 0; i < uniform.length; i++) {
            check(failures, Math.abs(uniform[i] - 4000) <= 400,
                    "s=1: bucket " + i + " count " + uniform[i] + " far from uniform 4000");
        }
    }

    private static void singleMember(List<String> failures) {
        Population pop = pop(77);
        List<EvaluatedCandidate> picks = new RouletteWheelSelector(2.0).selectParents(pop, 10, context(3L, 0));
        for (EvaluatedCandidate p : picks) {
            check(failures, p == pop.get(0), "μ=1: pick is not the single member");
        }
    }

    private static void determinism(List<String> failures) {
        Population pop = pop(30, 10, 20, 10, 30, 10);
        List<EvaluatedCandidate> a = new RouletteWheelSelector(1.5).selectParents(pop, 30, context(9001L, 1));
        List<EvaluatedCandidate> b = new RouletteWheelSelector(1.5).selectParents(pop, 30, context(9001L, 1));
        check(failures, a.equals(b), "determinism: same (seed, stream) picked differently");
        List<EvaluatedCandidate> c = new RouletteWheelSelector(1.5).selectParents(pop, 30, context(9002L, 1));
        check(failures, !a.equals(c), "determinism: different seeds picked identically");
    }

    private static void timerContract(List<String> failures) {
        RouletteWheelSelector selector = new RouletteWheelSelector(1.5);
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

    /** First rank whose cumulative weight strictly exceeds the point. */
    private static int searchRank(double[] cumulative, double point) {
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

    private static int[] pickCounts(RouletteWheelSelector selector, Population pop, int draws,
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
