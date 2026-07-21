package qapSolver.GA.Initialization;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.GA.PopulationInitializer;
import qapSolver.Random.Randomizer;

/**
 * Uniform random initialization: each candidate is an independent uniform
 * draw from all n! permutations — a fresh identity array put through the
 * context stream's Fisher–Yates {@link Randomizer#shuffle(int[])}. The
 * assumption-free baseline: maximum diversity, no structural bias, the
 * reference every seeded strategy has to beat.
 *
 * <p>The operator holds no randomness of its own — no seed, no stream. Every
 * draw comes from {@code context.getRandomizer()} at call time, so the batch
 * is a pure function of (master seed, stream id) and a run replays exactly.
 *
 * <p>The population size μ is the constructor parameter (configuration
 * belongs to steps); the engine derives μ from the returned batch. Duplicate
 * permutations are possible and permitted — vanishingly rare at real sizes;
 * duplicate-free initialization is a separate future strategy.
 */
public final class RandomInitializer extends PopulationInitializer {

    private final int populationSize;

    /**
     * @param populationSize μ, the number of candidates per batch
     * @throws IllegalArgumentException if {@code populationSize < 1}
     */
    public RandomInitializer(int populationSize) {
        if (populationSize < 1) {
            throw new IllegalArgumentException("populationSize must be >= 1: " + populationSize);
        }
        this.populationSize = populationSize;
    }

    /** μ fresh uniform permutations, drawn in order from the context's stream. */
    @Override
    protected List<Candidate> doInitialize(AlgorithmContext context) {
        int n = context.getInstance().getSize();
        Randomizer random = context.getRandomizer();
        List<Candidate> batch = new ArrayList<>(populationSize);
        for (int k = 0; k < populationSize; k++) {
            int[] permutation = new int[n];
            for (int i = 0; i < n; i++) {
                permutation[i] = i;
            }
            random.shuffle(permutation);
            batch.add(new Candidate(permutation));
        }
        return batch;
    }
}
