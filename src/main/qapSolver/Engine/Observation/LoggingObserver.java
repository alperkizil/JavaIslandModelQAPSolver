package qapSolver.Engine.Observation;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvolutionObserver;
import qapSolver.Engine.Population;
import qapSolver.Engine.PopulationStatistics;

/**
 * Verbose run logging — the concrete observer that <em>is</em> the GA's
 * verbose mode: register it on the context to switch logging on, don't to
 * switch it off (no engine flag; absence costs nothing). Three events:
 *
 * <ul>
 * <li><b>New best</b> → the highlight stream, marked {@code ***}: generation,
 *     value, full evaluations and elapsed millis from the found-at stamps.
 *     The highlight stream defaults to {@code System.err}, which NetBeans
 *     (and most IDEs) render in red in the output window — that is the "red
 *     font"; ANSI escapes are deliberately avoided (stock NetBeans shows
 *     them as garbage).</li>
 * <li><b>Every generation</b> (0 included) → the main stream: generation
 *     count, best / worst / average fitness and population standard
 *     deviation, via {@link PopulationStatistics} (Welford, σ = population
 *     form ÷N) — this class only formats.</li>
 * <li><b>Run end</b> → the main stream: final best value, permutation
 *     (0-based facility→location) and total wall time as {@code mm:ss}
 *     (total minutes — no wrap at an hour).</li>
 * </ul>
 *
 * <p>Numbers format under {@code Locale.ROOT} (dot decimals on every
 * machine). Read-only, consumes no randomness, engine-thread only — the
 * observer contract.
 */
public final class LoggingObserver extends EvolutionObserver {

    private final PrintStream out;
    private final PrintStream highlight;

    /** Logs to {@code System.out}, new-best lines to {@code System.err} (red in IDEs). */
    public LoggingObserver() {
        this(System.out, System.err);
    }

    /**
     * @param out main stream: per-generation statistics and the run-end line
     * @param highlight new-best stream (IDE-red when {@code System.err})
     * @throws IllegalArgumentException if either stream is null
     */
    public LoggingObserver(PrintStream out, PrintStream highlight) {
        if (out == null || highlight == null) {
            throw new IllegalArgumentException("out and highlight streams must be non-null");
        }
        this.out = out;
        this.highlight = highlight;
    }

    @Override
    public void onNewBest(AlgorithmContext context) {
        highlight.println(String.format(Locale.ROOT, "*** gen %d: new best %d (evals=%d, %d ms) ***",
                context.getBestFoundGeneration(), context.getBestValue(),
                context.getBestFoundEvaluations(), context.getBestFoundMillis()));
    }

    @Override
    public void onGenerationComplete(AlgorithmContext context, Population population) {
        PopulationStatistics stats = PopulationStatistics.of(population);
        out.println(String.format(Locale.ROOT, "gen %d | best %d | worst %d | avg %.2f | sd %.2f",
                context.getGeneration(), stats.getBestFitness(), stats.getWorstFitness(),
                stats.getMeanFitness(), stats.getStandardDeviation()));
    }

    @Override
    public void onRunEnd(AlgorithmContext context) {
        if (!context.hasIncumbent()) {
            out.println("run end: no incumbent");
            return;
        }
        out.println(String.format(Locale.ROOT, "run end: best %d, permutation %s, total %s",
                context.getBestValue(), Arrays.toString(context.getBestPermutation()),
                mmss(context.elapsedMillis())));
    }

    /** Total wall time as mm:ss; minutes are total minutes (no hour wrap). */
    private static String mmss(long millis) {
        return String.format(Locale.ROOT, "%02d:%02d", millis / 60_000, (millis / 1000) % 60);
    }
}
