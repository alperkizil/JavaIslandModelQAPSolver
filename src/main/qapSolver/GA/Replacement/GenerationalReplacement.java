package qapSolver.GA.Replacement;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.GA.ReplacementStrategy;

/**
 * Full generational turnover: the offspring <em>are</em> the next
 * generation; every parent dies. Requires λ = μ and fails loudly on any
 * mismatch — running this strategy with {@code offspringCount ≠ μ} is a
 * configuration error, not something to paper over. λ ≠ μ schemes
 * ((μ,λ)/(μ+λ) truncation) are future sibling strategies, not options here.
 *
 * <p>No survivor pressure by design: which children were bred is parent
 * selection's decision, and survival of the old best is the elitism
 * bracket's — this step just swaps the whole pool (builds a fresh
 * {@link Population}; the old one is left untouched for the engine to
 * discard). Deterministic, consumes no randomness.
 */
public final class GenerationalReplacement extends ReplacementStrategy {

    @Override
    protected Population doReplace(Population current, List<EvaluatedCandidate> offspring,
            AlgorithmContext context) {
        if (offspring.size() != current.size()) {
            throw new IllegalStateException("generational replacement requires λ = μ: offspring "
                    + offspring.size() + " != population " + current.size());
        }
        return new Population(offspring);
    }
}
