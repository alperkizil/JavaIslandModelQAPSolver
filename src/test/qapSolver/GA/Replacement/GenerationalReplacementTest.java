package qapSolver.GA.Replacement;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link GenerationalReplacement}: full
 * turnover at λ = μ (fresh Population, offspring references slot-for-slot in
 * input order, input population object and members untouched); loud failure
 * on λ ≠ μ in both directions; no randomness consumed; and step-timer
 * bookkeeping.
 *
 * Usage: GenerationalReplacementTest (no arguments; synthetic members, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class GenerationalReplacementTest {

    private GenerationalReplacementTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        fullTurnover(failures);
        sizeMismatch(failures);
        noRandomnessConsumed(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void fullTurnover(List<String> failures) {
        Population current = population(10, 20, 30, 40);
        List<EvaluatedCandidate> before = snapshot(current);
        List<EvaluatedCandidate> offspring = members(15, 5, 35, 25);

        Population next = new GenerationalReplacement().replace(current, offspring, context(1L));
        check(failures, next != current, "turnover: must build a fresh Population");
        check(failures, next.size() == 4, "turnover: size " + next.size() + " != 4");
        for (int i = 0; i < 4; i++) {
            check(failures, next.get(i) == offspring.get(i),
                    "turnover: slot " + i + " is not the offspring reference in input order");
            check(failures, current.get(i) == before.get(i),
                    "turnover: input population was modified at slot " + i);
        }
    }

    private static void sizeMismatch(List<String> failures) {
        GenerationalReplacement replacement = new GenerationalReplacement();
        Population current = population(10, 20, 30, 40);
        for (int lambda : new int[] {3, 5}) {
            boolean threw = false;
            try {
                replacement.replace(current, members(fitnesses(lambda)), context(2L));
            } catch (IllegalStateException e) {
                threw = true;
            }
            check(failures, threw, "mismatch: λ=" + lambda + " vs μ=4 must throw IllegalStateException");
        }
        check(failures, current.size() == 4, "mismatch: failed call changed the population size");
    }

    private static void noRandomnessConsumed(List<String> failures) {
        AlgorithmContext ctx = context(3L);
        new GenerationalReplacement().replace(population(10, 20), members(1, 2), ctx);
        Randomizer untouched = new RandomSource(3L).derive(0);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "randomness: replacement consumed draws from the context stream");
    }

    private static void timerContract(List<String> failures) {
        GenerationalReplacement replacement = new GenerationalReplacement();
        check(failures, replacement.getInvocations() == 0, "timer: fresh step has invocations != 0");
        replacement.replace(population(10, 20), members(1, 2), context(4L));
        replacement.replace(population(30, 40), members(3, 4), context(5L));
        check(failures, replacement.getInvocations() == 2,
                "timer: after two calls invocations = " + replacement.getInvocations());
        check(failures, replacement.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // ---- helpers ----

    private static long[] fitnesses(int count) {
        long[] values = new long[count];
        for (int i = 0; i < count; i++) {
            values[i] = 10L * (i + 1);
        }
        return values;
    }

    private static List<EvaluatedCandidate> members(long... fitnesses) {
        List<EvaluatedCandidate> list = new ArrayList<>(fitnesses.length);
        for (long f : fitnesses) {
            list.add(new EvaluatedCandidate(new int[] {0, 1, 2, 3}, f));
        }
        return list;
    }

    private static Population population(long... fitnesses) {
        return new Population(members(fitnesses));
    }

    private static List<EvaluatedCandidate> snapshot(Population pop) {
        List<EvaluatedCandidate> out = new ArrayList<>(pop.size());
        for (int i = 0; i < pop.size(); i++) {
            out.add(pop.get(i));
        }
        return out;
    }

    private static AlgorithmContext context(long seed) {
        QAPInstance instance = new QAPInstance("synthetic4", new int[4][4], new int[4][4]);
        return new AlgorithmContext(instance, new RandomSource(seed).derive(0));
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
