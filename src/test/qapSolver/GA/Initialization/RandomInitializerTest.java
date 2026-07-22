package qapSolver.GA.Initialization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link RandomInitializer}: constructor
 * validation, batch shape (size μ, valid fresh permutations, distinct owned
 * arrays), and — the repeatability claim — a bit-exact replay from an
 * independently derived same-seed stream: every candidate must equal identity
 * + one shuffle, in order, with no extra draws, proving all randomness comes
 * from the engine-owned context stream and nowhere else. Plus same-seed
 * determinism / cross-seed difference, the n=1 edge, 3! ordering uniformity
 * through the operator, and step-timer bookkeeping.
 *
 * Usage: RandomInitializerTest (no arguments; synthetic instances, no dataset
 * dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class RandomInitializerTest {

    private RandomInitializerTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        batchContract(failures);
        streamReplay(failures);
        determinism(failures);
        singleElementInstance(failures);
        orderingUniformity(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        for (int bad : new int[] {0, -1, Integer.MIN_VALUE}) {
            boolean threw = false;
            try {
                new RandomInitializer(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor(" + bad + "): expected IllegalArgumentException");
        }
        new RandomInitializer(1); // minimum legal μ must construct
    }

    /** Size μ; every member a valid fresh permutation on its own array. */
    private static void batchContract(List<String> failures) {
        int n = 20;
        for (int mu : new int[] {1, 2, 30}) {
            List<Candidate> batch = new RandomInitializer(mu)
                    .initialize(context(n, 42L, 0));
            check(failures, batch.size() == mu,
                    "batch: μ=" + mu + " returned size " + batch.size());
            for (Candidate c : batch) {
                check(failures, c.size() == n, "batch: candidate size " + c.size() + " != n=" + n);
                check(failures, isPermutation(c.getPermutation(), n),
                        "batch: candidate is not a valid 0-based permutation: "
                                + Arrays.toString(c.getPermutation()));
            }
            // Ownership: every candidate on its own array (no shared references).
            for (int i = 0; i < batch.size(); i++) {
                for (int j = i + 1; j < batch.size(); j++) {
                    check(failures, batch.get(i).getPermutation() != batch.get(j).getPermutation(),
                            "batch: candidates " + i + " and " + j + " share one array");
                }
            }
        }

        // Content distinctness: duplicates are legal but a duplicate among 30
        // uniform draws from 20! (p ≈ 2e-16) means an array-reuse bug in practice.
        List<Candidate> batch = new RandomInitializer(30).initialize(context(n, 7L, 0));
        for (int i = 0; i < batch.size(); i++) {
            for (int j = i + 1; j < batch.size(); j++) {
                check(failures, !Arrays.equals(batch.get(i).getPermutation(),
                                batch.get(j).getPermutation()),
                        "batch: candidates " + i + " and " + j + " identical (n=20, μ=30)");
            }
        }
    }

    /**
     * The repeatability claim: the whole batch must replay bit-exactly from an
     * independently derived (sameSeed, sameId) stream — candidate k is identity
     * + the stream's k-th shuffle, in order, and afterwards both streams are at
     * the same position (no hidden extra draws). Any operator-private
     * randomness or construction change breaks this test.
     */
    private static void streamReplay(List<String> failures) {
        int n = 15;
        int mu = 10;
        AlgorithmContext context = context(n, 42L, 3);
        List<Candidate> batch = new RandomInitializer(mu).initialize(context);

        Randomizer replay = new RandomSource(42L).derive(3);
        for (int k = 0; k < mu; k++) {
            int[] expected = identity(n);
            replay.shuffle(expected);
            check(failures, Arrays.equals(expected, batch.get(k).getPermutation()),
                    "replay: candidate " + k + " expected " + Arrays.toString(expected)
                            + " got " + Arrays.toString(batch.get(k).getPermutation()));
        }
        check(failures, context.getRandomizer().nextLong() == replay.nextLong(),
                "replay: stream positions diverged — initializer drew extra randomness");
    }

    private static void determinism(List<String> failures) {
        int n = 20;
        int mu = 10;
        List<Candidate> a = new RandomInitializer(mu).initialize(context(n, 9001L, 1));
        List<Candidate> b = new RandomInitializer(mu).initialize(context(n, 9001L, 1));
        for (int k = 0; k < mu; k++) {
            check(failures, Arrays.equals(a.get(k).getPermutation(), b.get(k).getPermutation()),
                    "determinism: same (seed, stream) differ at candidate " + k);
        }

        List<Candidate> c = new RandomInitializer(mu).initialize(context(n, 9002L, 1));
        boolean anyDiffers = false;
        for (int k = 0; k < mu && !anyDiffers; k++) {
            anyDiffers = !Arrays.equals(a.get(k).getPermutation(), c.get(k).getPermutation());
        }
        check(failures, anyDiffers, "determinism: different seeds produced identical batches");
    }

    private static void singleElementInstance(List<String> failures) {
        List<Candidate> batch = new RandomInitializer(4).initialize(context(1, 5L, 0));
        check(failures, batch.size() == 4, "n=1: batch size " + batch.size());
        for (Candidate c : batch) {
            check(failures, c.size() == 1 && c.getPermutation()[0] == 0,
                    "n=1: candidate is not [0]: " + Arrays.toString(c.getPermutation()));
        }
    }

    /** End-to-end uniformity: all 3! orderings, no bias added by the operator. */
    private static void orderingUniformity(List<String> failures) {
        List<Candidate> batch = new RandomInitializer(6000).initialize(context(3, 31337L, 0));
        Map<String, Integer> orderings = new HashMap<>();
        for (Candidate c : batch) {
            orderings.merge(Arrays.toString(c.getPermutation()), 1, Integer::sum);
        }
        check(failures, orderings.size() == 6,
                "uniformity: saw " + orderings.size() + " of 6 orderings");
        for (Map.Entry<String, Integer> e : orderings.entrySet()) {
            check(failures, Math.abs(e.getValue() - 1000) <= 150,
                    "uniformity: ordering " + e.getKey() + " count " + e.getValue()
                            + " far from 1000");
        }
    }

    /** AlgorithmStep bookkeeping: the final entry point records every call. */
    private static void timerContract(List<String> failures) {
        RandomInitializer init = new RandomInitializer(5);
        check(failures, init.getInvocations() == 0, "timer: fresh step has invocations != 0");
        init.initialize(context(8, 1L, 0));
        check(failures, init.getInvocations() == 1,
                "timer: after one call invocations = " + init.getInvocations());
        init.initialize(context(8, 2L, 0));
        check(failures, init.getInvocations() == 2,
                "timer: after two calls invocations = " + init.getInvocations());
        check(failures, init.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    /** Context over a synthetic n-by-n zero instance and the derived stream. */
    private static AlgorithmContext context(int n, long masterSeed, int streamId) {
        QAPInstance instance = new QAPInstance("synthetic" + n, new int[n][n], new int[n][n]);
        return new AlgorithmContext(instance, new RandomSource(masterSeed).derive(streamId));
    }

    private static int[] identity(int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }
        return p;
    }

    private static boolean isPermutation(int[] p, int n) {
        if (p.length != n) {
            return false;
        }
        boolean[] seen = new boolean[n];
        for (int v : p) {
            if (v < 0 || v >= n || seen[v]) {
                return false;
            }
            seen[v] = true;
        }
        return true;
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
