package qapSolver.GA.Improvement;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link NoOpImprovement}: pure identity
 * (the very same list object back, members untouched by reference and
 * order); the empty batch; context untouched (no evaluation counts, no
 * randomness, no incumbent interaction); and step-timer bookkeeping.
 *
 * Usage: NoOpImprovementTest (no arguments; synthetic members, no dataset
 * dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class NoOpImprovementTest {

    private NoOpImprovementTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        identity(failures);
        emptyBatch(failures);
        contextUntouched(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void identity(List<String> failures) {
        List<EvaluatedCandidate> batch = members(30, 10, 20);
        List<EvaluatedCandidate> snapshot = new ArrayList<>(batch);
        List<EvaluatedCandidate> result = new NoOpImprovement().improve(batch, context(1L));
        check(failures, result == batch, "identity: must return the very same list object");
        check(failures, result.size() == 3, "identity: size changed");
        for (int i = 0; i < 3; i++) {
            check(failures, result.get(i) == snapshot.get(i),
                    "identity: member " + i + " reference or order changed");
        }
    }

    private static void emptyBatch(List<String> failures) {
        List<EvaluatedCandidate> empty = new ArrayList<>();
        List<EvaluatedCandidate> result = new NoOpImprovement().improve(empty, context(2L));
        check(failures, result == empty && result.isEmpty(), "empty: identity must hold for []");
    }

    private static void contextUntouched(List<String> failures) {
        AlgorithmContext ctx = context(3L);
        new NoOpImprovement().improve(members(10, 20), ctx);
        check(failures, ctx.getFullEvaluations() == 0 && ctx.getDeltaEvaluations() == 0,
                "context: NoOp counted evaluations");
        check(failures, !ctx.hasIncumbent(), "context: NoOp touched the incumbent");
        Randomizer untouched = new RandomSource(3L).derive(0);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "context: NoOp consumed randomness");
    }

    private static void timerContract(List<String> failures) {
        NoOpImprovement improvement = new NoOpImprovement();
        check(failures, improvement.getInvocations() == 0, "timer: fresh step has invocations != 0");
        improvement.improve(members(10), context(4L));
        improvement.improve(members(20), context(5L));
        check(failures, improvement.getInvocations() == 2,
                "timer: after two calls invocations = " + improvement.getInvocations());
        check(failures, improvement.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // ---- helpers ----

    private static List<EvaluatedCandidate> members(long... fitnesses) {
        List<EvaluatedCandidate> list = new ArrayList<>(fitnesses.length);
        for (long f : fitnesses) {
            list.add(new EvaluatedCandidate(new int[] {0, 1, 2, 3}, f));
        }
        return list;
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
