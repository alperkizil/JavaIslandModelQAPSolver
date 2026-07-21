package qapSolver.Random;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The single source of randomness for a solver run: holds the master seed and
 * derives independent {@link Randomizer} streams by id. Stream i is a pure
 * function of (masterSeed, i) — independent of derivation order, thread count,
 * and scheduling — so a run is replayed exactly by reusing the master seed.
 * This class is immutable and therefore thread-safe; the derived streams are
 * thread-confined (see {@link Randomizer}).
 *
 * <p>Derivation mirrors {@code SplittableRandom.split()}, keyed by index
 * instead of call order: stream i's state is {@code mix64} of the point
 * {@code masterSeed + (2i+1)·GOLDEN_GAMMA} and its gamma is {@code mixGamma}
 * of the next point. All derivation points are distinct across streams
 * (odd·odd multiples of an odd gamma never collide mod 2^64), so no two
 * streams share inputs.
 */
public final class RandomSource {

    private static final AtomicLong DEFAULT_SEEDS = new AtomicLong(System.nanoTime());

    private final long masterSeed;

    /** Source with an explicit master seed: the reproducible-run entry point. */
    public RandomSource(long masterSeed) {
        this.masterSeed = masterSeed;
    }

    /**
     * Source with a per-JVM unique seed for unseeded runs. Log
     * {@link #getMasterSeed()} so the run can still be replayed.
     */
    public RandomSource() {
        this(SplitMix64.mix64(DEFAULT_SEEDS.getAndAdd(SplitMix64.GOLDEN_GAMMA)));
    }

    /** The seed every stream of this source is derived from. */
    public long getMasterSeed() {
        return masterSeed;
    }

    /**
     * The stream for the given id (&ge; 0): same source seed and id, same
     * sequence — always, on any JDK. Callers assign ids by role (e.g. one per
     * island, a dedicated id for a migration coordinator).
     */
    public Randomizer derive(int streamId) {
        if (streamId < 0) {
            throw new IllegalArgumentException("streamId must be >= 0: " + streamId);
        }
        long point = masterSeed + (2L * streamId + 1) * SplitMix64.GOLDEN_GAMMA;
        long state = SplitMix64.mix64(point);
        long gamma = SplitMix64.mixGamma(point + SplitMix64.GOLDEN_GAMMA);
        return new Randomizer(state, gamma);
    }
}
