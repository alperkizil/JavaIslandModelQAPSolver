package qapSolver.GA;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.AlgorithmStep;
import qapSolver.Engine.Candidate;

/**
 * Step (e): in-place variation of an unevaluated candidate. Accepting only
 * {@link Candidate} is the type-state guarantee at work — an evaluated
 * individual cannot be mutated without an explicit
 * {@code toCandidate()} copy, so a stale fitness is unrepresentable.
 *
 * <p>Unlike crossover, the rate lives <em>inside</em> the operator: the
 * engine calls {@link #mutate} on every offspring unconditionally, and the
 * implementation decides what happens — per-individual probability, per-gene
 * rates, k-swap strength are all its own parameters. ("No crossover" is a
 * different data path in the engine; "no mutation" is just identity, which
 * needs no engine branch.) Doing nothing is a legitimate outcome; the
 * invocation is still timed and counted.
 *
 * <p>Contract: mutate the candidate's array in place, preserving permutation
 * validity — rearrangements (swaps, insertions, scrambles) do this by
 * construction. All randomness from the context's {@code Randomizer}.
 * Strength guidance from the dataset analysis: the measured autocorrelation
 * length is ~0.25·n across all families, so a kick of about n/4 swaps is
 * what it takes to leave a basin — the natural "hot" setting, while 1–2
 * swaps is the classic "cold" one.
 *
 * <p>Implementations override {@link #doMutate}; {@link #mutate} is the
 * final, timed entry point (see {@link AlgorithmStep}).
 */
public abstract class MutationOperator extends AlgorithmStep {

    /** Mutates the candidate in place; see the class contract. */
    public final void mutate(Candidate candidate, AlgorithmContext context) {
        long start = System.nanoTime();
        try {
            doMutate(candidate, context);
        } finally {
            recordSince(start);
        }
    }

    /** The variation itself; bound by the class contract. */
    protected abstract void doMutate(Candidate candidate, AlgorithmContext context);
}
