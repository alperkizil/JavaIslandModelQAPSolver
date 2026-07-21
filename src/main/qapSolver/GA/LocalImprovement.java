package qapSolver.GA;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.AlgorithmStep;
import qapSolver.Engine.EvaluatedCandidate;

/**
 * Step (h): the memetic slot — hill climbing, simulated annealing, or any
 * other improvement metaheuristic applied to the evaluated offspring, between
 * evaluation and replacement.
 *
 * <p>Bulk on purpose: the whole offspring batch arrives in one call, so the
 * implementation owns its budget policy — improve everything, only the top
 * k, a random fraction, or nothing until stagnation
 * ({@code context.generationsSinceImprovement()}) says otherwise.
 *
 * <p>A transformation, like evaluation: returns one result per input, in
 * input order — either the same reference (left alone) or a <em>new</em>
 * {@link EvaluatedCandidate} whose fitness is exact for its permutation.
 * Improvement is the goal, not a guarantee (an annealer's walk may end where
 * it ends; returning each candidate's best-visited point is the usual
 * choice). Inputs are never modified. The engine offers results to the
 * incumbent afterwards — implementations never touch {@code offerIncumbent}.
 *
 * <p>The intended shape inside: copy the permutation into mutable scratch,
 * walk it with O(n) delta evaluations and a running {@code long} cost, wrap
 * the result once at the end — immutable boundary, untouched hot loop.
 * Count honestly: {@code countDeltaEvaluations} for delta moves (batching
 * is fine), {@code countFullEvaluation} only for true O(n²) recomputations.
 * Exact deltas on the 37 asymmetric instances need the general
 * two-orientation formula — the delta utility is this step's prerequisite.
 * Plateau-heavy families (esc, tai-c: high sparsity, few distinct values)
 * reward sideways-move tolerance.
 *
 * <p>All randomness from the context's {@code Randomizer}. A NoOp
 * implementation returns its input list unchanged.
 *
 * <p>Implementations override {@link #doImprove}; {@link #improve} is the
 * final, timed entry point (see {@link AlgorithmStep}).
 */
public abstract class LocalImprovement extends AlgorithmStep {

    /** Improves the offspring batch; see the class contract. */
    public final List<EvaluatedCandidate> improve(List<EvaluatedCandidate> candidates, AlgorithmContext context) {
        long start = System.nanoTime();
        try {
            return doImprove(candidates, context);
        } finally {
            recordSince(start);
        }
    }

    /** The improvement itself; bound by the class contract. */
    protected abstract List<EvaluatedCandidate> doImprove(List<EvaluatedCandidate> candidates,
            AlgorithmContext context);
}
