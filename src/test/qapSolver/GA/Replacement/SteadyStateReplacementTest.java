package qapSolver.GA.Replacement;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.Population;
import qapSolver.Model.QAPInstance;
import qapSolver.Random.RandomSource;
import qapSolver.Random.Randomizer;

/**
 * Plain main-class test harness for {@link SteadyStateReplacement}: in-place
 * replace-worst (same Population object back, worst slot swapped, all other
 * slots untouched); unconditional acceptance (a child worse than everyone
 * still evicts the worst); sequential birth-event semantics (later children
 * evict earlier ones; insertion order changes the outcome); first-index tie
 * break on the worst; λ > μ churn against a hand-traced expected state; μ
 * preserved everywhere; no randomness consumed; and step-timer bookkeeping.
 *
 * Usage: SteadyStateReplacementTest (no arguments; synthetic members, no
 * dataset dependency). Exit code 0 = full pass, 1 = any failure.
 */
public final class SteadyStateReplacementTest {

    private SteadyStateReplacementTest() {
    }

    public static void main(String[] args) {
        List<String> failures = new ArrayList<>();

        inPlaceReplaceWorst(failures);
        unconditionalAcceptance(failures);
        sequentialBirths(failures);
        insertionOrderMatters(failures);
        worstTieFirstIndex(failures);
        churnBeyondMu(failures);
        noRandomnessConsumed(failures);
        timerContract(failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void inPlaceReplaceWorst(List<String> failures) {
        Population pop = population(10, 30, 20);
        EvaluatedCandidate keep0 = pop.get(0);
        EvaluatedCandidate keep2 = pop.get(2);
        EvaluatedCandidate child = member(15);
        List<EvaluatedCandidate> offspring = new ArrayList<>();
        offspring.add(child);

        Population returned = new SteadyStateReplacement().replace(pop, offspring, context(1L));
        check(failures, returned == pop, "in-place: must return the same Population object");
        check(failures, pop.size() == 3, "in-place: μ changed to " + pop.size());
        check(failures, pop.get(1) == child, "in-place: worst slot (index 1) not replaced");
        check(failures, pop.get(0) == keep0 && pop.get(2) == keep2,
                "in-place: a non-worst slot was touched");
    }

    private static void unconditionalAcceptance(List<String> failures) {
        Population pop = population(10, 20, 30);
        EvaluatedCandidate awful = member(99);
        List<EvaluatedCandidate> offspring = new ArrayList<>();
        offspring.add(awful);
        new SteadyStateReplacement().replace(pop, offspring, context(2L));
        check(failures, pop.get(2) == awful,
                "unconditional: a child worse than everyone must still evict the worst");
    }

    private static void sequentialBirths(List<String> failures) {
        Population pop = population(10, 20, 30);
        EvaluatedCandidate keep0 = pop.get(0);
        EvaluatedCandidate keep1 = pop.get(1);
        EvaluatedCandidate c99 = member(99);
        EvaluatedCandidate c50 = member(50);
        List<EvaluatedCandidate> offspring = new ArrayList<>();
        offspring.add(c99);
        offspring.add(c50);
        new SteadyStateReplacement().replace(pop, offspring, context(3L));
        // c99 evicts 30; c50 then evicts c99 (the current worst).
        check(failures, pop.get(0) == keep0 && pop.get(1) == keep1 && pop.get(2) == c50,
                "sequential: expected {10, 20, c50} — later child must evict the earlier one");
    }

    private static void insertionOrderMatters(List<String> failures) {
        Population pop = population(10, 20, 30);
        EvaluatedCandidate c50 = member(50);
        EvaluatedCandidate c99 = member(99);
        List<EvaluatedCandidate> offspring = new ArrayList<>();
        offspring.add(c50);
        offspring.add(c99);
        new SteadyStateReplacement().replace(pop, offspring, context(4L));
        // c50 evicts 30; c99 then evicts c50 — reversed order, different survivor.
        check(failures, pop.get(2) == c99,
                "order: expected c99 to survive when inserted last (got a different slot 2)");
    }

    private static void worstTieFirstIndex(List<String> failures) {
        Population pop = population(30, 10, 30);
        EvaluatedCandidate tied2 = pop.get(2);
        EvaluatedCandidate child = member(5);
        List<EvaluatedCandidate> offspring = new ArrayList<>();
        offspring.add(child);
        new SteadyStateReplacement().replace(pop, offspring, context(5L));
        check(failures, pop.get(0) == child && pop.get(2) == tied2,
                "tie: worst tie must break to the first index");
    }

    private static void churnBeyondMu(List<String> failures) {
        // μ=2, λ=4 — hand-traced: {10,20}→{10,15}→{10,12}→{10,18}→{10,11}.
        Population pop = population(10, 20);
        EvaluatedCandidate keep = pop.get(0);
        EvaluatedCandidate c15 = member(15);
        EvaluatedCandidate c12 = member(12);
        EvaluatedCandidate c18 = member(18);
        EvaluatedCandidate c11 = member(11);
        List<EvaluatedCandidate> offspring = new ArrayList<>();
        offspring.add(c15);
        offspring.add(c12);
        offspring.add(c18);
        offspring.add(c11);
        new SteadyStateReplacement().replace(pop, offspring, context(6L));
        check(failures, pop.size() == 2, "churn: μ changed to " + pop.size());
        check(failures, pop.get(0) == keep && pop.get(1) == c11,
                "churn: expected {10, c11} after the traced eviction sequence");
    }

    private static void noRandomnessConsumed(List<String> failures) {
        AlgorithmContext ctx = context(7L);
        Population pop = population(10, 20);
        List<EvaluatedCandidate> offspring = new ArrayList<>();
        offspring.add(member(5));
        new SteadyStateReplacement().replace(pop, offspring, ctx);
        Randomizer untouched = new RandomSource(7L).derive(0);
        check(failures, ctx.getRandomizer().nextLong() == untouched.nextLong(),
                "randomness: replacement consumed draws from the context stream");
    }

    private static void timerContract(List<String> failures) {
        SteadyStateReplacement replacement = new SteadyStateReplacement();
        check(failures, replacement.getInvocations() == 0, "timer: fresh step has invocations != 0");
        for (long seed = 8; seed <= 9; seed++) {
            Population pop = population(10, 20);
            List<EvaluatedCandidate> offspring = new ArrayList<>();
            offspring.add(member(5));
            replacement.replace(pop, offspring, context(seed));
        }
        check(failures, replacement.getInvocations() == 2,
                "timer: after two calls invocations = " + replacement.getInvocations());
        check(failures, replacement.getTotalNanos() >= 0, "timer: negative total nanos");
    }

    // ---- helpers ----

    private static EvaluatedCandidate member(long fitness) {
        return new EvaluatedCandidate(new int[] {0, 1, 2, 3}, fitness);
    }

    private static Population population(long... fitnesses) {
        List<EvaluatedCandidate> list = new ArrayList<>(fitnesses.length);
        for (long f : fitnesses) {
            list.add(member(f));
        }
        return new Population(list);
    }

    private static AlgorithmContext context(long seed) {
        QAPInstance instance = new QAPInstance("synthetic4", new int[4][4], new int[4][4]);
        return new AlgorithmContext(instance, new RandomSource(seed).derive(0));
    }

    private static void check(List<String> failures, boolean ok, String message) {
        if (!ok) {
            failures.add(message);
        }
    }
}
