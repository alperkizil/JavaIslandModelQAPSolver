package qapSolver.Reader;

import qapSolver.Model.QAPInstance;
import qapSolver.Model.SampleQAPSolution;

/**
 * Built-in reference solutions for the eight QAPLIB instances whose proven
 * optima ship without a {@code .sln} file: esc32a–d, esc32h and esc64a
 * (optimal permutations published only in the deposit's README PDF,
 * Eschermann–Wunderlich section) and tai10a/tai10b (optima computed exactly
 * by exhaustive enumeration in this project — 10! candidates).
 *
 * <p>Permutations are stored 0-based in the standard facility→location
 * convention. The PDF prints its six 1-based; shifted, each reproduces its
 * optimum in the direct orientation (verified computationally against the
 * inverse as well, mirroring {@link SolutionReader}'s value-driven
 * normalization — no inversion was needed). Construction re-verifies every
 * entry via {@link SampleQAPSolution}'s auto-verification, so a corrupted
 * table can never leave this class as a valid solution.
 *
 * <p>Served exclusively through {@link SolutionRepository#find(String)};
 * the file-oriented methods ({@code get}, {@code getAll}, {@code getFamily},
 * {@code listNames}) deliberately stay blind to these entries.
 */
final class KnownOptimalSolutions {

    private static final String[] NAMES = {
            "esc32a", "esc32b", "esc32c", "esc32d", "esc32h", "esc64a", "tai10a", "tai10b"};

    private static final long[] VALUES = {130, 168, 642, 200, 438, 116, 135028, 1183760};

    private static final int[][] PERMUTATIONS = {
            {10, 2, 6, 22, 18, 26, 14, 13, 19, 16, 27, 8, 11, 3, 7, 1, 25, 23, 31, 12, 21, 24,
                    5, 17, 28, 9, 29, 20, 0, 4, 15, 30},
            {14, 30, 6, 7, 22, 23, 15, 31, 13, 9, 29, 25, 4, 5, 12, 8, 1, 0, 20, 21, 28, 24,
                    17, 16, 11, 26, 19, 10, 2, 18, 27, 3},
            {14, 11, 26, 12, 21, 7, 23, 22, 19, 18, 3, 1, 0, 6, 5, 2, 4, 17, 16, 20, 13, 28,
                    15, 31, 25, 10, 30, 29, 27, 9, 24, 8},
            {17, 28, 9, 1, 24, 31, 21, 19, 23, 16, 29, 8, 0, 25, 30, 20, 18, 22, 26, 15, 12, 5,
                    2, 10, 14, 6, 7, 4, 13, 3, 11, 27},
            {0, 18, 28, 21, 11, 3, 29, 24, 8, 6, 26, 10, 20, 5, 4, 12, 13, 30, 9, 27, 7, 2, 22,
                    25, 16, 1, 31, 14, 23, 17, 19, 15},
            {0, 1, 8, 49, 2, 60, 3, 61, 4, 53, 63, 5, 6, 51, 55, 7, 54, 9, 62, 17, 10, 50, 11,
                    12, 13, 14, 19, 42, 15, 40, 16, 46, 22, 18, 23, 20, 52, 21, 27, 24, 25, 26,
                    28, 59, 29, 58, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 41, 43, 44, 45, 47,
                    48, 56, 57},
            {8, 0, 7, 5, 9, 4, 3, 2, 6, 1},
            {4, 5, 0, 3, 6, 7, 8, 2, 1, 9}};

    private KnownOptimalSolutions() {
    }

    /** Whether a built-in optimum exists for this instance name. */
    static boolean contains(String name) {
        return indexOf(name) >= 0;
    }

    /**
     * The built-in solution for the given instance, auto-verified at
     * construction. The instance must be one of the eight
     * ({@link #contains}); anything else throws.
     */
    static SampleQAPSolution build(QAPInstance instance) {
        int i = indexOf(instance.getName());
        if (i < 0) {
            throw new IllegalArgumentException(
                    "no built-in optimum for '" + instance.getName() + "'");
        }
        return new SampleQAPSolution(instance, VALUES[i], PERMUTATIONS[i].clone());
    }

    private static int indexOf(String name) {
        for (int i = 0; i < NAMES.length; i++) {
            if (NAMES[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
