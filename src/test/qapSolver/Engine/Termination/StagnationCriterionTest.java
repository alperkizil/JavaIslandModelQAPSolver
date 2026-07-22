package qapSolver.Engine.Termination;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link StagnationCriterion}: constructor
 * validation; no incumbent ⇒ never stops; the exact stop boundary (false
 * through X−1 stagnant generations, true at X); a strict improvement
 * resetting the clock (no stop at the would-be generation, stop X after the
 * improvement); a value tie NOT resetting the clock; read-only behavior
 * (stream untouched); and step-timer bookkeeping.
 *
 * Usage: StagnationCriterionTest (no arguments; synthetic instances, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class StagnationCriterionTest {

    private StagnationCriterionTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        noIncumbent(failures);
        stopBoundary(failures);
        improvementResets(failures);
        tieDoesNotReset(failures);
        readOnly(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        for (int bad : new int[] {0, -3}) {
            boolean threw = false;
            try {
                new StagnationCriterion(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor X=" + bad + ": expected throw");
        }
        new StagnationCriterion(1);
    }

    private static void noIncumbent(List<String> failures) {
        StagnationCriterion criterion = new StagnationCriterion(1);
        AlgorithmContext ctx = context(1L);
        ctx.advanceGeneration();
        ctx.advanceGeneration();
        check(failures, !criterion.shouldTerminate(ctx),
                "no incumbent: criterion stopped a run with nothing evaluated");
    }

    private static void stopBoundary(List<String> failures) {
        StagnationCriterion criterion = new StagnationCriterion(3);
        AlgorithmContext ctx = startedContext(2L, 1000L); // incumbent at generation 0
        for (int gen = 0; gen < 3; gen++) {
            check(failures, !criterion.shouldTerminate(ctx),
                    "boundary: stopped early at stagnation " + gen);
            ctx.advanceGeneration();
        }
        check(failures, criterion.shouldTerminate(ctx), "boundary: no stop at stagnation 3");
    }

    private static void improvementResets(List<String> failures) {
        StagnationCriterion criterion = new StagnationCriterion(3);
        AlgorithmContext ctx = startedContext(3L, 1000L);
        ctx.advanceGeneration();
        ctx.advanceGeneration();
        ctx.offerIncumbent(new EvaluatedCandidate(identity(4), 999L)); // improvement at gen 2
        ctx.advanceGeneration(); // gen 3: would stop without the improvement
        check(failures, !criterion.shouldTerminate(ctx),
                "reset: stopped although the incumbent improved at generation 2");
        ctx.advanceGeneration();
        check(failures, !criterion.shouldTerminate(ctx), "reset: stopped at stagnation 2");
        ctx.advanceGeneration(); // gen 5: stagnation 3 since the improvement
        check(failures, criterion.shouldTerminate(ctx),
                "reset: no stop 3 stagnant generations after the improvement");
    }

    private static void tieDoesNotReset(List<String> failures) {
        StagnationCriterion criterion = new StagnationCriterion(3);
        AlgorithmContext ctx = startedContext(4L, 1000L);
        ctx.advanceGeneration();
        ctx.advanceGeneration();
        ctx.offerIncumbent(new EvaluatedCandidate(identity(4), 1000L)); // tie at gen 2
        ctx.advanceGeneration(); // gen 3: stagnation 3 — the tie must not have reset it
        check(failures, criterion.shouldTerminate(ctx),
                "tie: an equal-value offer reset the stagnation clock");
    }

    private static void readOnly(List<String> failures) {
        StagnationCriterion criterion = new StagnationCriterion(5);
        AlgorithmContext ctx = startedContext(5L, 1000L);
        ctx.advanceGeneration();
        criterion.shouldTerminate(ctx);
        check(failures, ctx.getGeneration() == 1 && ctx.getBestValue() == 1000L,
                "read-only: check modified context state");
        Randomizer untouched = new RandomSource(5L).derive(0);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "read-only: check consumed randomness");
    }

    private static void timerContract(List<String> failures) {
        StagnationCriterion criterion = new StagnationCriterion(2);
        check(failures, criterion.getInvocations() == 0, "timer: fresh step has invocations != 0");
        AlgorithmContext ctx = startedContext(6L, 1000L);
        criterion.shouldTerminate(ctx);
        criterion.shouldTerminate(ctx);
        check(failures, criterion.getInvocations() == 2,
                "timer: after two checks invocations = " + criterion.getInvocations());
        check(failures, criterion.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // ---- helpers ----

    private static int[] identity(int n) {
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) {
            permutation[i] = i;
        }
        return permutation;
    }

    private static AlgorithmContext context(long seed) {
        QAPInstance instance = new QAPInstance("synthetic4", new int[4][4], new int[4][4]);
        return new AlgorithmContext(instance, new RandomSource(seed).derive(0));
    }

    /** Started context with an incumbent of the given value offered at generation 0. */
    private static AlgorithmContext startedContext(long seed, long value) {
        AlgorithmContext ctx = context(seed);
        ctx.start();
        ctx.offerIncumbent(new EvaluatedCandidate(identity(4), value));
        return ctx;
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
