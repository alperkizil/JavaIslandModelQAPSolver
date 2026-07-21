package qapSolver.Random;

/**
 * One deterministic pseudorandom stream (SplitMix64: state advances by a
 * per-stream odd gamma, {@code mix64} avalanches it into output). Obtained
 * from {@link RandomSource#derive(int)}; the full sequence is a pure function
 * of (master seed, stream id).
 *
 * <p><b>Deliberately NOT thread-safe.</b> Each instance must be confined to a
 * single thread (one island, one worker). That confinement — not locking — is
 * what makes multi-threaded runs reproducible: no shared mutable state means
 * no dependence on scheduling, and no contention in hot loops.
 */
public final class Randomizer {

    private long state;
    private final long gamma; // odd by construction (SplitMix64.mixGamma)

    Randomizer(long state, long gamma) {
        this.state = state;
        this.gamma = gamma;
    }

    /** Next 64 uniformly distributed pseudorandom bits — the native primitive. */
    public long nextLong() {
        state += gamma;
        return SplitMix64.mix64(state);
    }

    /** Uniform int in [0, bound); bound must be positive. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        int r = next31();
        int m = bound - 1;
        if ((bound & m) == 0) { // power of two: scale the top bits, no bias possible
            return (int) ((bound * (long) r) >>> 31);
        }
        // Reject draws from the final incomplete bucket of 2^31 (detected via
        // int overflow of the bucket base + m) so every value is exactly uniform.
        for (int u = r; u - (r = u % bound) + m < 0; u = next31()) {
        }
        return r;
    }

    /** Uniform double in [0, 1) with 53 significant bits. */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /**
     * In-place Fisher–Yates shuffle: uniform over all length! orderings,
     * allocation-free. Shuffling a permutation yields a uniform random
     * permutation.
     */
    public void shuffle(int[] array) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            int tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
    }

    /** Top 31 bits of the next output (all output bits are avalanche-quality). */
    private int next31() {
        return (int) (nextLong() >>> 33);
    }
}
