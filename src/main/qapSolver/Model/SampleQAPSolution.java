package qapSolver.Model;

/**
 * Reference ("sample") solution shipped with QAPLIB as a .sln file: the
 * objective value is the one reported by the file header, never corrected.
 * Consequence of construction-time verification: the nine quirk files come
 * out with isValid() = false — kra32 (header typo, true optimum 88700) and
 * the eight inverse-convention files (esc128, kra30a, kra30b, ste36c,
 * tai60a, tai80a, tho30, tho150), whose value only reproduces on the
 * inverted permutation. Dataset facts, not reader bugs.
 */
public final class SampleQAPSolution extends QAPSolution {

    /** @see QAPSolution#QAPSolution(QAPInstance, long, int[]) */
    public SampleQAPSolution(QAPInstance instance, long reportedValue, int[] permutation) {
        super(instance, reportedValue, permutation);
    }
}
