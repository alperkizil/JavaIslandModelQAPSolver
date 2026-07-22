package qapSolver.Engine.Termination;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link TimeLimitCriterion}: constructor
 * validation; unstarted context fails loudly (IllegalStateException from the
 * clock); a generous limit does not stop a fresh run; a 1 ms limit stops
 * once the clock passes it (bounded busy-wait); read-only behavior (stream
 * untouched); and step-timer bookkeeping.
 *
 * Usage: TimeLimitCriterionTest (no arguments; synthetic instances, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class TimeLimitCriterionTest {

    private TimeLimitCriterionTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        unstartedFailsLoud(failures);
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
        for (long bad : new long[] {0L, -100L}) {
            boolean threw = false;
            try {
                new TimeLimitCriterion(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor limit=" + bad + ": expected throw");
        }
        new TimeLimitCriterion(1L);
    }

    private static void unstartedFailsLoud(List<String> failures) {
        TimeLimitCriterion criterion = new TimeLimitCriterion(100L);
        boolean threw = false;
        try {
            criterion.shouldTerminate(context(1L));
        } catch (IllegalStateException e) {
            threw = true;
        }
        check(failures, threw, "unstarted: expected IllegalStateException from the clock");
    }

    private static void stopBoundary(List<String> failures) {
        AlgorithmContext ctx = context(2L);
        ctx.start();
        check(failures, !new TimeLimitCriterion(60_000L).shouldTerminate(ctx),
                "boundary: generous limit stopped a fresh run");

        TimeLimitCriterion tight = new TimeLimitCriterion(1L);
        while (ctx.elapsedMillis() < 2) {
            // bounded busy-wait: sub-millisecond loop until the clock passes the limit
        }
        check(failures, tight.shouldTerminate(ctx), "boundary: 1 ms limit did not stop after 2 ms");
    }

    private static void readOnly(List<String> failures) {
        AlgorithmContext ctx = context(3L);
        ctx.start();
        new TimeLimitCriterion(60_000L).shouldTerminate(ctx);
        check(failures, ctx.getGeneration() == 0 && ctx.getFullEvaluations() == 0,
                "read-only: check modified context counters");
        Randomizer untouched = new RandomSource(3L).derive(0);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "read-only: check consumed randomness");
    }

    private static void timerContract(List<String> failures) {
        TimeLimitCriterion criterion = new TimeLimitCriterion(60_000L);
        check(failures, criterion.getInvocations() == 0, "timer: fresh step has invocations != 0");
        AlgorithmContext ctx = context(4L);
        ctx.start();
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
