package qapSolver.Model;

import qapSolver.Objective.SolutionVerifier;

/**
 * Abstract base for any QAP solution. Holds the full common state:
 * the name of the instance it belongs to, the objective value, the
 * permutation in 0-based indexing (p[i] = location assigned to facility i),
 * the size n, and a validity flag — set automatically at construction by
 * running {@link SolutionVerifier} against the instance: valid means the
 * claimed objective value is reproduced by the objective function on this
 * permutation. Subclasses only express where the solution came from
 * (file-reported sample, custom/solver-produced, …).
 */
public abstract class QAPSolution {

    private final String instanceName;
    private final long objectiveValue;
    private final int[] permutation;
    private final boolean valid;

    /**
     * Takes ownership of the permutation array (not copied); it must be a
     * valid 0-based permutation of {0, …, n−1} with n matching the instance
     * size. The instance is used for name and verification only — it is not
     * retained.
     *
     * @throws IllegalArgumentException if the permutation is invalid or its
     *         length differs from the instance size
     */
    protected QAPSolution(QAPInstance instance, long objectiveValue, int[] permutation) {
        if (instance == null || permutation == null) {
            throw new IllegalArgumentException("instance and permutation must be non-null");
        }
        int n = permutation.length;
        if (n != instance.getSize()) {
            throw new IllegalArgumentException(instance.getName() + ": permutation length " + n
                    + " != instance size " + instance.getSize());
        }
        boolean[] seen = new boolean[n];
        for (int i = 0; i < n; i++) {
            int p = permutation[i];
            if (p < 0 || p >= n || seen[p]) {
                throw new IllegalArgumentException(instance.getName()
                        + ": not a valid 0-based permutation at index " + i + " (value " + p + ")");
            }
            seen[p] = true;
        }
        this.instanceName = instance.getName();
        this.objectiveValue = objectiveValue;
        this.permutation = permutation;
        // Safe here: verification reads only getValue()/getPermutation(), both
        // final and assigned above.
        this.valid = SolutionVerifier.verify(instance, this);
    }

    /** Name of the instance this solution belongs to. */
    public final String getInstanceName() {
        return instanceName;
    }

    /** The claimed objective value (validity says whether it is reproduced). */
    public final long getValue() {
        return objectiveValue;
    }

    /** Problem size n (the number in the instance name: tai40 → 40). */
    public final int getSize() {
        return permutation.length;
    }

    /** Internal reference, not a copy — do not mutate. Always 0-based. */
    public final int[] getPermutation() {
        return permutation;
    }

    /** True iff the claimed value was reproduced by the objective function at construction. */
    public final boolean isValid() {
        return valid;
    }

    @Override
    public String toString() {
        return instanceName + " (n=" + getSize() + ", value=" + objectiveValue + ", valid=" + valid + ")";
    }
}
