package qapSolver.Engine;

/**
 * An unevaluated permutation on its way through breeding: the mutable working
 * object between creation (initialization, crossover) and fitness evaluation.
 * It holds nothing but the permutation — a fitness exists only on
 * {@link EvaluatedCandidate}, so "stale fitness" is unrepresentable: mutation
 * type-checks against Candidate and cannot touch an evaluated one.
 *
 * <p>Ownership: the constructor takes the array (not copied) and
 * {@link #getPermutation()} exposes it for in-place variation. Exactly one
 * step owns a Candidate at a time — the step that created it, then the
 * mutation operator, then the fitness evaluator, which consumes it (the array
 * moves into the resulting {@link EvaluatedCandidate}); a Candidate must not
 * be used after evaluation.
 *
 * <p>Hot-path trust, as in {@code ObjectiveFunction}: the caller guarantees
 * the array is a valid 0-based permutation of {0, …, n−1}. Only nullness is
 * checked here; validity is asserted by tests, not on every construction.
 */
public final class Candidate {

    private final int[] permutation;

    /** Takes ownership of the permutation array (not copied). */
    public Candidate(int[] permutation) {
        if (permutation == null) {
            throw new IllegalArgumentException("permutation must be non-null");
        }
        this.permutation = permutation;
    }

    /** Internal reference, not a copy — the in-place mutation surface. */
    public int[] getPermutation() {
        return permutation;
    }

    /** Problem size n. */
    public int size() {
        return permutation.length;
    }
}
