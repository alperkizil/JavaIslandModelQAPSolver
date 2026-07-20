package qapSolver.Reader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import qapSolver.Model.QAPInstance;
import qapSolver.Model.SampleQAPSolution;

/**
 * Facade pairing the two sides of the QAPLIB deposit: the .dat instances and
 * the .sln sample solutions, matched by shared instance name. Exposes the
 * underlying repositories for their full API instead of duplicating it.
 */
public final class QAPDataset {

    private final InstanceRepository instances;
    private final SolutionRepository solutions;

    public QAPDataset(Path datDirectory, Path slnDirectory) {
        this.instances = new InstanceRepository(datDirectory);
        this.solutions = new SolutionRepository(slnDirectory);
    }

    public InstanceRepository getInstanceRepository() {
        return instances;
    }

    public SolutionRepository getSolutionRepository() {
        return solutions;
    }

    /** One instance by name. @throws IOException if missing or malformed */
    public QAPInstance getInstance(String name) throws IOException {
        return instances.get(name);
    }

    /** The sample solution matching an instance name; empty if it has none. */
    public Optional<SampleQAPSolution> findSolution(String name) throws IOException {
        return solutions.find(name);
    }

    /**
     * Every instance in the dataset paired with its sample solution where one
     * exists, sorted by name. This reads everything — intended for sweeps
     * (benchmarks, verification), not hot paths.
     */
    public List<Pair> pairs() throws IOException {
        List<Pair> result = new ArrayList<>();
        for (String name : instances.listNames()) {
            result.add(new Pair(instances.get(name), solutions.find(name).orElse(null)));
        }
        return result;
    }

    /** An instance together with its sample solution, if it has one. */
    public static final class Pair {

        private final QAPInstance instance;
        private final SampleQAPSolution solution;

        Pair(QAPInstance instance, SampleQAPSolution solution) {
            this.instance = instance;
            this.solution = solution;
        }

        public QAPInstance getInstance() {
            return instance;
        }

        public Optional<SampleQAPSolution> getSolution() {
            return Optional.ofNullable(solution);
        }
    }
}
