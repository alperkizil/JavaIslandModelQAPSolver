package qapSolver.Reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import qapSolver.Model.SampleQAPSolution;

/**
 * Name-based access to a directory of QAPLIB .sln reference solutions,
 * mirroring {@link InstanceRepository}. Matching a solution to its instance
 * is by shared name; not every instance has one (eight do not), hence
 * {@link #find(String)}. Constructing a solution verifies it against its
 * instance, so this repository needs the instance side too. File parsing
 * stays in {@link SolutionReader}; no caching; all listings sorted by name.
 */
public final class SolutionRepository {

    private final Path directory;
    private final InstanceRepository instances;

    public SolutionRepository(Path directory, InstanceRepository instances) {
        if (directory == null || instances == null) {
            throw new IllegalArgumentException("directory and instances must be non-null");
        }
        this.directory = directory;
        this.instances = instances;
    }

    /**
     * Reads one solution by instance name (file name without the .sln
     * extension).
     *
     * @throws IOException if the solution does not exist or is malformed
     */
    public SampleQAPSolution get(String name) throws IOException {
        Path file = directory.resolve(name + ".sln");
        if (!Files.isRegularFile(file)) {
            throw new IOException("no such solution '" + name + "' in " + directory);
        }
        return SolutionReader.read(file, instances.get(name));
    }

    /**
     * Like {@link #get(String)} but returns empty if the instance has no
     * .sln file. A file that exists but is malformed still throws.
     */
    public Optional<SampleQAPSolution> find(String name) throws IOException {
        if (!Files.isRegularFile(directory.resolve(name + ".sln"))) {
            return Optional.empty();
        }
        return Optional.of(get(name));
    }

    /**
     * Reads every solution of one family (alphabetic prefix before the first
     * digit, exact match — see {@link InstanceRepository#familyOf(String)}).
     * Unknown family → empty list.
     */
    public List<SampleQAPSolution> getFamily(String family) throws IOException {
        if (family == null || family.isEmpty()) {
            throw new IllegalArgumentException("family must be non-empty");
        }
        List<SampleQAPSolution> result = new ArrayList<>();
        for (String name : listNames()) {
            if (InstanceRepository.familyOf(name).equals(family)) {
                result.add(get(name));
            }
        }
        return result;
    }

    /** Reads all solutions in the directory. */
    public List<SampleQAPSolution> getAll() throws IOException {
        List<SampleQAPSolution> result = new ArrayList<>();
        for (String name : listNames()) {
            result.add(get(name));
        }
        return result;
    }

    /** Sorted solution names present in the directory; loads nothing. */
    public List<String> listNames() throws IOException {
        return RepositoryFiles.names(directory, ".sln");
    }
}
