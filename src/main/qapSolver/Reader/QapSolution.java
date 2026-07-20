package qapSolver.Reader;

/**
 * Immutable reference solution for a QAP instance: a name, the size n, the
 * reported objective value, and a permutation normalized to 0-based indexing
 * (p[i] = location assigned to facility i).
 *
 * Note: eight QAPLIB .sln files store the inverse permutation convention
 * (their value reproduces under Σ A[p(i)][p(j)]·B[i][j]). That is a semantic
 * property this class cannot detect — it stores the permutation exactly as
 * given; consumers decide the orientation.
 */
public final class QapSolution {

    private final String name;
    private final int size;
    private final long value;
    private final int[] permutation;

    /**
     * Takes ownership of the permutation array (not copied); it must be a
     * valid 0-based permutation of {0, …, n−1}.
     *
     * @throws IllegalArgumentException if it is not a valid permutation
     */
    public QapSolution(String name, long value, int[] permutation) {
        if (name == null || permutation == null) {
            throw new IllegalArgumentException("name and permutation must be non-null");
        }
        int n = permutation.length;
        if (n == 0) {
            throw new IllegalArgumentException(name + ": permutation is empty");
        }
        boolean[] seen = new boolean[n];
        for (int i = 0; i < n; i++) {
            int p = permutation[i];
            if (p < 0 || p >= n || seen[p]) {
                throw new IllegalArgumentException(name + ": not a valid 0-based permutation at index "
                        + i + " (value " + p + ")");
            }
            seen[p] = true;
        }
        this.name = name;
        this.size = n;
        this.value = value;
        this.permutation = permutation;
    }

    public String getName() {
        return name;
    }

    /** Problem size n. */
    public int getSize() {
        return size;
    }

    /** Objective value as reported by the file header (not re-evaluated). */
    public long getValue() {
        return value;
    }

    /** Internal reference, not a copy — do not mutate. Always 0-based. */
    public int[] getPermutation() {
        return permutation;
    }

    @Override
    public String toString() {
        return name + " (n=" + size + ", value=" + value + ")";
    }
}
