package qapSolver.GA.Selection;

import qapSolver.Engine.Population;

/**
 * Per-generation linear-ranking table shared by the distribution-sampling
 * selectors (roulette wheel, SUS). Members are ranked worst → best by
 * (fitness descending, index ascending) — a total order, so the ranking is
 * unique and replay-deterministic even on tie-heavy families. Rank r
 * (0 = worst) gets weight {@code (2−s) + 2(s−1)·r/(μ−1)}: the best member
 * weighs s, the worst 2−s, the weights sum to exactly μ. s ∈ [1, 2] is the
 * pressure knob — 1 = uniform, 2 = strongest (worst weight drops to 0).
 * Rank-based weights are the minimization transform of choice here: scale-free
 * where raw proportional selection collapses on QAP's compressed relative
 * cost spreads.
 *
 * <p>Built once per bulk selection call (the reason {@code selectParents} is
 * bulk); read-only on the population. Sampling: {@link #sample} binary-searches
 * one point (roulette), {@link #advance} walks monotonically increasing points
 * (SUS pointers). A zero-weight rank (the worst at s = 2) is unreachable by
 * either — cumulative strictly exceeds the point before it.
 */
final class RankWeights {

    private final int[] memberByRank;    // rank r (0 = worst) -> population index
    private final double[] cumulative;   // cumulative[r] = Σ weights of ranks 0..r

    /** Ranks the population and builds the cumulative weight table. */
    RankWeights(Population population, double s) {
        int size = population.size();
        if (size == 0) {
            throw new IllegalArgumentException("population must be non-empty");
        }
        memberByRank = new int[size];
        for (int i = 0; i < size; i++) {
            memberByRank[i] = i;
        }
        // Insertion sort, worst first: fitness descending, ties index ascending.
        // Per-generation on a small μ; primitive and stable (strict shifts only).
        for (int i = 1; i < size; i++) {
            int key = memberByRank[i];
            long keyFitness = population.get(key).getFitness();
            int j = i - 1;
            while (j >= 0 && population.get(memberByRank[j]).getFitness() < keyFitness) {
                memberByRank[j + 1] = memberByRank[j];
                j--;
            }
            memberByRank[j + 1] = key;
        }
        cumulative = new double[size];
        double sum = 0.0;
        for (int r = 0; r < size; r++) {
            double weight = size == 1
                    ? 1.0
                    : (2.0 - s) + 2.0 * (s - 1.0) * r / (size - 1);
            sum += weight;
            cumulative[r] = sum;
        }
    }

    /** Total weight mass — exactly μ up to floating-point summation. */
    double total() {
        return cumulative[cumulative.length - 1];
    }

    /** Population index of the member holding rank r (0 = worst). */
    int memberAt(int rank) {
        return memberByRank[rank];
    }

    /**
     * Population index for a sampling point in [0, {@link #total()}): the
     * first rank whose cumulative weight strictly exceeds the point (binary
     * search, clamped to the last rank against floating-point edge rounding).
     */
    int sample(double point) {
        int lo = 0;
        int hi = cumulative.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (cumulative[mid] > point) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return memberByRank[lo];
    }

    /**
     * SUS walk: the first rank at or after {@code fromRank} whose cumulative
     * weight strictly exceeds {@code point}, clamped to the last rank. Points
     * must be non-decreasing across calls; the walk is O(μ + count) overall.
     */
    int advance(int fromRank, double point) {
        int rank = fromRank;
        while (rank < cumulative.length - 1 && cumulative[rank] <= point) {
            rank++;
        }
        return rank;
    }
}
