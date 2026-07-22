package qapSolver;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Evaluation.CachingEvaluator;
import qapSolver.Engine.Evaluation.ExactEvaluator;
import qapSolver.Engine.Evaluation.MultithreadedExactEvaluator;
import qapSolver.Engine.FitnessEvaluator;
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
 * Crossover-rate experiment: the first parameter study on the composed GA.
 * A deliberate duplicate of {@link Main} (which stays untouched as the
 * canonical smoke runner) that repeats Main's full sweep once per candidate
 * crossover rate — {@code 0.50, 0.60, 0.70, 0.80, 0.85, 0.90} — with every
 * other parameter pinned to the agreed smoke setup, and closes with a
 * cross-rate comparison. Runs are paired: the same seeds per instance for
 * every rate, so rate is the only thing that varies between sections.
 *
 * <p>Per rate it prints one summary line per instance (best/mean/worst gap,
 * solved runs, time); per-run lines are dropped — only INVALID / BELOW-REF
 * markers stay loud. The comparison at the end reports per rate:
 * <ul>
 * <li><b>solved-inst</b> — instances whose reference value was matched by at
 *     least one of the seeded runs (and <b>solved-runs</b> — runs that matched
 *     over all instances);</li>
 * <li><b>mean-gap / med-gap</b> — mean and median over instances of the
 *     per-instance mean gap to the reference (instances with reference 0,
 *     i.e. esc16f, excluded from gap aggregation only);</li>
 * <li><b>avg-rank</b> — the optimality rank: per instance the rates are
 *     ranked 1..k by mean best value (fractional ranks on ties, which the
 *     plateau families produce in bulk), averaged over instances — lower is
 *     better and scale-free across instances;</li>
 * <li><b>best</b> — instances where the rate holds or ties the lowest mean
 *     best value;</li>
 * </ul>
 * plus a family x rate table of mean gaps, because per CLAUDE.md some
 * families (lipa-a as a pass/fail stress test, the structured tai-b) should
 * not drive global parameter choices. Instances are compared only if every
 * rate completed all their runs.
 *
 * <p>Usage: {@code java -cp out/main qapSolver.CrossoverRateExperiment [-v]
 * [-data <dir>] [-soln <dir>] [instance ...]} — CLI identical to Main;
 * without names the full deposit (136 instances) runs per rate. Exit codes:
 * 0 = every run of every rate produced a valid (auto-verified) solution;
 * 1 = any invalid result or instance failure; 2 = usage error.
 */
public final class CrossoverRateExperiment {

    /** Stream role id of a single-engine run; islands will claim further ids. */
    private static final int ENGINE_STREAM_ID = 0;

    private CrossoverRateExperiment() {
    }

    public static void main(String[] args) {
        // ---- parameters (edit here for parameter testing) ----
        // instance set: all instances in the data directory; CLI names override
        double[] crossoverRates = {0.50, 0.60, 0.70, 0.80, 0.85, 0.90};
        long[] seeds = {1, 2, 3, 4, 5};
        int populationSize = 100;
        int offspringCount = populationSize; // generational replacement requires λ = μ
        int tournamentSize = 3;
        double winProbability = 1.0;
        double baselineFraction = 0.05;
        double hotFraction = 0.25;
        double coolingFactor = 0.5;
        int stagnationThreshold = 20;
        int eliteCount = 2;
        int cacheCapacity = 100_000; // 0 = no fitness cache
        int evaluatorWorkers = 1;    // 1 = sequential exact; >1 = master-slave parallel evaluation
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
                System.err.println("Usage: java qapSolver.CrossoverRateExperiment"
                        + " [-v] [-data <dir>] [-soln <dir>] [instance ...]");
                System.exit(2);
            } else {
                names.add(arg);
            }
        }
        QAPDataset dataset = new QAPDataset(datDir, slnDir);
        if (names.isEmpty()) {
            try {
                names.addAll(dataset.getInstanceRepository().listNames());
            } catch (IOException e) {
                System.err.println("ERROR listing instances in " + datDir + ": " + e.getMessage());
                System.exit(1);
            }
        }

        System.out.println("QAP GA crossover-rate experiment - pure-GA baseline (no local improvement)");
        System.out.printf(Locale.ROOT, "  crossover=PMX, rates=%s (all else fixed)%n",
                rateList(crossoverRates));
        System.out.printf(Locale.ROOT,
                "  mu=%d lambda=%d  selection=tournament(t=%d,p=%.2f)%n",
                populationSize, offspringCount, tournamentSize, winProbability);
        System.out.printf(Locale.ROOT,
                "  mutation=reheating-swap(base=%.2f,hot=%.2f,cool=%.2f,stagnation=%d)%n",
                baselineFraction, hotFraction, coolingFactor, stagnationThreshold);
        String evaluatorDesc = evaluatorWorkers > 1
                ? "mt-exact(workers=" + evaluatorWorkers + ")" : "exact";
        if (cacheCapacity > 0) {
            evaluatorDesc = "caching(" + evaluatorDesc + ",cap=" + cacheCapacity + ")";
        }
        System.out.printf(Locale.ROOT,
                "  elitism=best-%d  replacement=generational  evaluator=%s%n",
                eliteCount, evaluatorDesc);
        System.out.printf(Locale.ROOT,
                "  termination=%d generations  seeds=%s  data=%s (%d instances)%n",
                maxGenerations, Arrays.toString(seeds), datDir, names.size());

        long sweepStart = System.nanoTime();
        List<RateReport> rateReports = new ArrayList<>();
        for (double crossoverRate : crossoverRates) {
            GAConfiguration config = new GAConfiguration(populationSize, offspringCount,
                    crossoverRate, tournamentSize, winProbability, baselineFraction, hotFraction,
                    coolingFactor, stagnationThreshold, eliteCount, cacheCapacity, evaluatorWorkers,
                    maxGenerations);
            rateReports.add(runRate(dataset, names, seeds, config, verbose));
        }
        double sweepSeconds = (System.nanoTime() - sweepStart) / 1e9;

        printComparison(rateReports, seeds.length, sweepSeconds);

        boolean pass = true;
        for (RateReport rateReport : rateReports) {
            for (InstanceReport report : rateReport.instances) {
                pass &= !report.failed && report.allValid();
            }
        }
        System.exit(pass ? 0 : 1);
    }

    /** One full instance sweep at one crossover rate; per-instance summary lines. */
    private static RateReport runRate(QAPDataset dataset, List<String> names, long[] seeds,
            GAConfiguration config, boolean verbose) {
        System.out.printf(Locale.ROOT, "%n=== crossover rate %.2f ===%n", config.crossoverRate);
        System.out.printf(Locale.ROOT, "  %-9s %4s %10s  %9s %9s %9s  %6s  %8s%n",
                "instance", "n", "ref", "best-gap", "mean-gap", "worst-gap", "solved", "time");
        RateReport rateReport = new RateReport(config.crossoverRate);
        long rateStart = System.nanoTime();
        int runs = 0;
        int valid = 0;
        int refHits = 0;
        for (String name : names) {
            InstanceReport report = runInstance(dataset, name, seeds, config, verbose);
            rateReport.instances.add(report);
            printInstanceLine(report);
            for (RunResult run : report.runs) {
                runs++;
                if (run.best.isValid()) {
                    valid++;
                }
                if (report.ref >= 0 && run.best.getValue() == report.ref) {
                    refHits++;
                }
            }
        }
        rateReport.seconds = (System.nanoTime() - rateStart) / 1e9;
        System.out.printf(Locale.ROOT,
                "  rate %.2f totals: %d runs, %d valid, %d matched ref, %.1fs%n",
                config.crossoverRate, runs, valid, refHits, rateReport.seconds);
        return rateReport;
    }

    /** All seeded runs on one instance; quiet except INVALID / BELOW-REF markers. */
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
        try {
            for (long seed : seeds) {
                if (verbose) {
                    System.out.printf(Locale.ROOT, "---- %s seed %d (rate %.2f) ----%n",
                            name, seed, config.crossoverRate);
                }
                RunResult result = runOnce(instance, seed, config, verbose);
                report.runs.add(result);
                if (!result.best.isValid()) {
                    System.out.printf(Locale.ROOT, "  %-9s seed %d: INVALID! value %d%n",
                            name, result.seed, result.best.getValue());
                }
                if (ref >= 0 && result.best.getValue() < ref) {
                    System.out.printf(Locale.ROOT, "  %-9s seed %d: BELOW-REF! value %d < ref %d%n",
                            name, result.seed, result.best.getValue(), ref);
                }
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
        FitnessEvaluator leaf = config.evaluatorWorkers > 1
                ? new MultithreadedExactEvaluator(config.evaluatorWorkers)
                : new ExactEvaluator();
        CachingEvaluator cache = config.cacheCapacity > 0
                ? new CachingEvaluator(leaf, config.cacheCapacity) : null;
        FitnessEvaluator evaluator = cache != null ? cache : leaf;
        try {
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
                    cache != null ? cache.getHits() : 0,
                    cache != null ? cache.getHits() + cache.getMisses() : 0,
                    context.elapsedMillis());
        } finally {
            if (leaf instanceof MultithreadedExactEvaluator) {
                ((MultithreadedExactEvaluator) leaf).shutdown();
            }
        }
    }

    private static void printInstanceLine(InstanceReport report) {
        if (report.failed || report.runs.isEmpty()) {
            System.out.printf(Locale.ROOT, "  %-9s FAILED%n", report.name);
            return;
        }
        long bestValue = Long.MAX_VALUE;
        long worstValue = Long.MIN_VALUE;
        double valueSum = 0;
        int refHits = 0;
        double totalSeconds = 0;
        for (RunResult run : report.runs) {
            long value = run.best.getValue();
            bestValue = Math.min(bestValue, value);
            worstValue = Math.max(worstValue, value);
            valueSum += value;
            if (value == report.ref) {
                refHits++;
            }
            totalSeconds += run.millis / 1000.0;
        }
        double meanValue = valueSum / report.runs.size();
        System.out.printf(Locale.ROOT, "  %-9s %4d %10s  %9s %9s %9s  %6s  %7.2fs%n",
                report.name, report.n,
                report.ref >= 0 ? String.valueOf(report.ref) : "n/a",
                gapString(bestValue, report.ref),
                meanGapString(meanValue, report.ref),
                gapString(worstValue, report.ref),
                report.ref >= 0 ? refHits + "/" + report.runs.size() : "n/a",
                totalSeconds);
    }

    /**
     * The cross-rate comparison: solved counts, gap aggregates, fractional
     * optimality ranks, and the family x rate gap table. Only instances with
     * complete runs under every rate are compared.
     */
    private static void printComparison(List<RateReport> rateReports, int seedCount,
            double sweepSeconds) {
        int rateCount = rateReports.size();
        int instanceCount = rateReports.get(0).instances.size();

        int ranked = 0;
        int[] solvedInstances = new int[rateCount];
        int[] solvedRuns = new int[rateCount];
        double[] rankSum = new double[rateCount];
        int[] bestCount = new int[rateCount];
        List<List<Double>> gapsPerRate = new ArrayList<>();
        for (int r = 0; r < rateCount; r++) {
            gapsPerRate.add(new ArrayList<>());
        }
        Map<String, double[]> familyGapSums = new LinkedHashMap<>();
        Map<String, Integer> familyGapCounts = new LinkedHashMap<>();

        for (int i = 0; i < instanceCount; i++) {
            boolean complete = true;
            for (RateReport rateReport : rateReports) {
                InstanceReport report = rateReport.instances.get(i);
                complete &= !report.failed && report.runs.size() == seedCount;
            }
            if (!complete) {
                System.out.printf(Locale.ROOT, "  (skipping %s: incomplete under some rate)%n",
                        rateReports.get(0).instances.get(i).name);
                continue;
            }
            ranked++;
            InstanceReport first = rateReports.get(0).instances.get(i);
            double[] meanValues = new double[rateCount];
            for (int r = 0; r < rateCount; r++) {
                InstanceReport report = rateReports.get(r).instances.get(i);
                double valueSum = 0;
                boolean hitRef = false;
                for (RunResult run : report.runs) {
                    long value = run.best.getValue();
                    valueSum += value;
                    if (report.ref >= 0 && value == report.ref) {
                        hitRef = true;
                        solvedRuns[r]++;
                    }
                }
                meanValues[r] = valueSum / report.runs.size();
                if (hitRef) {
                    solvedInstances[r]++;
                }
            }
            double[] ranks = fractionalRanks(meanValues);
            double minMean = meanValues[0];
            for (int r = 1; r < rateCount; r++) {
                minMean = Math.min(minMean, meanValues[r]);
            }
            for (int r = 0; r < rateCount; r++) {
                rankSum[r] += ranks[r];
                if (meanValues[r] == minMean) {
                    bestCount[r]++;
                }
            }
            if (first.ref > 0) {
                String family = familyOf(first.name);
                double[] sums = familyGapSums.computeIfAbsent(family, k -> new double[rateCount]);
                familyGapCounts.merge(family, 1, Integer::sum);
                for (int r = 0; r < rateCount; r++) {
                    double gap = 100.0 * (meanValues[r] - first.ref) / first.ref;
                    gapsPerRate.get(r).add(gap);
                    sums[r] += gap;
                }
            }
        }

        System.out.printf(Locale.ROOT,
                "%n=== Crossover-rate comparison (%d instances x %d seeds per rate, %.1fs total) ===%n",
                ranked, seedCount, sweepSeconds);
        System.out.printf(Locale.ROOT, "  %5s  %11s  %11s  %9s  %9s  %8s  %5s%n",
                "rate", "solved-inst", "solved-runs", "mean-gap", "med-gap", "avg-rank", "best");
        int bestByRank = 0;
        int bestBySolved = 0;
        for (int r = 0; r < rateCount; r++) {
            List<Double> gaps = gapsPerRate.get(r);
            System.out.printf(Locale.ROOT, "  %5.2f  %8d/%-3d %8d/%-4d %8.3f%% %8.3f%%  %8.3f  %5d%n",
                    rateReports.get(r).rate, solvedInstances[r], ranked,
                    solvedRuns[r], ranked * seedCount,
                    mean(gaps), median(gaps), rankSum[r] / ranked, bestCount[r]);
            if (rankSum[r] < rankSum[bestByRank]) {
                bestByRank = r;
            }
            if (solvedInstances[r] > solvedInstances[bestBySolved]) {
                bestBySolved = r;
            }
        }
        System.out.printf(Locale.ROOT,
                "  avg-rank: rates ranked 1..%d per instance by mean best value, ties fractional;"
                        + " best = holds/ties the lowest mean%n", rateCount);
        System.out.printf(Locale.ROOT,
                "  lowest avg rank: rate %.2f (%.3f); most instances solved: rate %.2f (%d)%n",
                rateReports.get(bestByRank).rate, rankSum[bestByRank] / ranked,
                rateReports.get(bestBySolved).rate, solvedInstances[bestBySolved]);

        System.out.printf(Locale.ROOT, "%n=== Mean gap by family x rate ===%n");
        System.out.printf(Locale.ROOT, "  %-7s %4s", "family", "#");
        for (RateReport rateReport : rateReports) {
            System.out.printf(Locale.ROOT, " %7.2f", rateReport.rate);
        }
        System.out.println();
        for (Map.Entry<String, double[]> entry : familyGapSums.entrySet()) {
            int count = familyGapCounts.get(entry.getKey());
            System.out.printf(Locale.ROOT, "  %-7s %4d", entry.getKey(), count);
            for (int r = 0; r < rateCount; r++) {
                System.out.printf(Locale.ROOT, " %6.2f%%", entry.getValue()[r] / count);
            }
            System.out.println();
        }
        System.out.printf(Locale.ROOT, "  %-7s %4d", "ALL", gapsPerRate.get(0).size());
        for (int r = 0; r < rateCount; r++) {
            System.out.printf(Locale.ROOT, " %6.2f%%", mean(gapsPerRate.get(r)));
        }
        System.out.println();
    }

    /** Fractional ranks 1..k ascending by value; tied values share the average position. */
    private static double[] fractionalRanks(double[] values) {
        int k = values.length;
        Integer[] order = new Integer[k];
        for (int i = 0; i < k; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(values[a], values[b]));
        double[] ranks = new double[k];
        int i = 0;
        while (i < k) {
            int j = i;
            while (j + 1 < k && values[order[j + 1]] == values[order[i]]) {
                j++;
            }
            double rank = (i + j + 2) / 2.0; // average of 1-based positions i+1 .. j+1
            for (int t = i; t <= j; t++) {
                ranks[order[t]] = rank;
            }
            i = j + 1;
        }
        return ranks;
    }

    /** Alphabetic prefix of the instance name, the repository's family convention. */
    private static String familyOf(String name) {
        int i = 0;
        while (i < name.length() && Character.isLetter(name.charAt(i))) {
            i++;
        }
        return name.substring(0, i);
    }

    private static double mean(List<Double> values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return values.isEmpty() ? Double.NaN : sum / values.size();
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return Double.NaN;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid)
                : (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private static String rateList(double[] rates) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < rates.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format(Locale.ROOT, "%.2f", rates[i]));
        }
        return sb.append('}').toString();
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

    /** One full-sweep result at one crossover rate, instances in run order. */
    private static final class RateReport {

        final double rate;
        final List<InstanceReport> instances = new ArrayList<>();
        double seconds;

        RateReport(double rate) {
            this.rate = rate;
        }
    }

    /**
     * One GA parameter set, immutable — built from the parameter block in
     * {@link #main} once per crossover rate and handed whole to the run
     * helpers; validation stays where it lives, in the operators' own
     * constructors.
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
        final int evaluatorWorkers;
        final int maxGenerations;

        GAConfiguration(int populationSize, int offspringCount, double crossoverRate,
                int tournamentSize, double winProbability, double baselineFraction,
                double hotFraction, double coolingFactor, int stagnationThreshold,
                int eliteCount, int cacheCapacity, int evaluatorWorkers, int maxGenerations) {
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
            this.evaluatorWorkers = evaluatorWorkers;
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
    }

    /** All runs of one instance under one rate; ref = -1 means it has no .sln. */
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
