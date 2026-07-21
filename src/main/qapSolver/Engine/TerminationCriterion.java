package qapSolver.Engine;

/**
 * Decides when a run is over, from run state alone: generation or evaluation
 * budgets, wall clock, target value reached, stagnation — and combinators
 * (and/or) as future concrete implementations. Checked by the solver between
 * generations.
 *
 * <p>Criteria are read-only and must not consume randomness — drawing from
 * the context's stream in a check would shift every stochastic step after it
 * and break replay. The external {@code stopRequested} flag is honored by the
 * solver loop itself; criteria don't need to re-check it.
 *
 * <p>Implementations override {@link #doShouldTerminate};
 * {@link #shouldTerminate} is the final, timed entry point (see
 * {@link AlgorithmStep}).
 */
public abstract class TerminationCriterion extends AlgorithmStep {

    /** True when the run should stop, given the current run state. */
    public final boolean shouldTerminate(AlgorithmContext context) {
        long start = System.nanoTime();
        try {
            return doShouldTerminate(context);
        } finally {
            recordSince(start);
        }
    }

    /** The check itself; read-only, no randomness. */
    protected abstract boolean doShouldTerminate(AlgorithmContext context);
}
