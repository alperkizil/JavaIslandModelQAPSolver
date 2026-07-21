package qapSolver.Engine;

import java.util.List;

/**
 * The evaluation step: turns unevaluated {@link Candidate}s into
 * {@link EvaluatedCandidate}s. Deliberately a bulk transformation — this one
 * signature is the seam for both planned futures: a caching decorator wraps
 * another evaluator and intercepts per candidate; a master–slave
 * implementation farms the batch out to workers.
 *
 * <p>Contract:
 * <ul>
 * <li><b>Ownership</b> — consumes the candidates: each array moves into the
 *     resulting EvaluatedCandidate (no copy); a Candidate must not be used
 *     after being evaluated.</li>
 * <li><b>Order</b> — returns exactly one result per input, in input order,
 *     regardless of internal computation order. That is what keeps parallel
 *     evaluation replay-identical to sequential.</li>
 * <li><b>Exactness</b> — each fitness is the exact objective value of its
 *     permutation; the run boundary re-verifies the final result.</li>
 * <li><b>Counting</b> — call {@code context.countFullEvaluation()} once per
 *     actual O(n²) computation; cache hits must not count.</li>
 * </ul>
 */
public abstract class FitnessEvaluator {

    /** Evaluates the batch; see the class contract. */
    public abstract List<EvaluatedCandidate> evaluate(List<Candidate> candidates, AlgorithmContext context);
}
