package qapSolver.Objective;

import qapSolver.Model.QAPInstance;
import qapSolver.Model.QAPSolution;

/**
 * Verifies a solution's claimed value by recomputing it with
 * {@link ObjectiveFunction} on the given instance. Purely mechanical: the
 * caller is responsible for pairing the right instance with the right
 * solution; dataset quirks (kra32 header typo, the eight inverse-convention
 * .sln files) are consumer knowledge, not verifier knowledge.
 */
public final class SolutionVerifier {

    private SolutionVerifier() {
    }

    /**
     * True iff the claimed value equals the recomputed objective value.
     *
     * @throws IllegalArgumentException if permutation length and instance size differ
     */
    public static boolean verify(QAPInstance instance, QAPSolution solution) {
        return ObjectiveFunction.evaluate(instance, solution) == solution.getValue();
    }
}
