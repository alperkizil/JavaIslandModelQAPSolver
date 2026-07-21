package qapSolver.GA.Elitism;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;

/**
 * Plain main-class test harness for {@link BestKElitePreserver}: constructor
 * validation; extract contract (best-k by (fitness, first-index), reference
 * snapshots, best-first order, read-only population, k = 0 empty, k >= μ
 * throws); reinsert contract (present-by-reference and present-by-permutation-
 * content leave the population untouched, missing elites overwrite the worst
 * unprotected slots, size preserved); the all-tied eviction trap (protected
 * slots keep elite #1 from being evicted by elite #2); duplicate-genotype
 * collapse; the no-slot-left guard; and step-timer bookkeeping (both phases on
 * one timer).
 *
 * Usage: BestKElitePreserverTest (no arguments; synthetic members with
 * fabricated fitnesses, no dataset dependency). Exit code 0 = full pass,
 * 1 = any failure.
 */
public final class BestKElitePreserverTest {

    private BestKElitePreserverTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        constructorContract(failures);
        extractContract(failures);
        reinsertPresent(failures);
        reinsertMissing(failures);
        evictionTrap(failures);
        duplicateCollapse(failures);
        noSlotGuard(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void constructorContract(List<String> failures) {
        for (int bad : new int[] {-1, Integer.MIN_VALUE}) {
            boolean threw = false;
            try {
                new BestKElitePreserver(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(failures, threw, "constructor(" + bad + "): expected IllegalArgumentException");
        }
        new BestKElitePreserver(0); // off is legal
        new BestKElitePreserver(1);
    }

    private static void extractContract(List<String> failures) {
        AlgorithmContext ctx = context();
        // Fitnesses 50, 10, 30, 10, 70 — the 10s tie on indices 1 and 3.
        EvaluatedCandidate m0 = ec(50, 0, 1, 2, 3);
        EvaluatedCandidate m1 = ec(10, 1, 0, 2, 3);
        EvaluatedCandidate m2 = ec(30, 2, 1, 0, 3);
        EvaluatedCandidate m3 = ec(10, 3, 1, 2, 0);
        EvaluatedCandidate m4 = ec(70, 0, 2, 1, 3);
        Population pop = pop(m0, m1, m2, m3, m4);
        List<EvaluatedCandidate> before = snapshot(pop);

        List<EvaluatedCandidate> one = new BestKElitePreserver(1).extract(pop, ctx);
        check(failures, one.size() == 1 && one.get(0) == m1,
                "extract k=1: expected [m1] (first-index tie-break), got " + one);

        List<EvaluatedCandidate> three = new BestKElitePreserver(3).extract(pop, ctx);
        check(failures, three.size() == 3
                        && three.get(0) == m1 && three.get(1) == m3 && three.get(2) == m2,
                "extract k=3: expected [m1, m3, m2] best-first, got " + three);

        check(failures, new BestKElitePreserver(0).extract(pop, ctx).isEmpty(),
                "extract k=0: expected empty list");

        check(failures, Arrays.equals(before.toArray(), snapshot(pop).toArray()),
                "extract: population was modified");

        for (int badK : new int[] {5, 7}) { // k >= μ = 5
            boolean threw = false;
            try {
                new BestKElitePreserver(badK).extract(pop, ctx);
            } catch (IllegalStateException e) {
                threw = true;
            }
            check(failures, threw, "extract k=" + badK + " on μ=5: expected IllegalStateException");
        }
        new BestKElitePreserver(4).extract(pop, ctx); // k = μ − 1 is legal
    }

    /** Elites present by reference or by content: population left untouched. */
    private static void reinsertPresent(List<String> failures) {
        AlgorithmContext ctx = context();
        BestKElitePreserver preserver = new BestKElitePreserver(2);

        // By reference: the elite objects themselves survived replacement.
        EvaluatedCandidate e1 = ec(5, 0, 1, 2, 3);
        EvaluatedCandidate e2 = ec(8, 1, 0, 2, 3);
        Population byRef = pop(ec(40, 2, 1, 0, 3), e1, e2, ec(60, 3, 1, 2, 0));
        List<EvaluatedCandidate> before = snapshot(byRef);
        preserver.reinsert(byRef, Arrays.asList(e1, e2), ctx);
        check(failures, Arrays.equals(before.toArray(), snapshot(byRef).toArray()),
                "reinsert by-reference-present: population changed");

        // By content: same permutations as new objects, different fitness
        // objects impossible — same permutation implies same true fitness, so
        // fabricate equal-fitness copies; presence must be judged on content.
        Population byContent = pop(ec(40, 2, 1, 0, 3), ec(5, 0, 1, 2, 3),
                ec(8, 1, 0, 2, 3), ec(60, 3, 1, 2, 0));
        before = snapshot(byContent);
        preserver.reinsert(byContent, Arrays.asList(e1, e2), ctx);
        check(failures, Arrays.equals(before.toArray(), snapshot(byContent).toArray()),
                "reinsert by-content-present: population changed");

        // Empty elites (k = 0 path): no-op.
        before = snapshot(byContent);
        preserver.reinsert(byContent, Collections.emptyList(), ctx);
        check(failures, Arrays.equals(before.toArray(), snapshot(byContent).toArray()),
                "reinsert empty elites: population changed");
    }

    /** Missing elites overwrite the worst slots, worst first; size preserved. */
    private static void reinsertMissing(List<String> failures) {
        AlgorithmContext ctx = context();
        EvaluatedCandidate e1 = ec(5, 0, 1, 2, 3);
        EvaluatedCandidate e2 = ec(8, 1, 0, 2, 3);
        EvaluatedCandidate s0 = ec(20, 2, 1, 0, 3);
        EvaluatedCandidate s1 = ec(40, 3, 1, 2, 0);
        EvaluatedCandidate s2 = ec(30, 0, 2, 1, 3);
        Population pop = pop(s0, s1, s2);

        new BestKElitePreserver(2).reinsert(pop, Arrays.asList(e1, e2), ctx);
        check(failures, pop.size() == 3, "reinsert missing: size changed to " + pop.size());
        check(failures, pop.get(0) == s0, "reinsert missing: slot 0 (fitness 20) was touched");
        check(failures, pop.get(1) == e1, "reinsert missing: e1 not at the worst slot (40)");
        check(failures, pop.get(2) == e2, "reinsert missing: e2 not at the next-worst slot (30)");
    }

    /**
     * The all-tied trap: with every fitness equal, the plain worst-index
     * tie-break lands on the same slot twice — protection must keep elite #2
     * from evicting the freshly reinserted elite #1, and must keep a found
     * elite's slot from being overwritten.
     */
    private static void evictionTrap(List<String> failures) {
        AlgorithmContext ctx = context();
        BestKElitePreserver preserver = new BestKElitePreserver(2);

        // Both elites missing, all fitness 100.
        EvaluatedCandidate e1 = ec(100, 0, 1, 2, 3);
        EvaluatedCandidate e2 = ec(100, 1, 0, 2, 3);
        Population pop = pop(ec(100, 2, 1, 0, 3), ec(100, 3, 1, 2, 0),
                ec(100, 0, 2, 1, 3), ec(100, 1, 2, 3, 0));
        preserver.reinsert(pop, Arrays.asList(e1, e2), ctx);
        check(failures, contains(pop, e1), "eviction trap: elite #1 was evicted by elite #2");
        check(failures, contains(pop, e2), "eviction trap: elite #2 missing");
        check(failures, pop.size() == 4, "eviction trap: size changed");

        // Elite #1 already present at the tie-break slot, elite #2 missing.
        Population found = pop(e1, ec(100, 3, 1, 2, 0), ec(100, 0, 2, 1, 3),
                ec(100, 1, 2, 3, 0));
        preserver.reinsert(found, Arrays.asList(e1, e2), ctx);
        check(failures, found.get(0) == e1,
                "eviction trap: found elite's slot was overwritten");
        check(failures, contains(found, e2), "eviction trap: elite #2 not reinserted");
    }

    /** Elites sharing one permutation survive once, not once per copy. */
    private static void duplicateCollapse(List<String> failures) {
        AlgorithmContext ctx = context();
        EvaluatedCandidate e1 = ec(5, 0, 1, 2, 3);
        EvaluatedCandidate e2 = ec(5, 0, 1, 2, 3); // same permutation, distinct object
        EvaluatedCandidate s0 = ec(20, 2, 1, 0, 3);
        EvaluatedCandidate s1 = ec(40, 3, 1, 2, 0);
        EvaluatedCandidate s2 = ec(30, 0, 2, 1, 3);
        Population pop = pop(s0, s1, s2);

        new BestKElitePreserver(2).reinsert(pop, Arrays.asList(e1, e2), ctx);
        int copies = 0;
        for (int i = 0; i < pop.size(); i++) {
            if (pop.get(i).samePermutationAs(e1)) {
                copies++;
            }
        }
        check(failures, copies == 1, "duplicate collapse: genotype present " + copies + " times");
        check(failures, pop.get(1) == e1, "duplicate collapse: e1 not at the worst slot");
        check(failures, pop.get(2) == s2, "duplicate collapse: e2 overwrote a second slot");
    }

    /** Direct misuse: more missing elites than slots fails loud, not silent. */
    private static void noSlotGuard(List<String> failures) {
        AlgorithmContext ctx = context();
        Population pop = pop(ec(20, 2, 1, 0, 3), ec(40, 3, 1, 2, 0));
        List<EvaluatedCandidate> three = Arrays.asList(
                ec(1, 0, 1, 2, 3), ec(2, 1, 0, 2, 3), ec(3, 0, 2, 1, 3));
        boolean threw = false;
        try {
            new BestKElitePreserver(2).reinsert(pop, three, ctx);
        } catch (IllegalStateException e) {
            threw = true;
        }
        check(failures, threw, "no-slot guard: expected IllegalStateException");
    }

    /** Both phases record on the one step timer: two invocations per generation. */
    private static void timerContract(List<String> failures) {
        AlgorithmContext ctx = context();
        BestKElitePreserver preserver = new BestKElitePreserver(1);
        check(failures, preserver.getInvocations() == 0, "timer: fresh step has invocations != 0");
        Population pop = pop(ec(20, 2, 1, 0, 3), ec(40, 3, 1, 2, 0));
        List<EvaluatedCandidate> elites = preserver.extract(pop, ctx);
        check(failures, preserver.getInvocations() == 1,
                "timer: after extract invocations = " + preserver.getInvocations());
        preserver.reinsert(pop, elites, ctx);
        check(failures, preserver.getInvocations() == 2,
                "timer: after reinsert invocations = " + preserver.getInvocations());
        check(failures, preserver.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    private static boolean contains(Population pop, EvaluatedCandidate candidate) {
        for (int i = 0; i < pop.size(); i++) {
            if (pop.get(i) == candidate) {
                return true;
            }
        }
        return false;
    }

    private static EvaluatedCandidate ec(long fitness, int... permutation) {
        return new EvaluatedCandidate(permutation, fitness);
    }

    private static Population pop(EvaluatedCandidate... members) {
        return new Population(Arrays.asList(members));
    }

    private static List<EvaluatedCandidate> snapshot(Population pop) {
        List<EvaluatedCandidate> out = new ArrayList<>(pop.size());
        for (int i = 0; i < pop.size(); i++) {
            out.add(pop.get(i));
        }
        return out;
    }

    private static AlgorithmContext context() {
        QAPInstance instance = new QAPInstance("synthetic4", new int[4][4], new int[4][4]);
        return new AlgorithmContext(instance, new RandomSource(1L).derive(0));
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
