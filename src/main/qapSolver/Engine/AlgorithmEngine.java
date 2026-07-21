package qapSolver.Engine;

import qapSolver.Model.CustomSolution;

/**
 * Abstract base of every metaheuristic engine — the seam the future island
 * layer holds islands by. The lifecycle is fixed here and {@code final};
 * concrete engines (GA now, possibly SA later — a single-point method is a
 * population of one) implement only {@link #doInitialize()} and
 * {@link #doStep()}.
 *
 * <p>Two ways to drive it:
 * <ul>
 * <li>{@link #run()} — the single-solver entry point: initialize, step until
 *     {@link #shouldTerminate()}, notify run end, return the best solution.
 *     Single-shot: a second {@code run()} (or {@code initialize()}) throws.</li>
 * <li>{@link #initialize()} / {@link #step()} / {@link #shouldTerminate()}
 *     driven externally — the island coordinator's mode, migrating between
 *     steps. The driver then owns run-end notification and stopping.</li>
 * </ul>
 *
 * <p>Generation numbering: initialization produces generation 0 (evaluated
 * initial population); {@code step()} advances the counter <em>before</em>
 * running the step body, so anything found during generation g is stamped g.
 * Both lifecycle methods fire the generation-complete notification;
 * {@code initialize()} starts the context clock first, so generation-0
 * evaluation is on the clock and the budget.
 *
 * <p>Termination is the one step slot living in this base, because every
 * metaheuristic terminates: checked between generations, after the external
 * stop flag.
 */
public abstract class AlgorithmEngine {

    protected final AlgorithmContext context;
    private final TerminationCriterion termination;
    private boolean initialized;

    protected AlgorithmEngine(AlgorithmContext context, TerminationCriterion termination) {
        if (context == null || termination == null) {
            throw new IllegalArgumentException("context and termination must be non-null");
        }
        this.context = context;
        this.termination = termination;
    }

    public final AlgorithmContext getContext() {
        return context;
    }

    public final boolean isInitialized() {
        return initialized;
    }

    /**
     * Starts the clock, notifies run start, builds and evaluates the initial
     * state via {@link #doInitialize()}, and reports it as generation 0.
     */
    public final void initialize() {
        if (initialized) {
            throw new IllegalStateException("engine already initialized");
        }
        context.start();
        context.notifyRunStart();
        doInitialize();
        initialized = true;
        context.notifyGenerationComplete(getPopulation());
    }

    /** Runs one generation; {@link #initialize()} must have happened. */
    public final void step() {
        if (!initialized) {
            throw new IllegalStateException("engine not initialized — call initialize() first");
        }
        context.advanceGeneration();
        doStep();
        context.notifyGenerationComplete(getPopulation());
    }

    /**
     * True when the run should stop: external stop request first, then the
     * termination criterion. The only place stop semantics live.
     */
    public final boolean shouldTerminate() {
        return context.isStopRequested() || termination.shouldTerminate(context);
    }

    /** The single-solver entry point; see the class doc. */
    public final CustomSolution run() {
        initialize();
        while (!shouldTerminate()) {
            step();
        }
        context.notifyRunEnd();
        return getBestSolution();
    }

    /**
     * The context incumbent as an auto-verifying {@link CustomSolution} — the
     * boundary where the trusted hot path is checked against the objective
     * for free. Constructs (and re-verifies, O(n²)) on every call: boundary
     * use, not per generation.
     */
    public final CustomSolution getBestSolution() {
        return new CustomSolution(context.getInstance(), context.getBestValue(),
                context.getBestPermutation().clone());
    }

    /** The current population; non-null and evaluated once initialized. */
    public abstract Population getPopulation();

    /** Builds and evaluates the initial state (generation 0). */
    protected abstract void doInitialize();

    /** One generation of the concrete metaheuristic. */
    protected abstract void doStep();
}
