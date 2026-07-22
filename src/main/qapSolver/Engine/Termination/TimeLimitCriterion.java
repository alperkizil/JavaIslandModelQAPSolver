package qapSolver.Engine.Termination;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.TerminationCriterion;

/**
 * Wall-clock termination: stop once {@code elapsedMillis ≥ limitMillis},
 * measured from {@code context.start()} — the engine starts the clock in
 * {@code initialize()}, so generation-0 work is on the clock. Checked
 * between generations: the limit is a stopping floor, overshooting by at
 * most one generation's duration.
 *
 * <p>The one deliberately machine-dependent criterion (same run, faster
 * box, more generations) — replay of a time-limited run is not
 * generation-identical across machines; use {@code MaxGenerationsCriterion}
 * or {@code EvaluationBudgetCriterion} for comparable experiments.
 * Checking against an unstarted context fails loudly
 * ({@code IllegalStateException} from the clock) — engine-driven runs are
 * always started.
 */
public final class TimeLimitCriterion extends TerminationCriterion {

    private final long limitMillis;

    /**
     * @param limitMillis wall-clock limit in milliseconds (≥ 1)
     * @throws IllegalArgumentException if {@code limitMillis < 1}
     */
    public TimeLimitCriterion(long limitMillis) {
        if (limitMillis < 1) {
            throw new IllegalArgumentException("limitMillis must be >= 1: " + limitMillis);
        }
        this.limitMillis = limitMillis;
    }

    @Override
    protected boolean doShouldTerminate(AlgorithmContext context) {
        return context.elapsedMillis() >= limitMillis;
    }
}
