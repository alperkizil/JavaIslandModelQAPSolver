package qapSolver.Model;

/** Permutation utilities shared across modules. */
public final class Permutations {

    private Permutations() {
    }

    /**
     * Returns the inverse q of a valid 0-based permutation p: q[p[i]] = i.
     * Converts between the two QAPLIB conventions (facility→location vs
     * location→facility). The input is not validated and not modified.
     */
    public static int[] inverseOf(int[] p) {
        int[] q = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            q[p[i]] = i;
        }
        return q;
    }
}
