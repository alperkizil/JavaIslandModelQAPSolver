package qapSolver.GA;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
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
 */
public abstract class PopulationInitializer {

    /** Builds the unevaluated initial batch; see the class contract. */
    public abstract List<Candidate> initialize(AlgorithmContext context);
}
