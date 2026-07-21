package qapSolver.Engine;

import java.util.Arrays;

/**
 * An evaluated point of the search space: a permutation and its objective
 * value, both fixed at construction. This is the only shape selection,
 * replacement, elitism and incumbent tracking ever see — holding an
 * EvaluatedCandidate means the fitness is known and belongs to exactly this
 * permutation. Sharing by reference (elites, future migration) is safe
 * because nothing may mutate one.
 *
 * <p>Ownership: the constructor takes the array (not copied) — the fitness
 * evaluator moves a {@link Candidate}'s array in here, so the evaluation path
 * copies nothing. {@link #getPermutation()} exposes the internal array under
 * the codebase-wide do-not-mutate contract; {@link #toCandidate()} is the one
 * sanctioned way back to mutable land and always copies.
 *
 * <p>The fitness is trusted, not verified — producing exact values is the
 * evaluator's contract, and the run boundary re-verifies for free when the
 * final result becomes a {@code CustomSolution}. (That auto-verifying class
 * is deliberately not used here: it would cost a full O(n²) recomputation per
 * offspring.)
 */
public final class EvaluatedCandidate {

    private final int[] permutation;
    private final long fitness;

    /** Takes ownership of the permutation array (not copied). */
    public EvaluatedCandidate(int[] permutation, long fitness) {
        if (permutation == null) {
            throw new IllegalArgumentException("permutation must be non-null");
        }
        this.permutation = permutation;
        this.fitness = fitness;
    }

    /** Internal reference, not a copy — do not mutate. */
    public int[] getPermutation() {
        return permutation;
    }

    /** Objective value of this permutation (minimization: lower is better). */
    public long getFitness() {
        return fitness;
    }

    /** Problem size n. */
    public int size() {
        return permutation.length;
    }

    /**
     * A fresh mutable {@link Candidate} carrying a copy of this permutation —
     * the explicit escape hatch for re-mutating an evaluated individual; this
     * object is untouched.
     */
    public Candidate toCandidate() {
        return new Candidate(permutation.clone());
    }

    /**
     * True iff the two candidates encode the same permutation. Duplicate
     * detection must use this, never fitness equality — tie-heavy instances
     * (esc, tai-c grids) give distinct permutations identical values.
     */
    public boolean samePermutationAs(EvaluatedCandidate other) {
        return other != null && Arrays.equals(permutation, other.permutation);
    }

    /**
     * Content hash of the permutation, pairing with
     * {@link #samePermutationAs}: equal permutations hash equal. For
     * set-based dedup and future fitness-cache keys. {@code equals}/
     * {@code hashCode} deliberately keep identity semantics — a content-based
     * hashCode over an exposed array would silently corrupt hash structures
     * if the do-not-mutate contract were ever broken.
     */
    public int permutationHash() {
        return Arrays.hashCode(permutation);
    }

    @Override
    public String toString() {
        return "EvaluatedCandidate(n=" + permutation.length + ", fitness=" + fitness + ")";
    }
}
