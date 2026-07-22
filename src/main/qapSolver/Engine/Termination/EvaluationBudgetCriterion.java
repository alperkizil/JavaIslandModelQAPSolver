package qapSolver.Engine.Termination;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.TerminationCriterion;

/**
 * Evaluation-budget termination: stop once the context counts
 * {@code ≥ budget} full O(n²) fitness computations. This is the
 * machine-independent effort metric — the standard budget for comparing
 * solver configurations fairly (cache hits never count, by the evaluator
 * contract; generation-0 evaluation is on the budget).
 *
 * <p>Checked between generations, so the budget is a stopping floor, not a
 * hard cap: the generation in flight completes, overshooting by at most one
 * generation's evaluations. Counts <em>full</em> evaluations only — the
 * delta-evaluation counter is a separate axis that gets its own criterion
 * when local search / SA lands.
 */
public final class EvaluationBudgetCriterion extends TerminationCriterion {

    private final long budget;

    /**
     * @param budget full evaluations allowed (≥ 1)
     * @throws IllegalArgumentException if {@code budget < 1}
     */
    public EvaluationBudgetCriterion(long budget) {
        if (budget < 1) {
            throw new IllegalArgumentException("budget must be >= 1: " + budget);
        }
        this.budget = budget;
    }

    @Override
    protected boolean doShouldTerminate(AlgorithmContext context) {
        return context.getFullEvaluations() >= budget;
    }
}
