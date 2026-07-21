package qapSolver.GA;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.AlgorithmStep;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;

/**
 * Step (f): survivor selection — decides which of the current members and
 * the evaluated (possibly locally improved) offspring form the next
 * generation.
 *
 * <p>Contract: the result has exactly {@code current.size()} members — the
 * population size is fixed at initialization and this step must preserve it
 * (the engine checks). The implementation may edit {@code current} in place
 * via {@code set} and return it (steady-state style) or build and return a
 * fresh {@link Population} (generational style); never null. Members come
 * from either side by reference — sharing is safe, they're immutable. All
 * randomness from the context's {@code Randomizer}; fitnesses are read, never
 * recomputed.
 *
 * <p>Elitism is deliberately <em>not</em> this step's job: the
 * {@code ElitePreserver} runs right after replacement, whatever strategy is
 * plugged in here — keep implementations elitism-free.
 *
 * <p>Future implementations: full generational turnover, steady-state
 * replace-worst, (μ+λ) truncation — and duplicate-aware variants, which must
 * detect duplicates with {@code samePermutationAs} (permutation content),
 * never fitness equality: the tie-heavy families (esc, tai-c) make equal
 * values routine between distinct permutations.
 *
 * <p>Implementations override {@link #doReplace}; {@link #replace} is the
 * final, timed entry point (see {@link AlgorithmStep}).
 */
public abstract class ReplacementStrategy extends AlgorithmStep {

    /** Forms the next generation's population; see the class contract. */
    public final Population replace(Population current, List<EvaluatedCandidate> offspring, AlgorithmContext context) {
        long start = System.nanoTime();
        try {
            return doReplace(current, offspring, context);
        } finally {
            recordSince(start);
        }
    }

    /** The survivor selection itself; bound by the class contract. */
    protected abstract Population doReplace(Population current, List<EvaluatedCandidate> offspring,
            AlgorithmContext context);
}
