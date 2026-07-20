package qapSolver.Reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Shared directory-listing helper for the repositories. */
final class RepositoryFiles {

    private RepositoryFiles() {
    }

    /** Sorted names (file name minus suffix) of all files ending in suffix. */
    static List<String> names(Path directory, String suffix) throws IOException {
        try (Stream<Path> s = Files.list(directory)) {
            return s.map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(suffix))
                    .map(f -> f.substring(0, f.length() - suffix.length()))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
