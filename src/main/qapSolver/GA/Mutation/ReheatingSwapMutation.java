package qapSolver.GA.Mutation;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.GA.MutationOperator;
import qapSolver.Random.Randomizer;

/**
 * Multi-swap mutation with an SA-style reheating schedule — the island's
 * designated escape mechanism from local optima. The operator carries a
 * "temperature" measured in swaps per child: normally it sits at a small
 * baseline; when the island has stagnated long enough <em>and</em> the
 * temperature has fully cooled, it reheats — jumps to a hot strength — and
 * then cools geometrically back down, exactly like SA reheating. Persistent
 * stagnation therefore produces periodic kick cycles; a found improvement
 * resets the stagnation clock and stops further reheats until stagnation
 * builds up again.
 *
 * <p>Both strength tiers scale with the instance: baseline
 * {@code max(1, round(baselineFraction·n))} and hot
 * {@code max(2, round(hotFraction·n))} swaps. Scaling is not a convenience
 * but a dataset fact: the measured autocorrelation length is ~0.25·n across
 * all families (see CLAUDE.md), so basin size grows linearly with n and an
 * escape kick must be a fraction of n — 0.25 is the data-backed default for
 * {@code hotFraction}. A size-20 and a size-100 instance get 5 vs 25 kick
 * swaps, never the same absolute treatment.
 *
 * <p>Deliberate SA fidelity: cooling is purely geometric
 * ({@code T ← max(baseline, T·coolingFactor)} per generation) and an
 * improvement during a hot phase does <em>not</em> quench the temperature —
 * the phase cools on its own, and elitism plus the context incumbent make
 * hot generations safe. Reheat requires both full cool-down and
 * {@code generationsSinceImprovement ≥ stagnationThreshold}, so the hot
 * phase cannot re-trigger itself into a permanently hot island.
 *
 * <p>Temperature is per-generation island state: all children of one
 * generation receive the same swap count, updated lazily on the first
 * {@link #mutate} call of each generation (the step is per-engine and
 * thread-confined, so this statefulness is contract-clean). Per child it
 * performs k independent uniform transpositions of two <em>distinct</em>
 * positions — exactly 2k draws from the context stream ({@code nextInt(n)}
 * then {@code nextInt(n−1)} per swap), the same random-walk model the
 * autocorrelation lengths were measured with. n = 1 has no transposition:
 * identity, zero draws. All constants are constructor-injected starting
 * points to be benchmarked, not universal optima.
 */
public final class ReheatingSwapMutation extends MutationOperator {

    private final double baselineFraction;
    private final double hotFraction;
    private final double coolingFactor;
    private final int stagnationThreshold;

    private int lastSeenGeneration = Integer.MIN_VALUE;
    private double temperature = -1.0; // uninitialized until the first call
    private int currentSwaps;

    /**
     * @param baselineFraction baseline strength as a fraction of n, in (0, 1];
     *        applied as {@code max(1, round(baselineFraction·n))} swaps
     * @param hotFraction reheat strength as a fraction of n, in (0, 1] and
     *        ≥ {@code baselineFraction}; {@code max(2, round(hotFraction·n))}
     *        swaps (0.25 matches the measured autocorrelation length)
     * @param coolingFactor geometric cooling per generation, in (0, 1)
     * @param stagnationThreshold generations without improvement (and fully
     *        cooled) before a reheat fires, ≥ 1
     * @throws IllegalArgumentException on any violated bound (NaN included)
     */
    public ReheatingSwapMutation(double baselineFraction, double hotFraction,
            double coolingFactor, int stagnationThreshold) {
        if (!(baselineFraction > 0.0 && baselineFraction <= 1.0)) {
            throw new IllegalArgumentException("baselineFraction must be in (0,1]: " + baselineFraction);
        }
        if (!(hotFraction > 0.0 && hotFraction <= 1.0)) {
            throw new IllegalArgumentException("hotFraction must be in (0,1]: " + hotFraction);
        }
        if (hotFraction < baselineFraction) {
            throw new IllegalArgumentException("hotFraction " + hotFraction
                    + " must be >= baselineFraction " + baselineFraction);
        }
        if (!(coolingFactor > 0.0 && coolingFactor < 1.0)) {
            throw new IllegalArgumentException("coolingFactor must be in (0,1): " + coolingFactor);
        }
        if (stagnationThreshold < 1) {
            throw new IllegalArgumentException("stagnationThreshold must be >= 1: " + stagnationThreshold);
        }
        this.baselineFraction = baselineFraction;
        this.hotFraction = hotFraction;
        this.coolingFactor = coolingFactor;
        this.stagnationThreshold = stagnationThreshold;
    }

    /**
     * Swaps applied to each child of the current generation. Meaningful after
     * the first {@link #mutate} call of the run; 0 before it. Read-only
     * observability for tests, logging and future observers.
     */
    public int getCurrentSwaps() {
        return currentSwaps;
    }

    @Override
    protected void doMutate(Candidate candidate, AlgorithmContext context) {
        int n = candidate.size();
        if (context.getGeneration() != lastSeenGeneration) {
            lastSeenGeneration = context.getGeneration();
            updateTemperature(n, context);
        }
        if (n < 2) {
            return; // no transposition exists; identity, zero draws
        }
        int[] permutation = candidate.getPermutation();
        Randomizer random = context.getRandomizer();
        for (int s = 0; s < currentSwaps; s++) {
            int i = random.nextInt(n);
            int j = random.nextInt(n - 1);
            if (j >= i) {
                j++;
            }
            int tmp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = tmp;
        }
    }

    /** One temperature step per generation: reheat if frozen and stagnant, else cool. */
    private void updateTemperature(int n, AlgorithmContext context) {
        int baseline = Math.max(1, (int) Math.round(baselineFraction * n));
        int hot = Math.max(2, (int) Math.round(hotFraction * n));
        if (temperature < 0.0) {
            temperature = baseline;
        }
        int stagnation = context.hasIncumbent() ? context.generationsSinceImprovement() : 0;
        boolean cooled = temperature <= baseline;
        if (cooled && stagnation >= stagnationThreshold) {
            temperature = hot;
        } else {
            temperature = Math.max(baseline, temperature * coolingFactor);
        }
        currentSwaps = (int) Math.round(temperature);
    }
}
