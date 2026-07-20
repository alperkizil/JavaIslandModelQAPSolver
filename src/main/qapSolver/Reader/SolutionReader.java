package qapSolver.Reader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import qapSolver.Model.SampleQAPSolution;

/**
 * Reads a QAPLIB .sln file: the size n and the objective value on the first
 * line, followed by the n-entry permutation, as whitespace- and/or
 * comma-separated integers (some files use commas). Permutations are
 * normalized to 0-based indexing: a file whose entries span 1..n is shifted
 * down by one, a file spanning 0..n−1 (tai40a) is kept as-is; anything else
 * is rejected. Strict token count: exactly 2 + n tokens.
 */
public final class SolutionReader {

    private SolutionReader() {
    }

    /**
     * Reads one solution; the name is the file name without its .sln extension.
     *
     * @throws IOException on I/O failure or any format violation
     */
    public static SampleQAPSolution read(Path file) throws IOException {
        String name = stripExtension(file.getFileName().toString());
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

        try {
            return new SampleQAPSolution(name, value, perm);
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
