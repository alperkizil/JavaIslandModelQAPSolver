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
 */
public abstract class TerminationCriterion {

    /** True when the run should stop, given the current run state. */
    public abstract boolean shouldTerminate(AlgorithmContext context);
}
