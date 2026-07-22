package qapSolver.GA.Replacement;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.GA.ReplacementStrategy;

/**
 * Steady-state replace-worst (GENITOR style): each child, in input order,
 * replaces the population's <em>current</em> worst member, in place. The
 * batch is treated as what it conceptually is — a sequence of λ birth
 * events — so later children may evict earlier ones that immediately became
 * the worst; feeding λ = 1–2 with large μ through the engine reproduces the
 * classic steady-state GA exactly.
 *
 * <p>Replacement is <em>unconditional</em> — a child worse than everyone
 * still enters (and is first in line to die at the next birth). Deliberate:
 * acceptance-if-better would double-dip on selection pressure (pressure is
 * parent selection's knob) and re-implement protection (the elitism
 * bracket's job, which runs right after this step and restores any evicted
 * elite). Unconditional turnover keeps the inflow at λ genotypes per
 * generation — on the tie-heavy plateau families that is what keeps the
 * population moving sideways instead of freezing.
 *
 * <p>Worst ties break to the first index ({@code Population.worstIndex()}),
 * so the whole strategy is deterministic and consumes no randomness. O(λ·μ)
 * scans, no allocation; returns the same (edited) population object.
 */
public final class SteadyStateReplacement extends ReplacementStrategy {

    @Override
    protected Population doReplace(Population current, List<EvaluatedCandidate> offspring,
            AlgorithmContext context) {
        for (int i = 0; i < offspring.size(); i++) {
            current.set(current.worstIndex(), offspring.get(i));
        }
        return current;
    }
}
