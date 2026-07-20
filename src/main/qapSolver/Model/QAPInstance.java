package qapSolver.Model;

/**
 * Immutable QAP instance: a name, the size n, and the two n-by-n integer
 * matrices A and B as they appear in the .dat file (A first, B second; which
 * one is "flow" vs "distance" varies by family). Matrices may be asymmetric
 * and may carry nonzero diagonals — consumers must never assume otherwise.
 */
public final class QAPInstance {

    private final String name;
    private final int size;
    private final int[][] matrixA;
    private final int[][] matrixB;

    /**
     * Takes ownership of the given arrays (they are not copied).
     *
     * @throws IllegalArgumentException if the matrices are not both n-by-n
     */
    public QAPInstance(String name, int[][] matrixA, int[][] matrixB) {
        if (name == null || matrixA == null || matrixB == null) {
            throw new IllegalArgumentException("name and matrices must be non-null");
        }
        int n = matrixA.length;
        if (n == 0 || matrixB.length != n) {
            throw new IllegalArgumentException(name + ": matrices must be non-empty and equal-sized");
        }
        for (int i = 0; i < n; i++) {
            if (matrixA[i].length != n || matrixB[i].length != n) {
                throw new IllegalArgumentException(name + ": matrices must be square (row " + i + ")");
            }
        }
        this.name = name;
        this.size = n;
        this.matrixA = matrixA;
        this.matrixB = matrixB;
    }

    public String getName() {
        return name;
    }

    /** Problem size n (facilities = locations). */
    public int getSize() {
        return size;
    }

    /** Internal reference, not a copy (hot loops need direct access) — do not mutate. */
    public int[][] getMatrixA() {
        return matrixA;
    }

    /** Internal reference, not a copy (hot loops need direct access) — do not mutate. */
    public int[][] getMatrixB() {
        return matrixB;
    }

    @Override
    public String toString() {
        return name + " (n=" + size + ")";
    }
}
