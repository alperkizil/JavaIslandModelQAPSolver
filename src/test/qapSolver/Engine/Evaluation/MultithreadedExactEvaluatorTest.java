package qapSolver.Engine.Evaluation;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link MultithreadedExactEvaluator}:
 * constructor validation; value-and-order equivalence with the sequential
 * {@link ExactEvaluator} across worker counts × batch sizes (including
 * batches smaller than, equal to and much larger than the worker count, and
 * the empty batch), with zero-copy array identity and full-evaluation
 * counting checked on the parallel side; a larger stress batch (100
 * candidates, 4 workers, n = 40) re-checking slot-exact equivalence under
 * real parallelism; no randomness consumed; worker-exception propagation
 * (a wrong-length permutation surfaces as the objective's
 * IllegalArgumentException and the evaluator stays usable); shutdown
 * (idempotent, evaluating afterwards throws IllegalStateException); and
 * step-timer bookkeeping (invocations = batches).
 *
 * Usage: MultithreadedExactEvaluatorTest (no arguments; synthetic
 * instances, no dataset dependency). Exit code 0 = full pass, 1 = any
 * failure.
 */
public final class MultithreadedExactEvaluatorTest {

    private MultithreadedExactEvaluatorTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        equivalence(failures);
        stressEquivalence(failures);
        stackedWithCache(failures);
        noRandomnessConsumed(failures);
        exceptionPropagation(failures);
        shutdownContract(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        for (int bad : new int[] {0, -2}) {
            boolean threw = false;
            try {
                new MultithreadedExactEvaluator(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor workerCount=" + bad + ": expected throw");
        }
        new MultithreadedExactEvaluator(1).shutdown();
    }

    private static void equivalence(List<String> failures) {
        QAPInstance instance = randomInstance(12, 31L);
        Randomizer permStream = new RandomSource(31L).derive(90);
        for (int workers : new int[] {1, 3, 8}) {
            MultithreadedExactEvaluator parallel = new MultithreadedExactEvaluator(workers);
            for (int batchSize : new int[] {0, 1, 2, 7, 8, 25}) {
                List<int[]> arrays = new ArrayList<>(batchSize);
                for (int k = 0; k < batchSize; k++) {
                    arrays.add(randomPermutation(12, permStream));
                }
                AlgorithmContext parallelCtx = context(instance, 1L);
                AlgorithmContext plainCtx = context(instance, 1L);

                List<Candidate> parallelBatch = new ArrayList<>();
                List<Candidate> plainBatch = new ArrayList<>();
                for (int[] p : arrays) {
                    parallelBatch.add(new Candidate(p));
                    plainBatch.add(new Candidate(p.clone()));
                }
                List<EvaluatedCandidate> got = parallel.evaluate(parallelBatch, parallelCtx);
                List<EvaluatedCandidate> expected = new ExactEvaluator().evaluate(plainBatch, plainCtx);

                String tag = "equivalence workers=" + workers + " batch=" + batchSize;
                check(failures, got.size() == batchSize, tag + ": wrong result count " + got.size());
                for (int i = 0; i < batchSize; i++) {
                    check(failures, got.get(i).getFitness() == expected.get(i).getFitness(),
                            tag + ": slot " + i + " fitness diverges from sequential");
                    check(failures, got.get(i).getPermutation() == arrays.get(i),
                            tag + ": slot " + i + " does not own its candidate's array");
                }
                check(failures, parallelCtx.getFullEvaluations() == batchSize,
                        tag + ": counted " + parallelCtx.getFullEvaluations() + " evaluations");
            }
            parallel.shutdown();
        }
    }

    private static void stressEquivalence(List<String> failures) {
        QAPInstance instance = randomInstance(40, 32L);
        Randomizer permStream = new RandomSource(32L).derive(90);
        int batchSize = 100;
        List<int[]> arrays = new ArrayList<>(batchSize);
        for (int k = 0; k < batchSize; k++) {
            arrays.add(randomPermutation(40, permStream));
        }
        List<Candidate> parallelBatch = new ArrayList<>();
        List<Candidate> plainBatch = new ArrayList<>();
        for (int[] p : arrays) {
            parallelBatch.add(new Candidate(p));
            plainBatch.add(new Candidate(p.clone()));
        }
        MultithreadedExactEvaluator parallel = new MultithreadedExactEvaluator(4);
        List<EvaluatedCandidate> got = parallel.evaluate(parallelBatch, context(instance, 2L));
        List<EvaluatedCandidate> expected = new ExactEvaluator().evaluate(plainBatch, context(instance, 2L));
        for (int i = 0; i < batchSize; i++) {
            check(failures, got.get(i).getFitness() == expected.get(i).getFitness(),
                    "stress: slot " + i + " fitness diverges from sequential");
        }
        parallel.shutdown();
    }

    /** The sanctioned stack: CachingEvaluator(MultithreadedExactEvaluator). */
    private static void stackedWithCache(List<String> failures) {
        QAPInstance instance = randomInstance(15, 37L);
        Randomizer permStream = new RandomSource(37L).derive(90);
        List<int[]> arrays = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            arrays.add(randomPermutation(15, permStream));
        }
        MultithreadedExactEvaluator parallel = new MultithreadedExactEvaluator(3);
        CachingEvaluator stack = new CachingEvaluator(parallel, 100);
        AlgorithmContext ctx = context(instance, 7L);

        List<Candidate> first = new ArrayList<>();
        for (int[] p : arrays) {
            first.add(new Candidate(p));
        }
        List<EvaluatedCandidate> firstResults = stack.evaluate(first, ctx);
        check(failures, ctx.getFullEvaluations() == 6,
                "stack: first pass expected 6 evaluations, got " + ctx.getFullEvaluations());

        // Same content again plus one new permutation: only the new one computes.
        List<Candidate> second = new ArrayList<>();
        for (int[] p : arrays) {
            second.add(new Candidate(p.clone()));
        }
        int[] fresh = randomPermutation(15, permStream);
        second.add(new Candidate(fresh));
        List<EvaluatedCandidate> secondResults = stack.evaluate(second, ctx);
        check(failures, ctx.getFullEvaluations() == 7,
                "stack: second pass expected 1 new evaluation, got total " + ctx.getFullEvaluations());
        for (int i = 0; i < 6; i++) {
            check(failures, secondResults.get(i).getFitness() == firstResults.get(i).getFitness(),
                    "stack: cached slot " + i + " fitness diverges");
        }
        long expectedFresh = qapSolver.Objective.ObjectiveFunction.evaluate(instance, fresh);
        check(failures, secondResults.get(6).getFitness() == expectedFresh,
                "stack: fresh slot fitness wrong through the full stack");
        check(failures, stack.getHits() == 6 && stack.getMisses() == 7,
                "stack: counters hits=" + stack.getHits() + " misses=" + stack.getMisses());
        parallel.shutdown();
    }

    private static void noRandomnessConsumed(List<String> failures) {
        QAPInstance instance = randomInstance(6, 33L);
        AlgorithmContext ctx = new AlgorithmContext(instance, new RandomSource(9L).derive(3));
        MultithreadedExactEvaluator parallel = new MultithreadedExactEvaluator(3);
        Randomizer permStream = new RandomSource(33L).derive(90);
        List<Candidate> batch = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            batch.add(new Candidate(randomPermutation(6, permStream)));
        }
        parallel.evaluate(batch, ctx);
        parallel.shutdown();
        Randomizer untouched = new RandomSource(9L).derive(3);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "randomness: evaluator consumed draws from the context stream");
    }

    private static void exceptionPropagation(List<String> failures) {
        QAPInstance instance = randomInstance(8, 34L);
        MultithreadedExactEvaluator parallel = new MultithreadedExactEvaluator(2);
        Randomizer permStream = new RandomSource(34L).derive(90);
        List<Candidate> bad = new ArrayList<>();
        bad.add(new Candidate(randomPermutation(8, permStream)));
        bad.add(new Candidate(new int[] {0, 1, 2})); // wrong length for n=8
        boolean threw = false;
        try {
            parallel.evaluate(bad, context(instance, 3L));
        } catch (IllegalArgumentException e) {
            threw = true; // the objective's own exception, rethrown as-is
        }
        check(failures, threw, "exceptions: wrong-length permutation must surface as IAE");

        // Evaluator must remain usable after a worker failure.
        List<Candidate> good = new ArrayList<>();
        good.add(new Candidate(randomPermutation(8, permStream)));
        AlgorithmContext ctx = context(instance, 4L);
        List<EvaluatedCandidate> results = parallel.evaluate(good, ctx);
        check(failures, results.size() == 1 && ctx.getFullEvaluations() == 1,
                "exceptions: evaluator unusable after a worker failure");
        parallel.shutdown();
    }

    private static void shutdownContract(List<String> failures) {
        QAPInstance instance = randomInstance(5, 35L);
        MultithreadedExactEvaluator parallel = new MultithreadedExactEvaluator(2);
        parallel.shutdown();
        parallel.shutdown(); // idempotent
        List<Candidate> batch = new ArrayList<>();
        batch.add(new Candidate(new int[] {0, 1, 2, 3, 4}));
        boolean threw = false;
        try {
            parallel.evaluate(batch, context(instance, 5L));
        } catch (IllegalStateException e) {
            threw = true;
        }
        check(failures, threw, "shutdown: evaluating after shutdown must throw IllegalStateException");
    }

    private static void timerContract(List<String> failures) {
        QAPInstance instance = randomInstance(5, 36L);
        MultithreadedExactEvaluator parallel = new MultithreadedExactEvaluator(2);
        check(failures, parallel.getInvocations() == 0, "timer: fresh step has invocations != 0");
        Randomizer permStream = new RandomSource(36L).derive(90);
        AlgorithmContext ctx = context(instance, 6L);
        for (int call = 0; call < 2; call++) {
            List<Candidate> batch = new ArrayList<>();
            for (int k = 0; k < 3; k++) {
                batch.add(new Candidate(randomPermutation(5, permStream)));
            }
            parallel.evaluate(batch, ctx);
        }
        check(failures, parallel.getInvocations() == 2,
                "timer: after two batches invocations = " + parallel.getInvocations());
        check(failures, parallel.getTotalNanos() >= 0, "timer: negative total nanos");
        parallel.shutdown();
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

    private static AlgorithmContext context(QAPInstance instance, long seed) {
        return new AlgorithmContext(instance, new RandomSource(seed).derive(0));
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
