package qapSolver.Reader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import qapSolver.Model.Permutations;
import qapSolver.Model.QAPInstance;
import qapSolver.Model.SampleQAPSolution;
import qapSolver.Objective.ObjectiveFunction;

/**
 * Reads a QAPLIB .sln file: the size n and the objective value on the first
 * line, followed by the n-entry permutation, as whitespace- and/or
 * comma-separated integers (some files use commas). Strict token count:
 * exactly 2 + n tokens. Two normalizations are applied so every solution in
 * memory obeys the same conventions:
 *
 * 1. Indexing: entries spanning 1..n are shifted to 0-based; a file already
 *    spanning 0..n−1 (tai40a) is kept; anything else is rejected.
 * 2. Orientation: if the claimed value does not reproduce under the standard
 *    convention Σ A[i][j]·B[p(i)][p(j)] but does on the inverted permutation,
 *    the inversion is stored (the eight known inverse-convention QAPLIB files:
 *    esc128, kra30a/b, ste36c, tai60a, tai80a, tho30, tho150). A file matching
 *    neither orientation (kra32's typo header) is stored as read, so it
 *    surfaces as isValid()=false. A future .sln writer must invert back for
 *    those eight files to match their on-disk convention.
 *
 * Constructing a solution verifies it against its instance (see
 * {@link qapSolver.Model.QAPSolution}), so the matching QAPInstance is
 * required; the file name must match the instance name.
 */
public final class SolutionReader {

    private SolutionReader() {
    }

    /**
     * Reads one solution for the given instance; the solution name is the
     * file name without its .sln extension and must equal the instance name.
     *
     * @throws IOException on I/O failure or any format violation
     */
    public static SampleQAPSolution read(Path file, QAPInstance instance) throws IOException {
        if (instance == null) {
            throw new IOException(file + ": instance must be non-null");
        }
        String name = stripExtension(file.getFileName().toString());
        if (!name.equals(instance.getName())) {
            throw new IOException(file + ": file name '" + name + "' does not match instance '"
                    + instance.getName() + "'");
        }
        String content = new String(Files.readAllBytes(file), StandardCharsets.US_ASCII).trim();
        if (content.isEmpty()) {
            throw new IOException(file + ": file is empty");
        }
        String[] tokens = content.split("[\\s,]+");

        if (tokens.length < 2) {
            throw new IOException(file + ": missing n / value header");
        }
        int n = parseInt(tokens, 0, file);
        if (n <= 0) {
            throw new IOException(file + ": invalid size n=" + n);
        }
        if (n != instance.getSize()) {
            throw new IOException(file + ": declares n=" + n + " but instance has n="
                    + instance.getSize());
        }
        long value = parseLong(tokens, 1, file);
        if (tokens.length != 2 + n) {
            throw new IOException(file + ": expected " + (2 + n) + " tokens for n=" + n
                    + ", found " + tokens.length);
        }

        int[] perm = new int[n];
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            perm[i] = parseInt(tokens, 2 + i, file);
            min = Math.min(min, perm[i]);
            max = Math.max(max, perm[i]);
        }
        if (min == 1 && max == n) {
            for (int i = 0; i < n; i++) {
                perm[i]--;
            }
        } else if (!(min == 0 && max == n - 1)) {
            throw new IOException(file + ": permutation entries span [" + min + ", " + max
                    + "], neither 1-based nor 0-based for n=" + n);
        }

        if (ObjectiveFunction.evaluate(instance, perm) != value) {
            int[] inverted = Permutations.inverseOf(perm);
            if (ObjectiveFunction.evaluate(instance, inverted) == value) {
                perm = inverted;
            }
        }

        try {
            return new SampleQAPSolution(instance, value, perm);
        } catch (IllegalArgumentException e) {
            throw new IOException(file + ": " + e.getMessage(), e);
        }
    }

    private static int parseInt(String[] tokens, int index, Path file) throws IOException {
        try {
            return Integer.parseInt(tokens[index]);
        } catch (NumberFormatException e) {
            throw new IOException(file + ": token " + index + " is not an integer: '"
                    + tokens[index] + "'", e);
        }
    }

    private static long parseLong(String[] tokens, int index, Path file) throws IOException {
        try {
            return Long.parseLong(tokens[index]);
        } catch (NumberFormatException e) {
            throw new IOException(file + ": token " + index + " is not an integer: '"
                    + tokens[index] + "'", e);
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
