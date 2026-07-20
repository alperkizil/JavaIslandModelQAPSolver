package qapSolver.Model;

/**
 * Abstract base for any QAP solution: a validated permutation in 0-based
 * indexing (p[i] = location assigned to facility i) plus an objective value
 * whose origin — file-reported, evaluated fitness, … — is defined by the
 * subclass.
 */
public abstract class QAPSolution {

    private final int[] permutation;

    /**
     * Takes ownership of the permutation array (not copied); it must be a
     * valid 0-based permutation of {0, …, n−1}.
     *
     * @throws IllegalArgumentException if it is not a valid permutation
     */
    protected QAPSolution(int[] permutation) {
        if (permutation == null) {
            throw new IllegalArgumentException("permutation must be non-null");
        }
        int n = permutation.length;
        if (n == 0) {
            throw new IllegalArgumentException("permutation is empty");
        }
        boolean[] seen = new boolean[n];
        for (int i = 0; i < n; i++) {
            int p = permutation[i];
            if (p < 0 || p >= n || seen[p]) {
                throw new IllegalArgumentException("not a valid 0-based permutation at index "
                        + i + " (value " + p + ")");
            }
            seen[p] = true;
        }
        this.permutation = permutation;
    }

    /** Problem size n. */
    public final int getSize() {
        return permutation.length;
    }

    /** Internal reference, not a copy — do not mutate. Always 0-based. */
    public final int[] getPermutation() {
        return permutation;
    }

    /** Objective value of this solution; its meaning is defined by the subclass. */
    public abstract long getValue();
}
