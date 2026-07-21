package qapSolver.Engine;

/**
 * Common base of every step abstraction, existing for one reason: built-in
 * wall-clock instrumentation. Each step abstract exposes its public method as
 * {@code final} — clock, delegate to the {@code protected doX(...)} hook,
 * record — and implementations override the hook. A step run by any engine
 * is therefore always measured; timing is a property of the framework, not
 * engine etiquette.
 *
 * <p>Accumulates total nanoseconds and invocation count. Read them from
 * whoever holds the step objects — the code that constructed the engine, or
 * a reporting observer handed the references. Per-call average is
 * total/invocations; per-generation curves come from diffing totals between
 * generation-complete events. Wrappers record in {@code finally}, so an
 * invocation that throws is still counted. Nested steps (a caching evaluator
 * decorating another evaluator) each record their own wall time — the outer
 * total includes the inner's.
 *
 * <p><b>Stateful ⇒ per-engine:</b> a step instance belongs to exactly one
 * engine and, like the context and {@code Randomizer}, is confined to that
 * engine's thread. Never share step objects across engines or islands.
 */
public abstract class AlgorithmStep {

    private long totalNanos;
    private long invocations;

    /**
     * Step abstracts only: records one invocation that began at
     * {@code startNanos} (a {@code System.nanoTime()} reading).
     */
    protected final void recordSince(long startNanos) {
        totalNanos += System.nanoTime() - startNanos;
        invocations++;
    }

    /** Total wall-clock nanoseconds spent inside this step so far. */
    public final long getTotalNanos() {
        return totalNanos;
    }

    /** Number of recorded invocations (aborted ones included, via finally). */
    public final long getInvocations() {
        return invocations;
    }
}
