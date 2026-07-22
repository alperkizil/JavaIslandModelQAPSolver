package qapSolver.Engine.Termination;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.TerminationCriterion;

/**
 * Generation-count termination: stop once {@code generation ≥ maxGenerations}.
 * Generation 0 is the initialized population, and the engine checks between
 * generations, so as the binding criterion this yields exactly
 * {@code maxGenerations} evolved generations after initialization. The
 * budget-free workhorse for comparable benchmark runs.
 */
public final class MaxGenerationsCriterion extends TerminationCriterion {

    private final int maxGenerations;

    /**
     * @param maxGenerations generations to evolve beyond initialization (≥ 1)
     * @throws IllegalArgumentException if {@code maxGenerations < 1}
     */
    public MaxGenerationsCriterion(int maxGenerations) {
        if (maxGenerations < 1) {
            throw new IllegalArgumentException("maxGenerations must be >= 1: " + maxGenerations);
        }
        this.maxGenerations = maxGenerations;
    }

    @Override
    protected boolean doShouldTerminate(AlgorithmContext context) {
        return context.getGeneration() >= maxGenerations;
    }
}
