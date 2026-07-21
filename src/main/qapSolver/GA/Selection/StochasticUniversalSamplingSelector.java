package qapSolver.GA.Selection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.GA.ParentSelector;
import qapSolver.Random.Randomizer;

/**
 * Stochastic universal sampling over linear rank weights: one uniform start
 * point, then {@code count} pointers spaced {@code total/count} apart walk
 * the {@link RankWeights} cumulative table in a single pass. Same expected
 * distribution as {@link RouletteWheelSelector} at the same
 * {@code selectionPressure} s ∈ [1, 2], but with minimal sampling variance —
 * every member's actual copy count is its expectation rounded down or up,
 * guaranteed.
 *
 * <p>The pointer walk yields picks ordered worst → best, and the engine
 * breeds parents pairwise in list order — unshuffled, equal neighbors would
 * self-pair constantly (clone crossovers). The picks are therefore
 * Fisher–Yates-shuffled before returning; the multiset (and the SUS
 * guarantee) is untouched.
 *
 * <p>Stream discipline: exactly one double for the start point plus
 * {@code count − 1} bounded ints for the shuffle, nothing else, no state.
 */
public final class StochasticUniversalSamplingSelector extends ParentSelector {

    private final double selectionPressure;

    /**
     * @param selectionPressure linear-ranking s ∈ [1, 2]
     * @throws IllegalArgumentException if s is not in [1, 2]
     */
    public StochasticUniversalSamplingSelector(double selectionPressure) {
        if (!(selectionPressure >= 1.0 && selectionPressure <= 2.0)) { // negated form rejects NaN
            throw new IllegalArgumentException("selectionPressure must be in [1, 2]: " + selectionPressure);
        }
        this.selectionPressure = selectionPressure;
    }

    @Override
    protected List<EvaluatedCandidate> doSelectParents(Population population, int count,
            AlgorithmContext context) {
        if (count == 0) {
            return new ArrayList<>(0);
        }
        RankWeights weights = new RankWeights(population, selectionPressure);
        Randomizer random = context.getRandomizer();
        double step = weights.total() / count;
        double start = random.nextDouble() * step;
        EvaluatedCandidate[] picks = new EvaluatedCandidate[count];
        int rank = 0;
        for (int k = 0; k < count; k++) {
            rank = weights.advance(rank, start + k * step);
            picks[k] = population.get(weights.memberAt(rank));
        }
        for (int i = count - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            EvaluatedCandidate tmp = picks[i];
            picks[i] = picks[j];
            picks[j] = tmp;
        }
        return new ArrayList<>(Arrays.asList(picks));
    }
}
