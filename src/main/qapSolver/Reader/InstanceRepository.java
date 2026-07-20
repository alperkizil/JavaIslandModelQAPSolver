package qapSolver.Reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Name-based access to a directory of QAPLIB .dat instances: one by name, a
 * whole family, or all of them. Collection-level concerns only — file parsing
 * stays in {@link InstanceReader}. No caching: every call reads fresh from
 * disk (a solver run loads few instances; callers needing reuse hold the
 * reference themselves). All listings are sorted by name for determinism.
 */
public final class InstanceRepository {

    private final Path directory;

    public InstanceRepository(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must be non-null");
        }
        this.directory = directory;
    }

    /**
     * Reads one instance by name (file name without the .dat extension).
     *
     * @throws IOException if the instance does not exist or is malformed
     */
    public QapInstance get(String name) throws IOException {
        Path file = directory.resolve(name + ".dat");
        if (!Files.isRegularFile(file)) {
            throw new IOException("no such instance '" + name + "' in " + directory);
        }
        return InstanceReader.read(file);
    }

    /**
     * Reads every instance of one family, e.g. "tai" → tai10a … tai256c.
     * The family of an instance is its alphabetic prefix before the first
     * digit (same convention as qaplib_characteristics.csv); the match is
     * exact, so "t" matches nothing. Unknown family → empty list.
     */
    public List<QapInstance> getFamily(String family) throws IOException {
        if (family == null || family.isEmpty()) {
            throw new IllegalArgumentException("family must be non-empty");
        }
        List<QapInstance> result = new ArrayList<>();
        for (String name : listNames()) {
            if (familyOf(name).equals(family)) {
                result.add(get(name));
            }
        }
        return result;
    }

    /** Reads all instances in the directory. */
    public List<QapInstance> getAll() throws IOException {
        List<QapInstance> result = new ArrayList<>();
        for (String name : listNames()) {
            result.add(get(name));
        }
        return result;
    }

    /** Sorted instance names present in the directory; loads nothing. */
    public List<String> listNames() throws IOException {
        try (Stream<Path> s = Files.list(directory)) {
            return s.map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".dat"))
                    .map(f -> f.substring(0, f.length() - ".dat".length()))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /** Alphabetic prefix before the first non-letter character. */
    public static String familyOf(String name) {
        int i = 0;
        while (i < name.length() && Character.isLetter(name.charAt(i))) {
            i++;
        }
        return name.substring(0, i);
    }
}
