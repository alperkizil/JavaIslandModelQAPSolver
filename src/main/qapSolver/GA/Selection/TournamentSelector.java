package qapSolver.GA.Selection;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.GA.ParentSelector;
import qapSolver.Random.Randomizer;

/**
 * Probabilistic tournament selection. Per pick: draw {@code tournamentSize}
 * contestants uniformly with replacement, order them best → worst (fitness
 * ascending, ties by draw order), then cascade — accept the best with
 * probability {@code winProbability}, else the next, and so on; if every
 * acceptance fails the last (worst) contestant wins. {@code p = 1} is the
 * classic deterministic tournament; {@code t = 1} is uniform random selection
 * (no acceptance draws, p irrelevant); {@code t > μ} is legal and saturates
 * toward always-best.
 *
 * <p>Comparison-based, so minimization-native and scale-free — pressure
 * depends only on t and p, never on cost magnitudes. Selection pressure is
 * per-island tuning: hot islands run t = 1–2, cold islands larger t.
 *
 * <p>Stream discipline: per pick, exactly t index draws plus one acceptance
 * draw per cascade step taken (none when t = 1). All randomness from the
 * context's stream; no state — picks are a pure function of the stream
 * position and the population.
 */
public final class TournamentSelector extends ParentSelector {

    private final int tournamentSize;
    private final double winProbability;

    /**
     * @param tournamentSize t ≥ 1 contestants per tournament
     * @param winProbability acceptance probability p ∈ (0, 1] of the cascade
     * @throws IllegalArgumentException if {@code t < 1} or p is not in (0, 1]
     */
    public TournamentSelector(int tournamentSize, double winProbability) {
        if (tournamentSize < 1) {
            throw new IllegalArgumentException("tournamentSize must be >= 1: " + tournamentSize);
        }
        if (!(winProbability > 0.0 && winProbability <= 1.0)) { // negated form rejects NaN
            throw new IllegalArgumentException("winProbability must be in (0, 1]: " + winProbability);
        }
        this.tournamentSize = tournamentSize;
        this.winProbability = winProbability;
    }

    @Override
    protected List<EvaluatedCandidate> doSelectParents(Population population, int count,
            AlgorithmContext context) {
        Randomizer random = context.getRandomizer();
        int size = population.size();
        List<EvaluatedCandidate> parents = new ArrayList<>(count);
        int[] contestants = new int[tournamentSize];
        for (int pick = 0; pick < count; pick++) {
            for (int i = 0; i < tournamentSize; i++) {
                contestants[i] = random.nextInt(size);
            }
            sortByFitness(contestants, population);
            int winner = contestants[tournamentSize - 1];
            for (int i = 0; i < tournamentSize - 1; i++) {
                if (random.nextDouble() < winProbability) {
                    winner = contestants[i];
                    break;
                }
            }
            parents.add(population.get(winner));
        }
        return parents;
    }

    /**
     * Insertion sort, best (lowest fitness) first; strict shifts keep it
     * stable, so equal-fitness contestants stay in draw order. t is small —
     * O(t²) beats allocation and boxing.
     */
    private static void sortByFitness(int[] contestants, Population population) {
        for (int i = 1; i < contestants.length; i++) {
            int key = contestants[i];
            long keyFitness = population.get(key).getFitness();
            int j = i - 1;
            while (j >= 0 && population.get(contestants[j]).getFitness() > keyFitness) {
                contestants[j + 1] = contestants[j];
                j--;
            }
            contestants[j + 1] = key;
        }
    }
}
