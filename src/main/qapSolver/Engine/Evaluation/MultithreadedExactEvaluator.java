package qapSolver.Engine.Evaluation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.FitnessEvaluator;
import qapSolver.Model.QAPInstance;
import qapSolver.Objective.ObjectiveFunction;

/**
 * Master–slave exact evaluation: the batch is partitioned into contiguous
 * chunks, a fixed pool of daemon workers computes raw {@code long} costs via
 * the pure static {@link ObjectiveFunction} over the immutable instance, and
 * the engine thread reassembles results in input order and performs
 * <em>all</em> context interaction (counting, array moves). Workers never
 * see the {@link AlgorithmContext} — its counters are thread-confined by
 * design, which is exactly why this class is a leaf and not a decorator
 * (see the package decision in HANDOFF).
 *
 * <p>Replay safety: costs are exact, results are index-ordered, and no
 * randomness is consumed, so a parallel run is bit-identical to a
 * sequential one regardless of worker scheduling — the contract's order
 * rule exists for this. Memory safety rides the executor's happens-before
 * edges: submission publishes the candidate arrays to workers,
 * {@code Future.get} publishes the costs back.
 *
 * <p>Worker failures surface loudly on the engine thread: a worker's
 * {@code RuntimeException} (e.g. a length mismatch from the objective) is
 * rethrown as-is; the evaluator stays usable afterwards. {@link #shutdown()}
 * stops the pool (idempotent; evaluating afterwards throws
 * {@code IllegalStateException}) — workers are daemon threads, so an
 * un-shut-down evaluator never blocks JVM exit.
 *
 * <p>Deployment note (HANDOFF): in the island model the parallelism budget
 * goes to islands first — this evaluator is for single-island runs on large
 * instances, not for stacking under parallel islands.
 */
public final class MultithreadedExactEvaluator extends FitnessEvaluator {

    private final int workerCount;
    private final ExecutorService pool;

    /**
     * @param workerCount fixed number of worker threads (≥ 1)
     * @throws IllegalArgumentException if {@code workerCount < 1}
     */
    public MultithreadedExactEvaluator(int workerCount) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be >= 1: " + workerCount);
        }
        this.workerCount = workerCount;
        AtomicInteger nextId = new AtomicInteger();
        this.pool = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable, "qap-eval-" + nextId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Stops the worker pool. Idempotent; the evaluator is unusable afterwards. */
    public void shutdown() {
        pool.shutdown();
    }

    @Override
    protected List<EvaluatedCandidate> doEvaluate(List<Candidate> candidates, AlgorithmContext context) {
        int size = candidates.size();
        List<EvaluatedCandidate> results = new ArrayList<>(size);
        if (size == 0) {
            return results;
        }
        QAPInstance instance = context.getInstance(); // read on the engine thread
        int chunks = Math.min(workerCount, size);
        List<Future<long[]>> futures = new ArrayList<>(chunks);
        for (int t = 0; t < chunks; t++) {
            int from = (int) ((long) size * t / chunks);
            int to = (int) ((long) size * (t + 1) / chunks);
            futures.add(submitChunk(instance, candidates, from, to));
        }
        int index = 0;
        for (int t = 0; t < chunks; t++) {
            long[] costs = join(futures.get(t));
            for (int k = 0; k < costs.length; k++) {
                context.countFullEvaluation();
                results.add(new EvaluatedCandidate(candidates.get(index).getPermutation(), costs[k]));
                index++;
            }
        }
        return results;
    }

    /** Pure cost computation for [from, to): reads immutable data only. */
    private Future<long[]> submitChunk(QAPInstance instance, List<Candidate> candidates,
            int from, int to) {
        try {
            return pool.submit(() -> {
                long[] costs = new long[to - from];
                for (int i = from; i < to; i++) {
                    costs[i - from] = ObjectiveFunction.evaluate(instance,
                            candidates.get(i).getPermutation());
                }
                return costs;
            });
        } catch (RejectedExecutionException e) {
            throw new IllegalStateException("evaluator is shut down", e);
        }
    }

    private static long[] join(Future<long[]> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for evaluation workers", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException("evaluation worker failed", cause);
        }
    }
}
