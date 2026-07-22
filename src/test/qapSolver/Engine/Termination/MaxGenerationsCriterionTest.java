package qapSolver.Engine.Termination;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link MaxGenerationsCriterion}:
 * constructor validation; the exact stop boundary (false through generation
 * limit−1, true at the limit and beyond); read-only behavior (context
 * counters and stream position untouched by checks); and step-timer
 * bookkeeping.
 *
 * Usage: MaxGenerationsCriterionTest (no arguments; synthetic instances, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class MaxGenerationsCriterionTest {

    private MaxGenerationsCriterionTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        stopBoundary(failures);
        readOnly(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        for (int bad : new int[] {0, -1}) {
            boolean threw = false;
            try {
                new MaxGenerationsCriterion(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor limit=" + bad + ": expected throw");
        }
        new MaxGenerationsCriterion(1);
    }

    private static void stopBoundary(List<String> failures) {
        MaxGenerationsCriterion criterion = new MaxGenerationsCriterion(5);
        AlgorithmContext ctx = context(1L);
        for (int gen = 0; gen < 5; gen++) {
            check(failures, !criterion.shouldTerminate(ctx),
                    "boundary: stopped early at generation " + gen);
            ctx.advanceGeneration();
        }
        check(failures, criterion.shouldTerminate(ctx), "boundary: no stop at the limit (5)");
        ctx.advanceGeneration();
        check(failures, criterion.shouldTerminate(ctx), "boundary: no stop beyond the limit (6)");
    }

    private static void readOnly(List<String> failures) {
        MaxGenerationsCriterion criterion = new MaxGenerationsCriterion(3);
        AlgorithmContext ctx = context(2L);
        ctx.advanceGeneration();
        criterion.shouldTerminate(ctx);
        check(failures, ctx.getGeneration() == 1 && ctx.getFullEvaluations() == 0,
                "read-only: check modified context counters");
        Randomizer untouched = new RandomSource(2L).derive(0);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "read-only: check consumed randomness");
    }

    private static void timerContract(List<String> failures) {
        MaxGenerationsCriterion criterion = new MaxGenerationsCriterion(2);
        check(failures, criterion.getInvocations() == 0, "timer: fresh step has invocations != 0");
        AlgorithmContext ctx = context(3L);
        criterion.shouldTerminate(ctx);
        criterion.shouldTerminate(ctx);
        check(failures, criterion.getInvocations() == 2,
                "timer: after two checks invocations = " + criterion.getInvocations());
        check(failures, criterion.getTotalNanos() >= 0, "timer: negative total nanos");
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
