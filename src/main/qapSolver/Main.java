package qapSolver;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Evaluation.CachingEvaluator;
import qapSolver.Engine.Evaluation.ExactEvaluator;
import qapSolver.Engine.Observation.LoggingObserver;
import qapSolver.Engine.Termination.MaxGenerationsCriterion;
import qapSolver.GA.GeneticAlgorithm;
import qapSolver.GA.Crossover.PartiallyMappedCrossover;
import qapSolver.GA.Elitism.BestKElitePreserver;
import qapSolver.GA.Improvement.NoOpImprovement;
import qapSolver.GA.Initialization.RandomInitializer;
import qapSolver.GA.Mutation.ReheatingSwapMutation;
import qapSolver.GA.Replacement.GenerationalReplacement;
import qapSolver.GA.Selection.TournamentSelector;
import qapSolver.Model.CustomSolution;
import qapSolver.Model.QAPInstance;
import qapSolver.Reader.QAPDataset;
import qapSolver.Random.RandomSource;

/**
 * Smoke-test entry point: the composed generational memetic GA in its pure-GA
 * baseline shape ({@code NoOpImprovement}) against small closed QAPLIB
 * instances, reporting each run's gap to the {@code .sln} reference value.
 * This is the first end-to-end exercise of the whole engine composition on
 * real dataset instances.
 *
 * <p>Every tunable parameter is a local variable in the parameter block at
 * the top of {@link #main}, bundled into one immutable {@link GAConfiguration}
 * handed to the run helpers — parameter testing is editing that block (or
 * looping it over several configurations). The agreed smoke setup:
 * μ = λ = 100, tournament(3, 1.0), PMX at rate 0.9, reheating swap mutation
 * (0.05, 0.25, 0.5, 20), best-2 elitism, generational replacement,
 * caching(exact) evaluation with capacity 100&nbsp;000, 500 generations, and
 * seeds 1–5 — five independent runs per instance, each with its own
 * {@code RandomSource} (stream id 0), so every run is bit-reproducible on any
 * JDK.
 *
 * <p>Usage:
 * {@code java -cp out/main qapSolver.Main [-v] [-data <dir>] [-soln <dir>]
 * [instance ...]}
 * — without instance names the default curated fourteen run: the small eight
 * (nug12, had12, rou12, scr12, chr12a, tai12a, esc16a, lipa20a — one per
 * family character) plus the mid-size closed six (bur26a, nug30, tho30,
 * tai30b, ste36a, sko42 — adds the asymmetric/nonzero-diagonal bur case, the
 * structured tai-b series and the largest closed sko). All closed, all with
 * a {@code .sln}. {@code -v} registers the {@code LoggingObserver} on every
 * run (full per-generation trace; new-best lines on stderr).
 *
 * <p>Exit codes: 0 = every run produced a valid (auto-verified) solution;
 * 1 = any invalid result or instance failure; 2 = usage error.
 */
public final class Main {

    /** Stream role id of a single-engine run; islands will claim further ids. */
    private static final int ENGINE_STREAM_ID = 0;

    private Main() {
    }

    public static void main(String[] args) {
        // ---- parameters (edit here for parameter testing) ----
        List<String> defaultInstances = Arrays.asList(
                "nug12", "had12", "rou12", "scr12", "chr12a", "tai12a", "esc16a", "lipa20a",
                "bur26a", "nug30", "tho30", "tai30b", "ste36a", "sko42");
        long[] seeds = {1, 2, 3, 4, 5};
        int populationSize = 100;
        int offspringCount = populationSize; // generational replacement requires λ = μ
        double crossoverRate = 0.9;
        int tournamentSize = 3;
        double winProbability = 1.0;
        double baselineFraction = 0.05;
        double hotFraction = 0.25;
        double coolingFactor = 0.5;
        int stagnationThreshold = 20;
        int eliteCount = 2;
        int cacheCapacity = 100_000;
        int maxGenerations = 500;
        // ------------------------------------------------------

        boolean verbose = false;
        Path datDir = Paths.get("QAPData", "qapdata");
        Path slnDir = Paths.get("QAPData", "qapsoln");
        List<String> names = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-v")) {
                verbose = true;
            } else if (arg.equals("-data") && i + 1 < args.length) {
                datDir = Paths.get(args[++i]);
            } else if (arg.equals("-soln") && i + 1 < args.length) {
                slnDir = Paths.get(args[++i]);
            } else if (arg.startsWith("-")) {
                System.err.println("Usage: java qapSolver.Main [-v] [-data <dir>] [-soln <dir>] [instance ...]");
                System.exit(2);
            } else {
                names.add(arg);
            }
        }
        if (names.isEmpty()) {
            names.addAll(defaultInstances);
        }

        GAConfiguration config = new GAConfiguration(populationSize, offspringCount,
                crossoverRate, tournamentSize, winProbability, baselineFraction, hotFraction,
                coolingFactor, stagnationThreshold, eliteCount, cacheCapacity, maxGenerations);

        System.out.println("QAP GA smoke run - pure-GA baseline (no local improvement)");
        System.out.printf(Locale.ROOT,
                "  mu=%d lambda=%d  selection=tournament(t=%d,p=%.2f)  crossover=PMX@%.2f%n",
                populationSize, offspringCount, tournamentSize, winProbability, crossoverRate);
        System.out.printf(Locale.ROOT,
                "  mutation=reheating-swap(base=%.2f,hot=%.2f,cool=%.2f,stagnation=%d)%n",
                baselineFraction, hotFraction, coolingFactor, stagnationThreshold);
        System.out.printf(Locale.ROOT,
                "  elitism=best-%d  replacement=generational  evaluator=caching(exact,cap=%d)%n",
                eliteCount, cacheCapacity);
        System.out.printf(Locale.ROOT,
                "  termination=%d generations  seeds=%s  data=%s%n",
                maxGenerations, Arrays.toString(seeds), datDir);

        QAPDataset dataset = new QAPDataset(datDir, slnDir);
        long sweepStart = System.nanoTime();
        List<InstanceReport> reports = new ArrayList<>();
        for (String name : names) {
            reports.add(runInstance(dataset, name, seeds, config, verbose));
        }
        double sweepSeconds = (System.nanoTime() - sweepStart) / 1e9;

        printSummary(reports, seeds.length, sweepSeconds);

        boolean pass = true;
        for (InstanceReport report : reports) {
            pass &= !report.failed && report.allValid();
        }
        System.exit(pass ? 0 : 1);
    }

    /** All seeded runs on one instance; prints per-run lines as they finish. */
    private static InstanceReport runInstance(QAPDataset dataset, String name, long[] seeds,
            GAConfiguration config, boolean verbose) {
        QAPInstance instance;
        long ref;
        try {
            instance = dataset.getInstance(name);
            ref = dataset.findSolution(name).map(s -> s.getValue()).orElse(-1L);
        } catch (IOException e) {
            System.err.printf(Locale.ROOT, "%nERROR loading %s: %s%n", name, e.getMessage());
            return InstanceReport.loadFailure(name);
        }
        InstanceReport report = new InstanceReport(name, instance.getSize(), ref);
        System.out.printf(Locale.ROOT, "%n%s  (n=%d, ref %s)%n",
                name, instance.getSize(), ref >= 0 ? String.valueOf(ref) : "n/a, no .sln");
        System.out.printf(Locale.ROOT, "  %4s | %9s | %8s | %9s | %9s | %5s | %6s | %6s%n",
                "seed", "best", "gap", "found-gen", "found-ev", "evals", "cache", "time");
        try {
            for (long seed : seeds) {
                if (verbose) {
                    System.out.printf(Locale.ROOT, "---- %s seed %d ----%n", name, seed);
                }
                RunResult result = runOnce(instance, seed, config, verbose);
                report.runs.add(result);
                String marker = "";
                if (!result.best.isValid()) {
                    marker += "  INVALID!";
                }
                if (ref >= 0 && result.best.getValue() < ref) {
                    marker += "  BELOW-REF!";
                }
                System.out.printf(Locale.ROOT,
                        "  %4d | %9d | %8s | %9d | %9d | %5d | %5.1f%% | %5.2fs%s%n",
                        result.seed, result.best.getValue(), gapString(result.best.getValue(), ref),
                        result.foundGeneration, result.foundEvaluations, result.fullEvaluations,
                        result.hitRatePct(), result.millis / 1000.0, marker);
            }
        } catch (RuntimeException e) {
            System.err.printf(Locale.ROOT, "%nERROR running %s:%n", name);
            e.printStackTrace();
            report.failed = true;
        }
        return report;
    }

    /** One seeded run: fresh source, context, and step objects (steps are stateful). */
    private static RunResult runOnce(QAPInstance instance, long seed, GAConfiguration config,
            boolean verbose) {
        RandomSource source = new RandomSource(seed);
        AlgorithmContext context = new AlgorithmContext(instance, source.derive(ENGINE_STREAM_ID));
        if (verbose) {
            context.addObserver(new LoggingObserver());
        }
        CachingEvaluator evaluator = new CachingEvaluator(new ExactEvaluator(), config.cacheCapacity);
        GeneticAlgorithm ga = new GeneticAlgorithm(context,
                new MaxGenerationsCriterion(config.maxGenerations),
                new RandomInitializer(config.populationSize),
                evaluator,
                new TournamentSelector(config.tournamentSize, config.winProbability),
                new PartiallyMappedCrossover(),
                new ReheatingSwapMutation(config.baselineFraction, config.hotFraction,
                        config.coolingFactor, config.stagnationThreshold),
                new GenerationalReplacement(),
                new BestKElitePreserver(config.eliteCount),
                new NoOpImprovement(),
                config.offspringCount, config.crossoverRate);
        CustomSolution best = ga.run();
        return new RunResult(seed, best,
                context.getBestFoundGeneration(), context.getBestFoundEvaluations(),
                context.getFullEvaluations(),
                evaluator.getHits(), evaluator.getHits() + evaluator.getMisses(),
                context.elapsedMillis());
    }

    private static void printSummary(List<InstanceReport> reports, int runsPerInstance,
            double sweepSeconds) {
        System.out.printf(Locale.ROOT,
                "%nSummary - gap vs .sln reference, %d seeded runs per instance%n", runsPerInstance);
        System.out.printf(Locale.ROOT,
                "  %-9s %4s %9s  %9s %9s %9s  %5s  %7s  %8s%n",
                "instance", "n", "ref", "best-gap", "mean-gap", "worst-gap", "hits", "cache", "time");
        int totalRuns = 0;
        int validRuns = 0;
        int refHits = 0;
        for (InstanceReport report : reports) {
            if (report.failed || report.runs.isEmpty()) {
                System.out.printf(Locale.ROOT, "  %-9s FAILED%n", report.name);
                continue;
            }
            long bestValue = Long.MAX_VALUE;
            long worstValue = Long.MIN_VALUE;
            double valueSum = 0;
            int instanceRefHits = 0;
            long totalHits = 0;
            long totalLookups = 0;
            double totalSeconds = 0;
            for (RunResult run : report.runs) {
                long value = run.best.getValue();
                bestValue = Math.min(bestValue, value);
                worstValue = Math.max(worstValue, value);
                valueSum += value;
                if (value == report.ref) {
                    instanceRefHits++;
                }
                totalHits += run.cacheHits;
                totalLookups += run.cacheLookups;
                totalSeconds += run.millis / 1000.0;
                totalRuns++;
                if (run.best.isValid()) {
                    validRuns++;
                }
            }
            refHits += instanceRefHits;
            double meanValue = valueSum / report.runs.size();
            System.out.printf(Locale.ROOT,
                    "  %-9s %4d %9s  %9s %9s %9s  %5s  %6.1f%%  %7.2fs%n",
                    report.name, report.n,
                    report.ref >= 0 ? String.valueOf(report.ref) : "n/a",
                    gapString(bestValue, report.ref),
                    meanGapString(meanValue, report.ref),
                    gapString(worstValue, report.ref),
                    report.ref >= 0 ? instanceRefHits + "/" + report.runs.size() : "n/a",
                    totalLookups == 0 ? 0.0 : 100.0 * totalHits / totalLookups,
                    totalSeconds);
        }
        System.out.printf(Locale.ROOT,
                "%n%d runs, %d valid, %d matched the reference - total %.1fs - %s%n",
                totalRuns, validRuns, refHits, sweepSeconds,
                totalRuns > 0 && validRuns == totalRuns ? "PASS" : "FAIL");
    }

    private static String gapString(long value, long ref) {
        if (ref < 0) {
            return "n/a";
        }
        if (ref == 0) {
            return value == 0 ? "0.000%" : "inf";
        }
        return String.format(Locale.ROOT, "%.3f%%", 100.0 * (value - ref) / ref);
    }

    private static String meanGapString(double meanValue, long ref) {
        if (ref <= 0) {
            return ref == 0 && meanValue == 0.0 ? "0.000%" : "n/a";
        }
        return String.format(Locale.ROOT, "%.3f%%", 100.0 * (meanValue - ref) / ref);
    }

    /**
     * One GA parameter set, immutable — built from the parameter block in
     * {@link #main} and handed whole to the run helpers. A future parameter
     * sweep constructs one per test point; validation stays where it lives,
     * in the operators' own constructors.
     */
    private static final class GAConfiguration {

        final int populationSize;
        final int offspringCount;
        final double crossoverRate;
        final int tournamentSize;
        final double winProbability;
        final double baselineFraction;
        final double hotFraction;
        final double coolingFactor;
        final int stagnationThreshold;
        final int eliteCount;
        final int cacheCapacity;
        final int maxGenerations;

        GAConfiguration(int populationSize, int offspringCount, double crossoverRate,
                int tournamentSize, double winProbability, double baselineFraction,
                double hotFraction, double coolingFactor, int stagnationThreshold,
                int eliteCount, int cacheCapacity, int maxGenerations) {
            this.populationSize = populationSize;
            this.offspringCount = offspringCount;
            this.crossoverRate = crossoverRate;
            this.tournamentSize = tournamentSize;
            this.winProbability = winProbability;
            this.baselineFraction = baselineFraction;
            this.hotFraction = hotFraction;
            this.coolingFactor = coolingFactor;
            this.stagnationThreshold = stagnationThreshold;
            this.eliteCount = eliteCount;
            this.cacheCapacity = cacheCapacity;
            this.maxGenerations = maxGenerations;
        }
    }

    /** One seeded run's outcome. */
    private static final class RunResult {

        final long seed;
        final CustomSolution best;
        final int foundGeneration;
        final long foundEvaluations;
        final long fullEvaluations;
        final long cacheHits;
        final long cacheLookups;
        final long millis;

        RunResult(long seed, CustomSolution best, int foundGeneration, long foundEvaluations,
                long fullEvaluations, long cacheHits, long cacheLookups, long millis) {
            this.seed = seed;
            this.best = best;
            this.foundGeneration = foundGeneration;
            this.foundEvaluations = foundEvaluations;
            this.fullEvaluations = fullEvaluations;
            this.cacheHits = cacheHits;
            this.cacheLookups = cacheLookups;
            this.millis = millis;
        }

        double hitRatePct() {
            return cacheLookups == 0 ? 0.0 : 100.0 * cacheHits / cacheLookups;
        }
    }

    /** All runs of one instance; ref = -1 means it has no .sln. */
    private static final class InstanceReport {

        final String name;
        final int n;
        final long ref;
        final List<RunResult> runs = new ArrayList<>();
        boolean failed;

        InstanceReport(String name, int n, long ref) {
            this.name = name;
            this.n = n;
            this.ref = ref;
        }

        static InstanceReport loadFailure(String name) {
            InstanceReport report = new InstanceReport(name, 0, -1);
            report.failed = true;
            return report;
        }

        boolean allValid() {
            for (RunResult run : runs) {
                if (!run.best.isValid()) {
                    return false;
                }
            }
            return !runs.isEmpty();
        }
    }
}
