package qapSolver.GA;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.AlgorithmStep;
import qapSolver.Engine.Candidate;

/**
 * Step (a): produces the unevaluated generation-0 batch. The engine runs it
 * exactly once, then evaluates the batch into the first {@code Population} —
 * initializers return {@link Candidate}s, never fitnesses.
 *
 * <p>The returned list's size <em>is</em> the population size for the whole
 * run: the size parameter lives in the concrete initializer (configuration
 * belongs to steps), and the engine derives everything from what it gets.
 *
 * <p>Contract: non-empty list; every candidate a fresh, owned, valid 0-based
 * permutation of n = {@code context.getInstance().getSize()}; all randomness
 * drawn from the context's {@code Randomizer}. Future implementations:
 * uniform random shuffles, heuristic seeding, duplicate-free variants.
 *
 * <p>Implementations override {@link #doInitialize}; {@link #initialize} is
 * the final, timed entry point (see {@link AlgorithmStep}).
 */
public abstract class PopulationInitializer extends AlgorithmStep {

    /** Builds the unevaluated initial batch; see the class contract. */
    public final List<Candidate> initialize(AlgorithmContext context) {
        long start = System.nanoTime();
        try {
            return doInitialize(context);
        } finally {
            recordSince(start);
        }
    }

    /** The initialization itself; bound by the class contract. */
    protected abstract List<Candidate> doInitialize(AlgorithmContext context);
}
