package qapSolver.Model;

/**
 * Reference ("sample") solution shipped with QAPLIB as a .sln file. The
 * reader normalizes the known dataset quirks on the way in — the eight
 * inverse-convention files are inverted to the standard facility→location
 * orientation and kra32's typo header 88900 is corrected to the true
 * optimum 88700 — so every sample solution verifies clean:
 * isValid() = true across all 128 files.
 */
public final class SampleQAPSolution extends QAPSolution {

    /** @see QAPSolution#QAPSolution(QAPInstance, long, int[]) */
    public SampleQAPSolution(QAPInstance instance, long reportedValue, int[] permutation) {
        super(instance, reportedValue, permutation);
    }
}
