package qapSolver.Engine.Evaluation;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.FitnessEvaluator;
import qapSolver.Objective.ObjectiveFunction;

/**
 * The baseline evaluator: sequential, exact, zero-copy. Each candidate gets
 * one full O(n²) {@link ObjectiveFunction} evaluation on the engine thread,
 * one {@code countFullEvaluation()} tick, and its array moved — not copied —
 * into the resulting {@link EvaluatedCandidate} (the ownership-transfer
 * contract of {@link FitnessEvaluator}). Results in input order; consumes no
 * randomness. The reference implementation every decorated or parallel stack
 * must reproduce value-for-value.
 */
public final class ExactEvaluator extends FitnessEvaluator {

    @Override
    protected List<EvaluatedCandidate> doEvaluate(List<Candidate> candidates, AlgorithmContext context) {
        List<EvaluatedCandidate> results = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            int[] permutation = candidates.get(i).getPermutation();
            long fitness = ObjectiveFunction.evaluate(context.getInstance(), permutation);
            context.countFullEvaluation();
            results.add(new EvaluatedCandidate(permutation, fitness));
        }
        return results;
    }
}
