package qapSolver.Reader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import qapSolver.Model.QAPInstance;

/**
 * Reads a QAPLIB .dat file: the size n followed by the n-by-n matrices A and B,
 * as whitespace- and/or comma-separated integers. Strict by design: the file
 * must contain exactly 1 + 2n² numeric tokens, so any format drift fails loudly
 * instead of producing a silently wrong instance.
 */
public final class InstanceReader {

    private InstanceReader() {
    }

    /**
     * Reads one instance; the instance name is the file name without its
     * .dat extension.
     *
     * @throws IOException on I/O failure or any format violation
     */
    public static QAPInstance read(Path file) throws IOException {
        String name = stripExtension(file.getFileName().toString());
        String content = new String(Files.readAllBytes(file), StandardCharsets.US_ASCII).trim();
        if (content.isEmpty()) {
            throw new IOException(file + ": file is empty");
        }
        String[] tokens = content.split("[\\s,]+");

        int n = parseInt(tokens, 0, file);
        if (n <= 0) {
            throw new IOException(file + ": invalid size n=" + n);
        }
        long expected = 1 + 2L * n * n;
        if (tokens.length != expected) {
            throw new IOException(file + ": expected " + expected + " tokens for n=" + n
                    + ", found " + tokens.length);
        }

        int[][] a = readMatrix(tokens, 1, n, file);
        int[][] b = readMatrix(tokens, 1 + n * n, n, file);
        return new QAPInstance(name, a, b);
    }

    private static int[][] readMatrix(String[] tokens, int offset, int n, Path file) throws IOException {
        int[][] m = new int[n][n];
        int k = offset;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                m[i][j] = parseInt(tokens, k++, file);
            }
        }
        return m;
    }

    private static int parseInt(String[] tokens, int index, Path file) throws IOException {
        try {
            return Integer.parseInt(tokens[index]);
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
