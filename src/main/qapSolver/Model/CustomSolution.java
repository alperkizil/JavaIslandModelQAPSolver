package qapSolver.Model;

/**
 * A solution we construct ourselves — hand-crafted or produced by our own
 * search — as opposed to one read from a QAPLIB .sln file. All state and
 * behavior (instance name, objective value, permutation, size, auto-verified
 * validity) comes from {@link QAPSolution}.
 */
public final class CustomSolution extends QAPSolution {

    /** @see QAPSolution#QAPSolution(QAPInstance, long, int[]) */
    public CustomSolution(QAPInstance instance, long objectiveValue, int[] permutation) {
        super(instance, objectiveValue, permutation);
    }
}
