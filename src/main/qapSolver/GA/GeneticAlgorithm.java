package qapSolver.GA;

import java.util.ArrayList;
import java.util.List;

import qapSolver.Engine.AlgorithmContext;
import qapSolver.Engine.AlgorithmEngine;
import qapSolver.Engine.Candidate;
import qapSolver.Engine.EvaluatedCandidate;
import qapSolver.Engine.FitnessEvaluator;
import qapSolver.Engine.Population;
import qapSolver.Engine.TerminationCriterion;

/**
 * The generational memetic GA: the composition of the step slots into the
 * canonical cycle. Swapping behavior means constructing it with different
 * step objects; a differently <em>shaped</em> cycle (pure steady-state, SA)
 * is a sibling of this class under {@link AlgorithmEngine}, which is why this
 * one is {@code final}.
 *
 * <p>One generation ({@code doStep}), letters as in the design discussion:
 * <ol>
 * <li>(g) elites extracted;</li>
 * <li>(c) {@code 2·offspringCount} parents selected in one bulk call;</li>
 * <li>(d) consecutive pairs bred: with probability {@code crossoverRate} the
 *     pair recombines, otherwise both parents clone through via
 *     {@code toCandidate()}; children collected up to
 *     {@code offspringCount}, surplus truncated;</li>
 * <li>(e) every child mutated (rates are the operator's own);</li>
 * <li>(b) the batch evaluated, results offered to the incumbent;</li>
 * <li>(h) the batch improved, results offered again;</li>
 * <li>(f) replacement forms the successor population;</li>
 * <li>(g) elites reinserted.</li>
 * </ol>
 * Initialization is (a) then (b): the initializer's batch — whose size fixes
 * the population size μ for the whole run — evaluated into generation 0.
 * Termination (i) lives in the {@link AlgorithmEngine} loop.
 *
 * <p>The engine enforces the step contracts as it goes: batch results must
 * match input sizes, crossover must yield at least one child, replacement
 * and elite reinsertion must preserve μ — violations throw
 * {@code IllegalStateException} immediately. The incumbent has a single
 * writer: only this class offers candidates, after evaluation and after
 * improvement.
 *
 * <p>Numeric configuration is deliberately minimal: {@code offspringCount}
 * (λ) and {@code crossoverRate} (probability a pair recombines) — everything
 * else belongs to the step objects.
 */
public final class GeneticAlgorithm extends AlgorithmEngine {

    private final PopulationInitializer initializer;
    private final FitnessEvaluator evaluator;
    private final ParentSelector parentSelector;
    private final CrossoverOperator crossover;
    private final MutationOperator mutation;
    private final ReplacementStrategy replacement;
    private final ElitePreserver elitePreserver;
    private final LocalImprovement localImprovement;
    private final int offspringCount;
    private final double crossoverRate;

    private Population population;

    /**
     * Wires the cycle. All steps are required — "off" is a NoOp step (empty
     * elite list, identity improvement), never a null.
     *
     * @param offspringCount λ, the children bred per generation (≥ 1)
     * @param crossoverRate probability in [0,1] that a parent pair recombines
     *        rather than cloning through
     */
    public GeneticAlgorithm(AlgorithmContext context, TerminationCriterion termination,
            PopulationInitializer initializer, FitnessEvaluator evaluator, ParentSelector parentSelector,
            CrossoverOperator crossover, MutationOperator mutation, ReplacementStrategy replacement,
            ElitePreserver elitePreserver, LocalImprovement localImprovement,
            int offspringCount, double crossoverRate) {
        super(context, termination);
        requireStep(initializer, "initializer");
        requireStep(evaluator, "evaluator");
        requireStep(parentSelector, "parentSelector");
        requireStep(crossover, "crossover");
        requireStep(mutation, "mutation");
        requireStep(replacement, "replacement");
        requireStep(elitePreserver, "elitePreserver");
        requireStep(localImprovement, "localImprovement");
        if (offspringCount < 1) {
            throw new IllegalArgumentException("offspringCount must be >= 1: " + offspringCount);
        }
        if (crossoverRate < 0.0 || crossoverRate > 1.0) {
            throw new IllegalArgumentException("crossoverRate must be in [0,1]: " + crossoverRate);
        }
        this.initializer = initializer;
        this.evaluator = evaluator;
        this.parentSelector = parentSelector;
        this.crossover = crossover;
        this.mutation = mutation;
        this.replacement = replacement;
        this.elitePreserver = elitePreserver;
        this.localImprovement = localImprovement;
        this.offspringCount = offspringCount;
        this.crossoverRate = crossoverRate;
    }

    @Override
    public Population getPopulation() {
        if (population == null) {
            throw new IllegalStateException("engine not initialized — call initialize() first");
        }
        return population;
    }

    @Override
    protected void doInitialize() {
        List<Candidate> initial = initializer.initialize(context);
        if (initial == null || initial.isEmpty()) {
            throw new IllegalStateException("initializer returned no candidates");
        }
        List<EvaluatedCandidate> evaluated = evaluator.evaluate(initial, context);
        requireBatch(evaluated, initial.size(), "evaluator");
        population = new Population(evaluated);
        offerAll(evaluated);
    }

    @Override
    protected void doStep() {
        List<EvaluatedCandidate> elites = elitePreserver.extract(population, context);
        if (elites == null) {
            throw new IllegalStateException("elite preserver returned null");
        }
        List<EvaluatedCandidate> parents = parentSelector.selectParents(population, 2 * offspringCount, context);
        requireBatch(parents, 2 * offspringCount, "parent selector");
        List<Candidate> bred = breed(parents);
        for (int i = 0; i < bred.size(); i++) {
            mutation.mutate(bred.get(i), context);
        }
        List<EvaluatedCandidate> offspring = evaluator.evaluate(bred, context);
        requireBatch(offspring, bred.size(), "evaluator");
        offerAll(offspring);
        List<EvaluatedCandidate> improved = localImprovement.improve(offspring, context);
        requireBatch(improved, offspring.size(), "local improvement");
        offerAll(improved);
        int mu = population.size();
        Population next = replacement.replace(population, improved, context);
        if (next == null || next.size() != mu) {
            throw new IllegalStateException("replacement must preserve the population size " + mu
                    + ", returned " + (next == null ? "null" : String.valueOf(next.size())));
        }
        population = next;
        elitePreserver.reinsert(population, elites, context);
        if (population.size() != mu) {
            throw new IllegalStateException("elite preserver changed the population size: "
                    + mu + " -> " + population.size());
        }
    }

    /**
     * (d): pairs consecutive parents, draws the rate per pair, collects
     * children until λ is reached (surplus from multi-child operators is
     * truncated deterministically).
     */
    private List<Candidate> breed(List<EvaluatedCandidate> parents) {
        List<Candidate> children = new ArrayList<>(offspringCount);
        for (int i = 0; i + 1 < parents.size() && children.size() < offspringCount; i += 2) {
            EvaluatedCandidate p1 = parents.get(i);
            EvaluatedCandidate p2 = parents.get(i + 1);
            if (context.getRandomizer().nextDouble() < crossoverRate) {
                List<Candidate> pair = crossover.recombine(p1, p2, context);
                if (pair == null || pair.isEmpty()) {
                    throw new IllegalStateException("crossover returned no children");
                }
                for (int c = 0; c < pair.size() && children.size() < offspringCount; c++) {
                    children.add(pair.get(c));
                }
            } else {
                children.add(p1.toCandidate());
                if (children.size() < offspringCount) {
                    children.add(p2.toCandidate());
                }
            }
        }
        if (children.size() != offspringCount) {
            throw new IllegalStateException("bred " + children.size()
                    + " children, expected offspringCount=" + offspringCount);
        }
        return children;
    }

    private void offerAll(List<EvaluatedCandidate> batch) {
        for (int i = 0; i < batch.size(); i++) {
            context.offerIncumbent(batch.get(i));
        }
    }

    private static void requireStep(Object step, String name) {
        if (step == null) {
            throw new IllegalArgumentException(name + " must be non-null");
        }
    }

    private static void requireBatch(List<?> batch, int expectedSize, String step) {
        if (batch == null || batch.size() != expectedSize) {
            throw new IllegalStateException(step + " returned "
                    + (batch == null ? "null" : batch.size() + " results") + ", expected " + expectedSize);
        }
    }
}
