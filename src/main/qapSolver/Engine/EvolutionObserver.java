package qapSolver.Engine;

/**
 * Hook points for watching a run without touching it: logging, convergence
 * curves, and later the island coordinator's monitoring all plug in here.
 * Every method is a no-op — subclasses override only the events they care
 * about.
 *
 * <p>Event order per run: {@link #onRunStart} once (clock already running);
 * {@link #onNewBest} every time the incumbent strictly improves — including
 * during initialization and local improvement; {@link #onGenerationComplete}
 * after every generation, generation 0 (the freshly initialized, evaluated
 * population) included; {@link #onRunEnd} once when {@code Solver.run()}
 * finishes. A coordinator driving {@code step()} directly owns run-end
 * semantics itself.
 *
 * <p>Observers are called on the engine's thread and must be read-only:
 * query the context and population, mutate nothing. Anything slow in an
 * observer stalls the run.
 */
public abstract class EvolutionObserver {

    /** The run is about to begin: context started, population not yet initialized. */
    public void onRunStart(AlgorithmContext context) {
    }

    /**
     * A generation finished and {@code population} is its outcome. Fired for
     * generation 0 with the initial population, then once per step.
     */
    public void onGenerationComplete(AlgorithmContext context, Population population) {
    }

    /**
     * The incumbent strictly improved; read the new best (value, permutation,
     * found-at stamps) from the context.
     */
    public void onNewBest(AlgorithmContext context) {
    }

    /** The run finished; final state is readable from the context. */
    public void onRunEnd(AlgorithmContext context) {
    }
}
