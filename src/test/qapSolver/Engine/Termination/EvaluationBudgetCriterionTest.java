package qapSolver.Engine.Termination;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link EvaluationBudgetCriterion}:
 * constructor validation (including long budgets); the exact stop boundary
 * (false through budget−1 counted evaluations, true at the budget and
 * beyond); read-only behavior (counters and stream untouched); and
 * step-timer bookkeeping.
 *
 * Usage: EvaluationBudgetCriterionTest (no arguments; synthetic instances,
 * no dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class EvaluationBudgetCriterionTest {

    private EvaluationBudgetCriterionTest() {
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
        for (long bad : new long[] {0L, -5L}) {
            boolean threw = false;
            try {
                new EvaluationBudgetCriterion(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor budget=" + bad + ": expected throw");
        }
        new EvaluationBudgetCriterion(1L);
        new EvaluationBudgetCriterion(10_000_000_000L); // beyond int range
    }

    private static void stopBoundary(List<String> failures) {
        EvaluationBudgetCriterion criterion = new EvaluationBudgetCriterion(10L);
        AlgorithmContext ctx = context(1L);
        for (int e = 0; e < 9; e++) {
            check(failures, !criterion.shouldTerminate(ctx),
                    "boundary: stopped early at " + ctx.getFullEvaluations() + " evaluations");
            ctx.countFullEvaluation();
        }
        check(failures, !criterion.shouldTerminate(ctx), "boundary: stopped at 9 of 10");
        ctx.countFullEvaluation();
        check(failures, criterion.shouldTerminate(ctx), "boundary: no stop at the budget (10)");
        ctx.countFullEvaluation();
        check(failures, criterion.shouldTerminate(ctx), "boundary: no stop beyond the budget (11)");
    }

    private static void readOnly(List<String> failures) {
        EvaluationBudgetCriterion criterion = new EvaluationBudgetCriterion(5L);
        AlgorithmContext ctx = context(2L);
        ctx.countFullEvaluation();
        criterion.shouldTerminate(ctx);
        check(failures, ctx.getFullEvaluations() == 1 && ctx.getGeneration() == 0,
                "read-only: check modified context counters");
        Randomizer untouched = new RandomSource(2L).derive(0);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "read-only: check consumed randomness");
    }

    private static void timerContract(List<String> failures) {
        EvaluationBudgetCriterion criterion = new EvaluationBudgetCriterion(2L);
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
