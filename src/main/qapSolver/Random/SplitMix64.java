package qapSolver.Random;

/**
 * The SplitMix64 algorithm's pure math, shared by {@link RandomSource}
 * (stream derivation) and {@link Randomizer} (generation). Hand-rolled rather
 * than delegated to the JDK so the emitted sequences are bit-identical on
 * every JDK, forever — {@code java.util.SplittableRandom}'s sequence is an
 * implementation detail, not a spec guarantee.
 *
 * Algorithm: Steele, Lea &amp; Flood, "Fast Splittable Pseudorandom Number
 * Generators" (OOPSLA 2014); reference implementation splitmix64.c by
 * Sebastiano Vigna. Verified against the reference's known output vectors
 * in RandomizerTest.
 */
final class SplitMix64 {

    /** 2^64 divided by the golden ratio, rounded to odd: the canonical state increment. */
    static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private SplitMix64() {
    }

    /** Stafford "variant 13" 64-bit finalizer: avalanches a state value into an output. */
    static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Conditions a raw value into a usable per-stream increment (gamma):
     * MurmurHash3 finalizer, forced odd, and forced to have at least 24 bit
     * transitions — the known weak-gamma guard from the OOPSLA 2014 paper.
     */
    static long mixGamma(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        z = (z ^ (z >>> 33)) | 1L;
        return Long.bitCount(z ^ (z >>> 1)) < 24 ? z ^ 0xAAAAAAAAAAAAAAAAL : z;
    }
}
