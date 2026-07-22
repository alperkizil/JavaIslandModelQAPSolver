package qapSolver.GA.Crossover;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.GA.CrossoverOperator;
import qapSolver.Random.Randomizer;

/**
 * Partially Mapped Crossover (PMX, Goldberg and Lingle 1985): one contiguous
 * stretch of positions is exchanged between the parents; every position
 * outside it keeps the own parent's value, and the duplicates that creates
 * are repaired by following the value pairing the exchanged stretch defines.
 * Position-preserving by construction — each child position holds its own
 * parent's assignment or one forced by the exchanged segment — which is the
 * facility→location semantics QAP wants (see the QAP note on
 * {@link CrossoverOperator}).
 *
 * <p>Shaped after Watchmaker's {@code ListOrderCrossover}: the two cut
 * points are an <em>ordered</em> pair of uniform draws, so the segment runs
 * from the first to the second wrapping around the array end, and its length
 * is uniform on 0..n−1. Equal draws (probability 1/n) mean an empty segment
 * and the children are exact copies of the parents — kept as in the
 * reference; deliberate clone-through is the engine's crossover-rate path
 * and stays there.
 *
 * <p>Repair, and why it terminates: segment position i pairs parent1's value
 * with parent2's value, giving two value→value tables (value-indexed
 * {@code int[]} here, not boxed maps — the breeding path stays primitive).
 * An outside position whose inherited value reappears inside the segment
 * follows the child's table until the value is free. Each table is injective
 * (segment values are pairwise distinct) and a chain starts at a value
 * outside the table's image (the own parent's outside values are exactly its
 * non-segment values), so chains cannot cycle and are pairwise disjoint:
 * O(n) total per call.
 *
 * <p>Always two children per call, each on a fresh array; parents are read
 * only. Exactly two {@code nextInt(n)} draws from the context stream, in
 * cut-point order — replay-pinned by the test harness. Hot-path trust as
 * codebase-wide: parents are assumed valid same-length permutations.
 */
public final class PartiallyMappedCrossover extends CrossoverOperator {

    @Override
    protected List<Candidate> doRecombine(EvaluatedCandidate parent1, EvaluatedCandidate parent2,
            AlgorithmContext context) {
        int n = parent1.size();
        Randomizer random = context.getRandomizer();
        int[] p1 = parent1.getPermutation();
        int[] p2 = parent2.getPermutation();
        int[] child1 = p1.clone();
        int[] child2 = p2.clone();

        int point1 = random.nextInt(n);
        int point2 = random.nextInt(n);
        int length = point2 - point1;
        if (length < 0) {
            length += n;
        }

        // Value→value tables of the exchanged segment; -1 = not in the segment.
        int[] toParent1 = new int[n]; // parent2's segment value → parent1's; repairs child1
        int[] toParent2 = new int[n]; // parent1's segment value → parent2's; repairs child2
        Arrays.fill(toParent1, -1);
        Arrays.fill(toParent2, -1);
        for (int i = 0; i < length; i++) {
            int index = point1 + i;
            if (index >= n) {
                index -= n;
            }
            int item1 = p1[index];
            int item2 = p2[index];
            child1[index] = item2;
            child2[index] = item1;
            toParent1[item2] = item1;
            toParent2[item1] = item2;
        }

        // The segment's complement starts at point2 and holds n − length
        // positions; inherited values colliding with the segment follow the
        // table until free (terminates: see class doc).
        for (int i = 0; i < n - length; i++) {
            int index = point2 + i;
            if (index >= n) {
                index -= n;
            }
            int v1 = child1[index];
            while (toParent1[v1] != -1) {
                v1 = toParent1[v1];
            }
            child1[index] = v1;
            int v2 = child2[index];
            while (toParent2[v2] != -1) {
                v2 = toParent2[v2];
            }
            child2[index] = v2;
        }

        List<Candidate> children = new ArrayList<>(2);
        children.add(new Candidate(child1));
        children.add(new Candidate(child2));
        return children;
    }
}
