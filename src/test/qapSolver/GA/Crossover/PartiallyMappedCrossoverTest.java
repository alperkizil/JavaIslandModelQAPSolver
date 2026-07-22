package qapSolver.GA.Crossover;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link PartiallyMappedCrossover}: the
 * recombination contract (exactly two children, fresh owned arrays, parents
 * untouched, valid permutations); a bit-exact replay against an independent
 * boxed transliteration of the Watchmaker reference algorithm on
 * independently derived same-seed streams, including final stream-position
 * agreement; structural PMX semantics via mirrored cut-point draws (segment
 * positions carry the other parent's values, conflict-free outside positions
 * keep the own parent's); the empty-segment clone case (probability 1/n);
 * chain-repair stress on rotation and reversal parents; same-seed
 * determinism vs cross-seed difference; the n = 1 edge; and step-timer
 * bookkeeping.
 *
 * Usage: PartiallyMappedCrossoverTest (no arguments; synthetic parents, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class PartiallyMappedCrossoverTest {

    private PartiallyMappedCrossoverTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        recombinationContract(failures);
        referenceReplay(failures);
        segmentSemantics(failures);
        emptySegmentClones(failures);
        chainRepairStress(failures);
        determinism(failures);
        sizeOneEdge(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void recombinationContract(List<String> failures) {
        int n = 12;
        int[] p1 = randomPermutation(n, new RandomSource(7L).derive(90));
        int[] p2 = randomPermutation(n, new RandomSource(7L).derive(91));
        EvaluatedCandidate parent1 = new EvaluatedCandidate(p1, 0L);
        EvaluatedCandidate parent2 = new EvaluatedCandidate(p2, 0L);
        int[] snap1 = p1.clone();
        int[] snap2 = p2.clone();

        PartiallyMappedCrossover crossover = new PartiallyMappedCrossover();
        for (long seed : new long[] {1L, 2L, 3L}) {
            List<Candidate> children = crossover.recombine(parent1, parent2, context(seed, 0, n));
            check(failures, children.size() == 2,
                    "contract: expected 2 children, got " + children.size());
            int[] c1 = children.get(0).getPermutation();
            int[] c2 = children.get(1).getPermutation();
            check(failures, c1 != p1 && c1 != p2 && c2 != p1 && c2 != p2,
                    "contract: child shares a parent's array");
            check(failures, c1 != c2, "contract: children share one array");
            check(failures, c1.length == n && c2.length == n, "contract: child length != n");
            check(failures, validPermutation(c1) && validPermutation(c2),
                    "contract: child is not a valid permutation (seed " + seed + ")");
        }
        check(failures, Arrays.equals(p1, snap1) && Arrays.equals(p2, snap2),
                "contract: a parent permutation was modified");
    }

    /** Bit-exact replay against an independent transliteration of the reference. */
    private static void referenceReplay(List<String> failures) {
        PartiallyMappedCrossover crossover = new PartiallyMappedCrossover();
        for (long seed : new long[] {11L, 42L, 12345L}) {
            for (int n : new int[] {2, 3, 7, 26, 60}) {
                int[] p1 = randomPermutation(n, new RandomSource(seed).derive(90));
                int[] p2 = randomPermutation(n, new RandomSource(seed).derive(91));
                AlgorithmContext ctx = context(seed, 3, n);
                List<Candidate> children = crossover.recombine(
                        new EvaluatedCandidate(p1.clone(), 0L), new EvaluatedCandidate(p2.clone(), 0L), ctx);

                Randomizer replay = new RandomSource(seed).derive(3);
                int[][] expected = watchmakerReference(p1, p2, replay);
                check(failures, Arrays.equals(children.get(0).getPermutation(), expected[0]),
                        "replay: child 1 diverges from the reference (seed " + seed + ", n " + n + ")");
                check(failures, Arrays.equals(children.get(1).getPermutation(), expected[1]),
                        "replay: child 2 diverges from the reference (seed " + seed + ", n " + n + ")");
                check(failures, ctx.getRandomizer().nextLong() == replay.nextLong(),
                        "replay: stream positions diverged — operator drew extra randomness"
                                + " (seed " + seed + ", n " + n + ")");
            }
        }
    }

    /** Structural PMX properties, cut points re-derived on a mirrored stream. */
    private static void segmentSemantics(List<String> failures) {
        int n = 15;
        PartiallyMappedCrossover crossover = new PartiallyMappedCrossover();
        for (long seed = 0; seed < 50; seed++) {
            int[] p1 = randomPermutation(n, new RandomSource(seed).derive(90));
            int[] p2 = randomPermutation(n, new RandomSource(seed).derive(91));
            List<Candidate> children = crossover.recombine(
                    new EvaluatedCandidate(p1.clone(), 0L), new EvaluatedCandidate(p2.clone(), 0L),
                    context(seed, 5, n));
            int[] c1 = children.get(0).getPermutation();
            int[] c2 = children.get(1).getPermutation();

            Randomizer mirror = new RandomSource(seed).derive(5);
            int point1 = mirror.nextInt(n);
            int point2 = mirror.nextInt(n);
            int length = point2 - point1;
            if (length < 0) {
                length += n;
            }
            boolean[] inSegment = new boolean[n];
            boolean[] segmentValue1 = new boolean[n]; // parent1 values inside the segment
            boolean[] segmentValue2 = new boolean[n]; // parent2 values inside the segment
            for (int i = 0; i < length; i++) {
                int index = (point1 + i) % n;
                inSegment[index] = true;
                segmentValue1[p1[index]] = true;
                segmentValue2[p2[index]] = true;
            }
            for (int i = 0; i < n; i++) {
                if (inSegment[i]) {
                    check(failures, c1[i] == p2[i] && c2[i] == p1[i],
                            "semantics: segment position " + i + " does not carry the other parent"
                                    + " (seed " + seed + ")");
                } else {
                    if (!segmentValue2[p1[i]]) {
                        check(failures, c1[i] == p1[i],
                                "semantics: conflict-free outside position " + i
                                        + " lost parent 1's value (seed " + seed + ")");
                    }
                    if (!segmentValue1[p2[i]]) {
                        check(failures, c2[i] == p2[i],
                                "semantics: conflict-free outside position " + i
                                        + " lost parent 2's value (seed " + seed + ")");
                    }
                }
            }
            check(failures, validPermutation(c1) && validPermutation(c2),
                    "semantics: invalid child (seed " + seed + ")");
        }
    }

    /** Equal cut draws (probability 1/n) must clone both parents exactly. */
    private static void emptySegmentClones(List<String> failures) {
        int n = 5;
        int[] p1 = randomPermutation(n, new RandomSource(4L).derive(90));
        int[] p2 = randomPermutation(n, new RandomSource(4L).derive(91));
        PartiallyMappedCrossover crossover = new PartiallyMappedCrossover();
        int degenerate = 0;
        for (long seed = 0; seed < 300; seed++) {
            Randomizer mirror = new RandomSource(seed).derive(5);
            if (mirror.nextInt(n) != mirror.nextInt(n)) {
                continue;
            }
            degenerate++;
            List<Candidate> children = crossover.recombine(
                    new EvaluatedCandidate(p1.clone(), 0L), new EvaluatedCandidate(p2.clone(), 0L),
                    context(seed, 5, n));
            check(failures, Arrays.equals(children.get(0).getPermutation(), p1)
                            && Arrays.equals(children.get(1).getPermutation(), p2),
                    "empty segment: children are not parent clones (seed " + seed + ")");
        }
        check(failures, degenerate >= 20,
                "empty segment: seed sweep hit only " + degenerate + " degenerate draws of ~60");
    }

    /** Rotation/reversal parents force maximal mapping chains; validity must hold. */
    private static void chainRepairStress(List<String> failures) {
        int n = 30;
        int[] identity = new int[n];
        int[] rotated = new int[n];
        int[] reversed = new int[n];
        for (int i = 0; i < n; i++) {
            identity[i] = i;
            rotated[i] = (i + 1) % n;
            reversed[i] = n - 1 - i;
        }
        PartiallyMappedCrossover crossover = new PartiallyMappedCrossover();
        for (int[] adversary : new int[][] {rotated, reversed}) {
            for (long seed = 0; seed < 20; seed++) {
                List<Candidate> children = crossover.recombine(
                        new EvaluatedCandidate(identity.clone(), 0L),
                        new EvaluatedCandidate(adversary.clone(), 0L), context(seed, 2, n));
                check(failures, validPermutation(children.get(0).getPermutation())
                                && validPermutation(children.get(1).getPermutation()),
                        "chain stress: invalid child (seed " + seed + ")");
            }
        }
    }

    private static void determinism(List<String> failures) {
        int n = 20;
        int[] p1 = randomPermutation(n, new RandomSource(7L).derive(90));
        int[] p2 = randomPermutation(n, new RandomSource(7L).derive(91));
        List<Candidate> a = new PartiallyMappedCrossover().recombine(
                new EvaluatedCandidate(p1.clone(), 0L), new EvaluatedCandidate(p2.clone(), 0L),
                context(9001L, 1, n));
        List<Candidate> b = new PartiallyMappedCrossover().recombine(
                new EvaluatedCandidate(p1.clone(), 0L), new EvaluatedCandidate(p2.clone(), 0L),
                context(9001L, 1, n));
        check(failures, Arrays.equals(a.get(0).getPermutation(), b.get(0).getPermutation())
                        && Arrays.equals(a.get(1).getPermutation(), b.get(1).getPermutation()),
                "determinism: same (seed, stream) produced different children");
        List<Candidate> c = new PartiallyMappedCrossover().recombine(
                new EvaluatedCandidate(p1.clone(), 0L), new EvaluatedCandidate(p2.clone(), 0L),
                context(9002L, 1, n));
        check(failures, !(Arrays.equals(a.get(0).getPermutation(), c.get(0).getPermutation())
                        && Arrays.equals(a.get(1).getPermutation(), c.get(1).getPermutation())),
                "determinism: different seeds produced identical children");
    }

    private static void sizeOneEdge(List<String> failures) {
        List<Candidate> children = new PartiallyMappedCrossover().recombine(
                new EvaluatedCandidate(new int[] {0}, 0L), new EvaluatedCandidate(new int[] {0}, 0L),
                context(1L, 0, 1));
        check(failures, children.size() == 2
                        && Arrays.equals(children.get(0).getPermutation(), new int[] {0})
                        && Arrays.equals(children.get(1).getPermutation(), new int[] {0}),
                "n=1: expected two [0] children");
    }

    private static void timerContract(List<String> failures) {
        int n = 8;
        int[] p1 = randomPermutation(n, new RandomSource(3L).derive(90));
        int[] p2 = randomPermutation(n, new RandomSource(3L).derive(91));
        PartiallyMappedCrossover crossover = new PartiallyMappedCrossover();
        check(failures, crossover.getInvocations() == 0, "timer: fresh step has invocations != 0");
        crossover.recombine(new EvaluatedCandidate(p1.clone(), 0L),
                new EvaluatedCandidate(p2.clone(), 0L), context(1L, 0, n));
        crossover.recombine(new EvaluatedCandidate(p1.clone(), 0L),
                new EvaluatedCandidate(p2.clone(), 0L), context(2L, 0, n));
        check(failures, crossover.getInvocations() == 2,
                "timer: after two pairs invocations = " + crossover.getInvocations());
        check(failures, crossover.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // ---- independent reference: boxed transliteration of Watchmaker's mate() ----

    private static int[][] watchmakerReference(int[] parent1, int[] parent2, Randomizer rng) {
        int size = parent1.length;
        List<Integer> offspring1 = boxed(parent1);
        List<Integer> offspring2 = boxed(parent2);

        int point1 = rng.nextInt(size);
        int point2 = rng.nextInt(size);
        int length = point2 - point1;
        if (length < 0) {
            length += size;
        }

        Map<Integer, Integer> mapping1 = new HashMap<>(length * 2);
        Map<Integer, Integer> mapping2 = new HashMap<>(length * 2);
        for (int i = 0; i < length; i++) {
            int index = (i + point1) % size;
            Integer item1 = offspring1.get(index);
            Integer item2 = offspring2.get(index);
            offspring1.set(index, item2);
            offspring2.set(index, item1);
            mapping1.put(item1, item2);
            mapping2.put(item2, item1);
        }

        checkUnmappedElements(offspring1, mapping2, point1, point2);
        checkUnmappedElements(offspring2, mapping1, point1, point2);
        return new int[][] {unboxed(offspring1), unboxed(offspring2)};
    }

    private static void checkUnmappedElements(List<Integer> offspring, Map<Integer, Integer> mapping,
            int mappingStart, int mappingEnd) {
        for (int i = 0; i < offspring.size(); i++) {
            if (!isInsideMappedRegion(i, mappingStart, mappingEnd)) {
                Integer mapped = offspring.get(i);
                while (mapping.containsKey(mapped)) {
                    mapped = mapping.get(mapped);
                }
                offspring.set(i, mapped);
            }
        }
    }

    private static boolean isInsideMappedRegion(int position, int startPoint, int endPoint) {
        boolean enclosed = position < endPoint && position >= startPoint;
        boolean wrapAround = startPoint > endPoint && (position >= startPoint || position < endPoint);
        return enclosed || wrapAround;
    }

    private static List<Integer> boxed(int[] values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int v : values) {
            list.add(v);
        }
        return list;
    }

    private static int[] unboxed(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    // ---- helpers ----

    private static int[] randomPermutation(int n, Randomizer random) {
        int[] permutation = new int[n];
        for (int i = 0; i < n; i++) {
            permutation[i] = i;
        }
        random.shuffle(permutation);
        return permutation;
    }

    private static boolean validPermutation(int[] p) {
        boolean[] seen = new boolean[p.length];
        for (int v : p) {
            if (v < 0 || v >= p.length || seen[v]) {
                return false;
            }
            seen[v] = true;
        }
        return true;
    }

    private static AlgorithmContext context(long masterSeed, int streamId, int n) {
        QAPInstance instance = new QAPInstance("synthetic" + n, new int[n][n], new int[n][n]);
        return new AlgorithmContext(instance, new RandomSource(masterSeed).derive(streamId));
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
