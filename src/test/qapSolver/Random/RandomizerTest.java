package qapSolver.Random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;

/**
 * Plain main-class test harness for {@link RandomSource}/{@link Randomizer}:
 * bit-exact SplitMix64 reference vectors (from Vigna's splitmix64.c, values
 * independently cross-checked against java.util.SplittableRandom), stream
 * derivation properties (order-independence, distinctness, gamma
 * conditioning), primitive contracts (nextInt/nextDouble ranges and rough
 * uniformity), Fisher–Yates shuffle behavior, and — the headline claim —
 * identical sequences whether streams run sequentially or on 8 concurrent
 * threads.
 *
 * Usage: RandomizerTest (no arguments, no dataset dependency).
 * Exit code 0 = full pass, 1 = any failure.
 */
public final class RandomizerTest {

    /** {seed, first four outputs} of the SplitMix64 reference implementation. */
    private static final long[][] VECTORS = {
            {0x0000000000000000L, 0xE220A8397B1DCDAFL, 0x6E789E6AA1B965F4L, 0x06C45D188009454FL, 0xF88BB8A8724C81ECL},
            {0x0000000000000001L, 0x910A2DEC89025CC1L, 0xBEEB8DA1658EEC67L, 0xF893A2EEFB32555EL, 0x71C18690EE42C90BL},
            {0x000000000000002AL, 0xBDD732262FEB6E95L, 0x28EFE333B266F103L, 0x47526757130F9F52L, 0x581CE1FF0E4AE394L},
            {0x9E3779B97F4A7C15L, 0x6E789E6AA1B965F4L, 0x06C45D188009454FL, 0xF88BB8A8724C81ECL, 0x1B39896A51A8749BL},
            {0xFFFFFFFFFFFFFFFFL, 0xE4D971771B652C20L, 0xE99FF867DBF682C9L, 0x382FF84CB27281E9L, 0x6D1DB36CCBA982D2L},
    };

    /**
     * Goldens for this project's derivation spec: first three outputs of
     * streams {0, 1, 2, 7} under master seed 42, computed independently from
     * the documented derivation formula (a Python transliteration, not this
     * Java code). Freezes the derivation so published seeds stay replayable
     * across refactors.
     */
    private static final long[][] DERIVED_STREAMS_SEED_42 = {
            {0L, 0x97C372BE01959835L, 0x4B16E43727C1D26CL, 0x1043C9A4AB8B3C49L},
            {1L, 0x31697C586280C6ADL, 0x9B1820D6E351BDB4L, 0x9731E945243EF146L},
            {2L, 0x950D05035AC16587L, 0xB7C6BCD0F1AB3967L, 0x65E4CC9D19E205C6L},
            {7L, 0xDAEC53CE7AED5E76L, 0x34F1E5D2A86BF707L, 0x77E2AA7350961F48L},
    };

    private RandomizerTest() {
    }

    public static void main(String[] args) throws InterruptedException {
        List<String> failures = new ArrayList<>();

        referenceVectors(failures);
        derivationGoldens(failures);
        derivationProperties(failures);
        nextIntContract(failures);
        nextDoubleContract(failures);
        shuffleContract(failures);
        threadSchedulingIndependence(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    /** A raw GOLDEN_GAMMA stream must reproduce the published algorithm bit-for-bit. */
    private static void referenceVectors(List<String> failures) {
        for (long[] row : VECTORS) {
            Randomizer r = new Randomizer(row[0], SplitMix64.GOLDEN_GAMMA);
            for (int i = 1; i < row.length; i++) {
                long got = r.nextLong();
                check(failures, got == row[i], String.format(
                        "vectors: seed 0x%016X output %d expected 0x%016X got 0x%016X",
                        row[0], i, row[i], got));
            }
        }
    }

    /** Derived streams must match the spec's independent transliteration bit-for-bit. */
    private static void derivationGoldens(List<String> failures) {
        RandomSource src = new RandomSource(42L);
        for (long[] row : DERIVED_STREAMS_SEED_42) {
            Randomizer r = src.derive((int) row[0]);
            for (int i = 1; i < row.length; i++) {
                long got = r.nextLong();
                check(failures, got == row[i], String.format(
                        "derivation goldens: stream %d output %d expected 0x%016X got 0x%016X",
                        row[0], i, row[i], got));
            }
        }
    }

    private static void derivationProperties(List<String> failures) {
        // Same (masterSeed, streamId) => same sequence, from the same or another source.
        RandomSource a = new RandomSource(4242L);
        RandomSource b = new RandomSource(4242L);
        check(failures, Arrays.equals(draw(a.derive(7), 100), draw(a.derive(7), 100)),
                "derive: same source, same id, sequences differ");
        check(failures, Arrays.equals(draw(a.derive(7), 100), draw(b.derive(7), 100)),
                "derive: equal-seed sources, same id, sequences differ");

        // Index-addressable: a stream is unaffected by which streams were derived
        // or drawn from before it.
        RandomSource s1 = new RandomSource(777L);
        Randomizer noise = s1.derive(0);
        draw(noise, 5);
        draw(s1.derive(2), 3);
        long[] afterTraffic = draw(s1.derive(1), 100);
        long[] fresh = draw(new RandomSource(777L).derive(1), 100);
        check(failures, Arrays.equals(afterTraffic, fresh),
                "derive: stream 1 depends on unrelated derivation/draw order");

        // Distinct ids and distinct seeds give distinct sequences.
        check(failures, !Arrays.equals(draw(a.derive(0), 100), draw(a.derive(1), 100)),
                "derive: ids 0 and 1 produced identical sequences");
        check(failures, !Arrays.equals(draw(new RandomSource(1L).derive(0), 100),
                        draw(new RandomSource(2L).derive(0), 100)),
                "derive: seeds 1 and 2 produced identical stream-0 sequences");

        // Gamma conditioning checked at its source, plus cross-stream distinctness.
        for (long seed : new long[] {42L, 0L, -1L}) {
            RandomSource src = new RandomSource(seed);
            Set<Long> firstOutputs = new HashSet<>();
            for (int id = 0; id < 1000; id++) {
                long point = seed + (2L * id + 1) * SplitMix64.GOLDEN_GAMMA;
                long gamma = SplitMix64.mixGamma(point + SplitMix64.GOLDEN_GAMMA);
                check(failures, (gamma & 1L) == 1L,
                        "mixGamma: even gamma for seed " + seed + " id " + id);
                check(failures, Long.bitCount(gamma ^ (gamma >>> 1)) >= 24,
                        "mixGamma: weak gamma (<24 transitions) for seed " + seed + " id " + id);
                firstOutputs.add(src.derive(id).nextLong());
            }
            check(failures, firstOutputs.size() == 1000,
                    "derive: first-output collision among 1000 streams, seed " + seed);
        }

        boolean threw = false;
        try {
            a.derive(-1);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(failures, threw, "derive(-1): expected IllegalArgumentException");

        // Unseeded sources: unique per construction, replayable via getMasterSeed().
        RandomSource u1 = new RandomSource();
        RandomSource u2 = new RandomSource();
        check(failures, u1.getMasterSeed() != u2.getMasterSeed(),
                "no-arg source: two constructions share a master seed");
        check(failures, Arrays.equals(draw(u1.derive(0), 50),
                        draw(new RandomSource(u1.getMasterSeed()).derive(0), 50)),
                "no-arg source: getMasterSeed() does not replay the run");
    }

    private static void nextIntContract(List<String> failures) {
        Randomizer r = new RandomSource(9001L).derive(0);

        for (int bad : new int[] {0, -5, Integer.MIN_VALUE}) {
            boolean threw = false;
            try {
                r.nextInt(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "nextInt(" + bad + "): expected IllegalArgumentException");
        }

        for (int i = 0; i < 1000; i++) {
            check(failures, r.nextInt(1) == 0, "nextInt(1): nonzero");
        }

        for (int bound : new int[] {2, 3, 7, 100, 1 << 30, Integer.MAX_VALUE}) {
            for (int i = 0; i < 10_000; i++) {
                int v = r.nextInt(bound);
                if (v < 0 || v >= bound) {
                    check(failures, false, "nextInt(" + bound + "): out of range value " + v);
                    break;
                }
            }
        }

        // Rough uniformity, one non-power-of-two and one power-of-two bound.
        checkBuckets(failures, r, 10, 200_000, "nextInt(10)");
        checkBuckets(failures, r, 16, 160_000, "nextInt(16)");
    }

    private static void checkBuckets(List<String> failures, Randomizer r, int bound, int draws, String label) {
        int[] counts = new int[bound];
        for (int i = 0; i < draws; i++) {
            counts[r.nextInt(bound)]++;
        }
        int expected = draws / bound;
        for (int v = 0; v < bound; v++) {
            check(failures, Math.abs(counts[v] - expected) <= 1500,
                    label + ": bucket " + v + " count " + counts[v] + " far from " + expected);
        }
    }

    private static void nextDoubleContract(List<String> failures) {
        Randomizer r = new RandomSource(555L).derive(0);
        double sum = 0, min = 1, max = 0;
        for (int i = 0; i < 200_000; i++) {
            double d = r.nextDouble();
            if (d < 0.0 || d >= 1.0) {
                check(failures, false, "nextDouble: out of [0,1): " + d);
                return;
            }
            sum += d;
            min = Math.min(min, d);
            max = Math.max(max, d);
        }
        double mean = sum / 200_000;
        check(failures, Math.abs(mean - 0.5) < 0.01, "nextDouble: mean " + mean + " far from 0.5");
        check(failures, min < 0.01 && max > 0.99, "nextDouble: range not covered, min=" + min + " max=" + max);
    }

    private static void shuffleContract(List<String> failures) {
        Randomizer r = new RandomSource(31337L).derive(0);

        r.shuffle(new int[0]);
        int[] single = {9};
        r.shuffle(single);
        check(failures, single[0] == 9, "shuffle: length-1 array changed");

        // Multiset preserved, including duplicates.
        int[] multi = {5, 5, 1, 2, 2, 2, 9};
        int[] multiSorted = multi.clone();
        Arrays.sort(multiSorted);
        r.shuffle(multi);
        int[] shuffledSorted = multi.clone();
        Arrays.sort(shuffledSorted);
        check(failures, Arrays.equals(multiSorted, shuffledSorted), "shuffle: multiset not preserved");

        // Shuffling a permutation yields a permutation.
        int[] perm = identity(50);
        r.shuffle(perm);
        boolean[] seen = new boolean[50];
        for (int v : perm) {
            if (v < 0 || v >= 50 || seen[v]) {
                check(failures, false, "shuffle: result is not a permutation");
                break;
            }
            seen[v] = true;
        }

        // Deterministic under a fixed stream; consecutive shuffles differ.
        int[] p1 = identity(20);
        new RandomSource(1L).derive(3).shuffle(p1);
        int[] p2 = identity(20);
        new RandomSource(1L).derive(3).shuffle(p2);
        check(failures, Arrays.equals(p1, p2), "shuffle: not deterministic for a fixed stream");
        int[] p3 = identity(20);
        Randomizer cont = new RandomSource(1L).derive(3);
        cont.shuffle(p3);
        int[] p4 = identity(20);
        cont.shuffle(p4);
        check(failures, !Arrays.equals(p3, p4), "shuffle: stream repeated an ordering back-to-back");

        // Uniformity over all 3! = 6 orderings.
        Map<String, Integer> orderings = new HashMap<>();
        for (int i = 0; i < 6000; i++) {
            int[] p = identity(3);
            r.shuffle(p);
            orderings.merge(Arrays.toString(p), 1, Integer::sum);
        }
        check(failures, orderings.size() == 6, "shuffle: saw " + orderings.size() + " of 6 orderings");
        for (Map.Entry<String, Integer> e : orderings.entrySet()) {
            check(failures, Math.abs(e.getValue() - 1000) <= 150,
                    "shuffle: ordering " + e.getKey() + " count " + e.getValue() + " far from 1000");
        }
    }

    /**
     * The headline claim: per-stream sequences are identical whether the
     * streams are drawn sequentially or concurrently from 8 threads racing on
     * a shared RandomSource.
     */
    private static void threadSchedulingIndependence(List<String> failures) throws InterruptedException {
        final int threads = 8;
        final int draws = 50_000;
        final long seed = 20260721L;

        long[][] expected = new long[threads][];
        RandomSource sequential = new RandomSource(seed);
        for (int id = 0; id < threads; id++) {
            expected[id] = draw(sequential.derive(id), draws);
        }

        RandomSource shared = new RandomSource(seed);
        long[][] actual = new long[threads][];
        Throwable[] errors = new Throwable[threads];
        CyclicBarrier start = new CyclicBarrier(threads);
        List<Thread> pool = new ArrayList<>();
        for (int id = 0; id < threads; id++) {
            final int streamId = id;
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    actual[streamId] = draw(shared.derive(streamId), draws);
                } catch (Throwable e) {
                    errors[streamId] = e;
                }
            });
            pool.add(t);
            t.start();
        }
        for (Thread t : pool) {
            t.join();
        }

        for (int id = 0; id < threads; id++) {
            check(failures, errors[id] == null, "threads: stream " + id + " threw " + errors[id]);
            check(failures, errors[id] == null && Arrays.equals(expected[id], actual[id]),
                    "threads: stream " + id + " sequence differs from sequential run");
        }
    }

    private static long[] draw(Randomizer r, int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = r.nextLong();
        }
        return out;
    }

    private static int[] identity(int n) {
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }
        return p;
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
