package qapSolver.GA.Elitism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.GA.ElitePreserver;

/**
 * Best-k elitism: {@code extract} returns references to the k lowest-fitness
 * members, best first, ties by first index; {@code reinsert} guarantees each
 * extracted genotype survived into the successor population. k is the sole
 * constructor parameter — {@code k = 0} is elitism off (empty extract, no-op
 * reinsert), so no separate NoOp class exists; {@code k >= } population size
 * is a misconfiguration and fails loud at extract time.
 *
 * <p>Reinsertion presence is judged by permutation content
 * ({@link EvaluatedCandidate#samePermutationAs}), never by fitness — on
 * tie-heavy families distinct permutations share values, and a genotype that
 * survived as a different object needs no slot. A missing elite overwrites
 * the worst member among <em>unprotected</em> slots: slots where an elite was
 * found or just reinserted are protected for the rest of the call, because on
 * an all-tied population the plain worst-index tie-break would hand elite #2
 * the very slot elite #1 was just written to. Elites that duplicate each
 * other's permutation collapse to one survivor — guaranteeing slots per copy
 * would only burn diversity.
 *
 * <p>Both phases are deterministic and consume no randomness.
 */
public final class BestKElitePreserver extends ElitePreserver {

    private final int eliteCount;

    /**
     * @param eliteCount k, the number of members to preserve; 0 = elitism off
     * @throws IllegalArgumentException if {@code eliteCount < 0}
     */
    public BestKElitePreserver(int eliteCount) {
        if (eliteCount < 0) {
            throw new IllegalArgumentException("eliteCount must be >= 0: " + eliteCount);
        }
        this.eliteCount = eliteCount;
    }

    /**
     * References to the k best members, best first; read-only on the
     * population.
     *
     * @throws IllegalStateException if {@code k >= population.size()} — a
     *         preserve-everything configuration leaves replacement no room
     */
    @Override
    protected List<EvaluatedCandidate> doExtract(Population population, AlgorithmContext context) {
        if (eliteCount == 0) {
            return Collections.emptyList();
        }
        if (eliteCount >= population.size()) {
            throw new IllegalStateException("eliteCount " + eliteCount
                    + " must be < population size " + population.size());
        }
        boolean[] picked = new boolean[population.size()];
        List<EvaluatedCandidate> elites = new ArrayList<>(eliteCount);
        for (int e = 0; e < eliteCount; e++) {
            int best = -1;
            long bestFitness = 0;
            for (int i = 0; i < population.size(); i++) {
                long fitness = population.get(i).getFitness();
                if (!picked[i] && (best < 0 || fitness < bestFitness)) {
                    best = i;
                    bestFitness = fitness;
                }
            }
            picked[best] = true;
            elites.add(population.get(best));
        }
        return elites;
    }

    /** The survival guarantee; see the class contract. */
    @Override
    protected void doReinsert(Population population, List<EvaluatedCandidate> elites,
            AlgorithmContext context) {
        if (elites.isEmpty()) {
            return;
        }
        boolean[] reserved = new boolean[population.size()];
        for (int e = 0; e < elites.size(); e++) {
            EvaluatedCandidate elite = elites.get(e);
            int found = -1;
            for (int i = 0; i < population.size(); i++) {
                EvaluatedCandidate member = population.get(i);
                if (member == elite || member.samePermutationAs(elite)) {
                    found = i;
                    break;
                }
            }
            if (found >= 0) {
                reserved[found] = true;
                continue;
            }
            int worst = -1;
            long worstFitness = 0;
            for (int i = 0; i < population.size(); i++) {
                long fitness = population.get(i).getFitness();
                if (!reserved[i] && (worst < 0 || fitness > worstFitness)) {
                    worst = i;
                    worstFitness = fitness;
                }
            }
            if (worst < 0) {
                throw new IllegalStateException(
                        "no unreserved slot left to reinsert elite " + e + " of " + elites.size());
            }
            population.set(worst, elite);
            reserved[worst] = true;
        }
    }
}
