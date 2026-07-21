package qapSolver.GA;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.AlgorithmStep;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;

/**
 * Step (d): recombines two evaluated parents into one or more unevaluated
 * children. The type flow is the design: parents arrive as
 * {@link EvaluatedCandidate} (fitness available to operators that want it),
 * children leave as {@link Candidate} — fresh, owned arrays with no fitness
 * to go stale.
 *
 * <p>Pure operator: a call always recombines. The crossover <em>rate</em>
 * belongs to the engine's breeding loop, which decides per pair whether to
 * invoke this operator or clone the parents through — so operators stay
 * reusable and testable in isolation.
 *
 * <p>Contract: at least one child per call (the engine's offspring
 * accounting relies on it), each child a fresh array — never a parent's
 * array, never shared between children; parents are not modified. All
 * randomness from the context's {@code Randomizer}. Invocation count on the
 * timer equals pairs recombined.
 *
 * <p>For QAP, note when implementing: position-preserving recombination
 * (cycle/position-based families) matches the facility→location assignment
 * semantics; order-based operators like OX preserve relative order — the
 * traveling-salesman invariant, not this one.
 *
 * <p>Implementations override {@link #doRecombine}; {@link #recombine} is
 * the final, timed entry point (see {@link AlgorithmStep}).
 */
public abstract class CrossoverOperator extends AlgorithmStep {

    /** Recombines two parents into 1+ children; see the class contract. */
    public final List<Candidate> recombine(EvaluatedCandidate parent1, EvaluatedCandidate parent2,
            AlgorithmContext context) {
        long start = System.nanoTime();
        try {
            return doRecombine(parent1, parent2, context);
        } finally {
            recordSince(start);
        }
    }

    /** The recombination itself; bound by the class contract. */
    protected abstract List<Candidate> doRecombine(EvaluatedCandidate parent1, EvaluatedCandidate parent2,
            AlgorithmContext context);
}
