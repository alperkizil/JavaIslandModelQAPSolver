package qapSolver.Engine.Observation;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link LoggingObserver}: constructor
 * validation; the per-generation statistics line (exact string against
 * hand-computed values, generation 0 included, one line per generation, on
 * the main stream only); the new-best line (highlight stream only, found-at
 * stamps, fired through the context's own dispatch on strict improvement,
 * silent on ties); the run-end line (exact string with best, permutation
 * and mm:ss total, plus the no-incumbent fallback); stream separation
 * throughout; and no randomness consumed by any event.
 *
 * Usage: LoggingObserverTest (no arguments; synthetic instances, no dataset
 * dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class LoggingObserverTest {

    private LoggingObserverTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        generationLine(failures);
        newBestLine(failures);
        runEndLine(failures);
        noRandomnessConsumed(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        for (int nullPosition = 0; nullPosition < 2; nullPosition++) {
            boolean threw = false;
            try {
                PrintStream stream = new PrintStream(new ByteArrayOutputStream(), true);
                new LoggingObserver(nullPosition == 0 ? null : stream,
                        nullPosition == 0 ? stream : null);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor: null stream (position " + nullPosition
                    + ") must throw");
        }
    }

    private static void generationLine(List<String> failures) {
        Streams streams = new Streams();
        AlgorithmContext ctx = context(1L);
        ctx.addObserver(streams.observer);

        ctx.notifyGenerationComplete(population(10, 20, 30, 40));
        ctx.advanceGeneration();
        ctx.advanceGeneration();
        ctx.notifyGenerationComplete(population(10, 10, 10));

        List<String> outLines = streams.outLines();
        check(failures, outLines.size() == 2,
                "generation: expected 2 lines, got " + outLines.size());
        check(failures, outLines.get(0).equals("gen 0 | best 10 | worst 40 | avg 25.00 | sd 11.18"),
                "generation: gen-0 line was '" + outLines.get(0) + "'");
        check(failures, outLines.get(1).equals("gen 2 | best 10 | worst 10 | avg 10.00 | sd 0.00"),
                "generation: gen-2 line was '" + outLines.get(1) + "'");
        check(failures, streams.highlightLines().isEmpty(),
                "generation: statistics leaked onto the highlight stream");
    }

    private static void newBestLine(List<String> failures) {
        Streams streams = new Streams();
        AlgorithmContext ctx = context(2L);
        ctx.addObserver(streams.observer);
        ctx.start();
        for (int i = 0; i < 120; i++) {
            ctx.countFullEvaluation();
        }

        ctx.offerIncumbent(new EvaluatedCandidate(identity(4), 1750L)); // improvement -> fires
        ctx.offerIncumbent(new EvaluatedCandidate(identity(4), 1750L)); // tie -> silent
        ctx.offerIncumbent(new EvaluatedCandidate(identity(4), 1700L)); // improvement -> fires

        List<String> highlight = streams.highlightLines();
        check(failures, highlight.size() == 2,
                "new best: expected 2 highlight lines (tie silent), got " + highlight.size());
        check(failures, highlight.get(0).startsWith("*** gen 0: new best 1750 (evals=120, ")
                        && highlight.get(0).endsWith(" ms) ***"),
                "new best: first line was '" + highlight.get(0) + "'");
        check(failures, highlight.get(1).startsWith("*** gen 0: new best 1700 (evals=120, "),
                "new best: second line was '" + highlight.get(1) + "'");
        check(failures, streams.outLines().isEmpty(),
                "new best: highlight content leaked onto the main stream");
    }

    private static void runEndLine(List<String> failures) {
        Streams streams = new Streams();
        AlgorithmContext ctx = context(3L);
        ctx.addObserver(streams.observer);
        ctx.start();
        ctx.offerIncumbent(new EvaluatedCandidate(new int[] {2, 0, 3, 1}, 555L));
        streams.highlightBuffer.reset(); // drop the new-best line; run end is what's under test
        ctx.notifyRunEnd();

        List<String> outLines = streams.outLines();
        check(failures, outLines.size() == 1, "run end: expected 1 line, got " + outLines.size());
        check(failures, outLines.get(0).equals(
                        "run end: best 555, permutation [2, 0, 3, 1], total 00:00"),
                "run end: line was '" + outLines.get(0) + "'");

        Streams empty = new Streams();
        AlgorithmContext bare = context(4L);
        bare.addObserver(empty.observer);
        bare.notifyRunEnd();
        check(failures, empty.outLines().size() == 1
                        && empty.outLines().get(0).equals("run end: no incumbent"),
                "run end: no-incumbent fallback line wrong");
    }

    private static void noRandomnessConsumed(List<String> failures) {
        Streams streams = new Streams();
        AlgorithmContext ctx = context(5L);
        ctx.addObserver(streams.observer);
        ctx.start();
        ctx.offerIncumbent(new EvaluatedCandidate(identity(4), 100L));
        ctx.notifyGenerationComplete(population(10, 20));
        ctx.notifyRunEnd();
        Randomizer untouched = new RandomSource(5L).derive(0);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "randomness: observer consumed draws from the context stream");
    }

    // ---- captured-stream fixture ----

    private static final class Streams {

        final ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        final ByteArrayOutputStream highlightBuffer = new ByteArrayOutputStream();
        final LoggingObserver observer = new LoggingObserver(
                new PrintStream(outBuffer, true), new PrintStream(highlightBuffer, true));

        List<String> outLines() {
            return lines(outBuffer);
        }

        List<String> highlightLines() {
            return lines(highlightBuffer);
        }

        private static List<String> lines(ByteArrayOutputStream buffer) {
            List<String> result = new ArrayList<>();
            for (String line : buffer.toString().split(System.lineSeparator())) {
                if (!line.isEmpty()) {
                    result.add(line);
                }
            }
            return result;
        }
    }

    // ---- helpers ----

    private static int[] identity(int n) {
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) {
            permutation[i] = i;
        }
        return permutation;
    }

    private static Population population(long... fitnesses) {
        List<EvaluatedCandidate> members = new ArrayList<>(fitnesses.length);
        for (long f : fitnesses) {
            members.add(new EvaluatedCandidate(new int[] {0, 1, 2, 3}, f));
        }
        return new Population(members);
    }

    private static AlgorithmContext context(long seed) {
        QAPInstance instance = new QAPInstance("synthetic4", new int[4][4], new int[4][4]);
        return new AlgorithmContext(instance, new RandomSource(seed).derive(0));
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
