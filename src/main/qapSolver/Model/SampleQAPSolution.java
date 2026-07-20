package qapSolver.Model;

/**
 * Reference ("sample") solution shipped with QAPLIB as a .sln file: the name
 * of the instance it belongs to plus the objective value exactly as reported
 * by the file header — never re-evaluated.
 *
 * Note: eight QAPLIB .sln files store the inverse permutation convention
 * (their value reproduces under Σ A[p(i)][p(j)]·B[i][j]) and kra32's header
 * value is a known typo. Those are dataset facts this class cannot detect —
 * it stores what the file says; consumers decide the orientation.
 */
public final class SampleQAPSolution extends QAPSolution {

    private final String name;
    private final long reportedValue;

    /**
     * @throws IllegalArgumentException if the name is null or the permutation
     *         is not a valid 0-based permutation
     */
    public SampleQAPSolution(String name, long reportedValue, int[] permutation) {
        super(permutation);
        if (name == null) {
            throw new IllegalArgumentException("name must be non-null");
        }
        this.name = name;
        this.reportedValue = reportedValue;
    }

    /** Name of the instance this solution belongs to. */
    public String getName() {
        return name;
    }

    /** Objective value as reported by the .sln file header. */
    @Override
    public long getValue() {
        return reportedValue;
    }

    @Override
    public String toString() {
        return name + " (n=" + getSize() + ", value=" + reportedValue + ")";
    }
}
