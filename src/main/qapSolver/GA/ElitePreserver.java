package qapSolver.GA;

import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.AlgorithmStep;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;

/**
 * Step (g): elitism as a two-phase bracket around the generation, composing
 * with any {@code ReplacementStrategy}. The engine calls {@link #extract} on
 * the current population before breeding, holds the result, and after
 * replacement calls {@link #reinsert} on the successor population — so a
 * preserved member survives even full generational turnover, and replacement
 * strategies stay elitism-free.
 *
 * <p>{@link #extract} returns references, typically the best k members —
 * immutability makes a reference a snapshot, nothing is copied — and must
 * not modify the population. An empty list means "preserve nothing" (a NoOp
 * implementation returns exactly that). {@link #reinsert} guarantees
 * survival however the implementation sees fit — the classic move is to
 * overwrite the worst member via {@code set} when an elite is missing — but
 * must preserve the population's size (the engine checks) and should leave
 * the population alone when the elites already made it through. The engine
 * hands back exactly what extract returned that generation; the preserver
 * itself stays stateless between phases.
 *
 * <p>Both phases record on this step's single timer: totals are
 * extract + reinsert combined, and the invocation count advances twice per
 * generation.
 *
 * <p>Implementations override {@link #doExtract} and {@link #doReinsert};
 * the public methods are the final, timed entry points (see
 * {@link AlgorithmStep}).
 */
public abstract class ElitePreserver extends AlgorithmStep {

    /** Picks the members to protect this generation; see the class contract. */
    public final List<EvaluatedCandidate> extract(Population population, AlgorithmContext context) {
        long start = System.nanoTime();
        try {
            return doExtract(population, context);
        } finally {
            recordSince(start);
        }
    }

    /** Ensures the extracted elites survived into the successor population. */
    public final void reinsert(Population population, List<EvaluatedCandidate> elites, AlgorithmContext context) {
        long start = System.nanoTime();
        try {
            doReinsert(population, elites, context);
        } finally {
            recordSince(start);
        }
    }

    /** The elite pick itself; read-only on the population. */
    protected abstract List<EvaluatedCandidate> doExtract(Population population, AlgorithmContext context);

    /** The survival guarantee itself; size-preserving edits only. */
    protected abstract void doReinsert(Population population, List<EvaluatedCandidate> elites,
            AlgorithmContext context);
}
