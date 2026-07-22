package qapSolver.GA.Improvement;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.GA.LocalImprovement;

/**
 * Local improvement switched off — the Null Object of slot (h): returns the
 * input batch as-is (same list, same references, zero work). Composing this
 * makes the engine a plain (non-memetic) GA, which is also the baseline
 * configuration real improvers (2-swap descent, SA) are measured against.
 * Trivially satisfies the contract: input order preserved, fitnesses exact,
 * nothing computed so nothing counted, no randomness.
 */
public final class NoOpImprovement extends LocalImprovement {

    @Override
    protected List<EvaluatedCandidate> doImprove(List<EvaluatedCandidate> candidates,
            AlgorithmContext context) {
        return candidates;
    }
}
