package qapSolver.Engine.Evaluation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.FitnessEvaluator;

/**
 * Caching decorator over any {@link FitnessEvaluator}: remembers
 * (permutation → fitness) and answers repeats without re-evaluating. Per
 * batch, candidates split three ways — cache hits are answered directly,
 * repeats <em>within</em> the batch are answered by their first occurrence,
 * and only genuinely new permutations are delegated to the inner evaluator
 * (an all-hit batch never invokes it). Results return in input order; every
 * result owns its own candidate's array (hits construct the
 * {@link EvaluatedCandidate} from the candidate's array zero-copy, misses
 * inherit the inner evaluator's move), so the ownership contract holds on
 * every path.
 *
 * <p>Counting stays honest by construction: this class never calls
 * {@code countFullEvaluation()} — only the inner evaluator counts, and it
 * only sees actual computations. Keys are defensive copies (cache
 * correctness never depends on the do-not-mutate discipline of exposed
 * arrays); lookups use transient no-copy keys, so the O(n) copy is paid only
 * once per new cache entry.
 *
 * <p>Bounded LRU: at most {@code capacity} entries, least-recently-used
 * evicted first (a hit refreshes recency). Memory is ~n ints per entry —
 * the capacity knob is per-preset, measured, not guessed. {@link #getHits}
 * (answered without delegation: cache + same-batch repeats) and
 * {@link #getMisses} (delegated) are the counters that decide, per instance
 * family, whether this decorator earns its place in the stack.
 *
 * <p>Engine-thread only, like every step: the map is unsynchronized by
 * design. In a decorated stack this class goes <em>outermost</em> —
 * wrapping it around a parallel evaluator keeps the cache out of worker
 * threads (see the package decision in HANDOFF).
 */
public final class CachingEvaluator extends FitnessEvaluator {

    private final FitnessEvaluator inner;
    private final int capacity;
    private final LinkedHashMap<PermutationKey, Long> cache;
    private long hits;
    private long misses;

    /**
     * @param inner the evaluator that computes genuinely new permutations
     * @param capacity maximum cached entries (≥ 1), LRU-evicted beyond
     * @throws IllegalArgumentException on null inner or capacity < 1
     */
    public CachingEvaluator(FitnessEvaluator inner, int capacity) {
        if (inner == null) {
            throw new IllegalArgumentException("inner evaluator must be non-null");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1: " + capacity);
        }
        this.inner = inner;
        this.capacity = capacity;
        this.cache = new LinkedHashMap<PermutationKey, Long>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<PermutationKey, Long> eldest) {
                return size() > CachingEvaluator.this.capacity;
            }
        };
    }

    /** Candidates answered without delegation: cache hits + same-batch repeats. */
    public long getHits() {
        return hits;
    }

    /** Candidates delegated to the inner evaluator. */
    public long getMisses() {
        return misses;
    }

    /** Entries currently cached (≤ capacity). */
    public int getCachedCount() {
        return cache.size();
    }

    @Override
    protected List<EvaluatedCandidate> doEvaluate(List<Candidate> candidates, AlgorithmContext context) {
        int size = candidates.size();
        List<EvaluatedCandidate> results = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            results.add(null);
        }

        List<Candidate> missBatch = new ArrayList<>();
        int[] missSlots = new int[size];
        int[] repeatSlots = new int[size];
        int[] repeatOfMiss = new int[size];
        int missCount = 0;
        int repeatCount = 0;
        HashMap<PermutationKey, Integer> pendingByKey = new HashMap<>();

        for (int i = 0; i < size; i++) {
            Candidate candidate = candidates.get(i);
            PermutationKey key = new PermutationKey(candidate.getPermutation());
            Long cached = cache.get(key); // refreshes LRU recency on hit
            if (cached != null) {
                hits++;
                results.set(i, new EvaluatedCandidate(candidate.getPermutation(), cached));
                continue;
            }
            Integer pendingIndex = pendingByKey.get(key);
            if (pendingIndex != null) {
                hits++;
                repeatSlots[repeatCount] = i;
                repeatOfMiss[repeatCount] = pendingIndex;
                repeatCount++;
                continue;
            }
            misses++;
            pendingByKey.put(key, missCount);
            missSlots[missCount] = i;
            missBatch.add(candidate);
            missCount++;
        }

        if (missCount > 0) {
            List<EvaluatedCandidate> evaluated = inner.evaluate(missBatch, context);
            if (evaluated == null || evaluated.size() != missCount) {
                throw new IllegalStateException("inner evaluator returned "
                        + (evaluated == null ? "null" : evaluated.size() + " results")
                        + ", expected " + missCount);
            }
            for (int m = 0; m < missCount; m++) {
                EvaluatedCandidate result = evaluated.get(m);
                results.set(missSlots[m], result);
                cache.put(new PermutationKey(result.getPermutation().clone()), result.getFitness());
            }
            for (int r = 0; r < repeatCount; r++) {
                EvaluatedCandidate source = evaluated.get(repeatOfMiss[r]);
                int slot = repeatSlots[r];
                results.set(slot, new EvaluatedCandidate(
                        candidates.get(slot).getPermutation(), source.getFitness()));
            }
        }
        return results;
    }

    /** Content key: transient (no copy) for lookups, cloned array for stored entries. */
    private static final class PermutationKey {

        private final int[] permutation;
        private final int hash;

        PermutationKey(int[] permutation) {
            this.permutation = permutation;
            this.hash = Arrays.hashCode(permutation);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof PermutationKey)) {
                return false;
            }
            return Arrays.equals(permutation, ((PermutationKey) other).permutation);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
