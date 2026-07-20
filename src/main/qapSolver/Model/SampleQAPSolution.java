package qapSolver.Model;

/**
 * Reference ("sample") solution shipped with QAPLIB as a .sln file: the
 * objective value is the one reported by the file header, never corrected.
 * The reader auto-normalizes the eight inverse-convention files to the
 * standard facility→location orientation, so they verify clean; the only
 * sample with isValid() = false is kra32, whose header value 88900 is a
 * known typo (its permutation evaluates to the true optimum 88700).
 */
public final class SampleQAPSolution extends QAPSolution {

    /** @see QAPSolution#QAPSolution(QAPInstance, long, int[]) */
    public SampleQAPSolution(QAPInstance instance, long reportedValue, int[] permutation) {
        super(instance, reportedValue, permutation);
    }
}
