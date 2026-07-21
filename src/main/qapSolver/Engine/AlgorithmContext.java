package qapSolver.Engine;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Model.QAPInstance;
import qapSolver.Random.Randomizer;

/**
 * Run state for one solver instance (one island, later): the problem, the
 * randomness stream, counters, the incumbent with its found-at stamps, the
 * clock, observer dispatch, and the stop flag. State only — configuration
 * (rates, sizes, budgets) lives in the step objects and the engine, never
 * here.
 *
 * <p>Writer discipline: the engine advances {@link #advanceGeneration()
 * generations} and {@link #offerIncumbent offers} candidates; the concrete
 * fitness evaluator calls {@link #countFullEvaluation()} once per actual
 * O(n²) computation (cache hits must not count); local search counts its
 * O(n) moves via {@link #countDeltaEvaluations}. Everything else reads.
 *
 * <p><b>Thread confinement:</b> like {@link Randomizer}, a context is
 * confined to its engine's thread. The single exception is
 * {@link #requestStop()}/{@link #isStopRequested()} — the volatile kill
 * switch a coordinator may flip from outside.
 */
public final class AlgorithmContext {

    private final QAPInstance instance;
    private final Randomizer randomizer;
    private final List<EvolutionObserver> observers = new ArrayList<>();

    private boolean started;
    private long startNanos;
    private int generation;
    private long fullEvaluations;
    private long deltaEvaluations;

    private int[] bestPermutation; // null while no incumbent; context-private copy
    private long bestValue;
    private int bestFoundGeneration;
    private long bestFoundEvaluations;
    private long bestFoundMillis;

    private volatile boolean stopRequested;

    public AlgorithmContext(QAPInstance instance, Randomizer randomizer) {
        if (instance == null || randomizer == null) {
            throw new IllegalArgumentException("instance and randomizer must be non-null");
        }
        this.instance = instance;
        this.randomizer = randomizer;
    }

    public QAPInstance getInstance() {
        return instance;
    }

    /** This run's randomness stream — every stochastic step draws from here. */
    public Randomizer getRandomizer() {
        return randomizer;
    }

    /** Registers an observer (engine thread; typically before the run starts). */
    public void addObserver(EvolutionObserver observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer must be non-null");
        }
        observers.add(observer);
    }

    // ---- lifecycle & clock ----

    /**
     * Starts the run clock; called exactly once, before any evaluation, so
     * generation-0 work is on the budget and the millis stamps are meaningful.
     */
    public void start() {
        if (started) {
            throw new IllegalStateException("run already started");
        }
        started = true;
        startNanos = System.nanoTime();
    }

    public boolean isStarted() {
        return started;
    }

    /** Wall-clock milliseconds since {@link #start()}. */
    public long elapsedMillis() {
        requireStarted();
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    // ---- counters ----

    /** Current generation; 0 is the initialized population. */
    public int getGeneration() {
        return generation;
    }

    /** Engine only: entering the next generation (before its steps run). */
    public void advanceGeneration() {
        generation++;
    }

    /** Concrete evaluators only: one actual O(n²) objective computation. */
    public void countFullEvaluation() {
        fullEvaluations++;
    }

    public long getFullEvaluations() {
        return fullEvaluations;
    }

    /** Local search / SA only: {@code count} O(n) delta evaluations. */
    public void countDeltaEvaluations(long count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive: " + count);
        }
        deltaEvaluations += count;
    }

    public long getDeltaEvaluations() {
        return deltaEvaluations;
    }

    // ---- incumbent ----

    /**
     * The single choke point for "new best": strictly better than the
     * incumbent (ties are not improvements — no observer spam on plateaus)
     * means the permutation is copied in, all found-at stamps are taken
     * (generation, full evaluations, elapsed millis), and
     * {@link EvolutionObserver#onNewBest} fires. Returns whether the offer
     * improved the incumbent.
     */
    public boolean offerIncumbent(EvaluatedCandidate candidate) {
        requireStarted();
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must be non-null");
        }
        if (candidate.size() != instance.getSize()) {
            throw new IllegalArgumentException(instance.getName() + ": candidate size "
                    + candidate.size() + " != n=" + instance.getSize());
        }
        if (bestPermutation != null && candidate.getFitness() >= bestValue) {
            return false;
        }
        bestPermutation = candidate.getPermutation().clone();
        bestValue = candidate.getFitness();
        bestFoundGeneration = generation;
        bestFoundEvaluations = fullEvaluations;
        bestFoundMillis = elapsedMillis();
        for (int i = 0; i < observers.size(); i++) {
            observers.get(i).onNewBest(this);
        }
        return true;
    }

    public boolean hasIncumbent() {
        return bestPermutation != null;
    }

    /** Best objective value seen so far. */
    public long getBestValue() {
        requireIncumbent();
        return bestValue;
    }

    /**
     * Best permutation seen so far — internal reference to the context's
     * private copy, not re-copied per call; do not mutate.
     */
    public int[] getBestPermutation() {
        requireIncumbent();
        return bestPermutation;
    }

    /** Generation in which the incumbent was found. */
    public int getBestFoundGeneration() {
        requireIncumbent();
        return bestFoundGeneration;
    }

    /** Full-evaluation count at the moment the incumbent was found. */
    public long getBestFoundEvaluations() {
        requireIncumbent();
        return bestFoundEvaluations;
    }

    /** Elapsed milliseconds at the moment the incumbent was found. */
    public long getBestFoundMillis() {
        requireIncumbent();
        return bestFoundMillis;
    }

    /**
     * Generations since the incumbent last improved — derived, not stored:
     * {@code generation − bestFoundGeneration}. Stagnation-based termination
     * and adaptive steps read this.
     */
    public int generationsSinceImprovement() {
        requireIncumbent();
        return generation - bestFoundGeneration;
    }

    // ---- observer dispatch ----

    /** Engine only: the run is beginning (clock started, nothing initialized). */
    public void notifyRunStart() {
        for (int i = 0; i < observers.size(); i++) {
            observers.get(i).onRunStart(this);
        }
    }

    /** Engine only: a generation (0 included) finished with this population. */
    public void notifyGenerationComplete(Population population) {
        if (population == null) {
            throw new IllegalArgumentException("population must be non-null");
        }
        for (int i = 0; i < observers.size(); i++) {
            observers.get(i).onGenerationComplete(this, population);
        }
    }

    /** Engine only: the run is over. */
    public void notifyRunEnd() {
        for (int i = 0; i < observers.size(); i++) {
            observers.get(i).onRunEnd(this);
        }
    }

    // ---- external stop ----

    /** May be called from any thread: ask the run to stop at the next check. */
    public void requestStop() {
        stopRequested = true;
    }

    public boolean isStopRequested() {
        return stopRequested;
    }

    private void requireStarted() {
        if (!started) {
            throw new IllegalStateException("run not started");
        }
    }

    private void requireIncumbent() {
        if (bestPermutation == null) {
            throw new IllegalStateException("no incumbent yet — nothing evaluated");
        }
    }
}
