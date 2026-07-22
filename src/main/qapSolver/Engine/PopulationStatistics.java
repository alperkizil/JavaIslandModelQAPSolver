package qapSolver.Engine;

import java.util.Locale;

/**
 * Immutable fitness statistics of one population snapshot: best, worst,
 * mean and population standard deviation (÷N), computed in a single
 * Welford pass — numerically stable, and overflow-free where a naive
 * sum-of-squares would overflow {@code long} at QAP magnitudes (fitness²
 * alone approaches 2⁶²). The shared utility behind logging, tracing and
 * any future adaptive step that reads population state.
 *
 * <p>Order-independent in exact terms (same multiset ⇒ same best/worst;
 * mean/σ agree to floating-point round-off of the single pass). Loud on
 * misuse: null or empty populations throw — statistics of nothing are a
 * caller bug, not a NaN to propagate.
 */
public final class PopulationStatistics {

    private final int size;
    private final long bestFitness;
    private final long worstFitness;
    private final double meanFitness;
    private final double standardDeviation;

    private PopulationStatistics(int size, long bestFitness, long worstFitness,
            double meanFitness, double standardDeviation) {
        this.size = size;
        this.bestFitness = bestFitness;
        this.worstFitness = worstFitness;
        this.meanFitness = meanFitness;
        this.standardDeviation = standardDeviation;
    }

    /**
     * Computes the statistics of {@code population} in one pass.
     *
     * @throws IllegalArgumentException if {@code population} is null
     * @throws IllegalStateException if {@code population} is empty
     */
    public static PopulationStatistics of(Population population) {
        if (population == null) {
            throw new IllegalArgumentException("population must be non-null");
        }
        if (population.isEmpty()) {
            throw new IllegalStateException("population is empty — no statistics to compute");
        }
        long best = Long.MAX_VALUE;
        long worst = Long.MIN_VALUE;
        double mean = 0.0;
        double m2 = 0.0;
        for (int i = 0; i < population.size(); i++) {
            long fitness = population.get(i).getFitness();
            if (fitness < best) {
                best = fitness;
            }
            if (fitness > worst) {
                worst = fitness;
            }
            double delta = fitness - mean;
            mean += delta / (i + 1);
            m2 += delta * (fitness - mean);
        }
        return new PopulationStatistics(population.size(),
                best, worst, mean, Math.sqrt(m2 / population.size()));
    }

    /** Members in the measured snapshot. */
    public int getSize() {
        return size;
    }

    /** Lowest fitness (minimization: the best). */
    public long getBestFitness() {
        return bestFitness;
    }

    /** Highest fitness (minimization: the worst). */
    public long getWorstFitness() {
        return worstFitness;
    }

    /** Arithmetic mean of all fitnesses. */
    public double getMeanFitness() {
        return meanFitness;
    }

    /** Population standard deviation (÷N, not the sample form). */
    public double getStandardDeviation() {
        return standardDeviation;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT,
                "PopulationStatistics(size=%d, best=%d, worst=%d, mean=%.2f, sd=%.2f)",
                size, bestFitness, worstFitness, meanFitness, standardDeviation);
    }
}
