package qapSolver.Reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import qapSolver.Model.QAPInstance;
import qapSolver.Model.SampleQAPSolution;

/**
 * Plain main-class test harness: reads every .dat and .sln in the QAPLIB
 * mirror and proves the readers correct by reproducing each .sln objective
 * value from its permutation under the standard convention — the reader
 * auto-normalizes indexing (0-based tai40a) and orientation (the eight
 * inverse-convention files), so 127 values must match directly; kra32 must
 * evaluate to the true optimum 88700 (its header 88900 is a known typo).
 *
 * Usage: InstanceReaderTest [datDir] [slnDir]
 * (defaults: QAPData/qapdata, QAPData/qapsoln relative to the working dir).
 * Exit code 0 = full pass, 1 = any failure.
 */
public final class InstanceReaderTest {

    private static final int EXPECTED_DAT = 136;
    private static final int EXPECTED_SLN = 128;

    /** Instances with no .sln file in the deposit. */
    private static final Set<String> EXPECTED_MISSING_SLN = Set.of(
            "esc32a", "esc32b", "esc32c", "esc32d", "esc32h", "esc64a", "tai10a", "tai10b");

    /** kra32.sln header says 88900 (typo); its permutation evaluates to the true optimum. */
    private static final long KRA32_TRUE_VALUE = 88700L;

    private InstanceReaderTest() {
    }

    public static void main(String[] args) throws IOException {
        Path datDir = Paths.get(args.length > 0 ? args[0] : "QAPData/qapdata");
        Path slnDir = Paths.get(args.length > 1 ? args[1] : "QAPData/qapsoln");

        List<String> failures = new ArrayList<>();

        Map<String, QAPInstance> instances = readAllInstances(datDir, failures);

        int direct = 0;
        boolean kra32Confirmed = false;
        List<Path> slnFiles = listFiles(slnDir, ".sln");
        if (slnFiles.size() != EXPECTED_SLN) {
            failures.add("expected " + EXPECTED_SLN + " .sln files, found " + slnFiles.size());
        }

        for (Path file : slnFiles) {
            String name = file.getFileName().toString();
            name = name.substring(0, name.length() - ".sln".length());
            QAPInstance inst = instances.get(name);
            if (inst == null) {
                failures.add(name + ": .sln has no matching .dat");
                continue;
            }
            SampleQAPSolution sol;
            try {
                // size agreement .dat vs .sln is enforced inside the reader
                sol = SolutionReader.read(file, inst);
            } catch (IOException e) {
                failures.add(e.getMessage());
                continue;
            }

            long costDirect = cost(inst.getMatrixA(), inst.getMatrixB(), sol.getPermutation());
            long costInverse = cost(inst.getMatrixB(), inst.getMatrixA(), sol.getPermutation());

            if (sol.getInstanceName().equals("kra32")) {
                if (costDirect == KRA32_TRUE_VALUE || costInverse == KRA32_TRUE_VALUE) {
                    kra32Confirmed = true;
                } else {
                    failures.add("kra32: expected " + KRA32_TRUE_VALUE + " (header-typo quirk), got direct="
                            + costDirect + " inverse=" + costInverse + " header=" + sol.getValue());
                }
            } else if (costDirect == sol.getValue()) {
                direct++;
            } else if (costInverse == sol.getValue()) {
                failures.add(sol.getInstanceName() + ": matches only under inverse convention — reader "
                        + "failed to auto-normalize orientation");
            } else {
                failures.add(sol.getInstanceName() + ": value " + sol.getValue() + " not reproduced (direct="
                        + costDirect + ", inverse=" + costInverse + ")");
            }
        }

        checkMissingSln(instances, slnFiles, failures);

        for (String f : failures) {
            System.out.println("FAIL: " + f);
        }
        System.out.println();
        System.out.println(".dat files read OK : " + instances.size() + "/" + EXPECTED_DAT);
        System.out.println(".sln values reproduced: " + (direct + (kra32Confirmed ? 1 : 0))
                + "/" + EXPECTED_SLN + " (" + direct + " standard-convention after normalization, kra32->"
                + KRA32_TRUE_VALUE + " " + (kra32Confirmed ? "confirmed" : "NOT confirmed") + ")");
        System.out.println("RESULT: " + (failures.isEmpty() ? "PASS" : failures.size() + " FAILURE(S)"));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static Map<String, QAPInstance> readAllInstances(Path datDir, List<String> failures)
            throws IOException {
        Map<String, QAPInstance> instances = new TreeMap<>();
        List<Path> datFiles = listFiles(datDir, ".dat");
        if (datFiles.size() != EXPECTED_DAT) {
            failures.add("expected " + EXPECTED_DAT + " .dat files, found " + datFiles.size());
        }
        for (Path file : datFiles) {
            try {
                QAPInstance inst = InstanceReader.read(file);
                instances.put(inst.getName(), inst);
            } catch (IOException e) {
                failures.add(e.getMessage());
            }
        }
        return instances;
    }

    private static void checkMissingSln(Map<String, QAPInstance> instances, List<Path> slnFiles,
            List<String> failures) {
        Set<String> slnNames = slnFiles.stream()
                .map(p -> {
                    String f = p.getFileName().toString();
                    return f.substring(0, f.length() - ".sln".length());
                })
                .collect(Collectors.toSet());
        Set<String> missing = new TreeSet<>(instances.keySet());
        missing.removeAll(slnNames);
        if (!missing.equals(EXPECTED_MISSING_SLN)) {
            failures.add("instances without .sln " + missing + " != expected " + EXPECTED_MISSING_SLN);
        }
    }

    private static List<Path> listFiles(Path dir, String suffix) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    /**
     * Test-local objective: Σᵢ Σⱼ a[i][j]·b[p(i)][p(j)] over all n² pairs,
     * diagonals included, accumulated in long. The inverse convention is
     * cost(b, a, p) by symmetry of the double sum. (The production evaluator
     * will be its own module later.)
     */
    private static long cost(int[][] a, int[][] b, int[] p) {
        long total = 0;
        int n = p.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                total += (long) a[i][j] * b[p[i]][p[j]];
            }
        }
        return total;
    }
}
