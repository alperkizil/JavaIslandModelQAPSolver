package qapSolver.Engine.Evaluation;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Model.QAPInstance;
import qapSolver.Objective.ObjectiveFunction;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link ExactEvaluator}: a hand-computed
 * anchor (n = 2, both permutations, checked against pencil-and-paper sums);
 * the bulk contract on random batches (exact values vs direct
 * {@link ObjectiveFunction} calls, input order preserved, zero-copy array
 * identity — each result owns exactly the array its candidate held); full
 * evaluations counted once per candidate across repeated calls; the empty
 * batch and n = 1 edges; no randomness consumed from the context stream; and
 * step-timer bookkeeping (invocations = batches).
 *
 * Usage: ExactEvaluatorTest (no arguments; synthetic instances, no dataset
 * dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class ExactEvaluatorTest {

    private ExactEvaluatorTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        handComputedAnchor(failures);
        bulkContract(failures);
        evaluationCounting(failures);
        edges(failures);
        noRandomnessConsumed(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    /** n = 2 with pencil-and-paper sums: identity = 70, transposition = 60. */
    private static void handComputedAnchor(List<String> failures) {
        QAPInstance instance = new QAPInstance("anchor2",
                new int[][] {{1, 2}, {3, 4}}, new int[][] {{5, 6}, {7, 8}});
        AlgorithmContext ctx = new AlgorithmContext(instance, new RandomSource(1L).derive(0));
        List<Candidate> batch = new ArrayList<>();
        batch.add(new Candidate(new int[] {0, 1}));
        batch.add(new Candidate(new int[] {1, 0}));
        List<EvaluatedCandidate> results = new ExactEvaluator().evaluate(batch, ctx);
        check(failures, results.size() == 2, "anchor: expected 2 results, got " + results.size());
        check(failures, results.get(0).getFitness() == 70L,
                "anchor: identity expected 70, got " + results.get(0).getFitness());
        check(failures, results.get(1).getFitness() == 60L,
                "anchor: transposition expected 60, got " + results.get(1).getFitness());
    }

    private static void bulkContract(List<String> failures) {
        for (int n : new int[] {3, 10, 30}) {
            QAPInstance instance = randomInstance(n, 17L);
            AlgorithmContext ctx = new AlgorithmContext(instance, new RandomSource(2L).derive(0));
            Randomizer permStream = new RandomSource(2L).derive(90);
            int batchSize = 7;
            List<Candidate> batch = new ArrayList<>(batchSize);
            List<int[]> arrays = new ArrayList<>(batchSize);
            for (int k = 0; k < batchSize; k++) {
                int[] p = randomPermutation(n, permStream);
                arrays.add(p);
                batch.add(new Candidate(p));
            }
            List<EvaluatedCandidate> results = new ExactEvaluator().evaluate(batch, ctx);
            check(failures, results.size() == batchSize,
                    "contract n=" + n + ": expected " + batchSize + " results, got " + results.size());
            for (int k = 0; k < batchSize; k++) {
                EvaluatedCandidate result = results.get(k);
                check(failures, result.getPermutation() == arrays.get(k),
                        "contract n=" + n + ": slot " + k + " does not own its candidate's array"
                                + " (zero-copy move violated or order broken)");
                long expected = ObjectiveFunction.evaluate(instance, arrays.get(k));
                check(failures, result.getFitness() == expected,
                        "contract n=" + n + ": slot " + k + " fitness " + result.getFitness()
                                + " != objective " + expected);
            }
        }
    }

    private static void evaluationCounting(List<String> failures) {
        QAPInstance instance = randomInstance(8, 3L);
        AlgorithmContext ctx = new AlgorithmContext(instance, new RandomSource(3L).derive(0));
        ExactEvaluator evaluator = new ExactEvaluator();
        Randomizer permStream = new RandomSource(3L).derive(90);
        evaluator.evaluate(batchOf(5, 8, permStream), ctx);
        check(failures, ctx.getFullEvaluations() == 5,
                "counting: after batch of 5 expected 5 full evaluations, got " + ctx.getFullEvaluations());
        evaluator.evaluate(batchOf(3, 8, permStream), ctx);
        check(failures, ctx.getFullEvaluations() == 8,
                "counting: after batches of 5+3 expected 8, got " + ctx.getFullEvaluations());
    }

    private static void edges(List<String> failures) {
        QAPInstance one = new QAPInstance("one", new int[][] {{7}}, new int[][] {{9}});
        AlgorithmContext ctx = new AlgorithmContext(one, new RandomSource(4L).derive(0));
        List<Candidate> single = new ArrayList<>();
        single.add(new Candidate(new int[] {0}));
        List<EvaluatedCandidate> results = new ExactEvaluator().evaluate(single, ctx);
        check(failures, results.size() == 1 && results.get(0).getFitness() == 63L,
                "edge n=1: expected fitness 7*9=63");

        List<EvaluatedCandidate> empty = new ExactEvaluator().evaluate(new ArrayList<>(), ctx);
        check(failures, empty.isEmpty(), "edge: empty batch must yield empty results");
        check(failures, ctx.getFullEvaluations() == 1,
                "edge: empty batch must not count evaluations, counter = " + ctx.getFullEvaluations());
    }

    private static void noRandomnessConsumed(List<String> failures) {
        QAPInstance instance = randomInstance(6, 5L);
        AlgorithmContext ctx = new AlgorithmContext(instance, new RandomSource(5L).derive(3));
        new ExactEvaluator().evaluate(batchOf(4, 6, new RandomSource(5L).derive(90)), ctx);
        Randomizer untouched = new RandomSource(5L).derive(3);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "randomness: evaluator consumed draws from the context stream");
    }

    private static void timerContract(List<String> failures) {
        QAPInstance instance = randomInstance(5, 6L);
        AlgorithmContext ctx = new AlgorithmContext(instance, new RandomSource(6L).derive(0));
        ExactEvaluator evaluator = new ExactEvaluator();
        check(failures, evaluator.getInvocations() == 0, "timer: fresh step has invocations != 0");
        Randomizer permStream = new RandomSource(6L).derive(90);
        evaluator.evaluate(batchOf(3, 5, permStream), ctx);
        evaluator.evaluate(batchOf(2, 5, permStream), ctx);
        check(failures, evaluator.getInvocations() == 2,
                "timer: after two batches invocations = " + evaluator.getInvocations());
        check(failures, evaluator.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // ---- helpers ----

    private static QAPInstance randomInstance(int n, long seed) {
        Randomizer random = new RandomSource(seed).derive(80);
        int[][] a = new int[n][n];
        int[][] b = new int[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                a[i][j] = random.nextInt(100);
                b[i][j] = random.nextInt(100);
            }
        }
        return new QAPInstance("synthetic" + n, a, b);
    }

    private static List<Candidate> batchOf(int count, int n, Randomizer permStream) {
        List<Candidate> batch = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            batch.add(new Candidate(randomPermutation(n, permStream)));
        }
        return batch;
    }

    private static int[] randomPermutation(int n, Randomizer random) {
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) {
            permutation[i] = i;
        }
        random.shuffle(permutation);
        return permutation;
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
