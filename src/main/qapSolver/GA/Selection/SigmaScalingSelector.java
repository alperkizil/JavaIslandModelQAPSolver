package qapSolver.GA.Selection;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.GA.ParentSelector;
import qapSolver.Random.Randomizer;

/**
 * Sigma-scaled fitness-proportional selection (standalone, roulette-sampled).
 * Each generation the population's fitness mean and standard deviation
 * rescale every member's weight — minimization form
 * {@code w = 1 + (mean − f) / 2σ}, floored at {@value #MIN_WEIGHT} when
 * non-positive, all weights 1 (uniform) when σ = 0. Normalizing against
 * population statistics is what makes a proportional scheme viable on QAP:
 * early on it caps the takeover of a lucky candidate (a raw wheel's failure
 * mode), and late — or on compressed-spread landscapes like tai-a/lipa —
 * it re-amplifies the few-percent differences a raw wheel would flatten
 * into uniformity.
 *
 * <p>No parameters: the pressure adapts to the population each generation
 * instead of being configured. Statistics and weights are recomputed per
 * bulk call; parents are independent spins over the member-indexed
 * cumulative table (binary search per spin).
 *
 * <p>Stream discipline: exactly {@code count} doubles from the context's
 * stream, nothing else, no state.
 */
public final class SigmaScalingSelector extends ParentSelector {

    /** Floor for members more than 2σ worse than the mean: rare, not immortal. */
    public static final double MIN_WEIGHT = 0.1;

    @Override
    protected List<EvaluatedCandidate> doSelectParents(Population population, int count,
            AlgorithmContext context) {
        List<EvaluatedCandidate> parents = new ArrayList<>(count);
        if (count == 0) {
            return parents;
        }
        int size = population.size();
        if (size == 0) {
            throw new IllegalArgumentException("population must be non-empty");
        }
        double mean = 0.0;
        for (int i = 0; i < size; i++) {
            mean += population.get(i).getFitness();
        }
        mean /= size;
        double variance = 0.0;
        for (int i = 0; i < size; i++) {
            double deviation = population.get(i).getFitness() - mean;
            variance += deviation * deviation;
        }
        double sigma = Math.sqrt(variance / size);

        double[] cumulative = new double[size];
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            double weight;
            if (sigma == 0.0) {
                weight = 1.0;
            } else {
                weight = 1.0 + (mean - population.get(i).getFitness()) / (2.0 * sigma);
                if (weight <= 0.0) {
                    weight = MIN_WEIGHT;
                }
            }
            sum += weight;
            cumulative[i] = sum;
        }

        Randomizer random = context.getRandomizer();
        for (int pick = 0; pick < count; pick++) {
            double point = random.nextDouble() * sum;
            int lo = 0;
            int hi = size - 1;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (cumulative[mid] > point) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
            parents.add(population.get(lo));
        }
        return parents;
    }
}
