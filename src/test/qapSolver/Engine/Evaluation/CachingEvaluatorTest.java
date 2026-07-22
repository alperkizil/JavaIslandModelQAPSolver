package qapSolver.Engine.Evaluation;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.FitnessEvaluator;
import qapSolver.Model.QAPInstance;
import qapSolver.Objective.ObjectiveFunction;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link CachingEvaluator}: constructor
 * validation; the miss path (exact values, order, zero-copy ownership,
 * delegation observed through a recording inner evaluator); the hit path
 * (same content re-evaluated: no inner invocation at all, no evaluation
 * counts, each result owning its own fresh array); mixed batches
 * (interleaved hits/misses, inner sees exactly the misses in order);
 * same-batch repeats (evaluated once, every slot owns its own array);
 * LRU semantics (eviction beyond capacity, hit-refreshed recency changes
 * the eviction victim); an equivalence sweep against an undecorated
 * {@link ExactEvaluator} on overlapping batches (identical values, strictly
 * fewer full evaluations); no randomness consumed; and step-timer
 * bookkeeping (decorator invocations = its calls; inner's only counts
 * delegated calls).
 *
 * Usage: CachingEvaluatorTest (no arguments; synthetic instances, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class CachingEvaluatorTest {

    private CachingEvaluatorTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        missPath(failures);
        hitPath(failures);
        mixedBatch(failures);
        sameBatchRepeats(failures);
        lruEviction(failures);
        equivalenceSweep(failures);
        noRandomnessConsumed(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        boolean threw = false;
        try {
            new CachingEvaluator(null, 10);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(failures, threw, "constructor: null inner must throw");
        for (int badCapacity : new int[] {0, -5}) {
            threw = false;
            try {
                new CachingEvaluator(new ExactEvaluator(), badCapacity);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor capacity=" + badCapacity + ": expected throw");
        }
        new CachingEvaluator(new ExactEvaluator(), 1);
    }

    private static void missPath(List<String> failures) {
        QAPInstance instance = randomInstance(8, 21L);
        AlgorithmContext ctx = context(instance, 1L);
        RecordingEvaluator recorder = new RecordingEvaluator();
        CachingEvaluator caching = new CachingEvaluator(recorder, 100);

        List<int[]> arrays = distinctPermutations(4, 8, 90L);
        List<EvaluatedCandidate> results = caching.evaluate(wrap(arrays), ctx);

        check(failures, results.size() == 4, "miss path: expected 4 results");
        for (int i = 0; i < 4; i++) {
            check(failures, results.get(i).getPermutation() == arrays.get(i),
                    "miss path: slot " + i + " does not own its candidate's array");
            long expected = ObjectiveFunction.evaluate(instance, arrays.get(i));
            check(failures, results.get(i).getFitness() == expected,
                    "miss path: slot " + i + " wrong fitness");
        }
        check(failures, ctx.getFullEvaluations() == 4,
                "miss path: expected 4 full evaluations, got " + ctx.getFullEvaluations());
        check(failures, caching.getHits() == 0 && caching.getMisses() == 4,
                "miss path: counters hits=" + caching.getHits() + " misses=" + caching.getMisses());
        check(failures, caching.getCachedCount() == 4,
                "miss path: cached count " + caching.getCachedCount());
        check(failures, recorder.batchSizes.size() == 1 && recorder.batchSizes.get(0) == 4,
                "miss path: inner should have seen one batch of 4");
    }

    private static void hitPath(List<String> failures) {
        QAPInstance instance = randomInstance(8, 22L);
        AlgorithmContext ctx = context(instance, 2L);
        RecordingEvaluator recorder = new RecordingEvaluator();
        CachingEvaluator caching = new CachingEvaluator(recorder, 100);

        List<int[]> arrays = distinctPermutations(3, 8, 91L);
        caching.evaluate(wrap(arrays), ctx);
        long innerInvocationsBefore = recorder.getInvocations();
        long evaluationsBefore = ctx.getFullEvaluations();

        // Same content, brand-new candidate arrays.
        List<int[]> fresh = new ArrayList<>();
        for (int[] a : arrays) {
            fresh.add(a.clone());
        }
        List<EvaluatedCandidate> results = caching.evaluate(wrap(fresh), ctx);

        for (int i = 0; i < 3; i++) {
            check(failures, results.get(i).getPermutation() == fresh.get(i),
                    "hit path: slot " + i + " must own the new candidate's array");
            long expected = ObjectiveFunction.evaluate(instance, fresh.get(i));
            check(failures, results.get(i).getFitness() == expected,
                    "hit path: slot " + i + " wrong fitness from cache");
        }
        check(failures, ctx.getFullEvaluations() == evaluationsBefore,
                "hit path: full evaluations changed on an all-hit batch");
        check(failures, recorder.getInvocations() == innerInvocationsBefore,
                "hit path: inner evaluator was invoked for an all-hit batch");
        check(failures, caching.getHits() == 3 && caching.getMisses() == 3,
                "hit path: counters hits=" + caching.getHits() + " misses=" + caching.getMisses());
    }

    private static void mixedBatch(List<String> failures) {
        QAPInstance instance = randomInstance(8, 23L);
        AlgorithmContext ctx = context(instance, 3L);
        RecordingEvaluator recorder = new RecordingEvaluator();
        CachingEvaluator caching = new CachingEvaluator(recorder, 100);

        List<int[]> known = distinctPermutations(2, 8, 92L);
        caching.evaluate(wrap(known), ctx);

        List<int[]> fresh = distinctPermutations(2, 8, 93L);
        // Interleave: known[0], fresh[0], known[1], fresh[1].
        List<int[]> batchArrays = new ArrayList<>();
        batchArrays.add(known.get(0).clone());
        batchArrays.add(fresh.get(0));
        batchArrays.add(known.get(1).clone());
        batchArrays.add(fresh.get(1));
        List<EvaluatedCandidate> results = caching.evaluate(wrap(batchArrays), ctx);

        for (int i = 0; i < 4; i++) {
            check(failures, results.get(i).getPermutation() == batchArrays.get(i),
                    "mixed: slot " + i + " does not own its array (order broken?)");
            long expected = ObjectiveFunction.evaluate(instance, batchArrays.get(i));
            check(failures, results.get(i).getFitness() == expected,
                    "mixed: slot " + i + " wrong fitness");
        }
        check(failures, ctx.getFullEvaluations() == 4,
                "mixed: expected 2+2 full evaluations, got " + ctx.getFullEvaluations());
        List<int[]> lastInnerBatch = recorder.batchContents.get(recorder.batchContents.size() - 1);
        check(failures, lastInnerBatch.size() == 2
                        && java.util.Arrays.equals(lastInnerBatch.get(0), fresh.get(0))
                        && java.util.Arrays.equals(lastInnerBatch.get(1), fresh.get(1)),
                "mixed: inner must see exactly the two misses in input order");
        check(failures, caching.getHits() == 2 && caching.getMisses() == 4,
                "mixed: counters hits=" + caching.getHits() + " misses=" + caching.getMisses());
    }

    private static void sameBatchRepeats(List<String> failures) {
        QAPInstance instance = randomInstance(8, 24L);
        AlgorithmContext ctx = context(instance, 4L);
        RecordingEvaluator recorder = new RecordingEvaluator();
        CachingEvaluator caching = new CachingEvaluator(recorder, 100);

        int[] p = distinctPermutations(1, 8, 94L).get(0);
        int[] q = distinctPermutations(1, 8, 95L).get(0);
        List<int[]> batchArrays = new ArrayList<>();
        batchArrays.add(p);
        batchArrays.add(q);
        batchArrays.add(p.clone()); // repeat of p within the same batch
        batchArrays.add(p.clone()); // and again
        List<EvaluatedCandidate> results = caching.evaluate(wrap(batchArrays), ctx);

        check(failures, ctx.getFullEvaluations() == 2,
                "repeats: expected 2 full evaluations for 2 unique contents, got "
                        + ctx.getFullEvaluations());
        check(failures, recorder.batchSizes.get(0) == 2,
                "repeats: inner should have seen only the 2 unique candidates");
        long expectedP = ObjectiveFunction.evaluate(instance, p);
        for (int slot : new int[] {0, 2, 3}) {
            check(failures, results.get(slot).getFitness() == expectedP,
                    "repeats: slot " + slot + " wrong fitness");
            check(failures, results.get(slot).getPermutation() == batchArrays.get(slot),
                    "repeats: slot " + slot + " must own its own array");
        }
        check(failures, results.get(0).getPermutation() != results.get(2).getPermutation()
                        && results.get(2).getPermutation() != results.get(3).getPermutation(),
                "repeats: repeated slots must not share one array");
        check(failures, caching.getHits() == 2 && caching.getMisses() == 2,
                "repeats: counters hits=" + caching.getHits() + " misses=" + caching.getMisses());
        check(failures, caching.getCachedCount() == 2,
                "repeats: one entry per unique content, cached " + caching.getCachedCount());
    }

    private static void lruEviction(List<String> failures) {
        QAPInstance instance = randomInstance(8, 25L);
        List<int[]> perms = distinctPermutations(3, 8, 96L);
        int[] a = perms.get(0);
        int[] b = perms.get(1);
        int[] c = perms.get(2);

        // Plain insertion order: A then B, cache full at 2; C evicts A (oldest).
        AlgorithmContext ctx1 = context(instance, 5L);
        CachingEvaluator caching1 = new CachingEvaluator(new ExactEvaluator(), 2);
        caching1.evaluate(wrap(listOf(a, b)), ctx1);
        caching1.evaluate(wrap(listOf(c)), ctx1);
        check(failures, caching1.getCachedCount() == 2,
                "lru: capacity 2 exceeded, cached " + caching1.getCachedCount());
        long evals = ctx1.getFullEvaluations(); // 3 so far
        caching1.evaluate(wrap(listOf(b.clone())), ctx1); // must still be a hit
        check(failures, ctx1.getFullEvaluations() == evals,
                "lru: B was evicted although A was older");
        caching1.evaluate(wrap(listOf(a.clone())), ctx1); // must be a miss again
        check(failures, ctx1.getFullEvaluations() == evals + 1,
                "lru: evicted A still answered from cache");

        // Hit-refreshed recency: A,B cached; hitting A makes B the LRU victim.
        AlgorithmContext ctx2 = context(instance, 6L);
        CachingEvaluator caching2 = new CachingEvaluator(new ExactEvaluator(), 2);
        caching2.evaluate(wrap(listOf(a, b)), ctx2);
        caching2.evaluate(wrap(listOf(a.clone())), ctx2); // refresh A
        caching2.evaluate(wrap(listOf(c)), ctx2); // evicts B, not A
        long evals2 = ctx2.getFullEvaluations(); // 3
        caching2.evaluate(wrap(listOf(a.clone())), ctx2);
        check(failures, ctx2.getFullEvaluations() == evals2,
                "lru: refreshed A was evicted — recency not access-ordered");
        caching2.evaluate(wrap(listOf(b.clone())), ctx2);
        check(failures, ctx2.getFullEvaluations() == evals2 + 1,
                "lru: B survived although A was refreshed after it");
    }

    /** Overlapping batches: decorated results identical, strictly fewer evaluations. */
    private static void equivalenceSweep(List<String> failures) {
        QAPInstance instance = randomInstance(12, 26L);
        Randomizer permStream = new RandomSource(7L).derive(97);
        List<int[]> pool = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            pool.add(randomPermutation(12, permStream));
        }
        // 3 batches drawing from the pool with overlaps.
        int[][][] batchPlan = {
                {pool.get(0), pool.get(1), pool.get(2)},
                {pool.get(2), pool.get(3), pool.get(0), pool.get(4)},
                {pool.get(5), pool.get(4), pool.get(1), pool.get(1)},
        };
        AlgorithmContext cachedCtx = context(instance, 8L);
        AlgorithmContext plainCtx = context(instance, 8L);
        CachingEvaluator caching = new CachingEvaluator(new ExactEvaluator(), 100);
        ExactEvaluator plain = new ExactEvaluator();
        for (int[][] batch : batchPlan) {
            List<int[]> cachedArrays = new ArrayList<>();
            List<int[]> plainArrays = new ArrayList<>();
            for (int[] p : batch) {
                cachedArrays.add(p.clone());
                plainArrays.add(p.clone());
            }
            List<EvaluatedCandidate> cachedResults = caching.evaluate(wrap(cachedArrays), cachedCtx);
            List<EvaluatedCandidate> plainResults = plain.evaluate(wrap(plainArrays), plainCtx);
            for (int i = 0; i < batch.length; i++) {
                check(failures, cachedResults.get(i).getFitness() == plainResults.get(i).getFitness(),
                        "sweep: decorated fitness diverges from undecorated at slot " + i);
            }
        }
        check(failures, plainCtx.getFullEvaluations() == 11,
                "sweep: undecorated must evaluate all 11, got " + plainCtx.getFullEvaluations());
        check(failures, cachedCtx.getFullEvaluations() == 6,
                "sweep: decorated must evaluate only the 6 unique, got "
                        + cachedCtx.getFullEvaluations());
    }

    private static void noRandomnessConsumed(List<String> failures) {
        QAPInstance instance = randomInstance(6, 27L);
        AlgorithmContext ctx = new AlgorithmContext(instance, new RandomSource(9L).derive(3));
        CachingEvaluator caching = new CachingEvaluator(new ExactEvaluator(), 10);
        caching.evaluate(wrap(distinctPermutations(3, 6, 98L)), ctx);
        Randomizer untouched = new RandomSource(9L).derive(3);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "randomness: decorator consumed draws from the context stream");
    }

    private static void timerContract(List<String> failures) {
        QAPInstance instance = randomInstance(5, 28L);
        AlgorithmContext ctx = context(instance, 10L);
        RecordingEvaluator recorder = new RecordingEvaluator();
        CachingEvaluator caching = new CachingEvaluator(recorder, 10);
        check(failures, caching.getInvocations() == 0, "timer: fresh step has invocations != 0");
        List<int[]> arrays = distinctPermutations(2, 5, 99L);
        caching.evaluate(wrap(arrays), ctx);
        List<int[]> fresh = new ArrayList<>();
        for (int[] p : arrays) {
            fresh.add(p.clone());
        }
        caching.evaluate(wrap(fresh), ctx); // all hits
        check(failures, caching.getInvocations() == 2,
                "timer: decorator invocations = " + caching.getInvocations() + ", expected 2");
        check(failures, recorder.getInvocations() == 1,
                "timer: inner invocations = " + recorder.getInvocations()
                        + ", expected 1 (all-hit batch skips inner)");
        check(failures, caching.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // ---- test double: exact inner evaluator recording what it is handed ----

    private static final class RecordingEvaluator extends FitnessEvaluator {

        final List<Integer> batchSizes = new ArrayList<>();
        final List<List<int[]>> batchContents = new ArrayList<>();

        @Override
        protected List<EvaluatedCandidate> doEvaluate(List<Candidate> candidates,
                AlgorithmContext context) {
            batchSizes.add(candidates.size());
            List<int[]> contents = new ArrayList<>(candidates.size());
            List<EvaluatedCandidate> results = new ArrayList<>(candidates.size());
            for (int i = 0; i < candidates.size(); i++) {
                int[] permutation = candidates.get(i).getPermutation();
                contents.add(permutation.clone());
                long fitness = ObjectiveFunction.evaluate(context.getInstance(), permutation);
                context.countFullEvaluation();
                results.add(new EvaluatedCandidate(permutation, fitness));
            }
            batchContents.add(contents);
            return results;
        }
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

    /** Pairwise-distinct random permutations (regenerated on collision). */
    private static List<int[]> distinctPermutations(int count, int n, long seed) {
        Randomizer random = new RandomSource(seed).derive(70);
        List<int[]> result = new ArrayList<>(count);
        while (result.size() < count) {
            int[] p = randomPermutation(n, random);
            boolean duplicate = false;
            for (int[] existing : result) {
                if (java.util.Arrays.equals(existing, p)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                result.add(p);
            }
        }
        return result;
    }

    private static int[] randomPermutation(int n, Randomizer random) {
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) {
            permutation[i] = i;
        }
        random.shuffle(permutation);
        return permutation;
    }

    private static List<int[]> listOf(int[]... arrays) {
        List<int[]> list = new ArrayList<>();
        for (int[] a : arrays) {
            list.add(a);
        }
        return list;
    }

    private static List<Candidate> wrap(List<int[]> arrays) {
        List<Candidate> batch = new ArrayList<>(arrays.size());
        for (int[] a : arrays) {
            batch.add(new Candidate(a));
        }
        return batch;
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
