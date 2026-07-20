package qapSolver.Objective;

import qapSolver.Model.QAPInstance;
import qapSolver.Model.QAPSolution;

/**
 * The QAP objective: cost(p) = Σᵢ Σⱼ A[i][j] · B[p(i)][p(j)] over all n²
 * pairs. No symmetry is assumed and diagonal terms are always included
 * (bur/tai64c/tai256c carry nonzero diagonals). Accumulates in long —
 * random tai100b solutions approach Integer.MAX_VALUE.
 */
public final class ObjectiveFunction {

    private ObjectiveFunction() {
    }

    /**
     * Full O(n²) evaluation of a raw 0-based permutation (p[i] = location of
     * facility i). Only the length is checked; the caller guarantees p is a
     * valid permutation (hot-path method).
     *
     * @throws IllegalArgumentException if p.length differs from the instance size
     */
    public static long evaluate(QAPInstance instance, int[] p) {
        int n = instance.getSize();
        if (p.length != n) {
            throw new IllegalArgumentException(instance.getName() + ": permutation length "
                    + p.length + " != n=" + n);
        }
        int[][] a = instance.getMatrixA();
        int[][] b = instance.getMatrixB();
        long total = 0;
        for (int i = 0; i < n; i++) {
            int[] ai = a[i];
            int[] bpi = b[p[i]];
            for (int j = 0; j < n; j++) {
                total += (long) ai[j] * bpi[p[j]];
            }
        }
        return total;
    }

    /** Full evaluation of a solution's permutation. */
    public static long evaluate(QAPInstance instance, QAPSolution solution) {
        return evaluate(instance, solution.getPermutation());
    }
}
