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
 * Plain main-class test harness for {@link TournamentSelector}: constructor
 * validation; the bulk contract (exact count, references only, population
 * untouched, count = 0 empty); a bit-exact stream replay of the documented
 * algorithm (t index draws + cascade acceptance draws per pick, stable
 * best-first ordering with draw-order tie-break, last-contestant fallback) on
 * an independently derived same-seed stream, including final stream-position
 * agreement; same-seed determinism / cross-seed difference; selection
 * pressure (t = 3 vs t = 1 vs the p knob, and t ≫ μ saturation); and
 * step-timer bookkeeping.
 *
 * Usage: TournamentSelectorTest (no arguments; synthetic members, no dataset
 * dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class TournamentSelectorTest {

    private TournamentSelectorTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        selectionContract(failures);
        streamReplay(failures);
        determinism(failures);
        pressure(failures);
        saturation(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        for (int badT : new int[] {0, -1}) {
            boolean threw = false;
            try {
                new TournamentSelector(badT, 0.5);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor t=" + badT + ": expected IllegalArgumentException");
        }
        for (double badP : new double[] {0.0, -0.5, 1.5, Double.NaN}) {
            boolean threw = false;
            try {
                new TournamentSelector(2, badP);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor p=" + badP + ": expected IllegalArgumentException");
        }
        new TournamentSelector(1, 1.0);
        new TournamentSelector(3, 1e-9); // any p > 0 is legal
    }

    private static void selectionContract(List<String> failures) {
        Population pop = pop(50, 10, 30, 10, 70);
        TournamentSelector selector = new TournamentSelector(3, 0.8);
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

    /** Bit-exact replica of the documented draw/sort/cascade on a same-seed stream. */
    private static void streamReplay(List<String> failures) {
        Population pop = pop(30, 10, 20, 10, 30, 10); // tie-heavy on purpose
        int t = 3;
        double p = 0.7;
        int count = 40;
        AlgorithmContext ctx = context(42L, 3);
        List<EvaluatedCandidate> picks = new TournamentSelector(t, p).selectParents(pop, count, ctx);

        Randomizer replay = new RandomSource(42L).derive(3);
        for (int k = 0; k < count; k++) {
            int[] contestants = new int[t];
            for (int i = 0; i < t; i++) {
                contestants[i] = replay.nextInt(pop.size());
            }
            // Stable best-first order: fitness ascending, ties keep draw order.
            for (int i = 1; i < t; i++) {
                int key = contestants[i];
                long keyFitness = pop.get(key).getFitness();
                int j = i - 1;
                while (j >= 0 && pop.get(contestants[j]).getFitness() > keyFitness) {
                    contestants[j + 1] = contestants[j];
                    j--;
                }
                contestants[j + 1] = key;
            }
            int winner = contestants[t - 1];
            for (int i = 0; i < t - 1; i++) {
                if (replay.nextDouble() < p) {
                    winner = contestants[i];
                    break;
                }
            }
            check(failures, picks.get(k) == pop.get(winner),
                    "replay: pick " + k + " diverges from the documented algorithm");
        }
        check(failures, ctx.getRandomizer().nextLong() == replay.nextLong(),
                "replay: stream positions diverged — selector drew extra randomness");
    }

    private static void determinism(List<String> failures) {
        Population pop = pop(30, 10, 20, 10, 30, 10);
        List<EvaluatedCandidate> a = new TournamentSelector(3, 0.7).selectParents(pop, 30, context(9001L, 1));
        List<EvaluatedCandidate> b = new TournamentSelector(3, 0.7).selectParents(pop, 30, context(9001L, 1));
        check(failures, a.equals(b), "determinism: same (seed, stream) picked differently");
        List<EvaluatedCandidate> c = new TournamentSelector(3, 0.7).selectParents(pop, 30, context(9002L, 1));
        check(failures, !a.equals(c), "determinism: different seeds picked identically");
    }

    private static void pressure(List<String> failures) {
        // 10 distinct fitnesses 0..90; best = index 0.
        Population pop = pop(0, 10, 20, 30, 40, 50, 60, 70, 80, 90);

        int[] t3 = pickCounts(new TournamentSelector(3, 1.0), pop, 20_000, context(5L, 0));
        check(failures, t3[0] > 4800, // expectation ≈ 5420
                "pressure t=3: best picked " + t3[0] + " of 20000, expected > 4800");
        check(failures, t3[9] < 150, // expectation ≈ 20
                "pressure t=3: worst picked " + t3[9] + " of 20000, expected < 150");

        int[] t1 = pickCounts(new TournamentSelector(1, 1.0), pop, 20_000, context(6L, 0));
        for (int i = 0; i < t1.length; i++) {
            check(failures, Math.abs(t1[i] - 2000) <= 300,
                    "pressure t=1: bucket " + i + " count " + t1[i] + " far from uniform 2000");
        }

        // The p knob on a 2-member population with t=2: P(best) = 0.25 + 0.5·p.
        Population two = pop(10, 20);
        int strongBest = pickCounts(new TournamentSelector(2, 1.0), two, 10_000, context(7L, 0))[0];
        int weakBest = pickCounts(new TournamentSelector(2, 0.1), two, 10_000, context(8L, 0))[0];
        check(failures, strongBest >= 7100 && strongBest <= 7900, // expectation 7500
                "pressure p=1: best picked " + strongBest + " of 10000, expected ~7500");
        check(failures, weakBest >= 2600 && weakBest <= 3400, // expectation 3000
                "pressure p=0.1: best picked " + weakBest + " of 10000, expected ~3000");
    }

    private static void saturation(List<String> failures) {
        Population pop = pop(10, 20, 30, 40, 50);
        int[] counts = pickCounts(new TournamentSelector(50, 1.0), pop, 1000, context(9L, 0));
        check(failures, counts[0] >= 950, // P(non-best per pick) = 0.8^50 ≈ 1.4e-5
                "saturation t=50>μ=5: best picked " + counts[0] + " of 1000");
    }

    private static void timerContract(List<String> failures) {
        TournamentSelector selector = new TournamentSelector(2, 1.0);
        Population pop = pop(10, 20, 30);
        check(failures, selector.getInvocations() == 0, "timer: fresh step has invocations != 0");
        selector.selectParents(pop, 4, context(1L, 0));
        selector.selectParents(pop, 4, context(2L, 0));
        check(failures, selector.getInvocations() == 2,
                "timer: after two bulk calls invocations = " + selector.getInvocations());
        check(failures, selector.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    private static int[] pickCounts(TournamentSelector selector, Population pop, int draws,
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
