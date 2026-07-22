package qapSolver.GA.Mutation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link ReheatingSwapMutation}:
 * constructor validation; the mutation contract (in-place on the same array,
 * permutation validity, baseline = exactly one transposition ⇒ exactly two
 * changed positions); bit-exact stream replay of the documented 2k-draw swap
 * sequence across two same-generation children with final stream-position
 * agreement; n-scaling of both tiers (n = 20 vs n = 100 baseline and hot
 * strengths differ); the full reheat cycle against a hard-coded temperature
 * trace (baseline → reheat at the stagnation threshold → geometric cooling →
 * re-reheat under persistent stagnation); improvement resetting the
 * stagnation clock and NOT quenching an in-progress cooling phase; the n = 1
 * identity edge (zero draws); same-seed determinism vs cross-seed
 * difference; and step-timer bookkeeping (invocations = children).
 *
 * Usage: ReheatingSwapMutationTest (no arguments; synthetic instances, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class ReheatingSwapMutationTest {

    private ReheatingSwapMutationTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        mutationContract(failures);
        streamReplay(failures);
        scalingWithN(failures);
        reheatCycle(failures);
        improvementResetsAndNoQuench(failures);
        sizeOneEdge(failures);
        determinism(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        double[][] badFractions = {
                {0.0, 0.25}, {-0.1, 0.25}, {1.5, 0.25}, {Double.NaN, 0.25},
                {0.02, 0.0}, {0.02, -0.2}, {0.02, 1.1}, {0.02, Double.NaN},
                {0.30, 0.25}, // hot < baseline
        };
        for (double[] f : badFractions) {
            boolean threw = false;
            try {
                new ReheatingSwapMutation(f[0], f[1], 0.5, 10);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor (base=" + f[0] + ", hot=" + f[1]
                    + "): expected IllegalArgumentException");
        }
        for (double badAlpha : new double[] {0.0, 1.0, -0.5, 1.5, Double.NaN}) {
            boolean threw = false;
            try {
                new ReheatingSwapMutation(0.02, 0.25, badAlpha, 10);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor alpha=" + badAlpha + ": expected IllegalArgumentException");
        }
        for (int badS : new int[] {0, -3}) {
            boolean threw = false;
            try {
                new ReheatingSwapMutation(0.02, 0.25, 0.5, badS);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor S=" + badS + ": expected IllegalArgumentException");
        }
        new ReheatingSwapMutation(0.02, 0.25, 0.5, 1);
        new ReheatingSwapMutation(1.0, 1.0, 1e-9, 1); // extreme but legal bounds
    }

    private static void mutationContract(List<String> failures) {
        int n = 20;
        ReheatingSwapMutation mutation = new ReheatingSwapMutation(0.02, 0.25, 0.5, 100);
        int[] permutation = randomPermutation(n, new RandomSource(5L).derive(90));
        int[] snapshot = permutation.clone();
        Candidate candidate = new Candidate(permutation);

        mutation.mutate(candidate, context(1L, 0, n));
        check(failures, candidate.getPermutation() == permutation,
                "contract: candidate array was replaced, not mutated in place");
        check(failures, mutation.getCurrentSwaps() == 1,
                "contract: baseline swaps at n=20 expected 1, got " + mutation.getCurrentSwaps());
        check(failures, validPermutation(permutation), "contract: mutated array is not a permutation");
        int changed = 0;
        for (int i = 0; i < n; i++) {
            if (permutation[i] != snapshot[i]) {
                changed++;
            }
        }
        check(failures, changed == 2,
                "contract: one transposition must change exactly 2 positions, changed " + changed);
    }

    /** Bit-exact replay of the 2k-draw swap sequence over two same-generation children. */
    private static void streamReplay(List<String> failures) {
        int n = 20;
        int k = 4; // baselineFraction 0.2 at n=20
        ReheatingSwapMutation mutation = new ReheatingSwapMutation(0.2, 0.25, 0.5, 100);
        AlgorithmContext ctx = context(42L, 3, n);

        int[] childA = randomPermutation(n, new RandomSource(42L).derive(90));
        int[] childB = randomPermutation(n, new RandomSource(42L).derive(91));
        int[] expectedA = childA.clone();
        int[] expectedB = childB.clone();

        mutation.mutate(new Candidate(childA), ctx);
        mutation.mutate(new Candidate(childB), ctx);
        check(failures, mutation.getCurrentSwaps() == k,
                "replay: expected k=" + k + ", got " + mutation.getCurrentSwaps());

        Randomizer replay = new RandomSource(42L).derive(3);
        for (int[] expected : new int[][] {expectedA, expectedB}) {
            for (int s = 0; s < k; s++) {
                int i = replay.nextInt(n);
                int j = replay.nextInt(n - 1);
                if (j >= i) {
                    j++;
                }
                int tmp = expected[i];
                expected[i] = expected[j];
                expected[j] = tmp;
            }
        }
        check(failures, Arrays.equals(childA, expectedA),
                "replay: child A diverges from the documented swap sequence");
        check(failures, Arrays.equals(childB, expectedB),
                "replay: child B diverges from the documented swap sequence");
        check(failures, ctx.getRandomizer().nextLong() == replay.nextLong(),
                "replay: stream positions diverged — operator drew extra randomness");
    }

    /** Both tiers must scale with n: size 20 and size 100 are never treated equally. */
    private static void scalingWithN(List<String> failures) {
        // Baseline tier: round(0.02·20) floors to 1, round(0.02·100) = 2.
        int[] sizes = {20, 100};
        int[] expectedBaseline = {1, 2};
        int[] expectedHot = {5, 25}; // round(0.25·n)
        for (int idx = 0; idx < sizes.length; idx++) {
            int n = sizes[idx];
            ReheatingSwapMutation mutation = new ReheatingSwapMutation(0.02, 0.25, 0.5, 1);
            mutation.mutate(new Candidate(identity(n)), context(1L, 0, n));
            check(failures, mutation.getCurrentSwaps() == expectedBaseline[idx],
                    "scaling: baseline at n=" + n + " expected " + expectedBaseline[idx]
                            + ", got " + mutation.getCurrentSwaps());

            ReheatingSwapMutation hot = new ReheatingSwapMutation(0.02, 0.25, 0.5, 1);
            AlgorithmContext ctx = startedContext(2L, 0, n, 1000L);
            hot.mutate(new Candidate(identity(n)), ctx); // generation 0: stagnation 0
            ctx.advanceGeneration(); // stagnation 1 >= threshold 1, fully cooled
            hot.mutate(new Candidate(identity(n)), ctx);
            check(failures, hot.getCurrentSwaps() == expectedHot[idx],
                    "scaling: hot at n=" + n + " expected " + expectedHot[idx]
                            + ", got " + hot.getCurrentSwaps());
        }
    }

    /** Hard-coded temperature trace: baseline, reheat at S, geometric cooling, re-reheat. */
    private static void reheatCycle(List<String> failures) {
        int n = 100;
        // floor = max(1, round(0.01·100)) = 1, hot = 25, alpha = 0.5, S = 3.
        ReheatingSwapMutation mutation = new ReheatingSwapMutation(0.01, 0.25, 0.5, 3);
        AlgorithmContext ctx = startedContext(7L, 0, n, 1000L); // incumbent at generation 0
        int[] expected = {1, 1, 1, 25, 13, 6, 3, 2, 1, 25};
        for (int gen = 0; gen < expected.length; gen++) {
            if (gen > 0) {
                ctx.advanceGeneration();
            }
            mutation.mutate(new Candidate(identity(n)), ctx);
            check(failures, mutation.getCurrentSwaps() == expected[gen],
                    "reheat cycle: generation " + gen + " expected " + expected[gen]
                            + " swaps, got " + mutation.getCurrentSwaps());
        }
    }

    /** An improvement resets the stagnation clock but never quenches a hot phase. */
    private static void improvementResetsAndNoQuench(List<String> failures) {
        int n = 100;
        ReheatingSwapMutation mutation = new ReheatingSwapMutation(0.01, 0.25, 0.5, 3);
        AlgorithmContext ctx = startedContext(8L, 0, n, 1000L);
        mutation.mutate(new Candidate(identity(n)), ctx); // generation 0
        ctx.advanceGeneration();
        mutation.mutate(new Candidate(identity(n)), ctx); // generation 1, stagnation 1
        ctx.advanceGeneration();
        ctx.offerIncumbent(new EvaluatedCandidate(identity(n), 999L)); // improvement at generation 2
        for (int gen = 2; gen <= 4; gen++) {
            if (gen > 2) {
                ctx.advanceGeneration();
            }
            mutation.mutate(new Candidate(identity(n)), ctx);
            check(failures, mutation.getCurrentSwaps() == 1,
                    "stagnation reset: generation " + gen + " (would-be reheat without the"
                            + " improvement) expected 1 swap, got " + mutation.getCurrentSwaps());
        }
        ctx.advanceGeneration(); // generation 5: stagnation 3 since the improvement
        mutation.mutate(new Candidate(identity(n)), ctx);
        check(failures, mutation.getCurrentSwaps() == 25,
                "stagnation reset: reheat expected at generation 5, got "
                        + mutation.getCurrentSwaps() + " swaps");

        ctx.advanceGeneration(); // generation 6: improvement lands mid-cooling
        ctx.offerIncumbent(new EvaluatedCandidate(identity(n), 998L));
        mutation.mutate(new Candidate(identity(n)), ctx);
        check(failures, mutation.getCurrentSwaps() == 13,
                "no quench: cooling must continue geometrically after an improvement, got "
                        + mutation.getCurrentSwaps() + " swaps");
    }

    private static void sizeOneEdge(List<String> failures) {
        ReheatingSwapMutation mutation = new ReheatingSwapMutation(0.5, 1.0, 0.5, 1);
        AlgorithmContext ctx = context(3L, 0, 1);
        Candidate candidate = new Candidate(new int[] {0});
        mutation.mutate(candidate, ctx);
        check(failures, candidate.getPermutation()[0] == 0, "n=1: permutation changed");
        Randomizer untouched = new RandomSource(3L).derive(0);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "n=1: operator consumed randomness for an identity mutation");
    }

    private static void determinism(List<String> failures) {
        int n = 20;
        int[] base = randomPermutation(n, new RandomSource(11L).derive(90));
        int[] a = base.clone();
        int[] b = base.clone();
        int[] c = base.clone();
        new ReheatingSwapMutation(0.1, 0.25, 0.5, 100).mutate(new Candidate(a), context(9001L, 1, n));
        new ReheatingSwapMutation(0.1, 0.25, 0.5, 100).mutate(new Candidate(b), context(9001L, 1, n));
        new ReheatingSwapMutation(0.1, 0.25, 0.5, 100).mutate(new Candidate(c), context(9002L, 1, n));
        check(failures, Arrays.equals(a, b), "determinism: same (seed, stream) mutated differently");
        check(failures, !Arrays.equals(a, c), "determinism: different seeds mutated identically");
    }

    private static void timerContract(List<String> failures) {
        int n = 10;
        ReheatingSwapMutation mutation = new ReheatingSwapMutation(0.1, 0.25, 0.5, 10);
        check(failures, mutation.getInvocations() == 0, "timer: fresh step has invocations != 0");
        AlgorithmContext ctx = context(1L, 0, n);
        mutation.mutate(new Candidate(identity(n)), ctx);
        mutation.mutate(new Candidate(identity(n)), ctx);
        mutation.mutate(new Candidate(identity(n)), ctx);
        check(failures, mutation.getInvocations() == 3,
                "timer: after three children invocations = " + mutation.getInvocations());
        check(failures, mutation.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // ---- helpers ----

    private static int[] identity(int n) {
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) {
            permutation[i] = i;
        }
        return permutation;
    }

    private static int[] randomPermutation(int n, Randomizer random) {
        int[] permutation = identity(n);
        random.shuffle(permutation);
        return permutation;
    }

    private static boolean validPermutation(int[] p) {
        boolean[] seen = new boolean[p.length];
        for (int v : p) {
            if (v < 0 || v >= p.length || seen[v]) {
                return false;
            }
            seen[v] = true;
        }
        return true;
    }

    private static AlgorithmContext context(long masterSeed, int streamId, int n) {
        QAPInstance instance = new QAPInstance("synthetic" + n, new int[n][n], new int[n][n]);
        return new AlgorithmContext(instance, new RandomSource(masterSeed).derive(streamId));
    }

    /** Started context with an incumbent offered at generation 0 (value {@code value}). */
    private static AlgorithmContext startedContext(long masterSeed, int streamId, int n, long value) {
        AlgorithmContext ctx = context(masterSeed, streamId, n);
        ctx.start();
        ctx.offerIncumbent(new EvaluatedCandidate(identity(n), value));
        return ctx;
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
