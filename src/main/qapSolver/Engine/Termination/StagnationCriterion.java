package qapSolver.Engine.Termination;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.TerminationCriterion;

/**
 * Stagnation termination: stop once the incumbent has not improved for
 * {@code maxStagnantGenerations} generations. Improvement is the context's
 * strict-improvement semantics — value ties do <em>not</em> reset the
 * clock. Without an incumbent there is nothing to stagnate from and the
 * check is false (engine-driven runs have one from generation 0 onward).
 *
 * <p>Knob pairing (HANDOFF): this criterion races the reheating mutation.
 * Set {@code maxStagnantGenerations} well above the mutation's stagnation
 * threshold plus its cool-down length — ideally several full reheat cycles
 * — or the run gives up before the escape mechanism has had its chances.
 */
public final class StagnationCriterion extends TerminationCriterion {

    private final int maxStagnantGenerations;

    /**
     * @param maxStagnantGenerations generations without strict improvement
     *        before stopping (≥ 1)
     * @throws IllegalArgumentException if {@code maxStagnantGenerations < 1}
     */
    public StagnationCriterion(int maxStagnantGenerations) {
        if (maxStagnantGenerations < 1) {
            throw new IllegalArgumentException("maxStagnantGenerations must be >= 1: "
                    + maxStagnantGenerations);
        }
        this.maxStagnantGenerations = maxStagnantGenerations;
    }

    @Override
    protected boolean doShouldTerminate(AlgorithmContext context) {
        return context.hasIncumbent()
                && context.generationsSinceImprovement() >= maxStagnantGenerations;
    }
}
