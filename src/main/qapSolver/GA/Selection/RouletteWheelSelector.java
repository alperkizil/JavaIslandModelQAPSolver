package qapSolver.GA.Selection;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.GA.ParentSelector;
import qapSolver.Random.Randomizer;

/**
 * Roulette-wheel sampling over linear rank weights: the {@link RankWeights}
 * table is built once per bulk call (the per-generation setup the bulk
 * contract exists for), then every parent is an independent spin — one
 * uniform point, one binary search. Rank weights, not raw fitness, are what
 * make a wheel meaningful on QAP: scale-free pressure via
 * {@code selectionPressure} s ∈ [1, 2] (1 = uniform, 2 = strongest; at s = 2
 * the worst member's weight is exactly 0 and it can never be drawn).
 *
 * <p>Independent spins mean sampling variance ~ binomial; if that variance
 * matters, {@link StochasticUniversalSamplingSelector} draws from the same
 * distribution with minimal spread.
 *
 * <p>Stream discipline: exactly {@code count} doubles from the context's
 * stream, nothing else, no state.
 */
public final class RouletteWheelSelector extends ParentSelector {

    private final double selectionPressure;

    /**
     * @param selectionPressure linear-ranking s ∈ [1, 2]
     * @throws IllegalArgumentException if s is not in [1, 2]
     */
    public RouletteWheelSelector(double selectionPressure) {
        if (!(selectionPressure >= 1.0 && selectionPressure <= 2.0)) { // negated form rejects NaN
            throw new IllegalArgumentException("selectionPressure must be in [1, 2]: " + selectionPressure);
        }
        this.selectionPressure = selectionPressure;
    }

    @Override
    protected List<EvaluatedCandidate> doSelectParents(Population population, int count,
            AlgorithmContext context) {
        List<EvaluatedCandidate> parents = new ArrayList<>(count);
        if (count == 0) {
            return parents;
        }
        RankWeights weights = new RankWeights(population, selectionPressure);
        Randomizer random = context.getRandomizer();
        double total = weights.total();
        for (int pick = 0; pick < count; pick++) {
            parents.add(population.get(weights.sample(random.nextDouble() * total)));
        }
        return parents;
    }
}
