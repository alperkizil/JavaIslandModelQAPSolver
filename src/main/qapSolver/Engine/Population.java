package qapSolver.Engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The pool of evaluated candidates a generation works on. Holding only
 * {@link EvaluatedCandidate} is the type-state boundary in action: anything
 * inside a Population can be selected, ranked and replaced, because its
 * fitness is known by construction. The unevaluated batch between breeding
 * and evaluation is a plain {@code List<Candidate>} — never a Population.
 *
 * <p>The container is mutable (replacement, elitism and future migration edit
 * it in place via {@link #set}); the members themselves are immutable, so
 * sharing them across populations and steps is safe. Confined to its engine's
 * thread, like everything around it.
 *
 * <p>Populations are where steps hand work to each other, so unlike the
 * hot-path candidate constructors they enforce their invariants: no null
 * members, and every member the same problem size as the first. Minimization
 * throughout: {@link #best()} is the lowest fitness, ties broken by first
 * index — deterministic, no sorting.
 */
public final class Population {

    private final List<EvaluatedCandidate> members;

    /** An empty population expecting about {@code initialCapacity} members. */
    public Population(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("initialCapacity must be >= 0: " + initialCapacity);
        }
        this.members = new ArrayList<>(initialCapacity);
    }

    /**
     * A population of the given members (membership is copied, so later
     * changes to the argument list don't affect this population; the members
     * themselves are shared — they are immutable).
     */
    public Population(List<EvaluatedCandidate> initialMembers) {
        if (initialMembers == null) {
            throw new IllegalArgumentException("initialMembers must be non-null");
        }
        this.members = new ArrayList<>(initialMembers.size());
        for (int i = 0; i < initialMembers.size(); i++) {
            add(initialMembers.get(i));
        }
    }

    /** Number of members. */
    public int size() {
        return members.size();
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    /** The member at {@code index}. */
    public EvaluatedCandidate get(int index) {
        return members.get(index);
    }

    /** Appends a member. */
    public void add(EvaluatedCandidate candidate) {
        requireCompatible(candidate);
        members.add(candidate);
    }

    /**
     * Replaces the member at {@code index}, returning the displaced member
     * (steady-state replacement wants to know whom it evicted).
     */
    public EvaluatedCandidate set(int index, EvaluatedCandidate candidate) {
        requireCompatible(candidate);
        return members.set(index, candidate);
    }

    /** Index of the best (lowest-fitness) member; first index on ties. */
    public int bestIndex() {
        return extremeIndex(true);
    }

    /** Index of the worst (highest-fitness) member; first index on ties. */
    public int worstIndex() {
        return extremeIndex(false);
    }

    /** The best (lowest-fitness) member. */
    public EvaluatedCandidate best() {
        return members.get(bestIndex());
    }

    /** The worst (highest-fitness) member. */
    public EvaluatedCandidate worst() {
        return members.get(worstIndex());
    }

    private int extremeIndex(boolean lowest) {
        if (members.isEmpty()) {
            throw new IllegalStateException("population is empty");
        }
        int extreme = 0;
        long extremeFitness = members.get(0).getFitness();
        for (int i = 1; i < members.size(); i++) {
            long fitness = members.get(i).getFitness();
            if (lowest ? fitness < extremeFitness : fitness > extremeFitness) {
                extreme = i;
                extremeFitness = fitness;
            }
        }
        return extreme;
    }

    private void requireCompatible(EvaluatedCandidate candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must be non-null");
        }
        if (!members.isEmpty() && candidate.size() != members.get(0).size()) {
            throw new IllegalArgumentException("candidate size " + candidate.size()
                    + " != population size n=" + members.get(0).size());
        }
    }

    @Override
    public String toString() {
        if (members.isEmpty()) {
            return "Population(empty)";
        }
        return "Population(size=" + members.size() + ", best=" + best().getFitness()
                + ", worst=" + worst().getFitness() + ")";
    }
}
