package qapSolver.GA;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.AlgorithmStep;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;

/**
 * Step (c): picks who gets to breed. Deliberately bulk — one call per
 * generation for all {@code count} parents — so selectors with per-generation
 * setup (rank tables, stochastic-universal-sampling pointers) pay it once,
 * not per draw.
 *
 * <p>Contract: returns exactly {@code count} members of the population, as
 * references — never copies (breeding copies what it needs; members are
 * immutable). Repeats are allowed and normal: a strong parent may appear many
 * times. The population must not be modified. All randomness from the
 * context's {@code Randomizer}.
 *
 * <p>Minimization-native: lower fitness is better, and selection pressure
 * must point that way. Comparison-based schemes (tournament, rank, uniform)
 * fit directly; fitness-proportional roulette does not exist here unless an
 * implementation supplies its own minimization transform.
 *
 * <p>Implementations override {@link #doSelectParents};
 * {@link #selectParents} is the final, timed entry point (see
 * {@link AlgorithmStep}).
 */
public abstract class ParentSelector extends AlgorithmStep {

    /** Selects {@code count} parents from the population; see the class contract. */
    public final List<EvaluatedCandidate> selectParents(Population population, int count, AlgorithmContext context) {
        long start = System.nanoTime();
        try {
            return doSelectParents(population, count, context);
        } finally {
            recordSince(start);
        }
    }

    /** The selection itself; bound by the class contract. */
    protected abstract List<EvaluatedCandidate> doSelectParents(Population population, int count, AlgorithmContext context);
}
